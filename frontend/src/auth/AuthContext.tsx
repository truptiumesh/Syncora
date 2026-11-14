import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
  useEffect,
} from 'react'
import { setAccessToken, subscribeToUnauthorized } from '../api/http'
import { authApi } from '../services/authService'
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../types/auth'

interface AuthContextValue {
  token: string | null
  user: UserResponse | null
  sessionExpired: boolean
  login: (request: LoginRequest) => Promise<void>
  register: (request: RegisterRequest) => Promise<void>
  logout: () => void
  setUser: (user: UserResponse) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null)
  const [user, setUser] = useState<UserResponse | null>(null)
  const [sessionExpired, setSessionExpired] = useState(false)

  const establishSession = useCallback((response: AuthResponse) => {
    setAccessToken(response.token)
    setToken(response.token)
    setUser(response.user)
    setSessionExpired(false)
  }, [])

  const login = useCallback(async (request: LoginRequest) => {
    establishSession(await authApi.login(request))
  }, [establishSession])

  const register = useCallback(async (request: RegisterRequest) => {
    establishSession(await authApi.register(request))
  }, [establishSession])

  const logout = useCallback(() => {
    setAccessToken(null)
    setToken(null)
    setUser(null)
    setSessionExpired(false)
  }, [])

  useEffect(() => subscribeToUnauthorized(() => {
    setToken(null)
    setUser(null)
    setSessionExpired(true)
  }), [])

  const value = useMemo(
    () => ({ token, user, sessionExpired, login, register, logout, setUser }),
    [token, user, sessionExpired, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider.')
  }
  return context
}
