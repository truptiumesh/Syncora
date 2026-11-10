export interface FileAttachment {
  id: string
  filename: string
  contentType: string
  size: number
  uploaderId: string
  uploaderName: string
  workspaceId: string
  projectId: string | null
  uploadedAt: string
}

export interface FileAttachmentPage {
  content: FileAttachment[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}
