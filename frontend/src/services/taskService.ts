import { http } from '../api/http'
import type {
  CreateTaskRequest,
  TaskCommentResponse,
  TaskFilters,
  TaskPage,
  TaskPriority,
  TaskResponse,
  TaskStatus,
  UpdateTaskRequest,
} from '../types/task'

export const taskApi = {
  async list(projectId: string, filters: TaskFilters = {}) {
    const response = await http.get<TaskPage>(`/api/v1/projects/${projectId}/tasks`, {
      params: filters,
    })
    return response.data
  },

  async create(projectId: string, request: CreateTaskRequest) {
    const response = await http.post<TaskResponse>(`/api/v1/projects/${projectId}/tasks`, request)
    return response.data
  },

  async get(taskId: string) {
    const response = await http.get<TaskResponse>(`/api/v1/tasks/${taskId}`)
    return response.data
  },

  async update(taskId: string, request: UpdateTaskRequest) {
    const response = await http.put<TaskResponse>(`/api/v1/tasks/${taskId}`, request)
    return response.data
  },

  async updateStatus(taskId: string, status: TaskStatus) {
    const response = await http.patch<TaskResponse>(`/api/v1/tasks/${taskId}/status`, { status })
    return response.data
  },

  async updatePriority(taskId: string, priority: TaskPriority) {
    const response = await http.patch<TaskResponse>(`/api/v1/tasks/${taskId}/priority`, { priority })
    return response.data
  },

  async assign(taskId: string, assigneeId: string | null) {
    const response = await http.patch<TaskResponse>(`/api/v1/tasks/${taskId}/assignee`, { assigneeId })
    return response.data
  },

  async delete(taskId: string) {
    await http.delete(`/api/v1/tasks/${taskId}`)
  },

  async listComments(taskId: string) {
    const response = await http.get<TaskCommentResponse[]>(`/api/v1/tasks/${taskId}/comments`)
    return response.data
  },

  async addComment(taskId: string, content: string) {
    const response = await http.post<TaskCommentResponse>(
      `/api/v1/tasks/${taskId}/comments`,
      { content },
    )
    return response.data
  },

  async updateComment(taskId: string, commentId: string, content: string) {
    const response = await http.put<TaskCommentResponse>(
      `/api/v1/tasks/${taskId}/comments/${commentId}`,
      { content },
    )
    return response.data
  },

  async deleteComment(taskId: string, commentId: string) {
    await http.delete(`/api/v1/tasks/${taskId}/comments/${commentId}`)
  },
}
