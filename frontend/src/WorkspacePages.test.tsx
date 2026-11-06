import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './auth/AuthContext'
import { WorkspaceDetailsPage } from './pages/WorkspaceDetailsPage'
import { WorkspaceListPage } from './pages/WorkspaceListPage'
import { workspaceApi } from './services/workspaceService'
import type { WorkspaceMemberResponse, WorkspaceResponse } from './types/workspace'

vi.mock('./services/workspaceService', () => ({
  workspaceApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    listMembers: vi.fn(),
    addMember: vi.fn(),
    updateMemberRole: vi.fn(),
    removeMember: vi.fn(),
    leave: vi.fn(),
  },
}))

const workspace: WorkspaceResponse = {
  id: '550e8400-e29b-41d4-a716-446655440000',
  name: 'Engineering Team',
  description: 'Build great things together',
  slug: 'engineering-team-unique',
  ownerId: '550e8400-e29b-41d4-a716-446655440001',
  memberCount: 2,
  currentUserRole: 'OWNER',
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
}

const member: WorkspaceMemberResponse = {
  id: '550e8400-e29b-41d4-a716-446655440002',
  userId: '550e8400-e29b-41d4-a716-446655440003',
  fullName: 'Alex Member',
  email: 'alex@example.com',
  profileImageUrl: null,
  role: 'MEMBER',
  joinedAt: '2026-01-02T10:00:00',
}

function renderWorkspacePage(path = '/workspaces') {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/workspaces" element={<WorkspaceListPage />} />
          <Route path="/workspaces/:workspaceId" element={<WorkspaceDetailsPage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('workspace pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(workspaceApi.list).mockResolvedValue([workspace])
    vi.mocked(workspaceApi.create).mockResolvedValue(workspace)
    vi.mocked(workspaceApi.get).mockResolvedValue(workspace)
    vi.mocked(workspaceApi.listMembers).mockResolvedValue([member])
    vi.mocked(workspaceApi.addMember).mockResolvedValue({
      ...member,
      id: '550e8400-e29b-41d4-a716-446655440004',
      fullName: 'New Teammate',
      email: 'new@example.com',
    })
  })

  it('displays the current user workspace list and member count', async () => {
    renderWorkspacePage()

    expect(await screen.findByRole('heading', { name: 'Engineering Team' })).toBeInTheDocument()
    expect(screen.getByText('2 members')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /open workspace/i })).toHaveAttribute(
      'href',
      `/workspaces/${workspace.id}`,
    )
  })

  it('creates a workspace from the create dialog', async () => {
    const user = userEvent.setup()
    renderWorkspacePage()
    await screen.findByRole('heading', { name: 'Engineering Team' })

    await user.click(screen.getByRole('button', { name: /create workspace/i }))
    fireEvent.change(screen.getByLabelText('Workspace name'), { target: { value: 'Design Team' } })
    fireEvent.change(screen.getByLabelText('Description Optional'), { target: { value: 'Design work' } })
    await user.click(screen.getAllByRole('button', { name: 'Create workspace' })[1])

    await waitFor(() => expect(workspaceApi.create).toHaveBeenCalledWith({
      name: 'Design Team',
      description: 'Design work',
    }))
  })

  it('shows an empty state when the user has no workspaces', async () => {
    vi.mocked(workspaceApi.list).mockResolvedValue([])
    renderWorkspacePage()

    expect(await screen.findByText('Your team’s next chapter starts here.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create your first workspace' })).toBeInTheDocument()
  })

  it('displays workspace member details and lets an owner add a member', async () => {
    const user = userEvent.setup()
    renderWorkspacePage(`/workspaces/${workspace.id}`)

    expect(await screen.findByRole('heading', { name: 'Workspace members 1' })).toBeInTheDocument()
    expect(screen.getByText('alex@example.com')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'new@example.com' } })
    await user.click(screen.getByRole('button', { name: 'Add member' }))

    await waitFor(() => expect(workspaceApi.addMember).toHaveBeenCalledWith(workspace.id, {
      email: 'new@example.com',
    }))
    expect(await screen.findByText('new@example.com')).toBeInTheDocument()
  })

  it('shows API errors instead of hiding a failed workspace request', async () => {
    vi.mocked(workspaceApi.list).mockRejectedValue(new Error('request failed'))
    renderWorkspacePage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Your workspaces could not be loaded.')
  })

  it('hides workspace management controls from a regular member', async () => {
    vi.mocked(workspaceApi.get).mockResolvedValue({
      ...workspace,
      currentUserRole: 'MEMBER',
    })
    renderWorkspacePage(`/workspaces/${workspace.id}`)

    expect(await screen.findByText('alex@example.com')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Add a teammate' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit details' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete workspace' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Leave workspace' })).toBeInTheDocument()
  })

  it('shows a loading state while the list is fetched', () => {
    vi.mocked(workspaceApi.list).mockImplementation(() => new Promise(() => undefined))
    renderWorkspacePage()

    expect(screen.getByRole('status')).toHaveTextContent('Loading workspaces...')
  })
})
