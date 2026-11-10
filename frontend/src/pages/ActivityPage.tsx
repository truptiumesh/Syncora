import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { ActivityFeed } from '../components/ActivityFeed'
import { Link } from 'react-router-dom'

export function ActivityPage() {
  return (
    <main className="workspace-page">
      <WorkspaceHeader />
      <section className="workspace-content activity-page-content">
        <Link className="back-link" to="/dashboard">← Dashboard</Link>
        <div className="workspace-page-heading">
          <div>
            <span className="eyebrow">YOUR WORKSPACES</span>
            <h1>Activity feed</h1>
            <p>Recent updates from the projects and teams you can access.</p>
          </div>
        </div>
        <ActivityFeed />
      </section>
    </main>
  )
}
