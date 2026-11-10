import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { CalendarPage } from './pages/CalendarPage'
import type { ProjectResponse } from './types/project'
import type { TaskResponse } from './types/task'
import type { WorkspaceResponse } from './types/workspace'
import { projectApi } from './services/projectService'
import { taskApi } from './services/taskService'
import { workspaceApi } from './services/workspaceService'

vi.mock('./components/WorkspaceHeader', () => ({
  WorkspaceHeader: () => <header>SYNCORA</header>,
}))

vi.mock('./services/workspaceService', () => ({
  workspaceApi: { list: vi.fn() },
}))

vi.mock('./services/projectService', () => ({
  projectApi: { list: vi.fn() },
}))

vi.mock('./services/taskService', () => ({
  taskApi: { list: vi.fn() },
}))

const workspace: WorkspaceResponse = {
  id: 'workspace-1',
  name: 'Design team',
  description: null,
  slug: 'design-team',
  ownerId: 'user-1',
  memberCount: 1,
  currentUserRole: 'OWNER',
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
}

const project: ProjectResponse = {
  id: 'project-1',
  workspaceId: workspace.id,
  name: 'Website refresh',
  description: null,
  key: 'WEB',
  status: 'ACTIVE',
  startDate: null,
  dueDate: null,
  createdBy: 'user-1',
  projectManager: 'Test User',
  memberCount: 1,
  currentUserRole: 'PROJECT_MANAGER',
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
  archivedAt: null,
}

const dueTask: TaskResponse = {
  id: 'task-1',
  projectId: project.id,
  title: 'Review landing page',
  description: null,
  taskKey: 'WEB-1',
  status: 'IN_PROGRESS',
  priority: 'URGENT',
  assigneeId: 'user-1',
  assigneeName: 'Test User',
  createdBy: 'user-1',
  creatorName: 'Test User',
  dueDate: (() => {
    const today = new Date()
    return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  })(),
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
  completedAt: null,
  commentCount: 0,
}

describe('CalendarPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(workspaceApi.list).mockResolvedValue([workspace])
    vi.mocked(projectApi.list).mockResolvedValue({
      content: [project],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 100,
      first: true,
      last: true,
    })
    vi.mocked(taskApi.list).mockResolvedValue({
      content: [dueTask],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 100,
      first: true,
      last: true,
    })
  })

  it('shows real due-date tasks and supports month, week, day, and date navigation', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><CalendarPage /></MemoryRouter>)

    expect(await screen.findByRole('link', { name: /WEB-1/ })).toHaveAttribute('href', '/tasks/task-1')
    expect(screen.getByRole('button', { name: 'Month' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: 'Week' }))
    expect(screen.getByRole('button', { name: 'Week' })).toHaveAttribute('aria-pressed', 'true')
    const weekHeading = screen.getByRole('heading', { level: 2 }).textContent
    await user.click(screen.getByRole('button', { name: 'Next period' }))
    await waitFor(() => expect(screen.getByRole('heading', { level: 2 }).textContent).not.toBe(weekHeading))

    await user.click(screen.getByRole('button', { name: 'Today' }))
    await user.click(screen.getByRole('button', { name: 'Day' }))
    expect(screen.getByRole('button', { name: 'Day' })).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByRole('link', { name: /Review landing page/ })).toHaveAttribute('href', '/tasks/task-1')
  })
})
