# SYNCORA

SYNCORA is a collaboration platform built with a Spring Boot backend and a React + TypeScript frontend. Modules 1 through 4 implement account authentication, workspace/team management, projects, and project tasks.

## Requirements

- Java 21
- Node.js 22+ and npm
- Docker with the Docker Compose plugin

## PostgreSQL

Start the existing PostgreSQL 16 service from the project root:

```powershell
docker compose up -d postgres
docker compose ps
```

The database listens on `localhost:5433` and uses the existing development database `syncora_db`. Credentials are configured in `docker-compose.yml`; do not reuse these development values in production.

On backend startup, Flyway applies pending migrations (`V1__create_users.sql` through `V4__create_tasks_and_task_comments.sql`). Hibernate remains configured with `ddl-auto=validate`, so it checks the migrated schema and does not create or update tables.

## Backend

In PowerShell, create a local JWT signing secret in the current terminal and start the backend:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
cd backend
.\gradlew.bat clean test
.\gradlew.bat bootRun
```

Set `JWT_SECRET` again in any new terminal before starting the application. The secret must be a Base64-encoded key of at least 256 bits. Gradle's `bootRun` and test tasks use UTC to avoid PostgreSQL timezone issues seen on this Windows development machine.

The database defaults can be overridden with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. The default URL is `jdbc:postgresql://localhost:5433/syncora_db`. The local CORS origin defaults to `http://localhost:5173` and can be overridden using `CORS_ALLOWED_ORIGIN`.

## Frontend

In a second PowerShell terminal:

```powershell
cd frontend
npm ci
npm test
npm run build
npm run dev
```

Vite runs at `http://localhost:5173`. The frontend API base URL defaults to `http://localhost:8080`; set `VITE_API_BASE_URL` to override it.

## Authentication and API

All API routes use the `/api/v1` prefix:

| Method | Endpoint | Access | Description |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Public | Create an account and return a JWT |
| `POST` | `/api/v1/auth/login` | Public | Authenticate and return a JWT |
| `GET` | `/api/v1/users/me` | Bearer JWT | Read the current profile |
| `PUT` | `/api/v1/users/me` | Bearer JWT | Update name, bio, and profile image URL |
| `PUT` | `/api/v1/users/me/password` | Bearer JWT | Change the password (returns `204`) |

Use the returned token as `Authorization: Bearer <token>` for protected endpoints. Public registration always assigns the `MEMBER` role; the API has no client-controlled role or ID fields. Passwords are hashed with BCrypt and are never included in responses. JWTs are signed with the environment-provided secret and expire after 15 minutes.

The frontend keeps the access token only in JavaScript memory and does not use local storage, session storage, or cookies. This reduces persistence and exposure if the browser profile is shared, but a page refresh requires the user to sign in again. Client logout clears the in-memory token; it does not revoke a token already issued, which remains valid until its short expiry.

## Structure

### Backend (`backend/src/main`)

```text
java/com/example/demo/
├── config/       Security and CORS configuration
├── controller/   Authentication, user, workspace, project, task, and comment controllers
├── dto/
│   ├── request/  Validated authentication, workspace, project, task, and comment requests
│   └── response/ Typed authentication, user, workspace, project, task, and API error responses
├── entity/       User, Workspace, WorkspaceMember, Project, ProjectMember, Task, TaskComment
├── enums/        Global/workspace/project roles and project/task statuses and priorities
├── exception/    Domain exceptions and centralized API error handling
├── repository/   User, workspace, project, task, and comment repositories
├── security/     JWT filter/service, user details, BCrypt password policy
└── service/      Authentication, user, workspace, project, task, comment, and authorization services

resources/
├── application.properties
└── db/migration/V1__create_users.sql through V4__create_tasks_and_task_comments.sql
```

### Frontend (`frontend/src`)

