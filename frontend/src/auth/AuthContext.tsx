import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { setAccessToken } from '../api/http'
import { authApi } from '../services/authService'
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../types/auth'

interface AuthContextValue {
  token: string | null
  user: UserResponse | null
  login: (request: LoginRequest) => Promise<void>
  register: (request: RegisterRequest) => Promise<void>
  logout: () => void
  setUser: (user: UserResponse) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null)
  const [user, setUser] = useState<UserResponse | null>(null)

  const establishSession = useCallback((response: AuthResponse) => {
    setAccessToken(response.token)
    setToken(response.token)
    setUser(response.user)
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
  }, [])

  const value = useMemo(
    () => ({ token, user, login, register, logout, setUser }),
    [token, user, login, register, logout],
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
