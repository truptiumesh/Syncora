export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED'
export type ProjectRole = 'PROJECT_MANAGER' | 'MEMBER'

export interface CreateProjectRequest {
  name: string
  description: string
  status: ProjectStatus
  startDate: string | null
  dueDate: string | null
}

export interface UpdateProjectRequest extends CreateProjectRequest {}

export interface ProjectResponse {
  id: string
  workspaceId: string
  name: string
  description: string | null
  key: string
  status: ProjectStatus
  startDate: string | null
  dueDate: string | null
  createdBy: string
  projectManager: string
  memberCount: number
  currentUserRole: ProjectRole | null
  createdAt: string
  updatedAt: string
  archivedAt: string | null
}

export interface ProjectMemberResponse {
  id: string
  userId: string
  fullName: string
  email: string
  profileImageUrl: string | null
  role: ProjectRole
  joinedAt: string
}

export interface ProjectPage {
  content: ProjectResponse[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}
