export interface DirectConversation {
  id: string
  workspaceId: string
  recipientId: string
  recipientName: string
  createdAt: string
  updatedAt: string
}

export interface DirectMessage {
  id: string
  conversationId: string
  senderId: string
  senderName: string
  content: string
  createdAt: string
}

export interface DirectMessagePage {
  content: DirectMessage[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}
