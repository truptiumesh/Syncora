import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PrimaryButton } from '../components/PrimaryButton'
import { FileManager } from '../components/FileManager'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { projectApi } from '../services/projectService'
import { workspaceApi } from '../services/workspaceService'
import type { ProjectMemberResponse, ProjectResponse, ProjectRole, ProjectStatus } from '../types/project'
import type { WorkspaceResponse } from '../types/workspace'
import { apiErrorMessage } from '../utils/apiError'

const editableStatuses: ProjectStatus[] = ['PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED']

export function ProjectDetailsPage() {
  const { projectId = '' } = useParams()
  const navigate = useNavigate()
  const [project, setProject] = useState<ProjectResponse | null>(null)
  const [workspace, setWorkspace] = useState<WorkspaceResponse | null>(null)
  const [members, setMembers] = useState<ProjectMemberResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [adding, setAdding] = useState(false)
  const [email, setEmail] = useState('')
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState<ProjectStatus>('PLANNING')
  const [startDate, setStartDate] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [error, setError] = useState('')

  const loadProject = useCallback(async () => {
    setError('')
    try {
      const details = await projectApi.get(projectId)
      const [workspaceDetails, projectMembers] = await Promise.all([
        workspaceApi.get(details.workspaceId),
        projectApi.listMembers(projectId),
      ])
      setProject(details)
      setWorkspace(workspaceDetails)
      setMembers(projectMembers)
      setName(details.name)
      setDescription(details.description ?? '')
      setStatus(details.status)
      setStartDate(details.startDate ?? '')
      setDueDate(details.dueDate ?? '')
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Project details could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [projectId])

  useEffect(() => { void loadProject() }, [loadProject])

  const isWorkspaceAdmin = workspace?.currentUserRole === 'OWNER' || workspace?.currentUserRole === 'ADMIN'
  const canManageProject = isWorkspaceAdmin || project?.currentUserRole === 'PROJECT_MANAGER'
  const isArchived = project?.archivedAt !== null

  async function saveProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!project) return
    setError('')
    if (startDate && dueDate && dueDate < startDate) {
      setError('Due date must be on or after the start date.')
      return
    }
    setSaving(true)
    try {
      const updated = await projectApi.update(project.id, {
        name: name.trim(),
        description: description.trim(),
        status,
        startDate: startDate || null,
        dueDate: dueDate || null,
      })
      setProject(updated)
      setEditing(false)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Project details could not be saved.'))
    } finally {
      setSaving(false)
    }
  }

  async function changeLifecycle(action: 'archive' | 'restore') {
    if (!project) return
    setError('')
    try {
      const updated = action === 'archive'
        ? await projectApi.archive(project.id)
        : await projectApi.restore(project.id)
      setProject(updated)
      setStatus(updated.status)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, `Project could not be ${action}d.`))
    }
  }

  async function deleteProject() {
    if (!project || !window.confirm('Permanently delete this project and its project memberships?')) return
    setError('')
    try {
      await projectApi.delete(project.id)
      navigate(`/workspaces/${project.workspaceId}/projects`, { replace: true })
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Project could not be deleted.'))
    }
  }

  async function addMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setAdding(true)
    try {
      const member = await projectApi.addMember(projectId, email.trim())
      setMembers((current) => [...current, member])
      setEmail('')
      setProject((current) => current ? { ...current, memberCount: current.memberCount + 1 } : current)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'This workspace member could not be added.'))
    } finally {
      setAdding(false)
    }
  }

  async function updateMemberRole(member: ProjectMemberResponse, role: ProjectRole) {
    setError('')
    try {
      const updated = await projectApi.updateMemberRole(projectId, member.id, role)
      const updatedMembers = members.map((item) => item.id === member.id ? updated : item)
      setMembers(updatedMembers)
      setProject((current) => current ? {
        ...current,
        projectManager: updatedMembers.find((item) => item.role === 'PROJECT_MANAGER')?.fullName ?? '',
      } : current)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Project member role could not be changed.'))
    }
  }

  async function removeMember(member: ProjectMemberResponse) {
    setError('')
    try {
      await projectApi.removeMember(projectId, member.id)
      const remainingMembers = members.filter((item) => item.id !== member.id)
      setMembers(remainingMembers)
      setProject((current) => current ? { ...current, memberCount: Math.max(0, current.memberCount - 1) } : current)
      setProject((current) => current ? {
        ...current,
        projectManager: remainingMembers.find((item) => item.role === 'PROJECT_MANAGER')?.fullName ?? '',
      } : current)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Project member could not be removed.'))
    }
  }

  if (loading) {
    return <main className="workspace-page"><WorkspaceHeader /><div className="workspace-loading" role="status"><span className="spinner" />Loading project...</div></main>
  }

  if (!project || !workspace) {
    return (
      <main className="workspace-page">
        <WorkspaceHeader />
        <section className="workspace-content">
          <p className="form-message form-error" role="alert">{error || 'Project not found.'}</p>
          <Link className="button-outline" to="/workspaces">Back to workspaces</Link>
        </section>
      </main>
    )
  }

  return (
    <main className="workspace-page">
      <WorkspaceHeader />
      <section className="workspace-content project-detail-content">
        <Link className="back-link" to={`/workspaces/${project.workspaceId}/projects`}>← {workspace.name} projects</Link>
        {error && <p className="form-message form-error" role="alert">{error}</p>}

        <section className="project-hero-card">
          <div className="project-hero-copy">
            <div className="project-card-top">
              <span className="project-key">{project.key}</span>
              <span className={`project-status status-${project.status.toLowerCase().replace('_', '-')}`}>{project.status.replace('_', ' ')}</span>
            </div>
            {editing ? (
              <form className="settings-form project-edit-form" onSubmit={saveProject}>
                <label className="field"><span>Project name</span><input required maxLength={120} value={name} onChange={(event) => setName(event.target.value)} /></label>
                <label className="field"><span>Description</span><textarea rows={3} maxLength={2000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
                <label className="field"><span>Status</span><select value={status} onChange={(event) => setStatus(event.target.value as ProjectStatus)}>{editableStatuses.map((item) => <option key={item} value={item}>{item.replace('_', ' ')}</option>)}</select></label>
                <div className="project-date-fields">
                  <label className="field"><span>Start date</span><input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} /></label>
                  <label className="field"><span>Due date</span><input type="date" min={startDate || undefined} value={dueDate} onChange={(event) => setDueDate(event.target.value)} /></label>
                </div>
                <div className="form-actions">
                  <button className="button-quiet" type="button" onClick={() => setEditing(false)}>Cancel</button>
                  <PrimaryButton type="submit" loading={saving}>Save project</PrimaryButton>
                </div>
              </form>
            ) : (
              <>
                <h1>{project.name}</h1>
                <p>{project.description || 'No project description yet.'}</p>
                <div className="project-overview-grid">
                  <span><strong>Project manager</strong>{project.projectManager || 'Assign a manager'}</span>
                  <span><strong>Start date</strong>{project.startDate || 'Not set'}</span>
                  <span><strong>Due date</strong>{project.dueDate || 'Not set'}</span>
                  <span><strong>Members</strong>{members.length}</span>
                  <span><strong>Your role</strong>{project.currentUserRole || workspace.currentUserRole}</span>
                </div>
                <Link className="button-outline project-tasks-link" to={`/projects/${project.id}/tasks`}>View project tasks →</Link>
                <Link className="button-outline project-tasks-link" to={`/projects/${project.id}/chat`}>Open project chat →</Link>
              </>
            )}
          </div>
          {!editing && canManageProject && (
            <div className="project-actions">
              {!isArchived && <button className="button-outline" type="button" onClick={() => setEditing(true)}>Edit project</button>}
              <button className="button-outline" type="button" onClick={() => void changeLifecycle(isArchived ? 'restore' : 'archive')}>
                {isArchived ? 'Restore project' : 'Archive project'}
              </button>
              {isWorkspaceAdmin && <button className="button-danger" type="button" onClick={() => void deleteProject()}>Delete project</button>}
            </div>
          )}
        </section>

        <FileManager projectId={projectId} canModerate={canManageProject} />

        <section className="project-members-section">
          <div className="workspace-detail-heading">
            <div>
              <span className="eyebrow">THE PROJECT TEAM</span>
              <h2>Project members <span className="member-count">{members.length}</span></h2>
              <p>Workspace members collaborating on this project.</p>
            </div>
          </div>
          <div className="workspace-members-layout">
            <section className="settings-card member-list-card" aria-label="Project members">
              {members.map((member) => (
                <article className="member-row" key={member.id}>
                  <div className="member-avatar" aria-hidden="true">{member.fullName.trim().charAt(0).toUpperCase()}</div>
                  <div className="member-identity"><strong>{member.fullName}</strong><span>{member.email}</span></div>
                  {canManageProject ? (
                    <label className="project-role-select">
                      <span className="sr-only">Role for {member.fullName}</span>
                      <select value={member.role} aria-label={`Role for ${member.fullName}`} onChange={(event) => void updateMemberRole(member, event.target.value as ProjectRole)}>
                        <option value="PROJECT_MANAGER">Project manager</option>
                        <option value="MEMBER">Member</option>
                      </select>
                    </label>
                  ) : <span className="role-pill">{member.role.replace('_', ' ')}</span>}
                  {canManageProject && (
                    <button className="button-quiet member-remove" type="button" onClick={() => void removeMember(member)}>Remove</button>
                  )}
                </article>
              ))}
            </section>

            {canManageProject && (
              <aside className="settings-card add-project-member-card">
                <span className="eyebrow">GROW THE TEAM</span>
                <h3>Add a project member</h3>
                <p>They must already be a member of this workspace.</p>
                <form className="settings-form" onSubmit={addMember}>
                  <label className="field"><span>Email address</span><input type="email" required maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} placeholder="teammate@example.com" /></label>
                  <PrimaryButton type="submit" loading={adding}>Add member</PrimaryButton>
                </form>
              </aside>
            )}
          </div>
        </section>
      </section>
    </main>
  )
}