```text
src/
├── api/          Axios client and in-memory access-token attachment
├── auth/         Authentication context
├── components/   Shared forms, authentication layout, and workspace header
├── pages/        Login, registration, profile, workspace, project, and task pages
├── services/     Typed authentication, user, workspace, project, and task API calls
├── test/         Test setup
├── types/        Authentication, workspace, project, task, and comment API types
├── utils/        API error-message handling
├── App.tsx       Routes and protected-route behavior
├── App.test.tsx, WorkspacePages.test.tsx, ProjectPages.test.tsx, TaskPages.test.tsx
└── main.tsx      React entry point
```

## Database schema

Flyway creates:

- `users`: UUID primary key, unique email, BCrypt password hash, profile fields, active flag, role, and UTC timestamps. A database check constraint restricts roles to `ADMIN`, `MANAGER`, or `MEMBER`.
- `workspaces`: UUID primary key, unique slug, owner foreign key to `users`, details, and timestamps.
- `workspace_members`: UUID keys to the workspace and user, workspace role, and joined timestamp. A unique constraint prevents duplicate memberships; deleting a workspace cascades to its memberships.
- `projects`: UUID primary key, workspace and creator foreign keys, workspace-unique project key, status, optional dates, timestamps, and archive timestamp. Deleting a workspace cascades to its projects.
- `project_members`: UUID key, project/workspace/user relationships, project role, and joined timestamp. Membership is unique per project/user; composite foreign keys ensure a project member is a member of that project's workspace. Deleting a project or workspace membership cascades to the project membership.
- `tasks`: UUID key, project and user relationships, server-generated key unique within its project, status, priority, optional assignee and due date, and lifecycle timestamps. Deleting a project cascades to its tasks; removing an assigned project member clears the task assignee.
- `task_comments`: UUID key, task and author relationships, content, and timestamps. Deleting a task cascades to its comments.
- `flyway_schema_history`: Flyway's migration tracking table.

Indexes support owner and membership queries. The database is not reset when tests run. Hibernate only validates the schema after Flyway migrations.

## Verification

The backend tests use the configured PostgreSQL database and cover Module 1 authentication, Flyway V1-V4, workspace authorization, project management, and task/comment authorization and lifecycle. Frontend tests cover authentication, workspaces, projects, tasks, comments, and loading/error states. Run both suites with the commands above.

## Current scope and limitations

- Logout is client-side only; there is no token blacklist or refresh-token flow.
- Tokens are not persisted across browser reloads.
- Profile images are supplied as a URL; file upload is not implemented.
- Account recovery, email verification, and global role administration are not part of this module.
- Tasks do not include notifications, real-time collaboration, file uploads, or task-specific custom fields.

## Module 2: Workspaces and teams

### Backend and database

Module 2 adds these backend packages and classes under the existing `com.example.demo` package:

- `entity`: `Workspace`, `WorkspaceMember`
- `enums`: `WorkspaceRole` (`OWNER`, `ADMIN`, `MEMBER`)
- `repository`: `WorkspaceRepository`, `WorkspaceMemberRepository`
- `service`: `WorkspaceService`, `WorkspaceMemberService`, `WorkspaceAuthorizationService`
- `controller`: `WorkspaceController`, `WorkspaceMemberController`
- `dto/request` and `dto/response`: validated workspace and membership API contracts

Flyway migration `V2__create_workspaces.sql` creates:

- `workspaces`, with a UUID primary key, unique slug, owner foreign key to `users`, details, and timestamps.
- `workspace_members`, with UUID foreign keys to `workspaces` and `users`, a workspace role, and joined timestamp. A unique constraint prevents the same user joining a workspace twice. Deleting a workspace cascades to its membership rows.

Indexes support owner and membership lookups. The existing V1 migration is unchanged; Hibernate continues to use `ddl-auto=validate`.

### Workspace API

Every endpoint below requires a valid bearer JWT. `{workspaceId}` and `{memberId}` are UUIDs.

