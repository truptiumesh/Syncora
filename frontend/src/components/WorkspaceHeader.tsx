import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { NotificationBell } from './NotificationBell'

export function WorkspaceHeader() {
  const { user } = useAuth()

  return (
    <header className="app-page-topbar">
      <Link className="app-topbar-home" to="/dashboard" aria-label="Go to dashboard">SYNCORA <span>·</span> Command centre</Link>
      <nav className="app-topbar-actions" aria-label="Account navigation">
        <NotificationBell />
        <Link className="app-topbar-profile" to="/profile">
          <span className="sidebar-avatar" aria-hidden="true">{user?.fullName.trim().charAt(0).toUpperCase() || '?'}</span>
          <span>{user?.fullName || 'My profile'}</span>
        </Link>
      </nav>
    </header>
  )
}
