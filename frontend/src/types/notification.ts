export type NotificationType =
  | 'TASK_ASSIGNED'
  | 'TASK_REASSIGNED'
  | 'TASK_STATUS_CHANGED'
  | 'TASK_PRIORITY_CHANGED'
  | 'TASK_COMMENT_ADDED'
  | 'PROJECT_MEMBER_ADDED'
  | 'PROJECT_UPDATED'
  | 'WORKSPACE_UPDATED'
  | 'WORKSPACE_MEMBER_ADDED'

export interface Notification {
  id: string
  type: NotificationType
  title: string
  message: string
  actorId: string | null
  actorName: string
  workspaceId: string | null
  workspaceName: string | null
  projectId: string | null
  projectName: string | null
  taskId: string | null
  taskKey: string | null
  taskTitle: string | null
  taskCommentId: string | null
  chatMessageId: string | null
  read: boolean
  createdAt: string
  readAt: string | null
}

export interface NotificationPage {
  content: Notification[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export type ActivityType =
  | 'WORKSPACE_CREATED'
  | 'WORKSPACE_UPDATED'
  | 'WORKSPACE_MEMBER_ADDED'
  | 'PROJECT_CREATED'
  | 'PROJECT_UPDATED'
  | 'PROJECT_MEMBER_ADDED'
  | 'TASK_CREATED'
  | 'TASK_UPDATED'
  | 'TASK_ASSIGNED'
  | 'TASK_REASSIGNED'
  | 'TASK_STATUS_CHANGED'
  | 'TASK_PRIORITY_CHANGED'
  | 'TASK_COMMENT_ADDED'
  | 'CHAT_MESSAGE_SENT'
  | 'CHAT_MESSAGE_EDITED'
  | 'CHAT_MESSAGE_DELETED'
  | 'FILE_UPLOADED'
  | 'FILE_DELETED'

export interface Activity {
  id: string
  type: ActivityType
  description: string
  actorId: string | null
  actorName: string
  workspaceId: string
  projectId: string | null
  projectName: string | null
  taskId: string | null
  taskKey: string | null
  taskTitle: string | null
  createdAt: string
}

export interface ActivityPage {
  content: Activity[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}
