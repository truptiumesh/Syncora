import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { PrimaryButton } from '../components/PrimaryButton'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { projectApi } from '../services/projectService'
import { taskApi } from '../services/taskService'
import { workspaceApi } from '../services/workspaceService'
import type { ProjectResponse, ProjectMemberResponse } from '../types/project'
import type { TaskCommentResponse, TaskPriority, TaskResponse, TaskStatus } from '../types/task'
import type { WorkspaceResponse } from '../types/workspace'
import { apiErrorMessage } from '../utils/apiError'

const taskStatuses: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE']
const taskPriorities: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']

export function TaskDetailsPage() {
  const { taskId = '' } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [task, setTask] = useState<TaskResponse | null>(null)
  const [project, setProject] = useState<ProjectResponse | null>(null)
  const [workspace, setWorkspace] = useState<WorkspaceResponse | null>(null)
  const [members, setMembers] = useState<ProjectMemberResponse[]>([])
  const [comments, setComments] = useState<TaskCommentResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [commenting, setCommenting] = useState(false)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [commentContent, setCommentContent] = useState('')
  const [editingComment, setEditingComment] = useState<string | null>(null)
  const [editingContent, setEditingContent] = useState('')

  const loadTask = useCallback(async () => {
    setError('')
    try {
      const details = await taskApi.get(taskId)
      const [projectDetails, taskComments] = await Promise.all([
        projectApi.get(details.projectId),
        taskApi.listComments(taskId),
      ])
      const [workspaceDetails, projectMembers] = await Promise.all([
        workspaceApi.get(projectDetails.workspaceId),
        projectApi.listMembers(details.projectId),
      ])
      setTask(details)
      setProject(projectDetails)
      setWorkspace(workspaceDetails)
      setMembers(projectMembers)
      setComments(taskComments)
      setTitle(details.title)
      setDescription(details.description ?? '')
      setDueDate(details.dueDate ?? '')
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Task details could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [taskId])

  useEffect(() => { void loadTask() }, [loadTask])

  const workspaceAdmin = workspace?.currentUserRole === 'OWNER' || workspace?.currentUserRole === 'ADMIN'
  const projectManager = project?.currentUserRole === 'PROJECT_MANAGER'
  const canModerateComments = workspaceAdmin || projectManager

  async function saveTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!task) return
    setError('')
    setSaving(true)
    try {
      const updated = await taskApi.update(task.id, {
        title: title.trim(),
        description: description.trim(),
        dueDate: dueDate || null,
      })
      setTask(updated)
      setEditing(false)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Task details could not be saved.'))
    } finally {
      setSaving(false)
    }
  }

  async function changeStatus(status: TaskStatus) {
    if (!task) return
    setError('')
    try {
      setTask(await taskApi.updateStatus(task.id, status))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Task status could not be updated.'))
    }
  }

  async function changePriority(priority: TaskPriority) {
    if (!task) return
    setError('')
    try {
      setTask(await taskApi.updatePriority(task.id, priority))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Task priority could not be updated.'))
    }
  }

  async function changeAssignee(assigneeId: string) {
    if (!task) return
    setError('')
    try {
      setTask(await taskApi.assign(task.id, assigneeId || null))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Task assignee could not be updated.'))
    }
  }

  async function addComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setCommenting(true)
    try {
      const comment = await taskApi.addComment(taskId, commentContent.trim())
      setComments((current) => [...current, comment])
      setCommentContent('')
      setTask((current) => current ? { ...current, commentCount: current.commentCount + 1 } : current)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Comment could not be added.'))
    } finally {
      setCommenting(false)
    }
  }

  async function saveComment(comment: TaskCommentResponse) {
    setError('')
    try {
      const updated = await taskApi.updateComment(taskId, comment.id, editingContent.trim())
      setComments((current) => current.map((item) => item.id === comment.id ? updated : item))
      setEditingComment(null)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Comment could not be updated.'))
    }
  }

  async function deleteComment(comment: TaskCommentResponse) {
    setError('')
    try {
      await taskApi.deleteComment(taskId, comment.id)
      setComments((current) => current.filter((item) => item.id !== comment.id))
      setTask((current) => current ? { ...current, commentCount: Math.max(0, current.commentCount - 1) } : current)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Comment could not be deleted.'))
    }
  }

  async function deleteTask() {
    if (!task || !window.confirm('Delete this task and all of its comments?')) return
    setError('')
    try {
      await taskApi.delete(task.id)
      navigate(`/projects/${task.projectId}/tasks`, { replace: true })
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Task could not be deleted.'))
    }
  }

  if (loading) {
    return <main className="workspace-page"><WorkspaceHeader /><div className="workspace-loading" role="status"><span className="spinner" />Loading task...</div></main>
  }

  if (!task || !project || !workspace) {
    return (
      <main className="workspace-page">
        <WorkspaceHeader />
        <section className="workspace-content">
          <p className="form-message form-error" role="alert">{error || 'Task not found.'}</p>
          <Link className="button-outline" to="/workspaces">Back to workspaces</Link>
        </section>
      </main>
    )
  }

  const overdue = task.dueDate !== null && task.dueDate < new Date().toISOString().slice(0, 10) && task.status !== 'DONE'

  return (
    <main className="workspace-page">
      <WorkspaceHeader />
      <section className="workspace-content task-detail-content">
        <Link className="back-link" to={`/projects/${task.projectId}/tasks`}>← {project.name} tasks</Link>
        {error && <p className="form-message form-error" role="alert">{error}</p>}
        <section className="task-detail-card">
          <div className="task-card-head">
            <span className="task-key">{task.taskKey}</span>
            {overdue && <span className="task-overdue">OVERDUE</span>}
          </div>
          {editing ? (
            <form className="settings-form task-edit-form" onSubmit={saveTask}>
              <label className="field"><span>Task title</span><input required maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
              <label className="field"><span>Description</span><textarea rows={4} maxLength={4000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
              <label className="field"><span>Due date</span><input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} /></label>
              <div className="form-actions">
                <button className="button-quiet" type="button" onClick={() => setEditing(false)}>Cancel</button>
                <PrimaryButton type="submit" loading={saving}>Save task</PrimaryButton>
              </div>
            </form>
          ) : (
            <>
              <h1>{task.title}</h1>
              <p className="task-detail-description">{task.description || 'No description yet.'}</p>
              <div className="task-detail-controls">
                <label className="field"><span>Status</span><select aria-label="Task status" value={task.status} onChange={(event) => void changeStatus(event.target.value as TaskStatus)}>{taskStatuses.map((item) => <option key={item} value={item}>{item.replace('_', ' ')}</option>)}</select></label>
                <label className="field"><span>Priority</span><select aria-label="Task priority" value={task.priority} onChange={(event) => void changePriority(event.target.value as TaskPriority)}>{taskPriorities.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
                <label className="field"><span>Assignee</span><select aria-label="Task assignee" value={task.assigneeId ?? ''} onChange={(event) => void changeAssignee(event.target.value)}><option value="">Unassigned</option>{members.map((member) => <option key={member.userId} value={member.userId}>{member.fullName}</option>)}</select></label>
                <span className="task-detail-fact"><strong>Due date</strong>{task.dueDate || 'Not set'}</span>
                <span className="task-detail-fact"><strong>Created by</strong>{task.creatorName}</span>
                <span className="task-detail-fact"><strong>Project</strong>{project.key}</span>
              </div>
              <div className="task-detail-actions">
                <button className="button-outline" type="button" onClick={() => setEditing(true)}>Edit task</button>
                {workspaceAdmin || projectManager ? <button className="button-danger" type="button" onClick={() => void deleteTask()}>Delete task</button> : null}
              </div>
            </>
          )}
        </section>

        <section className="task-comments-section">
          <div className="workspace-detail-heading">
            <div>
              <span className="eyebrow">TEAM DISCUSSION</span>
              <h2>Comments <span className="member-count">{comments.length}</span></h2>
              <p>Share progress and context with the project team.</p>
            </div>
          </div>
          <div className="task-comments-layout">
            <section className="settings-card task-comment-list" aria-label="Task comments">
              {comments.length === 0 ? <p className="task-no-comments">No comments yet. Start the conversation.</p> : comments.map((comment) => {
                const canEdit = comment.authorId === user?.id || canModerateComments
                return (
                  <article className="task-comment" key={comment.id}>
                    <div className="task-comment-avatar" aria-hidden="true">{comment.authorName.trim().charAt(0).toUpperCase()}</div>
                    <div className="task-comment-main">
                      <div className="task-comment-meta"><strong>{comment.authorName}</strong><time>{new Date(comment.createdAt).toLocaleString()}</time></div>
                      {editingComment === comment.id ? (
                        <form className="settings-form" onSubmit={(event) => { event.preventDefault(); void saveComment(comment) }}>
                          <label className="field"><span className="sr-only">Edit comment</span><textarea required maxLength={4000} value={editingContent} onChange={(event) => setEditingContent(event.target.value)} /></label>
                          <div className="form-actions">
                            <button className="button-quiet" type="button" onClick={() => setEditingComment(null)}>Cancel</button>
                            <PrimaryButton type="submit">Save comment</PrimaryButton>
                          </div>
                        </form>
                      ) : <p>{comment.content}</p>}
                      {canEdit && editingComment !== comment.id && (
                        <div className="task-comment-actions">
                          <button className="button-quiet" type="button" onClick={() => { setEditingComment(comment.id); setEditingContent(comment.content) }}>Edit</button>
                          <button className="button-quiet member-remove" type="button" onClick={() => void deleteComment(comment)}>Delete</button>
                        </div>
                      )}
                    </div>
                  </article>
                )
              })}
            </section>
            <aside className="settings-card add-task-comment-card">
              <span className="eyebrow">ADD CONTEXT</span>
              <h3>Write a comment</h3>
              <form className="settings-form" onSubmit={addComment}>
                <label className="field"><span>Comment</span><textarea required maxLength={4000} rows={4} value={commentContent} onChange={(event) => setCommentContent(event.target.value)} placeholder="What should the team know?" /></label>
                <PrimaryButton type="submit" loading={commenting}>Post comment</PrimaryButton>
              </form>
            </aside>
          </div>
        </section>
      </section>
    </main>
  )
}
