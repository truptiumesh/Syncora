export interface ChatMessage {
  id: string
  projectId: string
  senderId: string
  senderName: string
  content: string | null
  createdAt: string
  updatedAt: string
  editedAt: string | null
  deletedAt: string | null
  deleted: boolean
}

export interface ChatMessagePage {
  content: ChatMessage[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}
