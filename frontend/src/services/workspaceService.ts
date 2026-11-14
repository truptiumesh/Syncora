import { http } from '../api/http'
import type {
  AddWorkspaceMemberRequest,
  CreateWorkspaceRequest,
  UpdateWorkspaceRequest,
  WorkspaceMemberResponse,
  WorkspaceResponse,
  WorkspaceRole,
} from '../types/workspace'

export const requestedWorkspaceSet: CreateWorkspaceRequest[] = [
  {
    name: 'Engineering & Development',
    description: 'Manage software development activities, technical projects, sprint planning, bug tracking, and product releases.',
  },
  {
    name: 'Marketing & Growth',
    description: 'Plan marketing campaigns, manage social media activities, track leads, and improve brand awareness.',
  },
  {
    name: 'Human Resources',
    description: 'Manage recruitment, employee onboarding, training programs, and internal HR activities.',
  },
  {
    name: 'Product Management',
    description: 'Organize product roadmaps, feature planning, user feedback, and product development priorities.',
  },
  {
    name: 'Sales & Business Development',
    description: 'Track sales opportunities, manage client relationships, monitor business growth, and coordinate sales activities.',
  },
  {
    name: 'Design & Creative',
    description: 'Collaborate on UI/UX designs, branding, creative assets, and design review processes.',
  },
  {
    name: 'Customer Support',
    description: 'Manage customer inquiries, support tickets, issue resolution, and customer satisfaction initiatives.',
  },
  {
    name: 'Finance & Operations',
    description: 'Coordinate budgeting, financial reporting, expense tracking, and operational activities.',
  },
  {
    name: 'Research & Innovation',
    description: 'Organize research initiatives, experiments, technical investigations, and innovation projects.',
  },
  {
    name: 'IT & Infrastructure',
    description: 'Manage IT infrastructure, system maintenance, security improvements, and technical support operations.',
  },
]

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
