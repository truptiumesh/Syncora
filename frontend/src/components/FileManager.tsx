import { useCallback, useEffect, useState, type ChangeEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { fileApi } from '../services/fileService'
import type { FileAttachment, FileAttachmentPage } from '../types/file'
import { apiErrorMessage } from '../utils/apiError'

const PAGE_SIZE = 10
const ACCEPTED_TYPES = '.pdf,.jpg,.jpeg,.png,.gif'

interface FileManagerProps {
  workspaceId?: string
  projectId?: string
  canModerate: boolean
}

export function FileManager({ workspaceId, projectId, canModerate }: FileManagerProps) {
  const { user } = useAuth()
  const [page, setPage] = useState<FileAttachmentPage | null>(null)
  const [pageNumber, setPageNumber] = useState(0)
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const loadFiles = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = workspaceId
        ? await fileApi.listWorkspace(workspaceId, pageNumber, PAGE_SIZE)
        : projectId
          ? await fileApi.listProject(projectId, pageNumber, PAGE_SIZE)
          : null
      if (!result) throw new Error('A workspace or project is required.')
      setPage(result)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Files could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [pageNumber, projectId, workspaceId])

  useEffect(() => { void loadFiles() }, [loadFiles])

  async function uploadFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setError('')
    setSuccess('')
    setUploading(true)
    setUploadProgress(0)
    try {
      const uploaded = workspaceId
        ? await fileApi.uploadWorkspace(workspaceId, file, setUploadProgress)
        : projectId
          ? await fileApi.uploadProject(projectId, file, setUploadProgress)
          : null
      if (!uploaded) throw new Error('A workspace or project is required.')
      setSuccess(`${uploaded.filename} uploaded successfully.`)
      setPageNumber(0)
      if (pageNumber === 0) {
        setPage((current) => current
          ? {
            ...current,
            content: [uploaded, ...current.content.filter((item) => item.id !== uploaded.id)].slice(0, PAGE_SIZE),
            totalElements: current.content.some((item) => item.id === uploaded.id)
              ? current.totalElements
              : current.totalElements + 1,
          }
          : current)
      }
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'The file could not be uploaded.'))
    } finally {
      setUploading(false)
      setUploadProgress(null)
    }
  }

  async function downloadFile(file: FileAttachment) {
    setError('')
    try {
      await fileApi.download(file)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'The file could not be downloaded.'))
    }
  }

  async function deleteFile(file: FileAttachment) {
    setError('')
    setSuccess('')
    try {
      await fileApi.delete(file.id)
      setSuccess(`${file.filename} deleted.`)
      if (page?.content.length === 1 && pageNumber > 0) {
        setPageNumber((current) => current - 1)
      } else {
        await loadFiles()
      }
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'The file could not be deleted.'))
    }
  }

  return (
    <section className="file-manager settings-card" aria-label="Files and attachments">
      <header className="file-manager-heading">
        <div>
          <span className="eyebrow">{projectId ? 'PROJECT FILES' : 'WORKSPACE FILES'}</span>
          <h2>{projectId ? 'Project attachments' : 'Workspace files'}</h2>
        </div>
        <label className="button-outline file-upload-control">
          {uploading ? 'Uploading…' : 'Upload file'}
          <input
            type="file"
            accept={ACCEPTED_TYPES}
            aria-label="Upload file"
            disabled={uploading}
            onChange={(event) => void uploadFile(event)}
          />
        </label>
      </header>
      <p className="file-manager-hint">PDF, JPEG, PNG, or GIF · Maximum 10 MB</p>
      {uploading && (
        <div className="file-upload-progress" role="status">
          <progress value={uploadProgress ?? 0} max="100" />
          <span>{uploadProgress === null ? 'Uploading file…' : `Uploading ${uploadProgress}%`}</span>
        </div>
      )}
      {error && <p className="form-message form-error" role="alert">{error}</p>}
      {success && <p className="form-message form-success" role="status">{success}</p>}
      {loading ? (
        <p className="file-manager-state" role="status">Loading files…</p>
      ) : !page?.content.length ? (
        <p className="file-manager-state">No files yet. Upload a file to share it with this team.</p>
      ) : (
        <>
          <ul className="file-list">
            {page.content.map((file) => {
              const canDelete = canModerate || file.uploaderId === user?.id
              return (
                <li className="file-list-item" key={file.id}>
                  <div className="file-list-details">
                    <strong>{file.filename}</strong>
                    <span>{formatSize(file.size)} · {file.contentType}</span>
                    <span>Uploaded by {file.uploaderName} · {new Date(file.uploadedAt).toLocaleString()}</span>
                  </div>
                  <div className="file-list-actions">
                    <button className="button-quiet" type="button" onClick={() => void downloadFile(file)}>Download</button>
                    {canDelete && <button className="button-quiet" type="button" onClick={() => void deleteFile(file)}>Delete</button>}
                  </div>
                </li>
              )
            })}
          </ul>
          {page.totalPages > 1 && (
            <nav className="file-pagination" aria-label="File pages">
              <button className="button-quiet" type="button" disabled={page.first} onClick={() => setPageNumber((current) => Math.max(0, current - 1))}>Previous</button>
              <span>Page {page.number + 1} of {page.totalPages}</span>
              <button className="button-quiet" type="button" disabled={page.last} onClick={() => setPageNumber((current) => current + 1)}>Next</button>
            </nav>
          )}
        </>
      )}
    </section>
  )
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
