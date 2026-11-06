import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { AppRoutes } from './App'
import { AuthProvider } from './auth/AuthContext'
import { setAccessToken } from './api/http'
import { authApi } from './services/authService'
import type { AuthResponse, UserResponse } from './types/auth'

vi.mock('./services/authService', () => ({
  authApi: {
    register: vi.fn(),
    login: vi.fn(),
    getCurrentUser: vi.fn(),
    updateProfile: vi.fn(),
    changePassword: vi.fn(),
  },
}))

const userProfile: UserResponse = {
  id: '550e8400-e29b-41d4-a716-446655440000',
  fullName: 'Test User',
  email: 'test@example.com',
  profileImageUrl: null,
  bio: null,
  role: 'MEMBER',
  active: true,
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
}

const authResponse: AuthResponse = {
  token: 'test-access-token',
  tokenType: 'Bearer',
  user: userProfile,
}

function renderRoutes(path = '/login') {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('authentication pages and routes', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setAccessToken(null)
    vi.mocked(authApi.register).mockResolvedValue(authResponse)
    vi.mocked(authApi.login).mockResolvedValue(authResponse)
    vi.mocked(authApi.getCurrentUser).mockResolvedValue(userProfile)
  })

  it('redirects an unauthenticated visitor away from the profile', async () => {
    renderRoutes('/profile')

    expect(await screen.findByRole('heading', { name: 'Sign in to Syncora' })).toBeInTheDocument()
    expect(authApi.getCurrentUser).not.toHaveBeenCalled()
  })

  it('validates registration confirmation before submitting', async () => {
    renderRoutes('/register')

    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'Test User' } })
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'StrongPassword123' } })
    fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'DifferentPassword123' } })
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Your passwords do not match.')
    expect(authApi.register).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'StrongPassword123' } })
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByRole('heading', { name: 'Profile & settings' })).toBeInTheDocument()
    expect(authApi.register).toHaveBeenCalledWith({
      fullName: 'Test User',
      email: 'test@example.com',
      password: 'StrongPassword123',
    })
  })

  it('shows login errors and returns to a usable state', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.login).mockRejectedValueOnce(new Error('invalid credentials'))
    renderRoutes()

    await user.type(screen.getByLabelText('Email address'), 'test@example.com')
    await user.type(screen.getByLabelText('Password'), 'WrongPassword123')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not sign you in.')
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })

  it('allows a signed-in user to log out and protects the profile afterwards', async () => {
    const user = userEvent.setup()
    renderRoutes()
    await user.type(screen.getByLabelText('Email address'), 'test@example.com')
    await user.type(screen.getByLabelText('Password'), 'StrongPassword123')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Profile & settings' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /log out/i }))

    expect(await screen.findByRole('heading', { name: 'Sign in to Syncora' })).toBeInTheDocument()
    await waitFor(() => expect(authApi.getCurrentUser).toHaveBeenCalledTimes(1))
  })

  it('shows a loading state while login is in progress', async () => {
    let finishLogin: (response: AuthResponse) => void = () => undefined
    vi.mocked(authApi.login).mockImplementationOnce(() => new Promise((resolve) => {
      finishLogin = resolve
    }))
    renderRoutes()

    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'StrongPassword123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('button', { name: /please wait/i })).toBeDisabled()
    finishLogin(authResponse)
    await screen.findByRole('heading', { name: 'Profile & settings' })
  })
})
