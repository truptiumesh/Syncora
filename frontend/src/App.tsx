import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { ProjectDetailsPage } from './pages/ProjectDetailsPage'
import { ProjectListPage } from './pages/ProjectListPage'
import { RegisterPage } from './pages/RegisterPage'
import { WorkspaceDetailsPage } from './pages/WorkspaceDetailsPage'
import { WorkspaceListPage } from './pages/WorkspaceListPage'
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
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
      <Route path="/workspaces" element={<ProtectedRoute><WorkspaceListPage /></ProtectedRoute>} />
      <Route path="/workspaces/:workspaceId" element={<ProtectedRoute><WorkspaceDetailsPage /></ProtectedRoute>} />
      <Route path="/workspaces/:workspaceId/projects" element={<ProtectedRoute><ProjectListPage /></ProtectedRoute>} />
      <Route path="/projects/:projectId" element={<ProtectedRoute><ProjectDetailsPage /></ProtectedRoute>} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { token } = useAuth()
  return token ? children : <Navigate to="/login" replace />
}
