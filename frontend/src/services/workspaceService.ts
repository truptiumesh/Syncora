import { http } from '../api/http'
import type {
  AddWorkspaceMemberRequest,
  CreateWorkspaceRequest,
  UpdateWorkspaceRequest,
  WorkspaceMemberResponse,
  WorkspaceResponse,
  WorkspaceRole,
} from '../types/workspace'

export const workspaceApi = {
  async list() {
    const response = await http.get<WorkspaceResponse[]>('/api/v1/workspaces')
    return response.data
  },

  async get(workspaceId: string) {
    const response = await http.get<WorkspaceResponse>(`/api/v1/workspaces/${workspaceId}`)
    return response.data
  },

  async create(request: CreateWorkspaceRequest) {
    const response = await http.post<WorkspaceResponse>('/api/v1/workspaces', request)
    return response.data
  },

  async update(workspaceId: string, request: UpdateWorkspaceRequest) {
    const response = await http.put<WorkspaceResponse>(`/api/v1/workspaces/${workspaceId}`, request)
    return response.data
  },

  async delete(workspaceId: string) {
    await http.delete(`/api/v1/workspaces/${workspaceId}`)
  },

  async listMembers(workspaceId: string) {
    const response = await http.get<WorkspaceMemberResponse[]>(
      `/api/v1/workspaces/${workspaceId}/members`,
    )
    return response.data
  },

  async addMember(workspaceId: string, request: AddWorkspaceMemberRequest) {
    const response = await http.post<WorkspaceMemberResponse>(
      `/api/v1/workspaces/${workspaceId}/members`,
      request,
    )
    return response.data
  },

  async updateMemberRole(workspaceId: string, memberId: string, role: WorkspaceRole) {
    const response = await http.put<WorkspaceMemberResponse>(
      `/api/v1/workspaces/${workspaceId}/members/${memberId}/role`,
      { role },
    )
    return response.data
  },

  async removeMember(workspaceId: string, memberId: string) {
    await http.delete(`/api/v1/workspaces/${workspaceId}/members/${memberId}`)
  },

  async leave(workspaceId: string) {
    await http.delete(`/api/v1/workspaces/${workspaceId}/members/me`)
  },
}
