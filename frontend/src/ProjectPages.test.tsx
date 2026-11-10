import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './auth/AuthContext'
import { ProjectDetailsPage } from './pages/ProjectDetailsPage'
import { ProjectListPage } from './pages/ProjectListPage'
import { projectApi } from './services/projectService'
import { workspaceApi } from './services/workspaceService'
import { fileApi } from './services/fileService'
import type { ProjectMemberResponse, ProjectResponse } from './types/project'
import type { WorkspaceResponse } from './types/workspace'

vi.mock('./services/projectService', () => ({
  projectApi: {
    list: vi.fn(),
    create: vi.fn(),
    get: vi.fn(),
    update: vi.fn(),
    archive: vi.fn(),
    restore: vi.fn(),
    delete: vi.fn(),
    listMembers: vi.fn(),
    addMember: vi.fn(),
    updateMemberRole: vi.fn(),
    removeMember: vi.fn(),
  },
}))

vi.mock('./services/workspaceService', () => ({
  workspaceApi: {
    get: vi.fn(),
  },
}))

vi.mock('./services/fileService', () => ({
  fileApi: {
    listWorkspace: vi.fn(),
    listProject: vi.fn(),
    uploadWorkspace: vi.fn(),
    uploadProject: vi.fn(),
    download: vi.fn(),
    delete: vi.fn(),
  },
}))

const workspace: WorkspaceResponse = {
  id: '550e8400-e29b-41d4-a716-446655440000',
  name: 'Product Team',
  description: 'Product work',
  slug: 'product-team',
  ownerId: '550e8400-e29b-41d4-a716-446655440001',
  memberCount: 2,
  currentUserRole: 'OWNER',
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
}

const project: ProjectResponse = {
  id: '550e8400-e29b-41d4-a716-446655440010',
  workspaceId: workspace.id,
  name: 'Mobile launch',
  description: 'Ship the new mobile experience',
  key: 'MOBILE-LAUNCH',
  status: 'ACTIVE',
  startDate: '2026-11-10',
  dueDate: '2027-01-10',
  createdBy: workspace.ownerId,
  projectManager: 'Taylor Owner',
  memberCount: 2,
  currentUserRole: 'PROJECT_MANAGER',
  createdAt: '2026-11-01T10:00:00',
  updatedAt: '2026-11-01T10:00:00',
  archivedAt: null,
}

const member: ProjectMemberResponse = {
  id: '550e8400-e29b-41d4-a716-446655440020',
  userId: '550e8400-e29b-41d4-a716-446655440021',
  fullName: 'Alex Teammate',
  email: 'alex@example.com',
  profileImageUrl: null,
  role: 'MEMBER',
  joinedAt: '2026-11-02T10:00:00',
}

