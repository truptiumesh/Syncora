import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PrimaryButton } from '../components/PrimaryButton'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { projectApi } from '../services/projectService'
import { workspaceApi } from '../services/workspaceService'
import type { ProjectPage, ProjectResponse, ProjectStatus } from '../types/project'
import type { WorkspaceResponse } from '../types/workspace'
import { apiErrorMessage } from '../utils/apiError'

const statuses: ProjectStatus[] = ['PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED']

export function ProjectListPage() {
  const { workspaceId = '' } = useParams()
  const navigate = useNavigate()
  const [workspace, setWorkspace] = useState<WorkspaceResponse | null>(null)
  const [result, setResult] = useState<ProjectPage | null>(null)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<ProjectStatus | ''>('')
  const [archived, setArchived] = useState(false)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [newStatus, setNewStatus] = useState<ProjectStatus>('PLANNING')
  const [startDate, setStartDate] = useState('')
  const [dueDate, setDueDate] = useState('')

  useEffect(() => {
    let active = true
    workspaceApi.get(workspaceId)
      .then((value) => { if (active) setWorkspace(value) })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Workspace could not be loaded.'))
      })
    return () => { active = false }
  }, [workspaceId])

  useEffect(() => {
    let active = true
    setLoading(true)
    projectApi.list(workspaceId, {
      search: search.trim() || undefined,
      status: status || undefined,
      archived,
      page,
      size: 10,
    })
      .then((value) => { if (active) setResult(value) })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Projects could not be loaded.'))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [workspaceId, search, status, archived, page])

  async function createProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (startDate && dueDate && dueDate < startDate) {
      setError('Due date must be on or after the start date.')
      return
    }
    setCreating(true)
    try {
      const project = await projectApi.create(workspaceId, {
        name: name.trim(),
        description: description.trim(),
        status: newStatus,
        startDate: startDate || null,
        dueDate: dueDate || null,
      })
      navigate(`/projects/${project.id}`)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Project could not be created.'))
    } finally {
      setCreating(false)
    }
  }

  function updateSearch(value: string) {
    setSearch(value)
    setPage(0)
  }

  function updateStatus(value: ProjectStatus | '') {
    setStatus(value)
    setPage(0)
  }

  function updateArchived(value: boolean) {
    setArchived(value)
    setPage(0)
  }

  const projects = result?.content ?? []

  return (
    <main className="workspace-page">
      <WorkspaceHeader />
      <section className="workspace-content">
        <Link className="back-link" to={`/workspaces/${workspaceId}`}>← {workspace?.name || 'Workspace'}</Link>
        <div className="workspace-page-heading project-heading">
          <div>
            <span className="eyebrow">WORKSPACE PROJECTS</span>
            <h1>Projects</h1>
            <p>Plan, track, and organize your team’s initiatives.</p>
          </div>
          <button className="button-primary" type="button" onClick={() => setShowCreate(true)}>
            <span aria-hidden="true">＋</span> Create project
          </button>
        </div>

        {error && !showCreate && <p className="form-message form-error" role="alert">{error}</p>}

        <section className="project-filters" aria-label="Project filters">
          <label className="field project-search-field">
            <span>Search projects</span>
            <input
              aria-label="Search projects"
              type="search"
              value={search}
              onChange={(event) => updateSearch(event.target.value)}
              placeholder="Search by name or key"
            />
          </label>
          <label className="field project-select-field">
            <span>Status</span>
            <select aria-label="Status filter" value={status} onChange={(event) => updateStatus(event.target.value as ProjectStatus | '')}>
              <option value="">All statuses</option>
              {statuses.map((item) => <option key={item} value={item}>{item.replace('_', ' ')}</option>)}
            </select>
          </label>
          <label className="project-archived-filter">
            <input
              type="checkbox"
              checked={archived}
              onChange={(event) => updateArchived(event.target.checked)}
            />
            Show archived
          </label>
        </section>

        {loading ? (
          <div className="workspace-loading" role="status"><span className="spinner" />Loading projects...</div>
        ) : projects.length === 0 ? (
          <section className="workspace-empty project-empty">
            <span className="empty-icon" aria-hidden="true">✳</span>
            <h2>{search || status || archived ? 'No projects match these filters.' : 'Make your first project happen.'}</h2>
            <p>{search || status || archived ? 'Try adjusting your search or filters.' : 'Projects give your workspace a clear place to focus.'}</p>
            {!search && !status && !archived && (
              <button className="button-primary" type="button" onClick={() => setShowCreate(true)}>Create a project</button>
            )}
          </section>
        ) : (
          <>
            <div className="project-grid">
              {projects.map((project) => <ProjectCard key={project.id} project={project} />)}
            </div>
            <nav className="project-pagination" aria-label="Project pages">
              <span>{result?.totalElements ?? 0} projects</span>
              <div>
                <button className="button-outline" type="button" disabled={result?.first} onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</button>
                <span>Page {(result?.number ?? 0) + 1} of {Math.max(result?.totalPages ?? 1, 1)}</span>
                <button className="button-outline" type="button" disabled={result?.last} onClick={() => setPage((current) => current + 1)}>Next</button>
              </div>
            </nav>
          </>
        )}
      </section>

      {showCreate && (
        <div className="modal-backdrop">
          <section className="workspace-modal project-modal" role="dialog" aria-modal="true" aria-labelledby="create-project-title">
            <button className="modal-close" type="button" aria-label="Close dialog" disabled={creating} onClick={() => setShowCreate(false)}>×</button>
            <span className="eyebrow">A NEW INITIATIVE</span>
            <h2 id="create-project-title">Create a project</h2>
            <p>Choose a name and a few details to get started.</p>
            <form className="settings-form" onSubmit={createProject}>
              <label className="field">
                <span>Project name</span>
                <input required maxLength={120} value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. Mobile app launch" autoFocus />
              </label>
              <label className="field">
                <span>Description <span className="optional-label">Optional</span></span>
                <textarea rows={3} maxLength={2000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="What are you building?" />
              </label>
              <label className="field">
                <span>Status</span>
                <select value={newStatus} onChange={(event) => setNewStatus(event.target.value as ProjectStatus)}>
                  {statuses.map((item) => <option key={item} value={item}>{item.replace('_', ' ')}</option>)}
                </select>
              </label>
              <div className="project-date-fields">
                <label className="field"><span>Start date</span><input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} /></label>
                <label className="field"><span>Due date</span><input type="date" value={dueDate} min={startDate || undefined} onChange={(event) => setDueDate(event.target.value)} /></label>
              </div>
              {error && <p className="form-message form-error" role="alert">{error}</p>}
              <div className="form-actions">
                <button className="button-quiet" type="button" disabled={creating} onClick={() => setShowCreate(false)}>Cancel</button>
                <PrimaryButton type="submit" loading={creating}>Create project</PrimaryButton>
              </div>
            </form>
          </section>
        </div>
      )}
    </main>
  )
}

function ProjectCard({ project }: { project: ProjectResponse }) {
  return (
    <article className="project-card">
      <div className="project-card-top">
        <span className="project-key">{project.key}</span>
        <span className={`project-status status-${project.status.toLowerCase().replace('_', '-')}`}>{project.status.replace('_', ' ')}</span>
      </div>
      <h2><Link to={`/projects/${project.id}`}>{project.name}</Link></h2>
      <p className="project-description">{project.description || 'No project description yet.'}</p>
      <div className="project-card-meta">
        <span><strong>Manager</strong>{project.projectManager}</span>
        <span><strong>Members</strong>{project.memberCount}</span>
        <span><strong>Due</strong>{project.dueDate || 'Not set'}</span>
      </div>
    </article>
  )
}
