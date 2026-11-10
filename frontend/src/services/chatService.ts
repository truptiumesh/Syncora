import { http } from '../api/http'
import type { ChatMessage, ChatMessagePage } from '../types/chat'

export const chatApi = {
  async history(projectId: string, page = 0, size = 50) {
    const response = await http.get<ChatMessagePage>(`/api/v1/projects/${projectId}/messages`, {
      params: { page, size },
    })
    return response.data
  },

  async send(projectId: string, content: string) {
    const response = await http.post<ChatMessage>(`/api/v1/projects/${projectId}/messages`, { content })
    return response.data
  },

  async update(messageId: string, content: string) {
    const response = await http.put<ChatMessage>(`/api/v1/messages/${messageId}`, { content })
    return response.data
  },

  async delete(messageId: string) {
    await http.delete(`/api/v1/messages/${messageId}`)
  },
}