function renderProjectPage(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/workspaces/:workspaceId/projects" element={<ProjectListPage />} />
          <Route path="/projects/:projectId" element={<ProjectDetailsPage />} />
          <Route path="/projects/created" element={<div>Project created</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('project pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(workspaceApi.get).mockResolvedValue(workspace)
    vi.mocked(fileApi.listProject).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10, first: true, last: true,
    })
    vi.mocked(projectApi.list).mockResolvedValue({
      content: [project],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
      first: true,
      last: true,
    })
    vi.mocked(projectApi.create).mockResolvedValue({ ...project, id: 'created' })
    vi.mocked(projectApi.get).mockResolvedValue(project)
    vi.mocked(projectApi.listMembers).mockResolvedValue([{
      ...member,
      id: 'manager',
      userId: workspace.ownerId,
      fullName: 'Taylor Owner',
      email: 'owner@example.com',
      role: 'PROJECT_MANAGER',
    }, member])
    vi.mocked(projectApi.addMember).mockResolvedValue({
      ...member,
      id: 'new-member',
      fullName: 'New Teammate',
      email: 'new@example.com',
    })
    vi.mocked(projectApi.updateMemberRole).mockResolvedValue({ ...member, role: 'PROJECT_MANAGER' })
  })

  it('displays project cards with key, status, manager, and dates', async () => {
    renderProjectPage(`/workspaces/${workspace.id}/projects`)

    expect(await screen.findByRole('link', { name: 'Mobile launch' })).toHaveAttribute(
      'href',
      `/projects/${project.id}`,
    )
    expect(screen.getByText('MOBILE-LAUNCH')).toBeInTheDocument()
    expect(screen.getByText('Taylor Owner')).toBeInTheDocument()
    expect(screen.getByText('2027-01-10')).toBeInTheDocument()
  })

  it('creates a project with validated form data', async () => {
    const user = userEvent.setup()
    vi.mocked(projectApi.create).mockResolvedValueOnce({ ...project, id: 'created', name: 'New initiative' })
    vi.mocked(projectApi.list)
      .mockResolvedValueOnce({
        content: [project],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 10,
        first: true,
        last: true,
      })
      .mockResolvedValueOnce({
        content: [{ ...project, id: 'created', name: 'New initiative' }],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 10,
        first: true,
        last: true,
      })
    renderProjectPage(`/workspaces/${workspace.id}/projects`)

    await user.click(await screen.findByRole('button', { name: /create project/i }))
    fireEvent.change(screen.getByLabelText('Project name'), { target: { value: 'New initiative' } })
    fireEvent.change(screen.getByLabelText('Description Optional'), { target: { value: 'A new project' } })
    await user.click(screen.getAllByRole('button', { name: 'Create project' })[1])

    await waitFor(() => expect(projectApi.create).toHaveBeenCalledWith(workspace.id, {
      name: 'New initiative',
      description: 'A new project',
      status: 'PLANNING',
      startDate: null,
      dueDate: null,
    }))
    expect(await screen.findByText('“New initiative” was created and saved.')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'New initiative' })).toHaveAttribute('href', '/projects/created')
    expect(projectApi.list).toHaveBeenCalledTimes(2)
  })

  it('does not claim project persistence if the refreshed list omits the created record', async () => {
    const user = userEvent.setup()
    vi.mocked(projectApi.create).mockResolvedValueOnce({ ...project, id: 'not-persisted', name: 'Not persisted' })
    renderProjectPage(`/workspaces/${workspace.id}/projects`)

    await user.click(await screen.findByRole('button', { name: /create project/i }))
    fireEvent.change(screen.getByLabelText('Project name'), { target: { value: 'Not persisted' } })
    await user.click(screen.getAllByRole('button', { name: 'Create project' })[1])

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The server accepted “Not persisted”, but it was not present when the project list was refreshed.',
    )
    expect(screen.queryByText('“Not persisted” was created and saved.')).not.toBeInTheDocument()
  })

  it('sends search and status filters to the project API', async () => {
    renderProjectPage(`/workspaces/${workspace.id}/projects`)
    await screen.findByRole('link', { name: 'Mobile launch' })

    fireEvent.change(screen.getByRole('searchbox', { name: 'Search projects' }), {
      target: { value: 'Mobile' },
    })
    fireEvent.change(screen.getByLabelText('Status filter'), { target: { value: 'ACTIVE' } })

    await waitFor(() => expect(projectApi.list).toHaveBeenLastCalledWith(workspace.id, {
      search: 'Mobile',
      status: 'ACTIVE',
      archived: false,
      page: 0,
      size: 10,
    }))
  })

  it('loads project details and lets a manager add and promote members', async () => {
    const user = userEvent.setup()
    renderProjectPage(`/projects/${project.id}`)

    expect(await screen.findByRole('heading', { name: 'Mobile launch' })).toBeInTheDocument()
    expect(screen.getByText('alex@example.com')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'new@example.com' } })
    await user.click(screen.getByRole('button', { name: 'Add member' }))
    await waitFor(() => expect(projectApi.addMember).toHaveBeenCalledWith(project.id, 'new@example.com'))
    expect(await screen.findByText('new@example.com')).toBeInTheDocument()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Role for Alex Teammate' }), 'PROJECT_MANAGER')
    await waitFor(() => expect(projectApi.updateMemberRole).toHaveBeenCalledWith(
      project.id,
      member.id,
      'PROJECT_MANAGER',
    ))
  })

  it('shows loading and API error states', async () => {
    vi.mocked(projectApi.list).mockImplementation(() => new Promise(() => undefined))
    renderProjectPage(`/workspaces/${workspace.id}/projects`)
    expect(screen.getByRole('status')).toHaveTextContent('Loading projects...')

    vi.mocked(projectApi.list).mockRejectedValue(new Error('request failed'))
    renderProjectPage(`/workspaces/${workspace.id}/projects`)
    expect(await screen.findByRole('alert')).toHaveTextContent('Projects could not be loaded.')
  })
})
