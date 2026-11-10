import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProjectChatPage } from './pages/ProjectChatPage'
import { chatApi } from './services/chatService'
import { connectProjectChat, type ChatConnectionCallbacks } from './services/chatWebSocketService'
import { projectApi } from './services/projectService'
import { workspaceApi } from './services/workspaceService'
import type { ChatMessage } from './types/chat'
import type { ProjectResponse } from './types/project'
import type { WorkspaceResponse } from './types/workspace'

const authState = {
  token: 'test-token',
  user: {
    id: 'user-1',
    fullName: 'Taylor Owner',
    email: 'owner@example.com',
    profileImageUrl: null,
    bio: null,
    role: 'MEMBER' as const,
    active: true,
    createdAt: '2026-01-01T10:00:00',
    updatedAt: '2026-01-01T10:00:00',
  },
}

vi.mock('./auth/AuthContext', () => ({ useAuth: () => authState }))

vi.mock('./services/chatService', () => ({
  chatApi: {
    history: vi.fn(),
    send: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}))

const connectionControls: {
  callbacks?: ChatConnectionCallbacks
  disconnect: ReturnType<typeof vi.fn>
  send: ReturnType<typeof vi.fn>
} = {
  disconnect: vi.fn(async () => undefined),
  send: vi.fn(() => true),
}

vi.mock('./services/chatWebSocketService', () => ({
  connectProjectChat: vi.fn((_projectId: string, _token: string, callbacks: ChatConnectionCallbacks) => {
    connectionControls.callbacks = callbacks
    callbacks.onStateChange('connected')
    return {
      send: connectionControls.send,
      disconnect: connectionControls.disconnect,
    }
  }),
}))

vi.mock('./services/projectService', () => ({
  projectApi: { get: vi.fn() },
}))

vi.mock('./services/workspaceService', () => ({
  workspaceApi: { get: vi.fn() },
}))

const project: ProjectResponse = {
  id: 'project-1',
  workspaceId: 'workspace-1',
  name: 'Mobile launch',
  description: 'Project description',
  key: 'MOBILE',
  status: 'ACTIVE',
  startDate: null,
  dueDate: null,
  createdBy: 'user-1',
  projectManager: 'Taylor Owner',
  memberCount: 1,
  currentUserRole: 'PROJECT_MANAGER',
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
  archivedAt: null,
}

const workspace: WorkspaceResponse = {
  id: 'workspace-1',
  name: 'Product Team',
  description: null,
  slug: 'product-team',
  ownerId: 'user-1',
  memberCount: 1,
  currentUserRole: 'OWNER',
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
}

const message: ChatMessage = {
  id: 'message-1',
  projectId: project.id,
  senderId: authState.user.id,
  senderName: authState.user.fullName,
  content: 'Hello team',
  createdAt: '2026-11-06T10:00:00',
  updatedAt: '2026-11-06T10:00:00',
  editedAt: null,
  deletedAt: null,
  deleted: false,
}

function renderChat() {
  return render(
    <MemoryRouter initialEntries={[`/projects/${project.id}/chat`]}>
      <Routes>
        <Route path="/projects/:projectId/chat" element={<ProjectChatPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('project chat page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    connectionControls.callbacks = undefined
    connectionControls.disconnect = vi.fn(async () => undefined)
    connectionControls.send = vi.fn(() => true)
    vi.mocked(chatApi.history).mockResolvedValue({
      content: [message],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 50,
      first: true,
      last: true,
    })
    vi.mocked(chatApi.update).mockResolvedValue({ ...message, content: 'Updated hello', editedAt: '2026-11-06T10:01:00' })
    vi.mocked(chatApi.delete).mockResolvedValue(undefined)
    vi.mocked(projectApi.get).mockResolvedValue(project)
    vi.mocked(workspaceApi.get).mockResolvedValue(workspace)
  })

  it('renders chat history and connects to the project room', async () => {
    renderChat()
    expect(await screen.findByText('Hello team')).toBeInTheDocument()
    expect(connectProjectChat).toHaveBeenCalledWith(project.id, 'test-token', expect.any(Object))
    expect(screen.getByText('Connected')).toBeInTheDocument()
  })

  it('loads the next history page without duplicating overlapping messages', async () => {
    const user = userEvent.setup()
    vi.mocked(chatApi.history)
      .mockResolvedValueOnce({
        content: [message],
        totalElements: 2,
        totalPages: 2,
        number: 0,
        size: 50,
        first: true,
        last: false,
      })
      .mockResolvedValueOnce({
        content: [
          message,
          { ...message, id: 'message-2', content: 'Next page message', createdAt: '2026-11-06T10:01:00' },
        ],
        totalElements: 2,
        totalPages: 2,
        number: 1,
        size: 50,
        first: false,
        last: true,
      })
    renderChat()
    await screen.findByText('Hello team')
    await user.click(screen.getByRole('button', { name: 'Load more history' }))

    expect(await screen.findByText('Next page message')).toBeInTheDocument()
    expect(screen.getAllByText('Hello team')).toHaveLength(1)
    expect(chatApi.history).toHaveBeenLastCalledWith(project.id, 1, 50)
  })

  it('sends a message only through the socket and renders its server broadcast', async () => {
    const user = userEvent.setup()
    renderChat()
    await screen.findByText('Hello team')

    await user.type(screen.getByLabelText('Write a message'), 'Live update')
    await user.click(screen.getByRole('button', { name: 'Send' }))

    expect(connectionControls.send).toHaveBeenCalledWith('Live update')
    expect(screen.getByLabelText('Write a message')).toHaveValue('Live update')
    const broadcast = { ...message, id: 'message-2', content: 'Live update' }
    act(() => connectionControls.callbacks?.onMessage(broadcast))
    expect(await screen.findByText('Live update')).toBeInTheDocument()
  })

  it('rejects empty message input before sending', async () => {
    const user = userEvent.setup()
    renderChat()
    await screen.findByText('Hello team')
    await user.click(screen.getByRole('button', { name: 'Send' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Write a message before sending.')
    expect(connectionControls.send).not.toHaveBeenCalled()
  })

  it('edits and deletes own messages', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('confirm', vi.fn(() => true))
    renderChat()
    await screen.findByText('Hello team')

    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const input = screen.getByLabelText('Edit message')
    await user.clear(input)
    await user.type(input, 'Updated hello')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Updated hello')).toBeInTheDocument()
    expect(chatApi.update).toHaveBeenCalledWith(message.id, 'Updated hello')

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await waitFor(() => expect(chatApi.delete).toHaveBeenCalledWith(message.id))
    expect(await screen.findByText('This message was deleted.')).toBeInTheDocument()
    vi.unstubAllGlobals()
  })

  it('shows loading and history error states', async () => {
    vi.mocked(chatApi.history).mockImplementation(() => new Promise(() => undefined))
    renderChat()
    expect(await screen.findByText('Loading chat history...')).toBeInTheDocument()

    vi.mocked(chatApi.history).mockRejectedValue(new Error('request failed'))
    renderChat()
    expect(await screen.findByRole('alert')).toHaveTextContent('Project chat could not be loaded.')
  })

  it('shows disconnected state and cleans up the socket on unmount', async () => {
    const { unmount } = renderChat()
    await screen.findByText('Hello team')
    act(() => connectionControls.callbacks?.onStateChange('reconnecting'))
    expect(await screen.findByText('Reconnecting...')).toBeInTheDocument()
    unmount()
    await waitFor(() => expect(connectionControls.disconnect).toHaveBeenCalledTimes(1))
  })
})
