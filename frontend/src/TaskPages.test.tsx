import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './auth/AuthContext'
import { TaskDetailsPage } from './pages/TaskDetailsPage'
import { TaskListPage } from './pages/TaskListPage'
import { projectApi } from './services/projectService'
import { taskApi } from './services/taskService'
import { workspaceApi } from './services/workspaceService'
import type { ProjectMemberResponse, ProjectResponse } from './types/project'
import type { TaskCommentResponse, TaskPage, TaskResponse } from './types/task'
import type { WorkspaceResponse } from './types/workspace'

vi.mock('./services/projectService', () => ({
  projectApi: {
    get: vi.fn(),
    listMembers: vi.fn(),
  },
}))

vi.mock('./services/taskService', () => ({
  taskApi: {
    list: vi.fn(),
    create: vi.fn(),
    get: vi.fn(),
    update: vi.fn(),
    updateStatus: vi.fn(),
    updatePriority: vi.fn(),
    assign: vi.fn(),
    delete: vi.fn(),
    listComments: vi.fn(),
    addComment: vi.fn(),
    updateComment: vi.fn(),
    deleteComment: vi.fn(),
  },
}))

vi.mock('./services/workspaceService', () => ({
  workspaceApi: {
    get: vi.fn(),
  },
}))

const workspace: WorkspaceResponse = {
  id: 'workspace-1',
  name: 'Product Team',
  description: 'Product work',
  slug: 'product-team',
  ownerId: 'user-1',
  memberCount: 2,
  currentUserRole: 'OWNER',
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
}

const project: ProjectResponse = {
  id: 'project-1',
  workspaceId: workspace.id,
  name: 'Mobile launch',
  description: 'Ship the mobile experience',
  key: 'MOBILE',
  status: 'ACTIVE',
  startDate: '2026-11-10',
  dueDate: '2027-01-10',
  createdBy: 'user-1',
  projectManager: 'Taylor Owner',
  memberCount: 2,
  currentUserRole: 'PROJECT_MANAGER',
  createdAt: '2026-11-01T10:00:00',
  updatedAt: '2026-11-01T10:00:00',
  archivedAt: null,
}

const member: ProjectMemberResponse = {
  id: 'project-member-1',
  userId: 'user-2',
  fullName: 'Alex Teammate',
  email: 'alex@example.com',
  profileImageUrl: null,
  role: 'MEMBER',
  joinedAt: '2026-11-02T10:00:00',
}

const task: TaskResponse = {
  id: 'task-1',
  projectId: project.id,
  title: 'Implement project dashboard',
  description: 'Build the first screen',
  taskKey: 'MOBILE-1',
  status: 'TODO',
  priority: 'HIGH',
  assigneeId: member.userId,
  assigneeName: member.fullName,
  createdBy: 'user-1',
  creatorName: 'Taylor Owner',
  dueDate: '2027-01-10',
  createdAt: '2026-11-01T10:00:00',
  updatedAt: '2026-11-01T10:00:00',
  completedAt: null,
  commentCount: 1,
}

const comment: TaskCommentResponse = {
  id: 'comment-1',
  taskId: task.id,
  authorId: 'user-1',
  authorName: 'Taylor Owner',
  content: 'Please include the mobile layout.',
  createdAt: '2026-11-01T10:00:00',
  updatedAt: '2026-11-01T10:00:00',
}

const taskPage: TaskPage = {
  content: [task],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 10,
  first: true,
  last: true,
}

