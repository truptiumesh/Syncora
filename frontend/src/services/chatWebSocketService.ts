import { Client } from '@stomp/stompjs'
import type { ChatMessage } from '../types/chat'
import { apiBaseUrl } from '../api/http'

export type ChatConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'disconnected' | 'error'

export interface ChatConnectionCallbacks {
  onStateChange: (state: ChatConnectionState, error?: string) => void
  onMessage: (message: ChatMessage) => void
}

export interface ChatConnection {
  send: (content: string) => boolean
  disconnect: () => Promise<void>
}

export function connectProjectChat(
  projectId: string,
  token: string,
  callbacks: ChatConnectionCallbacks,
): ChatConnection {
  const socketUrl = new URL('/ws', apiBaseUrl)
  socketUrl.protocol = socketUrl.protocol === 'https:' ? 'wss:' : 'ws:'

  const client = new Client({
    brokerURL: socketUrl.toString(),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      callbacks.onStateChange('connected')
      client.subscribe(`/topic/projects/${projectId}/messages`, (frame) => {
        try {
          callbacks.onMessage(JSON.parse(frame.body) as ChatMessage)
        } catch {
          callbacks.onStateChange('error', 'The chat server returned an invalid message.')
        }
      })
    },
    onStompError: () => callbacks.onStateChange('error', 'Chat connection error. Reconnecting...'),
    onWebSocketClose: () => callbacks.onStateChange('reconnecting'),
    onWebSocketError: () => callbacks.onStateChange('error', 'Chat connection is unavailable.'),
  })

  callbacks.onStateChange('connecting')
  client.activate()

  return {
    send(content: string) {
      if (!client.connected) return false
      client.publish({
        destination: `/app/projects/${projectId}/messages`,
        body: JSON.stringify({ content }),
      })
      return true
    },
    async disconnect() {
      await client.deactivate()
      callbacks.onStateChange('disconnected')
    },
  }
}
