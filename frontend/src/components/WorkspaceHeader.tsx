import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function WorkspaceHeader() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function signOut() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className="topbar">
      <Link className="brand" to="/workspaces" aria-label="Syncora workspaces">
        <span className="brand-mark" aria-hidden="true"><i /><i /><i /><i /></span>
        <span>syncora</span>
      </Link>
      <nav className="topbar-user" aria-label="Account navigation">
        <Link className="topbar-email workspace-nav-link" to="/profile">Profile</Link>
        <span className="topbar-email">{user?.email}</span>
        <button className="button-quiet" type="button" onClick={signOut}>
          Log out <span aria-hidden="true">↗</span>
        </button>
      </nav>
    </header>
  )
}
