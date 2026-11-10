import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import {
  Activity,
  Bell,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Files,
  FolderKanban,
  LayoutDashboard,
  LogOut,
  MessageCircle,
  PanelLeftClose,
  PanelLeftOpen,
  UserRound,
  UsersRound,
  ListChecks,
} from 'lucide-react'
import { useAuth } from '../auth/AuthContext'

const navigation = [
  { label: 'Dashboard', to: '/dashboard', Icon: LayoutDashboard },
  { label: 'Workspaces', to: '/workspaces', Icon: UsersRound },
  { label: 'Projects', to: '/projects', Icon: FolderKanban },
  { label: 'Tasks', to: '/tasks', Icon: ListChecks },
  { label: 'Calendar', to: '/calendar', Icon: CalendarDays },
  { label: 'Messages', to: '/messages', Icon: MessageCircle },
  { label: 'Activity', to: '/activity', Icon: Activity },
  { label: 'Notifications', to: '/notifications', Icon: Bell },
  { label: 'Files', to: '/files', Icon: Files },
  { label: 'My Profile', to: '/profile', Icon: UserRound },
]

export function AppSidebar() {
  const { logout, user } = useAuth()
  const navigate = useNavigate()
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)

  function signOut() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <>
      <button
        className="sidebar-mobile-toggle"
        type="button"
        aria-label={mobileOpen ? 'Close navigation menu' : 'Open navigation menu'}
        aria-expanded={mobileOpen}
        onClick={() => setMobileOpen((open) => !open)}
      >
        {mobileOpen ? <PanelLeftClose size={20} /> : <PanelLeftOpen size={20} />}
      </button>
      {mobileOpen && <button className="sidebar-scrim" type="button" aria-label="Close navigation menu" onClick={() => setMobileOpen(false)} />}
      <aside className={`app-sidebar${collapsed ? ' app-sidebar-collapsed' : ''}${mobileOpen ? ' app-sidebar-mobile-open' : ''}`}>
        <NavLink className="sidebar-brand" to="/dashboard" aria-label="SYNCORA Dashboard" onClick={() => setMobileOpen(false)}>
          <span className="sidebar-brand-mark" aria-hidden="true"><i /><i /><i /><i /></span>
          {!collapsed && <span className="sidebar-brand-name">syncora</span>}
        </NavLink>
        <div className="sidebar-caption">{!collapsed && 'WORKSPACE'}</div>
        <nav className="sidebar-navigation" aria-label="Main navigation">
          {navigation.map(({ label, to, Icon }) => (
            <NavLink
              key={to}
              className={({ isActive }) => `sidebar-link${isActive ? ' sidebar-link-active' : ''}`}
              to={to}
              title={collapsed ? label : undefined}
              aria-label={label}
              onClick={() => setMobileOpen(false)}
            >
              <Icon size={18} strokeWidth={1.8} aria-hidden="true" />
              {!collapsed && <span>{label}</span>}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          {!collapsed && user && (
            <div className="sidebar-user">
              <span className="sidebar-avatar" aria-hidden="true">{user.fullName.trim().charAt(0).toUpperCase()}</span>
              <span className="sidebar-user-copy"><strong>{user.fullName}</strong><small>{user.email}</small></span>
            </div>
          )}
          <button className="sidebar-link sidebar-logout" type="button" title={collapsed ? 'Log out' : undefined} onClick={signOut}>
            <LogOut size={18} strokeWidth={1.8} aria-hidden="true" />
            {!collapsed && <span>Log out</span>}
          </button>
          <button
            className="sidebar-collapse"
            type="button"
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            onClick={() => setCollapsed((value) => !value)}
          >
            {collapsed ? <ChevronRight size={17} /> : <ChevronLeft size={17} />}
            {!collapsed && <span>Collapse menu</span>}
          </button>
        </div>
      </aside>
    </>
  )
}
