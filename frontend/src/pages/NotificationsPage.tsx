import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Bell, CheckCheck } from 'lucide-react'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { notificationApi } from '../services/notificationService'
import type { Notification, NotificationPage } from '../types/notification'
import { apiErrorMessage } from '../utils/apiError'

function notificationHref(notification: Notification) {
  if (notification.taskId) return `/tasks/${notification.taskId}`
  if (notification.projectId) return `/projects/${notification.projectId}`
  if (notification.workspaceId) return `/workspaces/${notification.workspaceId}`
  return '/activity'
}

export function NotificationsPage() {
  const [page, setPage] = useState<NotificationPage | null>(null)
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')

  async function load(pageIndex = 0, append = false) {
    if (append) setLoadingMore(true)
    else setLoading(true)
    setError('')
    try {
      const result = await notificationApi.list(pageIndex, 20)
      setPage(result)
      setNotifications((current) => append ? [...current, ...result.content] : result.content)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Notifications could not be loaded.'))
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }

  useEffect(() => { void load() }, [])

  async function markRead(notification: Notification) {
    if (notification.read) return
    try {
      const updated = await notificationApi.markRead(notification.id)
      setNotifications((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Notification could not be marked as read.'))
    }
  }

  async function markAllRead() {
    setError('')
    try {
      await notificationApi.markAllRead()
      setNotifications((current) => current.map((item) => ({ ...item, read: true })))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Notifications could not be marked as read.'))
    }
  }

  const hasUnread = notifications.some((item) => !item.read)

  return (
    <main className="workspace-page app-directory-page">
      <WorkspaceHeader />
      <section className="workspace-content notifications-page-content">
        <header className="directory-heading">
          <div><span className="eyebrow">KEEP IN THE LOOP</span><h1>Notifications</h1><p>Updates about the work and people you follow.</p></div>
          <button className="button-outline" type="button" disabled={!hasUnread} onClick={() => void markAllRead()}><CheckCheck size={15} /> Mark all read</button>
        </header>
        {error && <div className="widget-error" role="alert"><p>{error}</p><button className="button-outline" type="button" onClick={() => void load()}>Retry</button></div>}
        {loading ? <div className="workspace-loading" role="status"><span className="spinner" />Loading notifications...</div>
          : notifications.length === 0 ? <div className="command-empty"><Bell size={24} /><strong>You’re all caught up</strong><span>New updates will appear here.</span></div>
            : <ul className="full-notification-list">{notifications.map((item) => (
              <li className={item.read ? '' : 'full-notification-unread'} key={item.id}>
                <span className="notification-status-dot" aria-hidden="true" />
                <div><Link to={notificationHref(item)} onClick={() => void markRead(item)}><strong>{item.title}</strong><p>{item.message}</p></Link><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString()}</time></div>
                <Link className="notification-open-link" to={notificationHref(item)} aria-label={`Open ${item.title}`}><ArrowRight size={16} /></Link>
              </li>
            ))}</ul>}
        {page && !page.last && !loading && <button className="button-outline notification-more" disabled={loadingMore} type="button" onClick={() => void load(page.number + 1, true)}>{loadingMore ? 'Loading…' : 'Load more notifications'}</button>}
      </section>
    </main>
  )
}
