import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { PrimaryButton } from '../components/PrimaryButton'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { directMessageApi } from '../services/directMessageService'
import {
  connectDirectMessages,
  type DirectMessageConnection,
} from '../services/directMessageWebSocketService'
import type { ChatConnectionState } from '../services/chatWebSocketService'
import { workspaceApi } from '../services/workspaceService'
import type { DirectConversation, DirectMessage, DirectMessagePage } from '../types/directMessage'
import type { WorkspaceMemberResponse, WorkspaceResponse } from '../types/workspace'
import { apiErrorMessage } from '../utils/apiError'

const PAGE_SIZE = 50

function mergeMessages(existing: DirectMessage[], incoming: DirectMessage[]) {
  const messages = new Map(existing.map((message) => [message.id, message]))
  incoming.forEach((message) => messages.set(message.id, message))
  return [...messages.values()].sort((first, second) =>
    first.createdAt.localeCompare(second.createdAt) || first.id.localeCompare(second.id))
}

export function WorkspaceMessagesPage() {
  const { workspaceId = '' } = useParams()
  const { token, user } = useAuth()
  const connectionRef = useRef<DirectMessageConnection | null>(null)
  const [workspace, setWorkspace] = useState<WorkspaceResponse | null>(null)
  const [members, setMembers] = useState<WorkspaceMemberResponse[]>([])
  const [conversations, setConversations] = useState<DirectConversation[]>([])
  const [conversationId, setConversationId] = useState('')
  const [history, setHistory] = useState<DirectMessagePage | null>(null)
  const [recipientId, setRecipientId] = useState('')
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [connectionState, setConnectionState] = useState<ChatConnectionState>('disconnected')
  const [connectionError, setConnectionError] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    Promise.all([
      workspaceApi.get(workspaceId),
      workspaceApi.listMembers(workspaceId),
      directMessageApi.listConversations(workspaceId),
    ])
      .then(([workspaceDetails, workspaceMembers, memberConversations]) => {
        if (!active) return
        setWorkspace(workspaceDetails)
        setMembers(workspaceMembers)
        setConversations(memberConversations)
        if (memberConversations.length > 0) setConversationId(memberConversations[0].id)
      })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Workspace messages could not be loaded.'))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [workspaceId])

  useEffect(() => {
    if (!conversationId) {
      setHistory(null)
      return
    }
    let active = true
    setHistory(null)
    setError('')
    directMessageApi.history(conversationId, 0, PAGE_SIZE)
      .then((result) => { if (active) setHistory(result) })
      .catch((requestError: unknown) => {
        if (active) setError(apiErrorMessage(requestError, 'Conversation history could not be loaded.'))
      })
    return () => { active = false }
  }, [conversationId])

  useEffect(() => {
    if (!token || !conversationId) {
      setConnectionState('disconnected')
      return
    }
    let active = true
    const connection = connectDirectMessages(conversationId, token, {
      onStateChange(state, reason) {
        if (!active) return
        setConnectionState(state)
        setConnectionError(reason ?? '')
      },
      onMessage(message) {
        if (active && message.conversationId === conversationId) {
          setHistory((current) => current
            ? { ...current, content: mergeMessages(current.content, [message]) }
            : current)
        }
      },
    })
    connectionRef.current = connection
    return () => {
      active = false
      connectionRef.current = null
      void connection.disconnect()
    }
  }, [conversationId, token])

  async function startConversation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!recipientId) return
    setCreating(true)
    setError('')
    try {
      const conversation = await directMessageApi.createConversation(workspaceId, recipientId)
      setConversations((current) => [
        conversation,
        ...current.filter((item) => item.id !== conversation.id),
      ])
      setConversationId(conversation.id)
      setRecipientId('')
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'A conversation could not be started.'))
    } finally {
      setCreating(false)
    }
  }

  function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = content.trim()
    if (!trimmed) return
    if (trimmed.length > 4000) {
      setError('Messages must be 4000 characters or fewer.')
      return
    }
    setError('')
    if (!connectionRef.current?.send(trimmed)) {
      setError('Messaging is not connected. Your message was not sent.')
      return
    }
    setContent('')
  }

  const selectedConversation = conversations.find((item) => item.id === conversationId)
  const statusText = connectionState === 'connected' ? 'Connected'
    : connectionState === 'connecting' ? 'Connecting...'
      : connectionState === 'reconnecting' ? 'Reconnecting...'
        : connectionState === 'error' ? 'Connection issue'
          : 'Disconnected'

  return (
    <main className="workspace-page">
      <WorkspaceHeader />
      <section className="workspace-content direct-messages-content">
        <Link className="back-link" to={`/workspaces/${workspaceId}`}>← {workspace?.name || 'Workspace'}</Link>
        <div className="workspace-page-heading">
          <div><span className="eyebrow">PRIVATE CONVERSATIONS</span><h1>Messages</h1><p>One-to-one conversations with members of this workspace.</p></div>
        </div>
        {error && <p className="form-message form-error" role="alert">{error}</p>}
        {loading ? (
          <div className="workspace-loading" role="status"><span className="spinner" />Loading messages...</div>
        ) : (
          <section className="direct-messages-panel" aria-label="Workspace direct messages">
            <aside className="direct-conversation-sidebar">
              <form className="direct-start-form" onSubmit={(event) => void startConversation(event)}>
                <label className="field" htmlFor="direct-recipient"><span>Start a conversation</span></label>
                <select id="direct-recipient" value={recipientId} onChange={(event) => setRecipientId(event.target.value)}>
                  <option value="">Choose a workspace member</option>
                  {members.filter((member) => member.userId !== user?.id).map((member) => (
                    <option key={member.userId} value={member.userId}>{member.fullName}</option>
                  ))}
                </select>
                <PrimaryButton type="submit" loading={creating} disabled={!recipientId}>Message</PrimaryButton>
              </form>
              <h2>Conversations</h2>
              {conversations.length === 0 ? (
                <p className="direct-no-conversations">Start a conversation with a workspace member.</p>
              ) : (
                <ul>
                  {conversations.map((conversation) => (
                    <li key={conversation.id}>
                      <button
                        type="button"
                        className={conversation.id === conversationId ? 'direct-conversation active' : 'direct-conversation'}
                        onClick={() => setConversationId(conversation.id)}
                      >
                        <span className="direct-avatar" aria-hidden="true">{conversation.recipientName.trim().charAt(0).toUpperCase()}</span>
                        <span>{conversation.recipientName}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </aside>
            <div className="direct-chat-area">
              {selectedConversation ? (
                <>
                  <header className="direct-chat-header">
                    <span className="direct-avatar" aria-hidden="true">{selectedConversation.recipientName.trim().charAt(0).toUpperCase()}</span>
                    <strong>{selectedConversation.recipientName}</strong>
                    <span className={`chat-connection-state chat-state-${connectionState}`} role="status">{statusText}</span>
                  </header>
                  {connectionError && <p className="chat-connection-notice" role="status">{connectionError}</p>}
                  {!history ? <p className="workspace-loading" role="status">Loading conversation...</p> : history.content.length === 0 ? (
                    <div className="direct-chat-empty"><h2>Say hello</h2><p>Your conversation history will appear here.</p></div>
                  ) : (
                    <ol className="direct-message-list">
                      {mergeMessages(history.content, []).map((message) => (
                        <li key={message.id} className={message.senderId === user?.id ? 'direct-message own' : 'direct-message'}>
                          <span>{message.senderName}</span>
                          <p>{message.content}</p>
                          <time dateTime={message.createdAt}>{new Date(message.createdAt).toLocaleString()}</time>
                        </li>
                      ))}
                    </ol>
                  )}
                  <form className="direct-compose" onSubmit={sendMessage}>
                    <label className="sr-only" htmlFor="direct-message-input">Write a message</label>
                    <textarea id="direct-message-input" rows={2} maxLength={4000} value={content} onChange={(event) => setContent(event.target.value)} placeholder="Write a private message..." />
                    <div><span>Messages are visible only to this conversation’s participants.</span><PrimaryButton type="submit" disabled={connectionState !== 'connected' || !content.trim()}>Send</PrimaryButton></div>
                  </form>
                </>
              ) : (
                <div className="direct-chat-empty"><span aria-hidden="true">◌</span><h2>Your private messages</h2><p>Choose a conversation or start one with a workspace member.</p></div>
              )}
            </div>
          </section>
        )}
      </section>
    </main>
  )
}
