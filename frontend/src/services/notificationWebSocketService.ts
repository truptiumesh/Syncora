import { Client } from '@stomp/stompjs'
import type { Notification } from '../types/notification'
import { apiBaseUrl } from '../api/http'

export type NotificationConnectionState =
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'
  | 'error'

export interface NotificationConnection {
  disconnect: () => Promise<void>
}

export function connectNotifications(
  token: string,
  onNotification: (notification: Notification) => void,
  onStateChange: (state: NotificationConnectionState) => void,
): NotificationConnection {
  const socketUrl = new URL('/ws', apiBaseUrl)
  socketUrl.protocol = socketUrl.protocol === 'https:' ? 'wss:' : 'ws:'

  const client = new Client({
    brokerURL: socketUrl.toString(),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      onStateChange('connected')
      client.subscribe('/user/queue/notifications', (frame) => {
        try {
          onNotification(JSON.parse(frame.body) as Notification)
        } catch {
          onStateChange('error')
        }
      })
    },
    onStompError: () => onStateChange('error'),
    onWebSocketClose: () => onStateChange('reconnecting'),
    onWebSocketError: () => onStateChange('error'),
  })

  onStateChange('connecting')
  client.activate()

  return {
    async disconnect() {
      await client.deactivate()
      onStateChange('disconnected')
    },
  }
}
