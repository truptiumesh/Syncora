import { Client } from '@stomp/stompjs'
import type { DirectMessage } from '../types/directMessage'
import type { ChatConnectionState } from './chatWebSocketService'
import { apiBaseUrl } from '../api/http'

export interface DirectMessageConnection {
  send: (content: string) => boolean
  disconnect: () => Promise<void>
}

export function connectDirectMessages(
  conversationId: string,
  token: string,
  callbacks: {
    onStateChange: (state: ChatConnectionState, error?: string) => void
    onMessage: (message: DirectMessage) => void
  },
): DirectMessageConnection {
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
      client.subscribe(`/topic/direct/${conversationId}`, (frame) => {
        try {
          callbacks.onMessage(JSON.parse(frame.body) as DirectMessage)
        } catch {
          callbacks.onStateChange('error', 'The messaging server returned an invalid message.')
        }
      })
    },
    onStompError: () => callbacks.onStateChange('error', 'Messaging connection error. Reconnecting...'),
    onWebSocketClose: () => callbacks.onStateChange('reconnecting'),
    onWebSocketError: () => callbacks.onStateChange('error', 'Messaging connection is unavailable.'),
  })
  callbacks.onStateChange('connecting')
  client.activate()
  return {
    send(content) {
      if (!client.connected) return false
      client.publish({
        destination: `/app/direct/${conversationId}/messages`,
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
