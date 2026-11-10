import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { PrimaryButton } from '../components/PrimaryButton'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { apiErrorMessage } from '../utils/apiError'
import { chatApi } from '../services/chatService'
import {
  connectProjectChat,
  type ChatConnection,
  type ChatConnectionState,
} from '../services/chatWebSocketService'
import { projectApi } from '../services/projectService'
import { workspaceApi } from '../services/workspaceService'
import type { ChatMessage, ChatMessagePage } from '../types/chat'
import type { ProjectResponse } from '../types/project'
import type { WorkspaceResponse } from '../types/workspace'

const PAGE_SIZE = 50

function mergeMessages(existing: ChatMessage[], incoming: ChatMessage[]) {
  const byId = new Map(existing.map((message) => [message.id, message]))
  incoming.forEach((message) => byId.set(message.id, message))
  return [...byId.values()].sort((first, second) =>
    first.createdAt.localeCompare(second.createdAt) || first.id.localeCompare(second.id))
}

function mergeMessagePage(existing: ChatMessagePage | null, incoming: ChatMessagePage): ChatMessagePage {
  return {
    ...incoming,
    content: mergeMessages(existing?.content ?? [], incoming.content),
  }
}

export function ProjectChatPage() {
  const { projectId = '' } = useParams()
  const { token, user } = useAuth()
  const connectionRef = useRef<ChatConnection | null>(null)
  const [project, setProject] = useState<ProjectResponse | null>(null)
  const [workspace, setWorkspace] = useState<WorkspaceResponse | null>(null)
  const [history, setHistory] = useState<ChatMessagePage | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [connectionState, setConnectionState] = useState<ChatConnectionState>('connecting')
  const [connectionError, setConnectionError] = useState('')
  const [error, setError] = useState('')
  const [content, setContent] = useState('')
  const [sending, setSending] = useState(false)
  const [editingMessage, setEditingMessage] = useState<string | null>(null)
  const [editContent, setEditContent] = useState('')
  const [savingEdit, setSavingEdit] = useState(false)

  const loadPage = useCallback(async (pageIndex: number) => {
    const result = await chatApi.history(projectId, pageIndex, PAGE_SIZE)
    setHistory((current) => mergeMessagePage(current, result))
    setPage(pageIndex)
  }, [projectId])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    Promise.all([chatApi.history(projectId, 0, PAGE_SIZE), projectApi.get(projectId)])
      .then(async ([messages, projectDetails]) => {
        const workspaceDetails = await workspaceApi.get(projectDetails.workspaceId)
        if (!active) return
        setHistory(messages)
        setProject(projectDetails)
        setWorkspace(workspaceDetails)
      })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Project chat could not be loaded.'))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [projectId])

  useEffect(() => {
    if (!token) {
      setConnectionState('disconnected')
      return
    }
    let active = true
    const connection = connectProjectChat(projectId, token, {
      onStateChange(state, reason) {
        if (!active) return
        setConnectionState(state)
        setConnectionError(reason ?? '')
        if (state === 'error' || state === 'reconnecting' || state === 'disconnected') {
          setSending(false)
        }
      },
      onMessage(message) {
        if (active && message.projectId === projectId) {
          setHistory((current) => current
            ? { ...current, content: mergeMessages(current.content, [message]) }
            : current)
          if (message.senderId === user?.id) {
            setSending(false)
            setContent('')
          }
        }
      },
    })
    connectionRef.current = connection
    return () => {
      active = false
      connectionRef.current = null
      void connection.disconnect()
    }
  }, [projectId, token, user?.id])

  const canModerate = project?.currentUserRole === 'PROJECT_MANAGER'
    || workspace?.currentUserRole === 'OWNER'
    || workspace?.currentUserRole === 'ADMIN'

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = content.trim()
    if (!trimmed) {
      setError('Write a message before sending.')
      return
    }
    if (trimmed.length > 4000) {
      setError('Messages must be 4000 characters or fewer.')
      return
    }
    setError('')
    if (!connectionRef.current?.send(trimmed)) {
      setError('Chat is not connected. Your message was not sent.')
      return
    }
    setSending(true)
  }

  async function saveMessage(messageId: string) {
    const trimmed = editContent.trim()
    if (!trimmed) {
      setError('Write a message before saving.')
      return
    }
    setError('')
    setSavingEdit(true)
    try {
      const updated = await chatApi.update(messageId, trimmed)
      setHistory((current) => current
        ? { ...current, content: mergeMessages(current.content, [updated]) }
        : current)
      setEditingMessage(null)
      setEditContent('')
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Message could not be edited.'))
    } finally {
      setSavingEdit(false)
    }
  }

  async function deleteMessage(messageId: string) {
    if (!window.confirm('Delete this message?')) return
    setError('')
    try {
      await chatApi.delete(messageId)
      setHistory((current) => current
        ? {
          ...current,
          content: current.content.map((message) => message.id === messageId
            ? { ...message, content: null, deleted: true, deletedAt: new Date().toISOString() }
            : message),
        }
        : current)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Message could not be deleted.'))
    }
  }

  async function loadMore() {
    if (!history || history.last) return
    setLoadingMore(true)
    setError('')
    try {
      await loadPage(page + 1)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'More chat history could not be loaded.'))
    } finally {
      setLoadingMore(false)
    }
  }

  const messages = history?.content ?? []

  return (
    <main className="workspace-page">
      <WorkspaceHeader />
      <section className="workspace-content project-chat-content">
        <Link className="back-link" to={`/projects/${projectId}`}>← {project?.name || 'Project'}</Link>
        <div className="workspace-page-heading project-heading chat-heading">
          <div>
            <span className="eyebrow">{project?.key || 'PROJECT'} · TEAM CONVERSATION</span>
            <h1>Project chat</h1>
            <p>Keep project conversations and decisions together.</p>
          </div>
          <div className={`chat-connection-state chat-state-${connectionState}`} role="status">
            <span className="chat-connection-dot" aria-hidden="true" />
            {connectionState === 'connected' ? 'Connected'
              : connectionState === 'connecting' ? 'Connecting...'
                : connectionState === 'reconnecting' ? 'Reconnecting...'
                  : connectionState === 'error' ? 'Connection issue'
                    : 'Disconnected'}
          </div>
        </div>
        {connectionError && <p className="chat-connection-notice" role="status">{connectionError}</p>}
        {error && <p className="form-message form-error" role="alert">{error}</p>}

        <section className="chat-panel" aria-label="Project chat">
          {loading ? (
            <div className="workspace-loading" role="status"><span className="spinner" />Loading chat history...</div>
          ) : (
            <>
              {history && !history.last && (
                <button className="button-outline chat-load-more" type="button" disabled={loadingMore} onClick={() => void loadMore()}>
                  {loadingMore ? 'Loading...' : 'Load more history'}
                </button>
              )}
              {messages.length === 0 ? (
                <div className="chat-empty">
                  <span className="empty-icon" aria-hidden="true">✳</span>
                  <h2>Start the conversation</h2>
                  <p>Share an update or ask the team a question.</p>
                </div>
              ) : (
                <ol className="chat-message-list" aria-label="Chat messages">
                  {messages.map((message) => {
                    const ownMessage = message.senderId === user?.id
                    const canChange = ownMessage || canModerate
                    return (
                      <li className={`chat-message ${ownMessage ? 'chat-message-own' : ''}`} key={message.id}>
                        <div className="chat-message-avatar" aria-hidden="true">{message.senderName.trim().charAt(0).toUpperCase()}</div>
                        <article className="chat-message-body">
                          <header className="chat-message-meta">
                            <strong>{message.senderName}</strong>
                            <time dateTime={message.createdAt}>{new Date(message.createdAt).toLocaleString()}</time>
                            {message.editedAt && !message.deleted && <span>edited</span>}
                          </header>
                          {editingMessage === message.id ? (
                            <div className="chat-edit-form">
                              <label className="sr-only" htmlFor={`edit-${message.id}`}>Edit message</label>
                              <textarea id={`edit-${message.id}`} maxLength={4000} value={editContent} onChange={(event) => setEditContent(event.target.value)} />
                              <div className="chat-message-actions">
                                <button className="button-quiet" type="button" onClick={() => setEditingMessage(null)}>Cancel</button>
                                <PrimaryButton type="button" loading={savingEdit} onClick={() => void saveMessage(message.id)}>Save</PrimaryButton>
                              </div>
                            </div>
                          ) : (
                            <p className={message.deleted ? 'chat-message-deleted' : ''}>
                              {message.deleted ? 'This message was deleted.' : message.content}
                            </p>
                          )}
                          {canChange && !message.deleted && editingMessage !== message.id && (
                            <div className="chat-message-actions">
                              <button className="button-quiet" type="button" onClick={() => { setEditingMessage(message.id); setEditContent(message.content ?? '') }}>Edit</button>
                              <button className="button-quiet chat-delete-button" type="button" onClick={() => void deleteMessage(message.id)}>Delete</button>
                            </div>
                          )}
                        </article>
                      </li>
                    )
                  })}
                </ol>
              )}
            </>
          )}
          <form className="chat-compose" onSubmit={(event) => void sendMessage(event)}>
            <label className="sr-only" htmlFor="chat-message-input">Write a message</label>
            <textarea
              id="chat-message-input"
              maxLength={4000}
              rows={2}
              value={content}
              disabled={sending}
              onChange={(event) => setContent(event.target.value)}
              placeholder="Write a message..."
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  event.currentTarget.form?.requestSubmit()
                }
              }}
            />
            <div className="chat-compose-footer">
              <span>Press Enter to send · Shift + Enter for a new line · {content.length}/4000</span>
              <PrimaryButton type="submit" disabled={connectionState !== 'connected' || sending}>Send</PrimaryButton>
            </div>
          </form>
        </section>
      </section>
    </main>
  )
}
