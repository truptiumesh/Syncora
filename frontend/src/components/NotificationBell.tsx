import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { notificationApi } from '../services/notificationService'
import {
  connectNotifications,
  type NotificationConnection,
  type NotificationConnectionState,
} from '../services/notificationWebSocketService'
import type { Notification } from '../types/notification'
import { apiErrorMessage } from '../utils/apiError'

const RECENT_LIMIT = 10

function mergeNotifications(current: Notification[], incoming: Notification[]) {
  const byId = new Map(current.map((notification) => [notification.id, notification]))
  incoming.forEach((notification) => byId.set(notification.id, notification))
  return [...byId.values()]
    .sort((first, second) => second.createdAt.localeCompare(first.createdAt) || second.id.localeCompare(first.id))
    .slice(0, RECENT_LIMIT)
}

function notificationLink(notification: Notification) {
  if (notification.taskId) return `/tasks/${notification.taskId}`
  if (notification.projectId) return `/projects/${notification.projectId}`
  if (notification.workspaceId) return `/workspaces/${notification.workspaceId}`
  return '/activity'
}

export function NotificationBell() {
  const { token } = useAuth()
  const connectionRef = useRef<NotificationConnection | null>(null)
  const seenNotificationIds = useRef(new Set<string>())
  const [open, setOpen] = useState(false)
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [connectionState, setConnectionState] = useState<NotificationConnectionState>('disconnected')
  const liveNotificationVersion = useRef(0)

  const receiveNotification = useCallback((notification: Notification) => {
    const alreadySeen = seenNotificationIds.current.has(notification.id)
    seenNotificationIds.current.add(notification.id)
    if (!alreadySeen) liveNotificationVersion.current += 1
    setNotifications((current) => mergeNotifications(current, [notification]))
    if (!alreadySeen && !notification.read) {
      setUnreadCount((current) => current + 1)
    }
  }, [])

  useEffect(() => {
    if (!token) {
      setNotifications([])
      seenNotificationIds.current.clear()
      setUnreadCount(0)
      setConnectionState('disconnected')
      return
    }
    let active = true
    setError('')
    const connection = connectNotifications(token, receiveNotification, (state) => {
      if (active) setConnectionState(state)
    })
    connectionRef.current = connection
    const versionAtCountRequest = liveNotificationVersion.current
    notificationApi.list(0, RECENT_LIMIT)
      .then((page) => {
        if (!active) return
        page.content.forEach((notification) => seenNotificationIds.current.add(notification.id))
        setNotifications((current) => mergeNotifications(current, page.content))
      })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Notifications could not be loaded.'))
      })
    notificationApi.unreadCount()
      .then((count) => {
        if (active) setUnreadCount((current) =>
          liveNotificationVersion.current === versionAtCountRequest ? count : Math.max(current, count))
      })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Unread count could not be loaded.'))
      })
    return () => {
      active = false
      connectionRef.current = null
      void connection.disconnect()
    }
  }, [receiveNotification, token])

  async function toggleOpen() {
    const nextOpen = !open
    setOpen(nextOpen)
    if (!nextOpen) return
    setLoading(true)
    setError('')
    const versionAtCountRequest = liveNotificationVersion.current
    try {
      const [page, count] = await Promise.all([
        notificationApi.list(0, RECENT_LIMIT),
        notificationApi.unreadCount(),
      ])
      page.content.forEach((notification) => seenNotificationIds.current.add(notification.id))
      setNotifications((current) => mergeNotifications(current, page.content))
      setUnreadCount((current) =>
        liveNotificationVersion.current === versionAtCountRequest ? count : Math.max(current, count))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Notifications could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  async function markRead(notification: Notification) {
    if (notification.read) return
    setError('')
    try {
      const updated = await notificationApi.markRead(notification.id)
      setNotifications((current) => current.map((item) => item.id === updated.id ? updated : item))
      setUnreadCount((current) => Math.max(0, current - 1))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Notification could not be marked as read.'))
    }
  }

  async function markAllRead() {
    setError('')
    const unreadCountAtRequest = unreadCount
    const notificationIdsAtRequest = new Set(notifications.map((notification) => notification.id))
    try {
      await notificationApi.markAllRead()
      setNotifications((current) => current.map((notification) =>
        notificationIdsAtRequest.has(notification.id) ? { ...notification, read: true } : notification))
      setUnreadCount((current) => Math.max(0, current - unreadCountAtRequest))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Notifications could not be marked as read.'))
    }
  }

  const connectionLabel = connectionState === 'connected' ? 'Live updates connected'
    : connectionState === 'connecting' ? 'Connecting to live updates'
      : connectionState === 'reconnecting' ? 'Reconnecting live updates'
        : connectionState === 'error' ? 'Live updates unavailable'
          : 'Live updates disconnected'

  return (
    <div className="notification-bell">
      <button
        className="notification-bell-button"
        type="button"
        aria-label={`Notifications${unreadCount ? `, ${unreadCount} unread` : ''}`}
        aria-expanded={open}
        onClick={() => void toggleOpen()}
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
          <path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
        </svg>
        {unreadCount > 0 && <span className="notification-count">{unreadCount > 99 ? '99+' : unreadCount}</span>}
      </button>
      {open && (
        <section className="notification-panel" aria-label="Notifications">
          <header className="notification-panel-header">
            <div>
              <strong>Notifications</strong>
              <span>{connectionLabel}</span>
            </div>
            <button type="button" className="button-quiet" onClick={() => void markAllRead()} disabled={unreadCount === 0}>
              Mark all read
            </button>
          </header>
          {error && <p className="form-message form-error" role="alert">{error}</p>}
          {loading ? (
            <p className="notification-state" role="status">Loading notifications...</p>
          ) : notifications.length === 0 ? (
            <p className="notification-state">You’re all caught up.</p>
          ) : (
            <ul className="notification-list">
              {notifications.map((notification) => (
                <li className={notification.read ? 'notification-item' : 'notification-item notification-unread'} key={notification.id}>
                  <Link to={notificationLink(notification)} onClick={() => { void markRead(notification); setOpen(false) }}>
                    <strong>{notification.title}</strong>
                    <span>{notification.message}</span>
                    <time dateTime={notification.createdAt}>{new Date(notification.createdAt).toLocaleString()}</time>
                  </Link>
                  {!notification.read && (
                    <button className="button-quiet notification-mark-read" type="button" onClick={() => void markRead(notification)}>
                      Mark read
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
          <Link className="notification-activity-link" to="/activity" onClick={() => setOpen(false)}>View activity feed</Link>
        </section>
      )}
    </div>
  )
}
