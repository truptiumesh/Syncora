import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, FolderKanban } from 'lucide-react'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { listVisibleProjects, type WorkspaceProject } from '../services/workspaceDataService'
import { workspaceApi } from '../services/workspaceService'
import { apiErrorDetails } from '../utils/apiError'

export function ProjectsOverviewPage() {
  const [projects, setProjects] = useState<WorkspaceProject[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function load() {
    setLoading(true)
    setError('')
    try {
      const workspaces = await workspaceApi.list()
      const result = await listVisibleProjects(workspaces)
      setProjects(result.projects)
      if (result.failures) setError(result.failureDetails.join(' '))
    } catch (requestError) {
      setError(apiErrorDetails(requestError, 'Projects could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  const ordered = useMemo(
    () => [...projects].sort((a, b) => b.project.updatedAt.localeCompare(a.project.updatedAt)),
    [projects],
  )

  return (
    <main className="workspace-page app-directory-page">
      <WorkspaceHeader />
      <section className="workspace-content">
        <header className="directory-heading"><div><span className="eyebrow">YOUR PORTFOLIO</span><h1>Projects</h1><p>Browse active and recent projects across your workspaces.</p></div></header>
        {error && <div className="widget-error" role="alert"><p>{error}</p><button className="button-outline" type="button" onClick={() => void load()}>Retry</button></div>}
        {loading ? <div className="workspace-loading" role="status"><span className="spinner" />Loading projects...</div>
          : ordered.length === 0 ? <div className="command-empty"><FolderKanban size={24} /><strong>No projects found</strong><span>Projects you can access will appear here.</span><Link to="/workspaces">Browse workspaces</Link></div>
            : <div className="directory-grid">
              {ordered.map(({ project, workspaceName }) => (
                <article className="directory-card project-directory-card" key={project.id}>
                  <div className="directory-card-top"><span className="project-key">{project.key}</span><span className={`project-status status-${project.status.toLowerCase().replace('_', '-')}`}>{project.status.replace('_', ' ')}</span></div>
                  <h2><Link to={`/projects/${project.id}`}>{project.name}</Link></h2>
                  <p>{workspaceName}</p>
                  <div className="directory-card-bottom"><small>Updated {new Date(project.updatedAt).toLocaleDateString()}</small><Link to={`/projects/${project.id}`} aria-label={`Open ${project.name}`}><ArrowRight size={16} /></Link></div>
                </article>
              ))}
            </div>}
      </section>
    </main>
  )
}
