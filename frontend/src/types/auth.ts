export type UserRole = 'ADMIN' | 'MANAGER' | 'MEMBER'

export interface RegisterRequest {
  fullName: string
  email: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface UpdateProfileRequest {
  fullName: string
  bio: string
  profileImageUrl: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface UserResponse {
  id: string
  fullName: string
  email: string
  profileImageUrl: string | null
  bio: string | null
  role: UserRole
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface AuthResponse {
  token: string
  tokenType: 'Bearer'
  user: UserResponse
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  validationErrors: Record<string, string>
}