| Method | Endpoint | Permission |
| --- | --- | --- |
| `POST` | `/api/v1/workspaces` | Any authenticated user; creator becomes OWNER |
| `GET` | `/api/v1/workspaces` | Lists only the current user’s memberships |
| `GET` | `/api/v1/workspaces/{workspaceId}` | Workspace member |
| `PUT` | `/api/v1/workspaces/{workspaceId}` | OWNER or ADMIN |
| `DELETE` | `/api/v1/workspaces/{workspaceId}` | OWNER only |
| `GET` | `/api/v1/workspaces/{workspaceId}/members` | Workspace member |
| `POST` | `/api/v1/workspaces/{workspaceId}/members` | OWNER or ADMIN; adds an existing account by email as MEMBER |
| `PUT` | `/api/v1/workspaces/{workspaceId}/members/{memberId}/role` | OWNER; ADMIN can promote/demote regular members only |
| `DELETE` | `/api/v1/workspaces/{workspaceId}/members/{memberId}` | OWNER or ADMIN for MEMBER; OWNER only for ADMIN |
| `DELETE` | `/api/v1/workspaces/{workspaceId}/members/me` | ADMIN or MEMBER |

Authorization is enforced in services using the JWT-authenticated principal and workspace membership, not user IDs or global roles supplied by the frontend. Non-members cannot view or modify a workspace. OWNER cannot be removed, demoted, or assigned through member operations. Owners cannot leave until ownership transfer is implemented; the API returns a conflict with an explanatory message. Adding a teammate is direct and does not send an invitation email.

### Frontend

The protected `/workspaces` page lists the current user’s workspaces and opens a create dialog. `/workspaces/:workspaceId` shows workspace details, member count, roles, and controls for permitted member/workspace actions. Typed API calls are in `frontend/src/services/workspaceService.ts`; workspace types are in `frontend/src/types/workspace.ts`. The existing in-memory JWT handling is unchanged.

### Module 2 verification

Backend integration tests cover V2 migration, workspace creation/ownership, scoped listing and access, adding/duplicating members, workspace-specific role permissions, removal and leave rules, and workspace update/deletion. Frontend tests cover listing, empty/loading/error states, workspace creation, member display/addition, and role-based control visibility. Run:

```powershell
cd backend
.\gradlew.bat clean build
cd ..\frontend
npm test
npm run build
```

Ownership transfer and email delivery are out of scope. Workspace slugs remain stable when workspace names change.

## Module 3: Projects

Flyway migration `V3__create_projects.sql` adds the `projects` and `project_members` tables without changing V1 or V2. Project keys are generated from project names and made unique within each workspace. The database enforces workspace/project/user references, one membership per project/user, valid role/status values, and valid date order. Hibernate remains in `validate` mode.

Every project endpoint requires a valid bearer JWT:

| Method | Endpoint | Permission |
| --- | --- | --- |
| `POST` | `/api/v1/workspaces/{workspaceId}/projects` | Workspace member; creator becomes PROJECT_MANAGER |
| `GET` | `/api/v1/workspaces/{workspaceId}/projects` | Workspace member; supports `status`, `search`, `archived`, `page`, and `size` |
| `GET` | `/api/v1/projects/{projectId}` | Workspace OWNER/ADMIN or project member |
| `PUT` | `/api/v1/projects/{projectId}` | PROJECT_MANAGER or workspace OWNER/ADMIN |
| `PUT` | `/api/v1/projects/{projectId}/archive` | PROJECT_MANAGER or workspace OWNER/ADMIN |
| `PUT` | `/api/v1/projects/{projectId}/restore` | PROJECT_MANAGER or workspace OWNER/ADMIN |
| `DELETE` | `/api/v1/projects/{projectId}` | Workspace OWNER/ADMIN |
| `GET` | `/api/v1/projects/{projectId}/members` | Workspace OWNER/ADMIN or project member |
| `POST` | `/api/v1/projects/{projectId}/members` | PROJECT_MANAGER or workspace OWNER/ADMIN; existing workspace member only |
| `PUT` | `/api/v1/projects/{projectId}/members/{memberId}/role` | PROJECT_MANAGER or workspace OWNER/ADMIN |
| `DELETE` | `/api/v1/projects/{projectId}/members/{memberId}` | PROJECT_MANAGER or workspace OWNER/ADMIN |

