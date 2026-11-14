import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { ProjectDetailsPage } from './pages/ProjectDetailsPage'
import { ProjectChatPage } from './pages/ProjectChatPage'
import { ActivityPage } from './pages/ActivityPage'
import { ProjectListPage } from './pages/ProjectListPage'
import { TaskDetailsPage } from './pages/TaskDetailsPage'
import { TaskListPage } from './pages/TaskListPage'
import { RegisterPage } from './pages/RegisterPage'
import { WorkspaceDetailsPage } from './pages/WorkspaceDetailsPage'
import { WorkspaceListPage } from './pages/WorkspaceListPage'
import { DashboardPage } from './pages/DashboardPage'
import { WorkspaceMessagesPage } from './pages/WorkspaceMessagesPage'
import { CalendarPage } from './pages/CalendarPage'
import { ProjectsOverviewPage } from './pages/ProjectsOverviewPage'
import { TasksOverviewPage } from './pages/TasksOverviewPage'
import { MessagesOverviewPage } from './pages/MessagesOverviewPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { FilesOverviewPage } from './pages/FilesOverviewPage'
import { AppSidebar } from './components/AppSidebar'
import type { ReactNode } from 'react'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  )
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
      <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
      <Route path="/projects" element={<ProtectedRoute><ProjectsOverviewPage /></ProtectedRoute>} />
      <Route path="/tasks" element={<ProtectedRoute><TasksOverviewPage /></ProtectedRoute>} />
      <Route path="/calendar" element={<ProtectedRoute><CalendarPage /></ProtectedRoute>} />
      <Route path="/messages" element={<ProtectedRoute><MessagesOverviewPage /></ProtectedRoute>} />
      <Route path="/notifications" element={<ProtectedRoute><NotificationsPage /></ProtectedRoute>} />
      <Route path="/files" element={<ProtectedRoute><FilesOverviewPage /></ProtectedRoute>} />
      <Route path="/activity" element={<ProtectedRoute><ActivityPage /></ProtectedRoute>} />
      <Route path="/workspaces" element={<ProtectedRoute><WorkspaceListPage /></ProtectedRoute>} />
      <Route path="/workspaces/:workspaceId" element={<ProtectedRoute><WorkspaceDetailsPage /></ProtectedRoute>} />
      <Route path="/workspaces/:workspaceId/messages" element={<ProtectedRoute><WorkspaceMessagesPage /></ProtectedRoute>} />
      <Route path="/workspaces/:workspaceId/projects" element={<ProtectedRoute><ProjectListPage /></ProtectedRoute>} />
      <Route path="/projects/:projectId" element={<ProtectedRoute><ProjectDetailsPage /></ProtectedRoute>} />
      <Route path="/projects/:projectId/chat" element={<ProtectedRoute><ProjectChatPage /></ProtectedRoute>} />
      <Route path="/projects/:projectId/tasks" element={<ProtectedRoute><TaskListPage /></ProtectedRoute>} />
      <Route path="/tasks/:taskId" element={<ProtectedRoute><TaskDetailsPage /></ProtectedRoute>} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { token, sessionExpired } = useAuth()
  return token ? (
    <div className="app-shell">
      <AppSidebar />
      <div className="app-shell-content">{children}</div>
    </div>
  ) : <Navigate to="/login" replace state={{ sessionExpired }} />
}
