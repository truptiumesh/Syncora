import { http } from '../api/http'
import type { DirectConversation, DirectMessagePage } from '../types/directMessage'

export const directMessageApi = {
  async listConversations(workspaceId: string) {
    const response = await http.get<DirectConversation[]>(
      `/api/v1/workspaces/${workspaceId}/direct-conversations`,
    )
    return response.data
  },

  async createConversation(workspaceId: string, recipientId: string) {
    const response = await http.post<DirectConversation>(
      `/api/v1/workspaces/${workspaceId}/direct-conversations`,
      { recipientId },
    )
    return response.data
  },

  async history(conversationId: string, page = 0, size = 50) {
    const response = await http.get<DirectMessagePage>(
      `/api/v1/direct-conversations/${conversationId}/messages`,
      { params: { page, size } },
    )
    return response.data
  },
}