Workspace OWNER and ADMIN can see and administer every project in their workspace. Other workspace members see only projects they belong to. A project manager cannot be removed or demoted while they are the project's only manager; project role changes and removals are serialized per project. Archive preserves project and membership records; restore returns the project to ACTIVE. Permanent project deletion is restricted to workspace OWNER/ADMIN.

The protected workspace project list is `/workspaces/:workspaceId/projects`; project details and member management are at `/projects/:projectId`. Project forms and filters are responsive and use typed API calls. Authentication continues to use the existing in-memory token.

Run the backend and frontend verification from their respective directories:

```powershell
.\gradlew.bat clean build
npm test
npm run build
```

Project invitations by email, custom project-key editing, project transfer, and restoring a prior pre-archive status are outside Module 3. Restoring an archived project sets its status to ACTIVE.

## Module 4: Tasks and comments

Flyway migration `V4__create_tasks_and_task_comments.sql` creates `tasks` and `task_comments`, including foreign keys, status/priority/content checks, project-scoped task-key uniqueness, and indexes for project listing, filtering, sorting, assignees, due dates, and comments. V1-V3 remain unchanged, and Hibernate stays in `validate` mode.

Every task and comment endpoint requires a valid bearer JWT:

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/v1/projects/{projectId}/tasks` | Create a task; the API generates its task key and derives its creator from the authenticated user |
| `GET` | `/api/v1/projects/{projectId}/tasks` | Paginated list; supports `search`, `status`, `priority`, `assigneeId`, `overdue`, `page`, `size`, `sortBy`, and `sortDirection` |
| `GET` | `/api/v1/tasks/{taskId}` | Read task details |
| `PUT` | `/api/v1/tasks/{taskId}` | Update title, description, and due date |
| `PATCH` | `/api/v1/tasks/{taskId}/status` | Change status; entering DONE records completion time |
| `PATCH` | `/api/v1/tasks/{taskId}/priority` | Change priority |
| `PATCH` | `/api/v1/tasks/{taskId}/assignee` | Assign or unassign an existing project member |
| `DELETE` | `/api/v1/tasks/{taskId}` | Delete a task and its comments (project manager or workspace OWNER/ADMIN) |
| `GET` | `/api/v1/tasks/{taskId}/comments` | List task comments |
| `POST` | `/api/v1/tasks/{taskId}/comments` | Add a comment; author is the authenticated user |
| `PUT` | `/api/v1/tasks/{taskId}/comments/{commentId}` | Edit an own comment or moderate as an authorized manager |
| `DELETE` | `/api/v1/tasks/{taskId}/comments/{commentId}` | Delete an own comment or moderate as an authorized manager |

Services authorize operations through the existing project and workspace membership rules. Workspace OWNER/ADMIN and project members can access project tasks; assignment is restricted to active project members. Project roles do not replace global roles. Task keys are allocated while locking their project, with database uniqueness as a final safeguard. Mutations are rejected for archived projects.

The project details page links to the protected task list at `/projects/:projectId/tasks`; task details and comments are at `/tasks/:taskId`. The UI includes task creation, search and filters, sorting, pagination, assignee/status/priority controls, overdue indicators, and comment editing/deletion controls. The API remains authoritative for all permissions.

Verify from PowerShell in the corresponding directories:

```powershell
cd backend
.\gradlew.bat clean test
.\gradlew.bat build
cd ..\frontend
npm test
npm run build
```

Start the backend with `cd backend; .\gradlew.bat bootRun` after setting `JWT_SECRET`. In another terminal, start the frontend with `cd frontend; npm run dev`.
