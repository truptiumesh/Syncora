import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, ListTodo } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { listTasksForProjects, listVisibleProjects, type WorkspaceProject } from '../services/workspaceDataService'
import { workspaceApi } from '../services/workspaceService'
import type { TaskResponse } from '../types/task'
import { apiErrorDetails } from '../utils/apiError'

export function TasksOverviewPage() {
  const { user } = useAuth()
  const [tasks, setTasks] = useState<TaskResponse[]>([])
  const [projects, setProjects] = useState<WorkspaceProject[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function load() {
    setLoading(true)
    setError('')
    try {
      const workspaces = await workspaceApi.list()
      const projectResult = await listVisibleProjects(workspaces)
      setProjects(projectResult.projects)
      const taskResult = await listTasksForProjects(projectResult.projects)
      setTasks(taskResult.taskSets.flatMap(({ tasks: projectTasks }) => projectTasks))
      const failures = projectResult.failures + taskResult.failures
      if (failures) setError([...projectResult.failureDetails, ...taskResult.failureDetails].join(' '))
    } catch (requestError) {
      setError(apiErrorDetails(requestError, 'Tasks could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  const projectNames = useMemo(
    () => new Map(projects.map(({ project }) => [project.id, project])),
    [projects],
  )
  const myTasks = useMemo(
    () => tasks.filter((task) => task.assigneeId === user?.id)
      .sort((a, b) => (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999')),
    [tasks, user?.id],
  )

  return (
    <main className="workspace-page app-directory-page">
      <WorkspaceHeader />
      <section className="workspace-content">
        <header className="directory-heading"><div><span className="eyebrow">YOUR ACTIONS</span><h1>My tasks</h1><p>Tasks assigned to you across projects you can access.</p></div></header>
        {error && <div className="widget-error" role="alert"><p>{error}</p><button className="button-outline" type="button" onClick={() => void load()}>Retry</button></div>}
        {loading ? <div className="workspace-loading" role="status"><span className="spinner" />Loading tasks...</div>
          : myTasks.length === 0 ? <div className="command-empty"><ListTodo size={24} /><strong>No assigned tasks</strong><span>When a project task is assigned to you, it will appear here.</span><Link to="/projects">Browse projects</Link></div>
            : <div className="all-tasks-list">
              {myTasks.map((task) => {
                const project = projectNames.get(task.projectId)
                const overdue = Boolean(task.dueDate && task.dueDate < new Date().toISOString().slice(0, 10) && task.status !== 'DONE')
                return (
                  <article className="all-task-row" key={task.id}>
                    <span className={`task-priority-dot priority-${task.priority.toLowerCase()}`} aria-label={`${task.priority} priority`} />
                    <Link className="all-task-title" to={`/tasks/${task.id}`}><small>{task.taskKey} · {project?.name ?? 'Project'}</small><strong>{task.title}</strong></Link>
                    <span className={`command-task-status status-${task.status.toLowerCase().replace('_', '-')}`}>{task.status.replace('_', ' ')}</span>
                    <span className={overdue ? 'task-overdue' : 'all-task-date'}>{task.dueDate ? `${overdue ? 'Overdue · ' : ''}${new Date(`${task.dueDate}T00:00:00`).toLocaleDateString()}` : 'No due date'}</span>
                    <Link to={`/tasks/${task.id}`} aria-label={`Open ${task.taskKey}`}><ArrowRight size={16} /></Link>
                  </article>
                )
              })}
            </div>}
      </section>
    </main>
  )
}
