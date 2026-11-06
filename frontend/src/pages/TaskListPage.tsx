import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PrimaryButton } from '../components/PrimaryButton'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { projectApi } from '../services/projectService'
import { taskApi } from '../services/taskService'
import type { ProjectResponse, ProjectMemberResponse } from '../types/project'
import type { TaskPage, TaskPriority, TaskResponse, TaskStatus } from '../types/task'
import { apiErrorMessage } from '../utils/apiError'

const taskStatuses: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE']
const taskPriorities: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']

export function TaskListPage() {
  const { projectId = '' } = useParams()
  const navigate = useNavigate()
  const [project, setProject] = useState<ProjectResponse | null>(null)
  const [members, setMembers] = useState<ProjectMemberResponse[]>([])
  const [result, setResult] = useState<TaskPage | null>(null)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<TaskStatus | ''>('')
  const [priority, setPriority] = useState<TaskPriority | ''>('')
  const [assigneeId, setAssigneeId] = useState('')
  const [overdue, setOverdue] = useState(false)
  const [sortBy, setSortBy] = useState<'createdAt' | 'dueDate' | 'priority' | 'updatedAt'>('createdAt')
  const [sortDirection, setSortDirection] = useState<'ASC' | 'DESC'>('DESC')
  const [page, setPage] = useState(0)
  const [loadingProject, setLoadingProject] = useState(true)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [newPriority, setNewPriority] = useState<TaskPriority>('MEDIUM')
  const [newAssignee, setNewAssignee] = useState('')
  const [dueDate, setDueDate] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([projectApi.get(projectId), projectApi.listMembers(projectId)])
      .then(([details, projectMembers]) => {
        if (!active) return
        setProject(details)
        setMembers(projectMembers)
      })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Project details could not be loaded.'))
      })
      .finally(() => { if (active) setLoadingProject(false) })
    return () => { active = false }
  }, [projectId])

  useEffect(() => {
    let active = true
    setLoading(true)
    taskApi.list(projectId, {
      search: search.trim() || undefined,
      status: status || undefined,
      priority: priority || undefined,
      assigneeId: assigneeId || undefined,
      overdue,
      page,
      size: 10,
      sortBy,
      sortDirection,
    })
      .then((tasks) => { if (active) setResult(tasks) })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Tasks could not be loaded.'))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [projectId, search, status, priority, assigneeId, overdue, page, sortBy, sortDirection])

  async function createTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setCreating(true)
    try {
      const task = await taskApi.create(projectId, {
        title: title.trim(),
        description: description.trim(),
        priority: newPriority,
        assigneeId: newAssignee || null,
        dueDate: dueDate || null,
      })
      navigate(`/tasks/${task.id}`)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Task could not be created.'))
    } finally {
      setCreating(false)
    }
  }

  function updateTask(updated: TaskResponse) {
    setResult((current) => current
      ? { ...current, content: current.content.map((item) => item.id === updated.id ? updated : item) }
      : current)
  }

  async function changeStatus(task: TaskResponse, value: TaskStatus) {
    setError('')
    try {
      updateTask(await taskApi.updateStatus(task.id, value))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Task status could not be updated.'))
    }
  }

  async function changePriority(task: TaskResponse, value: TaskPriority) {
    setError('')
    try {
      updateTask(await taskApi.updatePriority(task.id, value))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Task priority could not be updated.'))
    }
  }

  function resetPage<T>(setter: (value: T) => void, value: T) {
    setter(value)
    setPage(0)
  }

  if (loadingProject) {
    return <main className="workspace-page"><WorkspaceHeader /><div className="workspace-loading" role="status"><span className="spinner" />Loading project tasks...</div></main>
  }

  return (
    <main className="workspace-page">
      <WorkspaceHeader />
      <section className="workspace-content task-list-content">
        <Link className="back-link" to={`/projects/${projectId}`}>← {project?.name || 'Project'}</Link>
        <div className="workspace-page-heading project-heading">
          <div>
            <span className="eyebrow">{project?.key || 'PROJECT'} TASKS</span>
            <h1>Tasks</h1>
            <p>Keep the project moving with clear owners and next steps.</p>
          </div>
          <button className="button-primary" type="button" onClick={() => setShowCreate(true)}>
            <span aria-hidden="true">＋</span> Create task
          </button>
        </div>

        {error && !showCreate && <p className="form-message form-error" role="alert">{error}</p>}

        <section className="task-filters" aria-label="Task filters">
          <label className="field task-search">
            <span>Search tasks</span>
            <input type="search" aria-label="Search tasks" value={search} onChange={(event) => resetPage(setSearch, event.target.value)} placeholder="Title or description" />
          </label>
          <label className="field"><span>Status</span>
            <select aria-label="Task status filter" value={status} onChange={(event) => resetPage(setStatus, event.target.value as TaskStatus | '')}>
              <option value="">All statuses</option>
              {taskStatuses.map((item) => <option key={item} value={item}>{item.replace('_', ' ')}</option>)}
            </select>
          </label>
          <label className="field"><span>Priority</span>
            <select aria-label="Task priority filter" value={priority} onChange={(event) => resetPage(setPriority, event.target.value as TaskPriority | '')}>
              <option value="">All priorities</option>
              {taskPriorities.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <label className="field"><span>Assignee</span>
            <select aria-label="Task assignee filter" value={assigneeId} onChange={(event) => resetPage(setAssigneeId, event.target.value)}>
              <option value="">Anyone</option>
              {members.map((member) => <option key={member.userId} value={member.userId}>{member.fullName}</option>)}
            </select>
          </label>
          <label className="field"><span>Sort by</span>
            <select aria-label="Task sort" value={sortBy} onChange={(event) => resetPage(setSortBy, event.target.value as typeof sortBy)}>
              <option value="createdAt">Created date</option>
              <option value="updatedAt">Updated date</option>
              <option value="dueDate">Due date</option>
              <option value="priority">Priority</option>
            </select>
          </label>
          <label className="task-overdue-filter">
            <input type="checkbox" checked={overdue} onChange={(event) => resetPage(setOverdue, event.target.checked)} />
            Overdue only
          </label>
        </section>
        <div className="task-sort-direction">
          <button className="button-quiet" type="button" aria-label="Toggle sort direction" onClick={() => setSortDirection((current) => current === 'ASC' ? 'DESC' : 'ASC')}>
            {sortDirection === 'ASC' ? 'Oldest first ↑' : 'Newest first ↓'}
          </button>
        </div>

        {loading ? (
          <div className="workspace-loading" role="status"><span className="spinner" />Loading tasks...</div>
        ) : !result?.content.length ? (
          <section className="workspace-empty task-empty">
            <span className="empty-icon" aria-hidden="true">✳</span>
            <h2>{search || status || priority || overdue ? 'No tasks match these filters.' : 'Make progress visible.'}</h2>
            <p>{search || status || priority || overdue ? 'Try changing your filters.' : 'Create the first task for this project.'}</p>
            {!search && !status && !priority && !overdue && <button className="button-primary" type="button" onClick={() => setShowCreate(true)}>Create a task</button>}
          </section>
        ) : (
          <>
            <div className="task-grid">
              {result.content.map((task) => (
                <article className="task-card" key={task.id}>
                  <div className="task-card-head">
                    <span className="task-key">{task.taskKey}</span>
                    <span className={`task-priority priority-${task.priority.toLowerCase()}`}>{task.priority}</span>
                  </div>
                  <h2><Link to={`/tasks/${task.id}`}>{task.title}</Link></h2>
                  <p>{task.description || 'No description'}</p>
                  <div className="task-card-fields">
                    <label className="field">
                      <span>Status</span>
                      <select aria-label={`Status for ${task.taskKey}`} value={task.status} onChange={(event) => void changeStatus(task, event.target.value as TaskStatus)}>
                        {taskStatuses.map((item) => <option key={item} value={item}>{item.replace('_', ' ')}</option>)}
                      </select>
                    </label>
                    <label className="field">
                      <span>Priority</span>
                      <select aria-label={`Priority for ${task.taskKey}`} value={task.priority} onChange={(event) => void changePriority(task, event.target.value as TaskPriority)}>
                        {taskPriorities.map((item) => <option key={item} value={item}>{item}</option>)}
                      </select>
                    </label>
                  </div>
                  <div className="task-card-footer">
                    <span>{task.assigneeName ? `Assigned to ${task.assigneeName}` : 'Unassigned'}</span>
                    <span className={task.dueDate && task.status !== 'DONE' && task.dueDate < new Date().toISOString().slice(0, 10) ? 'task-overdue' : ''}>
                      {task.dueDate ? `Due ${task.dueDate}` : 'No due date'}
                    </span>
                    <Link to={`/tasks/${task.id}`}>{task.commentCount} comments →</Link>
                  </div>
                </article>
              ))}
            </div>
            <nav className="project-pagination" aria-label="Task pages">
              <span>{result.totalElements} tasks</span>
              <div>
                <button className="button-outline" type="button" disabled={result.first} onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</button>
                <span>Page {result.number + 1} of {Math.max(result.totalPages, 1)}</span>
                <button className="button-outline" type="button" disabled={result.last} onClick={() => setPage((current) => current + 1)}>Next</button>
              </div>
            </nav>
          </>
        )}
      </section>

      {showCreate && (
        <div className="modal-backdrop">
          <section className="workspace-modal task-modal" role="dialog" aria-modal="true" aria-labelledby="create-task-title">
            <button className="modal-close" type="button" disabled={creating} aria-label="Close dialog" onClick={() => setShowCreate(false)}>×</button>
            <span className="eyebrow">A CLEAR NEXT STEP</span>
            <h2 id="create-task-title">Create a task</h2>
            <p>{project?.name}</p>
            <form className="settings-form" onSubmit={createTask}>
              <label className="field"><span>Task title</span><input aria-label="Task title" required maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="What needs to be done?" autoFocus /></label>
              <label className="field"><span>Description <span className="optional-label">Optional</span></span><textarea rows={3} maxLength={4000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
              <div className="task-form-grid">
                <label className="field"><span>Priority</span><select value={newPriority} onChange={(event) => setNewPriority(event.target.value as TaskPriority)}>{taskPriorities.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
                <label className="field"><span>Assignee</span><select value={newAssignee} onChange={(event) => setNewAssignee(event.target.value)}><option value="">Unassigned</option>{members.map((member) => <option key={member.userId} value={member.userId}>{member.fullName}</option>)}</select></label>
              </div>
              <label className="field"><span>Due date</span><input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} /></label>
              {error && <p className="form-message form-error" role="alert">{error}</p>}
              <div className="form-actions">
                <button className="button-quiet" type="button" disabled={creating} onClick={() => setShowCreate(false)}>Cancel</button>
                <PrimaryButton type="submit" loading={creating}>Create task</PrimaryButton>
              </div>
            </form>
          </section>
        </div>
      )}
    </main>
  )
}
