CREATE TABLE projects (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000),
    project_key VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_date DATE,
    due_date DATE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uk_projects_workspace_key UNIQUE (workspace_id, project_key),
    CONSTRAINT uk_projects_id_workspace UNIQUE (id, workspace_id),
    CONSTRAINT chk_projects_status CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT chk_projects_dates CHECK (start_date IS NULL OR due_date IS NULL OR due_date >= start_date),
    CONSTRAINT fk_projects_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_projects_creator FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_projects_workspace_created ON projects (workspace_id, created_at DESC);
CREATE INDEX idx_projects_workspace_status ON projects (workspace_id, status);

CREATE TABLE project_members (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT chk_project_members_role CHECK (role IN ('PROJECT_MANAGER', 'MEMBER')),
    CONSTRAINT fk_project_members_project_workspace FOREIGN KEY (project_id, workspace_id)
        REFERENCES projects (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_workspace_user FOREIGN KEY (workspace_id, user_id)
        REFERENCES workspace_members (workspace_id, user_id) ON DELETE CASCADE
);

CREATE INDEX idx_project_members_user_id ON project_members (user_id);
CREATE INDEX idx_project_members_project_role ON project_members (project_id, role);
