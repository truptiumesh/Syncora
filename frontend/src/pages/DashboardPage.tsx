import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  ArrowUpRight,
  CalendarClock,
  CheckCircle2,
  CircleAlert,
  FolderKanban,
  ListTodo,
  MessageCircle,
  Plus,
  Sparkles,
  UsersRound,
} from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { ActivityFeed } from '../components/ActivityFeed'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { notificationApi } from '../services/notificationService'
import { workspaceApi } from '../services/workspaceService'
import { listTasksForProjects, listVisibleProjects, type WorkspaceProject } from '../services/workspaceDataService'
import type { Activity, ActivityPage } from '../types/notification'
import type { TaskResponse } from '../types/task'
import type { WorkspaceResponse } from '../types/workspace'
import { apiErrorDetails } from '../utils/apiError'

interface Resource<T> {
  data: T
  loading: boolean
  error: string
}

function resource<T>(data: T): Resource<T> {
  return { data, loading: true, error: '' }
}

function localDate(value: string) {
  return new Date(`${value}T00:00:00`)
}

function dateOnly(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function greeting() {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

function StatCard({ title, value, detail, Icon, tone, loading, error, onRetry }: {
  title: string
  value: number | string
  detail: string
  Icon: typeof UsersRound
  tone: string
  loading: boolean
  error: string
  onRetry: () => void
}) {
  return (
    <article className={`command-stat command-stat-${tone}`}>
      <div className="command-stat-icon"><Icon size={19} strokeWidth={1.8} /></div>
      <span className="command-stat-label">{title}</span>
      {error ? (
        <div className="command-stat-error" role="status"><span>Unavailable</span><button type="button" onClick={onRetry}>Retry</button></div>
      ) : (
        <strong className="command-stat-value">{loading ? <span className="skeleton-value" aria-label="Loading">—</span> : value}</strong>
      )}
      <span className="command-stat-detail">{detail}</span>
    </article>
  )
}

function WidgetError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="widget-error" role="alert">
      <CircleAlert size={18} aria-hidden="true" />
      <div><strong>Couldn’t load this section</strong><p>{message}</p></div>
      <button className="button-outline" type="button" onClick={onRetry}>Retry</button>
    </div>
  )
}

