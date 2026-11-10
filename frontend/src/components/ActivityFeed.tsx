import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { notificationApi } from '../services/notificationService'
import type { Activity, ActivityPage, ActivityType } from '../types/notification'
import { apiErrorMessage } from '../utils/apiError'

const PAGE_SIZE = 20

const activityIcons: Partial<Record<ActivityType, string>> = {
  WORKSPACE_CREATED: '▦',
  WORKSPACE_UPDATED: '✎',
  WORKSPACE_MEMBER_ADDED: '＋',
  PROJECT_CREATED: '✦',
  PROJECT_UPDATED: '✎',
  PROJECT_MEMBER_ADDED: '＋',
  TASK_CREATED: '✓',
  TASK_UPDATED: '✎',
  TASK_ASSIGNED: '↗',
  TASK_REASSIGNED: '↗',
  TASK_STATUS_CHANGED: '◷',
  TASK_PRIORITY_CHANGED: '↟',
  TASK_COMMENT_ADDED: '▤',
  CHAT_MESSAGE_SENT: '◌',
  CHAT_MESSAGE_EDITED: '✎',
  CHAT_MESSAGE_DELETED: '×',
  FILE_UPLOADED: '⇧',
  FILE_DELETED: '×',
}

function activityDay(createdAt: string) {
  const date = new Date(createdAt)
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  if (date.toDateString() === today.toDateString()) return 'Today'
  if (date.toDateString() === yesterday.toDateString()) return 'Yesterday'
  return date.toLocaleDateString(undefined, { month: 'long', day: 'numeric', year: 'numeric' })
}

export function ActivityFeed({ initialActivities, initialPage }: {
  initialActivities?: Activity[]
  initialPage?: ActivityPage
}) {
  const [page, setPage] = useState<ActivityPage | null>(initialPage ?? null)
  const [activities, setActivities] = useState<Activity[]>(initialActivities ?? [])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (initialPage) {
      setLoading(false)
      return
    }
    let active = true
    notificationApi.activity(0, PAGE_SIZE)
      .then((result) => {
        if (!active) return
        setPage(result)
        setActivities(result.content)
      })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Activity could not be loaded.'))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [initialPage])

  async function loadMore() {
    if (!page || page.last) return
    setLoadingMore(true)
    setError('')
    try {
      const next = await notificationApi.activity(page.number + 1, PAGE_SIZE)
      setActivities((current) => {
        const byId = new Map(current.map((activity) => [activity.id, activity]))
        next.content.forEach((activity) => byId.set(activity.id, activity))
        return [...byId.values()]
      })
      setPage(next)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'More activity could not be loaded.'))
    } finally {
      setLoadingMore(false)
    }
  }

  if (loading) return <p className="workspace-loading" role="status">Loading activity...</p>
  if (error && activities.length === 0) return <p className="form-message form-error" role="alert">{error}</p>
  if (activities.length === 0) {
    return <section className="activity-empty"><h2>No activity yet</h2><p>Updates from your workspaces and projects will appear here.</p></section>
  }

  const groupedActivities = activities.reduce((groups, activity) => {
    const day = activityDay(activity.createdAt)
    const group = groups.get(day) ?? []
    group.push(activity)
    groups.set(day, group)
    return groups
  }, new Map<string, Activity[]>())

  return (
    <section className="activity-feed" aria-label="Activity feed">
      {error && <p className="form-message form-error" role="alert">{error}</p>}
      {[...groupedActivities.entries()].map(([day, dayActivities]) => (
        <section className="activity-day-group" key={day}>
          <h2>{day}</h2>
          <ol className="activity-list">
            {dayActivities.map((activity) => (
              <li className="activity-item" key={activity.id}>
                <span className="activity-marker" aria-hidden="true">{activityIcons[activity.type] || '•'}</span>
                <span className="activity-avatar" aria-hidden="true">{activity.actorName.trim().charAt(0).toUpperCase() || '?'}</span>
                <div className="activity-item-body">
                  <p><strong>{activity.actorName}</strong> {activity.description.replace(`${activity.actorName} `, '')}</p>
                  <div className="activity-context">
                    {activity.taskId && <Link to={`/tasks/${activity.taskId}`}>{activity.taskKey || activity.taskTitle || 'View task'}</Link>}
                    {!activity.taskId && activity.projectId && <Link to={`/projects/${activity.projectId}`}>{activity.projectName || 'View project'}</Link>}
                    {!activity.projectId && <Link to={`/workspaces/${activity.workspaceId}`}>View workspace</Link>}
                    <time dateTime={activity.createdAt}>{new Date(activity.createdAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}</time>
                  </div>
                </div>
              </li>
            ))}
          </ol>
        </section>
      ))}
      {page && !page.last && (
        <button className="button-outline activity-load-more" type="button" disabled={loadingMore} onClick={() => void loadMore()}>
          {loadingMore ? 'Loading...' : 'Load more'}
        </button>
      )}
    </section>
  )
}
