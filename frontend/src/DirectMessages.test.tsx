import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { WorkspaceMessagesPage } from './pages/WorkspaceMessagesPage'
import { directMessageApi } from './services/directMessageService'
import { connectDirectMessages } from './services/directMessageWebSocketService'
import { workspaceApi } from './services/workspaceService'

vi.mock('./auth/AuthContext', () => ({
  useAuth: () => ({
    token: 'in-memory-test-token',
    user: { id: 'user-current', fullName: 'Current User' },
  }),
}))

vi.mock('./services/directMessageService', () => ({
  directMessageApi: {
    listConversations: vi.fn(),
    createConversation: vi.fn(),
    history: vi.fn(),
  },
}))

vi.mock('./services/directMessageWebSocketService', () => ({
  connectDirectMessages: vi.fn(),
}))

vi.mock('./services/workspaceService', () => ({
  workspaceApi: {
    get: vi.fn(),
    listMembers: vi.fn(),
  },
}))

vi.mock('./services/notificationService', () => ({
  notificationApi: {
    list: vi.fn().mockResolvedValue({ content: [] }),
    unreadCount: vi.fn().mockResolvedValue(0),
    activity: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5, first: true, last: true }),
  },
}))

vi.mock('./services/notificationWebSocketService', () => ({
  connectNotifications: vi.fn(() => ({ disconnect: vi.fn().mockResolvedValue(undefined) })),
}))

const page = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 50,
  first: true,
  last: true,
}

const conversation = {
  id: 'conversation-1',
  workspaceId: 'workspace-1',
  recipientId: 'user-other',
  recipientName: 'Alex Teammate',
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/workspaces/workspace-1/messages']}>
      <Routes>
        <Route path="/workspaces/:workspaceId/messages" element={<WorkspaceMessagesPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('workspace direct messages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(workspaceApi.get).mockResolvedValue({
      id: 'workspace-1',
      name: 'Product Team',
      description: null,
      slug: 'product-team',
      ownerId: 'user-current',
      memberCount: 2,
      currentUserRole: 'OWNER',
      createdAt: '2026-01-01T10:00:00',
      updatedAt: '2026-01-01T10:00:00',
    })
    vi.mocked(workspaceApi.listMembers).mockResolvedValue([
      { id: 'member-current', userId: 'user-current', fullName: 'Current User', email: 'current@example.com', profileImageUrl: null, role: 'OWNER', joinedAt: '2026-01-01T10:00:00' },
      { id: 'member-other', userId: 'user-other', fullName: 'Alex Teammate', email: 'alex@example.com', profileImageUrl: null, role: 'MEMBER', joinedAt: '2026-01-01T10:00:00' },
    ])
    vi.mocked(directMessageApi.listConversations).mockResolvedValue([])
    vi.mocked(directMessageApi.history).mockResolvedValue(page)
    vi.mocked(directMessageApi.createConversation).mockResolvedValue(conversation)
    vi.mocked(connectDirectMessages).mockImplementation((_id, _token, callbacks) => {
      callbacks.onStateChange('connected')
      return { send: vi.fn(() => true), disconnect: vi.fn().mockResolvedValue(undefined) }
    })
  })

  it('starts a private conversation and shows the member in its thread', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { name: 'Messages' })
    await user.selectOptions(screen.getByLabelText('Start a conversation'), 'user-other')
    await user.click(screen.getByRole('button', { name: 'Message' }))

    expect(await screen.findByRole('heading', { name: 'Say hello' })).toBeInTheDocument()
    expect(directMessageApi.createConversation).toHaveBeenCalledWith('workspace-1', 'user-other')
    expect(connectDirectMessages).toHaveBeenCalledWith(
      conversation.id,
      'in-memory-test-token',
      expect.any(Object),
    )
  })

  it('renders real-time messages and disconnects the socket on unmount', async () => {
    vi.mocked(directMessageApi.listConversations).mockResolvedValueOnce([conversation])
    const { unmount } = renderPage()
    await screen.findByRole('heading', { name: 'Say hello' })

    const callbacks = vi.mocked(connectDirectMessages).mock.calls[0]?.[2]
    expect(callbacks).toBeDefined()
    await act(async () => {
      callbacks?.onMessage({
        id: 'message-1',
        conversationId: conversation.id,
        senderId: 'user-other',
        senderName: 'Alex Teammate',
        content: 'Hello in real time',
        createdAt: '2026-01-01T10:01:00',
      })
    })
    expect(await screen.findByText('Hello in real time')).toBeInTheDocument()
    unmount()

    const disconnect = vi.mocked(connectDirectMessages).mock.results[0]?.value.disconnect
    await waitFor(() => expect(disconnect).toHaveBeenCalled())
  })
})
