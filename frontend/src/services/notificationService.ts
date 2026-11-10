import { http } from '../api/http'
import type { ActivityPage, Notification, NotificationPage } from '../types/notification'

export const notificationApi = {
  async list(page = 0, size = 10) {
    const response = await http.get<NotificationPage>('/api/v1/notifications', {
      params: { page, size },
    })
    return response.data
  },

  async unreadCount() {
    const response = await http.get<{ count: number }>('/api/v1/notifications/unread-count')
    return response.data.count
  },

  async markRead(notificationId: string) {
    const response = await http.patch<Notification>(`/api/v1/notifications/${notificationId}/read`)
    return response.data
  },

  async markUnread(notificationId: string) {
    const response = await http.patch<Notification>(`/api/v1/notifications/${notificationId}/unread`)
    return response.data
  },

  async markAllRead() {
    const response = await http.patch<{ updatedCount: number }>('/api/v1/notifications/read-all')
    return response.data.updatedCount
  },

  async delete(notificationId: string) {
    await http.delete(`/api/v1/notifications/${notificationId}`)
  },

  async activity(page = 0, size = 20) {
    const response = await http.get<ActivityPage>('/api/v1/activity', { params: { page, size } })
    return response.data
  },
}
