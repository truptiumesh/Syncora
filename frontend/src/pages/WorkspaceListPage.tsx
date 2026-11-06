import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { FormField } from '../components/FormField'
import { PrimaryButton } from '../components/PrimaryButton'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { workspaceApi } from '../services/workspaceService'
import type { WorkspaceResponse } from '../types/workspace'
import { apiErrorMessage } from '../utils/apiError'

export function WorkspaceListPage() {
  const navigate = useNavigate()
  const [workspaces, setWorkspaces] = useState<WorkspaceResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    let active = true
    workspaceApi.list()
      .then((items) => {
        if (active) setWorkspaces(items)
      })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Your workspaces could not be loaded.'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  async function createWorkspace(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setCreating(true)
    try {
      const workspace = await workspaceApi.create({ name: name.trim(), description: description.trim() })
      setWorkspaces((current) => [workspace, ...current])
      setShowCreate(false)
      navigate(`/workspaces/${workspace.id}`)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'The workspace could not be created.'))
    } finally {
      setCreating(false)
    }
  }

  return (
    <main className="workspace-page">
      <WorkspaceHeader />
      <section className="workspace-content">
        <div className="workspace-page-heading">
          <div>
            <span className="eyebrow">YOUR TEAM SPACES</span>
            <h1>Workspaces</h1>
            <p>Bring your team together around the work that matters.</p>
          </div>
          <button className="button-primary" type="button" onClick={() => setShowCreate(true)}>
            <span aria-hidden="true">＋</span> Create workspace
          </button>
        </div>

        {error && !showCreate && <p className="form-message form-error" role="alert">{error}</p>}

        {loading ? (
          <div className="workspace-loading" role="status"><span className="spinner" />Loading workspaces...</div>
        ) : workspaces.length === 0 ? (
          <section className="workspace-empty">
            <span className="empty-icon" aria-hidden="true">✳</span>
            <h2>Your team’s next chapter starts here.</h2>
            <p>Create a workspace to bring people and projects into one shared space.</p>
            <button className="button-primary" type="button" onClick={() => setShowCreate(true)}>
              Create your first workspace
            </button>
          </section>
        ) : (
          <div className="workspace-grid">
            {workspaces.map((workspace) => (
              <article className="workspace-card" key={workspace.id}>
                <div className="workspace-card-mark" aria-hidden="true">{workspace.name.trim().charAt(0).toUpperCase()}</div>
                <span className={`role-pill role-${workspace.currentUserRole.toLowerCase()}`}>
                  {workspace.currentUserRole}
                </span>
                <h2>{workspace.name}</h2>
                <p className="workspace-description">{workspace.description || 'A shared space for your team’s work.'}</p>
                <div className="workspace-card-footer">
                  <span>{workspace.memberCount} {workspace.memberCount === 1 ? 'member' : 'members'}</span>
                  <Link className="button-outline" to={`/workspaces/${workspace.id}`}>
                    Open workspace <span aria-hidden="true">→</span>
                  </Link>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      {showCreate && (
        <div className="modal-backdrop" onMouseDown={(event) => {
          if (event.target === event.currentTarget && !creating) setShowCreate(false)
        }}>
          <section className="workspace-modal" role="dialog" aria-modal="true" aria-labelledby="create-workspace-title">
            <button className="modal-close" type="button" aria-label="Close dialog" onClick={() => setShowCreate(false)}>×</button>
            <span className="eyebrow">A NEW SHARED SPACE</span>
            <h2 id="create-workspace-title">Create a workspace</h2>
            <p>Give your team a home to organize and move work forward.</p>
            <form className="settings-form" onSubmit={createWorkspace}>
              <FormField
                id="workspaceName"
                label="Workspace name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="e.g. Product team"
                required
                maxLength={100}
                autoFocus
              />
              <div className="field">
                <label htmlFor="workspaceDescription">Description <span className="optional-label">Optional</span></label>
                <textarea
                  id="workspaceDescription"
                  rows={3}
                  maxLength={1000}
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  placeholder="What will your team work on together?"
                />
              </div>
              {error && <p className="form-message form-error" role="alert">{error}</p>}
              <div className="form-actions">
                <button className="button-quiet" type="button" disabled={creating} onClick={() => setShowCreate(false)}>Cancel</button>
                <PrimaryButton type="submit" loading={creating}>Create workspace</PrimaryButton>
              </div>
            </form>
          </section>
        </div>
      )}
    </main>
  )
}
