import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, MessageCircle, UsersRound } from 'lucide-react'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { workspaceApi } from '../services/workspaceService'
import type { WorkspaceResponse } from '../types/workspace'
import { apiErrorMessage } from '../utils/apiError'

export function MessagesOverviewPage() {
  const [workspaces, setWorkspaces] = useState<WorkspaceResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function load() {
    setLoading(true)
    setError('')
    try {
      setWorkspaces(await workspaceApi.list())
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Your workspaces could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  return (
    <main className="workspace-page app-directory-page">
      <WorkspaceHeader />
      <section className="workspace-content">
        <header className="directory-heading"><div><span className="eyebrow">TEAM CONVERSATIONS</span><h1>Messages</h1><p>Choose a workspace to open your private conversations.</p></div></header>
        {error && <div className="widget-error" role="alert"><p>{error}</p><button className="button-outline" type="button" onClick={() => void load()}>Retry</button></div>}
        {loading ? <div className="workspace-loading" role="status"><span className="spinner" />Loading workspaces...</div>
          : workspaces.length === 0 ? <div className="command-empty"><MessageCircle size={24} /><strong>No workspaces yet</strong><span>Join or create a workspace to message a teammate.</span><Link to="/workspaces">Browse workspaces</Link></div>
            : <div className="directory-grid">{workspaces.map((workspace) => (
              <article className="directory-card" key={workspace.id}>
                <div className="directory-card-mark"><UsersRound size={20} /></div><h2>{workspace.name}</h2><p>{workspace.memberCount} workspace members</p>
                <div className="directory-card-bottom"><small>Private one-to-one messages</small><Link to={`/workspaces/${workspace.id}/messages`} aria-label={`Open messages for ${workspace.name}`}><ArrowRight size={16} /></Link></div>
              </article>
            ))}</div>}
      </section>
    </main>
  )
}
