import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ActivityFeed } from './components/ActivityFeed'
import { NotificationBell } from './components/NotificationBell'
import { notificationApi } from './services/notificationService'
import type { Activity, Notification } from './types/notification'

const mocks = vi.hoisted(() => ({
  auth: { token: 'test-token', user: { id: 'user-1' } },
  onNotification: undefined as ((notification: Notification) => void) | undefined,
  onStateChange: undefined as ((
    state: 'connecting' | 'connected' | 'reconnecting' | 'disconnected' | 'error',
  ) => void) | undefined,
  disconnect: vi.fn(async () => undefined),
}))

vi.mock('./auth/AuthContext', () => ({ useAuth: () => mocks.auth }))

vi.mock('./services/notificationService', () => ({
  notificationApi: {
    list: vi.fn(),
    unreadCount: vi.fn(),
    markRead: vi.fn(),
    markUnread: vi.fn(),
    markAllRead: vi.fn(),
    delete: vi.fn(),
    activity: vi.fn(),
  },
}))

vi.mock('./services/notificationWebSocketService', () => ({
  connectNotifications: vi.fn((
    _token: string,
    onNotification: (notification: Notification) => void,
    onStateChange: (state: 'connecting' | 'connected' | 'reconnecting' | 'disconnected' | 'error') => void,
  ) => {
    mocks.onNotification = onNotification
    mocks.onStateChange = onStateChange
    onStateChange('connected')
    return { disconnect: mocks.disconnect }
  }),
}))

const notification: Notification = {
  id: 'notification-1',
  type: 'TASK_ASSIGNED',
  title: 'Task assigned',
  message: 'You were assigned to task APP-1.',
  actorId: 'actor-1',
  actorName: 'Alex Teammate',
  workspaceId: 'workspace-1',
  workspaceName: 'Engineering',
  projectId: 'project-1',
  projectName: 'Launch',
  taskId: 'task-1',
  taskKey: 'APP-1',
  taskTitle: 'Ship feature',
  taskCommentId: null,
  chatMessageId: null,
  read: false,
  createdAt: '2026-11-08T10:00:00',
  readAt: null,
}

const activity: Activity = {
  id: 'activity-1',
  type: 'TASK_CREATED',
  description: 'Alex Teammate created task APP-1.',
  actorId: 'actor-1',
  actorName: 'Alex Teammate',
  workspaceId: 'workspace-1',
  projectId: 'project-1',
  projectName: 'Launch',
  taskId: 'task-1',
  taskKey: 'APP-1',
  taskTitle: 'Ship feature',
  createdAt: '2026-11-08T10:00:00',
}

function renderBell() {
  return render(<MemoryRouter><NotificationBell /></MemoryRouter>)
}

function renderActivity() {
  return render(<MemoryRouter><ActivityFeed /></MemoryRouter>)
}

describe('notification bell and activity feed', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.auth.token = 'test-token'
    mocks.onNotification = undefined
    mocks.onStateChange = undefined
    mocks.disconnect = vi.fn(async () => undefined)
    vi.mocked(notificationApi.list).mockResolvedValue({
      content: [notification],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
      first: true,
      last: true,
    })
    vi.mocked(notificationApi.unreadCount).mockResolvedValue(1)
    vi.mocked(notificationApi.markRead).mockResolvedValue({ ...notification, read: true, readAt: '2026-11-08T10:01:00' })
    vi.mocked(notificationApi.markAllRead).mockResolvedValue(1)
    vi.mocked(notificationApi.activity).mockResolvedValue({
      content: [activity],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
      first: true,
      last: true,
    })
  })

  it('renders the unread count and recent notification list', async () => {
    const user = userEvent.setup()
    renderBell()
    await user.click(screen.getByRole('button', { name: /notifications/i }))

    expect(await screen.findByText('Task assigned')).toBeInTheDocument()
    expect(screen.getByText('You were assigned to task APP-1.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
  })

  it('marks individual notifications and all notifications as read', async () => {
    const user = userEvent.setup()
    const secondNotification = {
      ...notification,
      id: 'notification-2',
      title: 'Priority changed',
      createdAt: '2026-03-09T10:00:00',
    }
    vi.mocked(notificationApi.list).mockResolvedValue({
      content: [notification, secondNotification],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 10,
      first: true,
      last: true,
    })
    vi.mocked(notificationApi.unreadCount).mockResolvedValue(2)
    vi.mocked(notificationApi.markAllRead).mockResolvedValue(1)
    renderBell()
    await user.click(screen.getByRole('button', { name: /notifications/i }))
    await user.click((await screen.findAllByRole('button', { name: 'Mark read' }))[0])

    expect(notificationApi.markRead).toHaveBeenCalledWith(notification.id)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: 'Mark all read' }))
    expect(notificationApi.markAllRead).toHaveBeenCalled()
  })

  it('updates in real time and ignores duplicate notification events', async () => {
    const user = userEvent.setup()
    renderBell()
    await user.click(screen.getByRole('button', { name: /notifications/i }))
    await screen.findByText('Task assigned')
    const next = { ...notification, id: 'notification-2', title: 'Priority changed' }

    act(() => {
      mocks.onNotification?.(next)
      mocks.onNotification?.(next)
    })

    expect(await screen.findByText('Priority changed')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Notifications, 2 unread' })).toBeInTheDocument()
  })

  it('shows loading and API error states', async () => {
    vi.mocked(notificationApi.activity).mockImplementation(() => new Promise(() => undefined))
    renderActivity()
    expect(await screen.findByText('Loading activity...')).toBeInTheDocument()
  })

  it('reports an activity feed request failure', async () => {
    vi.mocked(notificationApi.activity).mockRejectedValue(new Error('request failed'))
    renderActivity()
    expect(await screen.findByRole('alert')).toHaveTextContent('Activity could not be loaded.')
  })

  it('renders activity and loads the next chronological page', async () => {
    const user = userEvent.setup()
    vi.mocked(notificationApi.activity)
      .mockResolvedValueOnce({
        content: [activity],
        totalElements: 2,
        totalPages: 2,
        number: 0,
        size: 20,
        first: true,
        last: false,
      })
      .mockResolvedValueOnce({
        content: [{ ...activity, id: 'activity-2', description: 'Alex Teammate updated task APP-1.' }],
        totalElements: 2,
        totalPages: 2,
        number: 1,
        size: 20,
        first: false,
        last: true,
      })
    renderActivity()
    expect(await screen.findByText('created task APP-1.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Load more' }))

    expect(await screen.findByText('updated task APP-1.')).toBeInTheDocument()
    expect(notificationApi.activity).toHaveBeenLastCalledWith(1, 20)
  })

  it('shows notification errors and cleans up the socket connection', async () => {
    vi.mocked(notificationApi.list).mockRejectedValue(new Error('request failed'))
    vi.mocked(notificationApi.unreadCount).mockRejectedValue(new Error('request failed'))
    const { unmount } = renderBell()
    await userEvent.setup().click(screen.getByRole('button', { name: /notifications/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Notifications could not be loaded.')
    unmount()
    await waitFor(() => expect(mocks.disconnect).toHaveBeenCalledTimes(1))
  })
})
