export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER'

export interface CreateWorkspaceRequest {
  name: string
  description: string
}

export interface UpdateWorkspaceRequest {
  name: string
  description: string
}

export interface AddWorkspaceMemberRequest {
  email: string
}

export interface WorkspaceResponse {
  id: string
  name: string
  description: string | null
  slug: string
  ownerId: string
  memberCount: number
  currentUserRole: WorkspaceRole
  createdAt: string
  updatedAt: string
}

export interface WorkspaceMemberResponse {
  id: string
  userId: string
  fullName: string
  email: string
  profileImageUrl: string | null
  role: WorkspaceRole
  joinedAt: string
}