export function DashboardPage() {
  const { user } = useAuth()
  const [workspaces, setWorkspaces] = useState<Resource<WorkspaceResponse[]>>(() => resource([]))
  const [projects, setProjects] = useState<Resource<WorkspaceProject[]>>(() => resource([]))
  const [tasks, setTasks] = useState<Resource<TaskResponse[]>>(() => resource([]))
  const [activities, setActivities] = useState<Resource<Activity[]>>(() => resource([]))

  const loadActivities = useCallback(async () => {
    setActivities((current) => ({ ...current, loading: true, error: '' }))
    try {
      const page = await notificationApi.activity(0, 5)
      setActivities({ data: page.content, loading: false, error: '' })
    } catch (requestError) {
      setActivities((current) => ({
        ...current,
        loading: false,
        error: apiErrorDetails(requestError, 'Recent activity could not be loaded.'),
      }))
    }
  }, [])

  const loadTasks = useCallback(async (visibleProjects: WorkspaceProject[]) => {
    if (visibleProjects.length === 0) {
      setTasks({ data: [], loading: false, error: '' })
      return
    }
    setTasks((current) => ({ ...current, loading: true, error: '' }))
    try {
      const result = await listTasksForProjects(visibleProjects)
      const allTasks = result.taskSets.flatMap(({ tasks: projectTasks }) => projectTasks)
      setTasks({
        data: allTasks,
        loading: false,
        error: result.failureDetails.join(' ') || '',
      })
    } catch (requestError) {
      setTasks((current) => ({
        ...current,
        loading: false,
        error: apiErrorDetails(requestError, 'Task data could not be loaded.'),
      }))
    }
  }, [])

  const loadProjects = useCallback(async (workspaceItems: WorkspaceResponse[]) => {
    if (workspaceItems.length === 0) {
      setProjects({ data: [], loading: false, error: '' })
      setTasks({ data: [], loading: false, error: '' })
      return
    }
    setProjects((current) => ({ ...current, loading: true, error: '' }))
    try {
      const result = await listVisibleProjects(workspaceItems)
      setProjects({
        data: result.projects,
        loading: false,
        error: result.failureDetails.join(' ') || '',
      })
      await loadTasks(result.projects)
    } catch (requestError) {
      const message = apiErrorDetails(requestError, 'Project data could not be loaded.')
      setProjects((current) => ({ ...current, loading: false, error: message }))
      setTasks((current) => ({ ...current, loading: false, error: 'Tasks are unavailable until project data loads.' }))
    }
  }, [loadTasks])

  const loadWorkspaces = useCallback(async () => {
    setWorkspaces((current) => ({ ...current, loading: true, error: '' }))
    try {
      const items = await workspaceApi.list()
      setWorkspaces({ data: items, loading: false, error: '' })
      await loadProjects(items)
    } catch (requestError) {
      const message = apiErrorDetails(requestError, 'Your workspaces could not be loaded.')
      setWorkspaces((current) => ({ ...current, loading: false, error: message }))
      setProjects((current) => ({ ...current, loading: false, error: 'Projects are unavailable until workspaces load.' }))
      setTasks((current) => ({ ...current, loading: false, error: 'Tasks are unavailable until workspaces load.' }))
    }
  }, [loadProjects])

  useEffect(() => {
    void loadWorkspaces()
    void loadActivities()
  }, [loadActivities, loadWorkspaces, user?.id])

  const activeProjectCount = useMemo(
    () => projects.data.filter(({ project }) => project.status === 'ACTIVE').length,
    [projects.data],
  )
  const myTasks = useMemo(
    () => tasks.data.filter((task) => task.assigneeId === user?.id),
    [tasks.data, user?.id],
  )
  const completedTasks = myTasks.filter((task) => task.status === 'DONE')
  const taskProjectById = useMemo(
    () => new Map(projects.data.map(({ project, workspaceName }) => [project.id, { project, workspaceName }])),
    [projects.data],
  )
  const projectProgress = useMemo(() => {
    const progress = new Map<string, { total: number; completed: number }>()
    tasks.data.forEach((task) => {
      const current = progress.get(task.projectId) ?? { total: 0, completed: 0 }
      current.total += 1
      if (task.status === 'DONE') current.completed += 1
      progress.set(task.projectId, current)
    })
    return progress
  }, [tasks.data])
  const recentProjects = useMemo(
    () => [...projects.data].sort((a, b) => b.project.updatedAt.localeCompare(a.project.updatedAt)).slice(0, 5),
    [projects.data],
  )
  const recentTasks = useMemo(() => {
    return [...myTasks]
      .sort((a, b) => {
        const statusOrder = Number(a.status === 'DONE') - Number(b.status === 'DONE')
        return statusOrder
          || (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31')
          || b.updatedAt.localeCompare(a.updatedAt)
      })
      .slice(0, 5)
  }, [myTasks])
  const deadlineTasks = useMemo(() => {
    const today = dateOnly(new Date())
    const limit = new Date()
    limit.setDate(limit.getDate() + 14)
    const limitDate = dateOnly(limit)
    return tasks.data
      .filter((task) => task.dueDate && task.status !== 'DONE' && task.dueDate <= limitDate)
      .sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? ''))
      .slice(0, 5)
      .map((task) => ({ task, overdue: task.dueDate! < today }))
  }, [tasks.data])

  const projectRetry = () => void (workspaces.error ? loadWorkspaces() : loadProjects(workspaces.data))
  const taskRetry = () => void (workspaces.error || projects.error ? loadWorkspaces() : loadTasks(projects.data))
  const projectError = projects.error || (workspaces.error ? 'Projects could not be loaded because workspace access failed.' : '')
  const taskError = tasks.error
    || (projects.error ? 'Task totals may be incomplete because project access failed.' : '')
  const activityPage: ActivityPage = {
    content: activities.data,
    totalElements: activities.data.length,
    totalPages: 1,
    number: 0,
    size: 5,
    first: true,
    last: true,
  }

  return (
    <main className="workspace-page command-page">
      <WorkspaceHeader />
      <section className="workspace-content command-content">
        <header className="command-welcome">
          <div>
            <span className="eyebrow">{new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).toUpperCase()}</span>
            <h1>{greeting()}, {user?.fullName.split(' ')[0] || 'there'} <Sparkles className="welcome-sparkle" size={18} aria-hidden="true" /></h1>
            <p>Here’s what’s happening across your workspace.</p>
          </div>
          <Link className="button-primary command-create-workspace" to="/workspaces?create=true"><Plus size={16} /> Create workspace</Link>
        </header>

        <section className="command-stats" aria-label="Workspace overview">
          <StatCard title="Total workspaces" value={workspaces.data.length} detail="Spaces you belong to" Icon={UsersRound} tone="violet" loading={workspaces.loading} error={workspaces.error} onRetry={() => void loadWorkspaces()} />
          <StatCard title="Active projects" value={activeProjectCount} detail="Currently in progress" Icon={FolderKanban} tone="blue" loading={projects.loading || workspaces.loading} error={projectError} onRetry={projectRetry} />
          <StatCard title="My tasks" value={myTasks.length} detail="Assigned across your projects" Icon={ListTodo} tone="indigo" loading={tasks.loading || projects.loading} error={taskError} onRetry={taskRetry} />
          <StatCard title="Completed tasks" value={completedTasks.length} detail="Assigned tasks completed" Icon={CheckCircle2} tone="green" loading={tasks.loading || projects.loading} error={taskError} onRetry={taskRetry} />
        </section>

        <section className="command-quick-actions" aria-label="Quick actions">
          <Link to="/workspaces?create=true"><span className="quick-action-icon quick-action-violet"><Plus size={17} /></span><span><strong>Create workspace</strong><small>Bring a team together</small></span><ArrowRight size={15} /></Link>
          <Link to={workspaces.data[0] ? `/workspaces/${workspaces.data[0].id}/projects?create=true` : '/workspaces?create=true'}><span className="quick-action-icon quick-action-blue"><FolderKanban size={17} /></span><span><strong>Create project</strong><small>Start a new initiative</small></span><ArrowRight size={15} /></Link>
          <Link to={recentProjects[0] ? `/projects/${recentProjects[0].project.id}/tasks?create=true` : '/projects'}><span className="quick-action-icon quick-action-indigo"><ListTodo size={17} /></span><span><strong>Create task</strong><small>Add the next action</small></span><ArrowRight size={15} /></Link>
          <Link to={workspaces.data[0] ? `/workspaces/${workspaces.data[0].id}/messages` : '/messages'}><span className="quick-action-icon quick-action-sky"><MessageCircle size={17} /></span><span><strong>Start a message</strong><small>Reach a teammate</small></span><ArrowRight size={15} /></Link>
        </section>

        <div className="command-grid">
          <div className="command-main-column">
            <section className="command-panel">
              <header className="command-panel-heading">
                <div><span className="eyebrow">YOUR FOCUS</span><h2>My tasks</h2></div>
                <Link to="/tasks">View all tasks <ArrowRight size={14} /></Link>
              </header>
              {taskError ? <WidgetError message={tasks.error || taskError} onRetry={taskRetry} /> : tasks.loading ? (
                <div className="command-loading" role="status">Loading tasks…</div>
              ) : recentTasks.length === 0 ? (
                <div className="command-empty"><ListTodo size={22} /><strong>You’re all caught up</strong><span>Assigned tasks with upcoming due dates will appear here.</span></div>
              ) : (
                <ul className="command-task-list">
                  {recentTasks.map((task) => {
                    const projectInfo = taskProjectById.get(task.projectId)
                    const overdue = Boolean(task.dueDate && task.dueDate < dateOnly(new Date()))
                    return (
                      <li key={task.id}>
                        <Link className="command-task-main" to={`/tasks/${task.id}`}>
                          <span className={`task-priority-dot priority-${task.priority.toLowerCase()}`} aria-label={`${task.priority} priority`} />
                          <span><strong>{task.title}</strong><small>{projectInfo?.project.key ?? 'Project'} · {projectInfo?.project.name ?? 'Project'}</small></span>
                        </Link>
                        <span className={`command-task-status status-${task.status.toLowerCase().replace('_', '-')}`}>{task.status.replace('_', ' ')}</span>
                        <span className={`command-task-due${overdue ? ' task-overdue' : ''}`}>{task.dueDate ? (overdue ? 'Overdue · ' : '') + localDate(task.dueDate).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) : 'No due date'}</span>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>

            <section className="command-panel">
              <header className="command-panel-heading">
                <div><span className="eyebrow">WHAT’S MOVING</span><h2>Recent projects</h2></div>
                <Link to="/projects">View all projects <ArrowRight size={14} /></Link>
              </header>
              {projectError ? <WidgetError message={projects.error || projectError} onRetry={projectRetry} /> : projects.loading ? (
                <div className="command-loading" role="status">Loading projects…</div>
              ) : recentProjects.length === 0 ? (
                <div className="command-empty"><FolderKanban size={22} /><strong>No projects yet</strong><span>Create your first project to organize work.</span><Link to="/workspaces?create=true">Choose a workspace</Link></div>
              ) : (
                <ul className="command-project-list">
                  {recentProjects.map(({ project }) => {
                    const counts = projectProgress.get(project.id) ?? { total: 0, completed: 0 }
                    const progress = counts.total ? Math.round((counts.completed / counts.total) * 100) : 0
                    return (
                      <li key={project.id}>
                        <div className="command-project-name"><span className="project-key">{project.key}</span><Link to={`/projects/${project.id}`}><strong>{project.name}</strong></Link><small>{projects.data.find((item) => item.project.id === project.id)?.workspaceName}</small></div>
                        <span className={`project-status status-${project.status.toLowerCase().replace('_', '-')}`}>{project.status.replace('_', ' ')}</span>
                        <div className="project-progress"><div><span>Progress</span><strong>{tasks.loading || taskError ? '—' : counts.total ? `${progress}%` : 'No tasks'}</strong></div><div className="progress-track"><span style={{ width: `${tasks.loading || taskError ? 0 : progress}%` }} /></div></div>
                        <Link className="project-open-link" to={`/projects/${project.id}`} aria-label={`Open ${project.name}`}><ArrowUpRight size={16} /></Link>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>

            <section className="command-panel">
              <header className="command-panel-heading">
                <div><span className="eyebrow">TEAM PULSE</span><h2>Recent activity</h2></div>
                <Link to="/activity">View all activity <ArrowRight size={14} /></Link>
              </header>
              {activities.error ? <WidgetError message={activities.error} onRetry={() => void loadActivities()} /> : activities.loading ? (
                <div className="command-loading" role="status">Loading activity…</div>
              ) : <ActivityFeed initialActivities={activities.data} initialPage={activityPage} />}
            </section>
          </div>

          <aside className="command-side-column">
            <section className="command-panel command-deadline-panel">
              <header className="command-panel-heading">
                <div><span className="eyebrow">STAY AHEAD</span><h2>Upcoming deadlines</h2></div>
                <Link to="/calendar" aria-label="Open calendar"><CalendarClock size={16} /></Link>
              </header>
              {taskError ? <WidgetError message={tasks.error || taskError} onRetry={taskRetry} /> : tasks.loading ? (
                <div className="command-loading" role="status">Loading deadlines…</div>
              ) : deadlineTasks.length === 0 ? (
                <div className="command-empty"><CalendarClock size={22} /><strong>No upcoming deadlines</strong><span>Tasks due in the next two weeks will appear here.</span></div>
              ) : (
                <ul className="deadline-list">
                  {deadlineTasks.map(({ task, overdue }) => (
                    <li key={task.id} className={overdue ? 'deadline-overdue' : ''}>
                      <span className={`deadline-date ${overdue ? 'deadline-date-overdue' : ''}`}><strong>{localDate(task.dueDate!).toLocaleDateString(undefined, { day: '2-digit' })}</strong><small>{localDate(task.dueDate!).toLocaleDateString(undefined, { month: 'short' })}</small></span>
                      <Link to={`/tasks/${task.id}`}><strong>{task.title}</strong><small>{task.taskKey} · {taskProjectById.get(task.projectId)?.project.name ?? 'Project'}</small></Link>
                      <span className={`task-priority-dot priority-${task.priority.toLowerCase()}`} aria-label={`${task.priority} priority`} />
                    </li>
                  ))}
                </ul>
              )}
              <Link className="deadline-calendar-link" to="/calendar">Open calendar <ArrowRight size={14} /></Link>
            </section>

            <section className="command-panel command-workspace-panel">
              <header className="command-panel-heading"><div><span className="eyebrow">YOUR TEAMS</span><h2>Workspaces</h2></div><Link to="/workspaces" aria-label="View all workspaces"><ArrowRight size={15} /></Link></header>
              {workspaces.error ? <WidgetError message={workspaces.error} onRetry={() => void loadWorkspaces()} /> : workspaces.loading ? (
                <div className="command-loading" role="status">Loading workspaces…</div>
              ) : workspaces.data.length === 0 ? (
                <div className="command-empty"><UsersRound size={22} /><strong>No workspace yet</strong><span>Create a team space to get started.</span><Link to="/workspaces?create=true">Create workspace</Link></div>
              ) : (
                <ul className="command-workspace-list">
                  {workspaces.data.slice(0, 4).map((workspace) => (
                    <li key={workspace.id}><Link to={`/workspaces/${workspace.id}`}><span className="workspace-monogram">{workspace.name.trim().charAt(0).toUpperCase()}</span><span><strong>{workspace.name}</strong><small>{workspace.memberCount} members</small></span><ArrowRight size={14} /></Link></li>
                  ))}
                </ul>
              )}
            </section>

            <section className="command-tip-card">
              <span className="command-tip-decoration" aria-hidden="true" />
              <span className="eyebrow">A CLEARER WAY TO WORK</span>
              <h2>One place. Better momentum.</h2>
              <p>Keep decisions, conversations, and next steps connected across your team.</p>
              <Link to="/workspaces">Explore your workspace <ArrowRight size={14} /></Link>
            </section>
          </aside>
        </div>
      </section>
    </main>
  )
}
