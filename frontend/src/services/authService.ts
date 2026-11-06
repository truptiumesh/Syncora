import { http } from '../api/http'
import type {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  RegisterRequest,
  UpdateProfileRequest,
  UserResponse,
} from '../types/auth'

export const authApi = {
  async register(request: RegisterRequest) {
    const response = await http.post<AuthResponse>('/api/v1/auth/register', request)
    return response.data
  },

  async login(request: LoginRequest) {
    const response = await http.post<AuthResponse>('/api/v1/auth/login', request)
    return response.data
  },

  async getCurrentUser() {
    const response = await http.get<UserResponse>('/api/v1/users/me')
    return response.data
  },

  async updateProfile(request: UpdateProfileRequest) {
    const response = await http.put<UserResponse>('/api/v1/users/me', request)
    return response.data
  },

  async changePassword(request: ChangePasswordRequest) {
    await http.put('/api/v1/users/me/password', request)
  },
}
