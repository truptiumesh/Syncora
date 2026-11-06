import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { FormField } from '../components/FormField'
import { PrimaryButton } from '../components/PrimaryButton'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { workspaceApi } from '../services/workspaceService'
import type { WorkspaceMemberResponse, WorkspaceResponse, WorkspaceRole } from '../types/workspace'
import { apiErrorMessage } from '../utils/apiError'

export function WorkspaceDetailsPage() {
  const { workspaceId = '' } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [workspace, setWorkspace] = useState<WorkspaceResponse | null>(null)
  const [members, setMembers] = useState<WorkspaceMemberResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [memberEmail, setMemberEmail] = useState('')
  const [addingMember, setAddingMember] = useState(false)
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [saving, setSaving] = useState(false)

  const loadWorkspace = useCallback(async () => {
    setError('')
    try {
      const [details, workspaceMembers] = await Promise.all([
        workspaceApi.get(workspaceId),
        workspaceApi.listMembers(workspaceId),
      ])
      setWorkspace(details)
      setMembers(workspaceMembers)
      setName(details.name)
      setDescription(details.description ?? '')
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Workspace details could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [workspaceId])

  useEffect(() => {
    void loadWorkspace()
  }, [loadWorkspace])

  const currentRole = workspace?.currentUserRole
  const canManageWorkspace = currentRole === 'OWNER' || currentRole === 'ADMIN'
  const isOwner = currentRole === 'OWNER'

  async function addMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setAddingMember(true)
    try {
      const member = await workspaceApi.addMember(workspaceId, { email: memberEmail.trim() })
      setMembers((current) => [...current, member])
      setMemberEmail('')
      setWorkspace((current) => current ? { ...current, memberCount: current.memberCount + 1 } : current)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'This person could not be added.'))
    } finally {
      setAddingMember(false)
    }
  }

  async function changeMemberRole(member: WorkspaceMemberResponse, role: WorkspaceRole) {
    setError('')
    try {
      const updated = await workspaceApi.updateMemberRole(workspaceId, member.id, role)
      setMembers((current) => current.map((item) => item.id === member.id ? updated : item))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'The member role could not be changed.'))
    }
  }

  async function removeMember(member: WorkspaceMemberResponse) {
    setError('')
    try {
      await workspaceApi.removeMember(workspaceId, member.id)
      setMembers((current) => current.filter((item) => item.id !== member.id))
      setWorkspace((current) => current ? { ...current, memberCount: Math.max(0, current.memberCount - 1) } : current)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'This person could not be removed.'))
    }
  }

  async function updateWorkspace(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setSaving(true)
    try {
      const updated = await workspaceApi.update(workspaceId, { name: name.trim(), description: description.trim() })
      setWorkspace(updated)
      setEditing(false)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Workspace details could not be saved.'))
    } finally {
      setSaving(false)
    }
  }

  async function leaveWorkspace() {
    setError('')
    try {
      await workspaceApi.leave(workspaceId)
      navigate('/workspaces', { replace: true })
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'You could not leave this workspace.'))
    }
  }

  async function deleteWorkspace() {
    if (!window.confirm('Delete this workspace and remove all of its memberships? This cannot be undone.')) return
    setError('')
    try {
      await workspaceApi.delete(workspaceId)
      navigate('/workspaces', { replace: true })
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'The workspace could not be deleted.'))
    }
  }

  if (loading) {
    return <main className="workspace-page"><WorkspaceHeader /><div className="workspace-loading" role="status"><span className="spinner" />Loading workspace...</div></main>
  }

  if (!workspace) {
    return (
      <main className="workspace-page">
        <WorkspaceHeader />
        <section className="workspace-content">
          <p className="form-message form-error" role="alert">{error || 'Workspace not found.'}</p>
          <Link className="button-outline" to="/workspaces">Back to workspaces</Link>
        </section>
      </main>
    )
  }

  return (
    <main className="workspace-page">
      <WorkspaceHeader />
      <section className="workspace-content workspace-detail-content">
        <Link className="back-link" to="/workspaces">← All workspaces</Link>
        {error && <p className="form-message form-error" role="alert">{error}</p>}
        <section className="workspace-hero-card">
          <div className="workspace-hero-mark" aria-hidden="true">{workspace.name.trim().charAt(0).toUpperCase()}</div>
          <div className="workspace-hero-copy">
            <span className="eyebrow">WORKSPACE</span>
            {editing ? (
              <form className="workspace-edit-form" onSubmit={updateWorkspace}>
                <FormField id="editWorkspaceName" label="Name" value={name} onChange={(event) => setName(event.target.value)} required maxLength={100} />
                <div className="field">
                  <label htmlFor="editWorkspaceDescription">Description</label>
                  <textarea id="editWorkspaceDescription" rows={2} maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)} />
                </div>
                <div className="form-actions">
                  <button className="button-quiet" type="button" onClick={() => setEditing(false)}>Cancel</button>
                  <PrimaryButton type="submit" loading={saving}>Save workspace</PrimaryButton>
                </div>
              </form>
            ) : (
              <>
                <h1>{workspace.name}</h1>
                <p>{workspace.description || 'A shared space for your team’s work.'}</p>
              </>
            )}
          </div>
          <span className={`role-pill role-${workspace.currentUserRole.toLowerCase()}`}>{workspace.currentUserRole}</span>
          {canManageWorkspace && !editing && (
            <button className="button-outline workspace-edit-button" type="button" onClick={() => setEditing(true)}>Edit details</button>
          )}
        </section>

        <section className="workspace-projects-callout">
          <div>
            <span className="eyebrow">YOUR WORK, ORGANIZED</span>
            <h2>Workspace projects</h2>
            <p>Create projects, track their progress, and manage the people involved.</p>
          </div>
          <Link className="button-primary" to={`/workspaces/${workspaceId}/projects`}>Browse projects <span aria-hidden="true">→</span></Link>
        </section>

        <div className="workspace-detail-heading">
          <div>
            <span className="eyebrow">THE PEOPLE</span>
            <h2>Workspace members <span className="member-count">{members.length}</span></h2>
            <p>See who’s part of this shared space.</p>
          </div>
        </div>

        <div className="workspace-members-layout">
          <section className="settings-card member-list-card" aria-label="Workspace members">
            {members.map((member) => {
              const isCurrentUser = member.userId === user?.id
              const canChangeRole = isOwner
                ? member.role !== 'OWNER'
                : currentRole === 'ADMIN' && member.role === 'MEMBER'
              const canRemove = member.role === 'MEMBER'
                ? canManageWorkspace
                : member.role === 'ADMIN' && isOwner
              return (
                <article className="workspace-member-row" key={member.id}>
                  <MemberAvatar member={member} />
                  <div className="member-identity">
                    <h3>{member.fullName} {isCurrentUser && <span className="you-label">YOU</span>}</h3>
                    <p>{member.email}</p>
                  </div>
                  <span className={`role-pill role-${member.role.toLowerCase()}`}>{member.role}</span>
                  {(canChangeRole || canRemove) && (
                    <div className="member-actions">
                      {canChangeRole && (
                        <label className="visually-hidden" htmlFor={`role-${member.id}`}>Change role for {member.fullName}</label>
                      )}
                      {canChangeRole && (
                        <select
                          id={`role-${member.id}`}
                          aria-label={`Change role for ${member.fullName}`}
                          value={member.role}
                          onChange={(event) => void changeMemberRole(member, event.target.value as WorkspaceRole)}
                        >
                          <option value="MEMBER">Member</option>
                          <option value="ADMIN">Admin</option>
                        </select>
                      )}
                      {canRemove && (
                        <button className="member-remove-button" type="button" onClick={() => void removeMember(member)}>
                          Remove
                        </button>
                      )}
                    </div>
                  )}
                </article>
              )
            })}
          </section>

          <aside className="workspace-side-column">
            {canManageWorkspace && (
              <section className="settings-card invite-card">
                <span className="card-icon" aria-hidden="true">＋</span>
                <h2>Add a teammate</h2>
                <p>Add someone who already has a Syncora account.</p>
                <form className="settings-form" onSubmit={addMember}>
                  <FormField
                    id="memberEmail"
                    label="Email address"
                    type="email"
                    autoComplete="email"
                    value={memberEmail}
                    onChange={(event) => setMemberEmail(event.target.value)}
                    placeholder="teammate@company.com"
                    required
                    maxLength={254}
                  />
                  <PrimaryButton type="submit" loading={addingMember}>Add member</PrimaryButton>
                </form>
              </section>
            )}
            <section className="settings-card workspace-role-card">
              <span className="eyebrow">YOUR ACCESS</span>
              <h2>{workspace.currentUserRole}</h2>
              <p>{workspace.currentUserRole === 'OWNER'
                ? 'You own this workspace and control its membership.'
                : workspace.currentUserRole === 'ADMIN'
                  ? 'You can manage workspace details and members.'
                  : 'You can view and collaborate in this workspace.'}</p>
              {isOwner ? (
                <p className="owner-leave-note" role="note">Transfer ownership before leaving this workspace.</p>
              ) : (
                <button className="button-quiet leave-button" type="button" onClick={() => void leaveWorkspace()}>Leave workspace</button>
              )}
              {isOwner && (
                <button className="workspace-delete-button" type="button" onClick={() => void deleteWorkspace()}>Delete workspace</button>
              )}
            </section>
          </aside>
        </div>
      </section>
    </main>
  )
}

function MemberAvatar({ member }: { member: WorkspaceMemberResponse }) {
  if (member.profileImageUrl) {
    return <img className="avatar" src={member.profileImageUrl} alt="" />
  }
  const initials = member.fullName.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
  return <span className="avatar avatar-fallback" aria-hidden="true">{initials}</span>
}
