import { http } from '../api/http'
import type {
  CreateProjectRequest,
  ProjectMemberResponse,
  ProjectPage,
  ProjectResponse,
  ProjectRole,
  ProjectStatus,
  UpdateProjectRequest,
} from '../types/project'

export interface ProjectFilters {
  status?: ProjectStatus
  search?: string
  archived?: boolean
  page?: number
  size?: number
}

export const projectApi = {
  async list(workspaceId: string, filters: ProjectFilters = {}) {
    const response = await http.get<ProjectPage>(`/api/v1/workspaces/${workspaceId}/projects`, {
      params: filters,
    })
    return response.data
  },

  async create(workspaceId: string, request: CreateProjectRequest) {
    const response = await http.post<ProjectResponse>(
      `/api/v1/workspaces/${workspaceId}/projects`,
      request,
    )
    return response.data
  },

  async get(projectId: string) {
    const response = await http.get<ProjectResponse>(`/api/v1/projects/${projectId}`)
    return response.data
  },

  async update(projectId: string, request: UpdateProjectRequest) {
    const response = await http.put<ProjectResponse>(`/api/v1/projects/${projectId}`, request)
    return response.data
  },

  async archive(projectId: string) {
    const response = await http.put<ProjectResponse>(`/api/v1/projects/${projectId}/archive`)
    return response.data
  },

  async restore(projectId: string) {
    const response = await http.put<ProjectResponse>(`/api/v1/projects/${projectId}/restore`)
    return response.data
  },

  async delete(projectId: string) {
    await http.delete(`/api/v1/projects/${projectId}`)
  },

  async listMembers(projectId: string) {
    const response = await http.get<ProjectMemberResponse[]>(
      `/api/v1/projects/${projectId}/members`,
    )
    return response.data
  },

  async addMember(projectId: string, email: string) {
    const response = await http.post<ProjectMemberResponse>(
      `/api/v1/projects/${projectId}/members`,
      { email },
    )
    return response.data
  },

  async updateMemberRole(projectId: string, memberId: string, role: ProjectRole) {
    const response = await http.put<ProjectMemberResponse>(
      `/api/v1/projects/${projectId}/members/${memberId}/role`,
      { role },
    )
    return response.data
  },

  async removeMember(projectId: string, memberId: string) {
    await http.delete(`/api/v1/projects/${projectId}/members/${memberId}`)
  },
}