function renderTaskPage(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/projects/:projectId/tasks" element={<TaskListPage />} />
          <Route path="/tasks/:taskId" element={<TaskDetailsPage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('task pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(projectApi.get).mockResolvedValue(project)
    vi.mocked(projectApi.listMembers).mockResolvedValue([member])
    vi.mocked(workspaceApi.get).mockResolvedValue(workspace)
    vi.mocked(taskApi.list).mockResolvedValue(taskPage)
    vi.mocked(taskApi.create).mockResolvedValue(task)
    vi.mocked(taskApi.get).mockResolvedValue(task)
    vi.mocked(taskApi.update).mockResolvedValue(task)
    vi.mocked(taskApi.updateStatus).mockImplementation(async (_id, status) => ({ ...task, status }))
    vi.mocked(taskApi.updatePriority).mockImplementation(async (_id, priority) => ({ ...task, priority }))
    vi.mocked(taskApi.assign).mockResolvedValue(task)
    vi.mocked(taskApi.listComments).mockResolvedValue([comment])
    vi.mocked(taskApi.addComment).mockResolvedValue({ ...comment, id: 'comment-2', content: 'I will take this.' })
  })

  it('lists tasks with their key, status, assignee, and due date', async () => {
    renderTaskPage(`/projects/${project.id}/tasks`)

    expect(await screen.findByRole('link', { name: 'Implement project dashboard' })).toHaveAttribute('href', `/tasks/${task.id}`)
    expect(screen.getByText('MOBILE-1')).toBeInTheDocument()
    expect(screen.getByText('Assigned to Alex Teammate')).toBeInTheDocument()
    expect(screen.getByText('Due 2027-01-10')).toBeInTheDocument()
  })

  it('sends search and status filters to the task API', async () => {
    renderTaskPage(`/projects/${project.id}/tasks`)
    await screen.findByRole('link', { name: 'Implement project dashboard' })

    fireEvent.change(screen.getByRole('searchbox', { name: 'Search tasks' }), { target: { value: 'dashboard' } })
    fireEvent.change(screen.getByLabelText('Task status filter'), { target: { value: 'IN_PROGRESS' } })

    await waitFor(() => expect(taskApi.list).toHaveBeenLastCalledWith(project.id, {
      search: 'dashboard',
      status: 'IN_PROGRESS',
      priority: undefined,
      assigneeId: undefined,
      overdue: false,
      page: 0,
      size: 10,
      sortBy: 'createdAt',
      sortDirection: 'DESC',
    }))
  })

  it('moves through task pages', async () => {
    const user = userEvent.setup()
    vi.mocked(taskApi.list).mockResolvedValue({
      ...taskPage,
      totalElements: 11,
      totalPages: 2,
      last: false,
    })
    renderTaskPage(`/projects/${project.id}/tasks`)

    await screen.findByRole('link', { name: 'Implement project dashboard' })
    await user.click(screen.getByRole('button', { name: 'Next' }))

    await waitFor(() => expect(taskApi.list).toHaveBeenLastCalledWith(project.id, expect.objectContaining({
      page: 1,
      size: 10,
    })))
  })

  it('creates a task using the selected priority and assignee', async () => {
    const user = userEvent.setup()
    renderTaskPage(`/projects/${project.id}/tasks`)

    await user.click(await screen.findByRole('button', { name: 'Create task' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Task title'), 'Review launch checklist')
    await user.selectOptions(within(dialog).getByLabelText('Priority'), 'URGENT')
    await user.selectOptions(within(dialog).getByLabelText('Assignee'), member.userId)
    await user.click(within(dialog).getByRole('button', { name: 'Create task' }))

    await waitFor(() => expect(taskApi.create).toHaveBeenCalledWith(project.id, {
      title: 'Review launch checklist',
      description: '',
      priority: 'URGENT',
      assigneeId: member.userId,
      dueDate: null,
    }))
  })

  it('updates status and priority from the task list', async () => {
    const user = userEvent.setup()
    renderTaskPage(`/projects/${project.id}/tasks`)

    await screen.findByRole('link', { name: 'Implement project dashboard' })
    await user.selectOptions(screen.getByLabelText('Status for MOBILE-1'), 'IN_PROGRESS')
    await user.selectOptions(screen.getByLabelText('Priority for MOBILE-1'), 'URGENT')

    await waitFor(() => {
      expect(taskApi.updateStatus).toHaveBeenCalledWith(task.id, 'IN_PROGRESS')
      expect(taskApi.updatePriority).toHaveBeenCalledWith(task.id, 'URGENT')
    })
  })

  it('shows task details and posts a comment', async () => {
    const user = userEvent.setup()
    renderTaskPage(`/tasks/${task.id}`)

    expect(await screen.findByRole('heading', { name: task.title })).toBeInTheDocument()
    expect(screen.getByText(comment.content)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Comment'), 'I will take this.')
    await user.click(screen.getByRole('button', { name: 'Post comment' }))

    await waitFor(() => expect(taskApi.addComment).toHaveBeenCalledWith(task.id, 'I will take this.'))
    expect(await screen.findByText('I will take this.')).toBeInTheDocument()
  })

  it('shows loading and API error states', async () => {
    vi.mocked(taskApi.list).mockImplementation(() => new Promise(() => undefined))
    renderTaskPage(`/projects/${project.id}/tasks`)
    expect(await screen.findByText('Loading tasks...')).toBeInTheDocument()

    vi.mocked(taskApi.list).mockRejectedValue(new Error('request failed'))
    renderTaskPage(`/projects/${project.id}/tasks`)
    expect(await screen.findByRole('alert')).toHaveTextContent('Tasks could not be loaded.')
  })
})
