import type { ProjectRole, ProjectStatus } from './project'

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface CreateTaskRequest {
  title: string
  description: string
  priority: TaskPriority
  assigneeId: string | null
  dueDate: string | null
}

export interface UpdateTaskRequest {
  title: string
  description: string
  dueDate: string | null
}

export interface TaskResponse {
  id: string
  projectId: string
  title: string
  description: string | null
  taskKey: string
  status: TaskStatus
  priority: TaskPriority
  assigneeId: string | null
  assigneeName: string | null
  createdBy: string
  creatorName: string
  dueDate: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
  commentCount: number
}

export interface TaskPage {
  content: TaskResponse[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export interface TaskCommentResponse {
  id: string
  taskId: string
  authorId: string
  authorName: string
  content: string
  createdAt: string
  updatedAt: string
}

export interface TaskFilters {
  search?: string
  status?: TaskStatus
  priority?: TaskPriority
  assigneeId?: string
  overdue?: boolean
  page?: number
  size?: number
  sortBy?: 'createdAt' | 'dueDate' | 'priority' | 'updatedAt'
  sortDirection?: 'ASC' | 'DESC'
}

export interface ProjectAccessInfo {
  id: string
  workspaceId: string
  name: string
  key: string
  status: ProjectStatus
  currentUserRole: ProjectRole | null
}
