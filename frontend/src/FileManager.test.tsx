import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FileManager } from './components/FileManager'
import { fileApi } from './services/fileService'
import type { FileAttachment, FileAttachmentPage } from './types/file'

const mocks = vi.hoisted(() => ({ user: { id: 'user-1' } }))

vi.mock('./auth/AuthContext', () => ({ useAuth: () => ({ user: mocks.user }) }))
vi.mock('./services/fileService', () => ({
  fileApi: {
    listWorkspace: vi.fn(),
    listProject: vi.fn(),
    uploadWorkspace: vi.fn(),
    uploadProject: vi.fn(),
    download: vi.fn(),
    delete: vi.fn(),
  },
}))

const attachment: FileAttachment = {
  id: 'file-1',
  filename: 'plan.pdf',
  contentType: 'application/pdf',
  size: 2048,
  uploaderId: 'user-1',
  uploaderName: 'Alex Member',
  workspaceId: 'workspace-1',
  projectId: null,
  uploadedAt: '2026-11-08T10:00:00',
}

const page: FileAttachmentPage = {
  content: [attachment],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 10,
  first: true,
  last: true,
}

describe('file manager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.user = { id: 'user-1' }
    vi.mocked(fileApi.listWorkspace).mockResolvedValue(page)
    vi.mocked(fileApi.listProject).mockResolvedValue(page)
    vi.mocked(fileApi.uploadWorkspace).mockResolvedValue(attachment)
    vi.mocked(fileApi.uploadProject).mockResolvedValue(attachment)
    vi.mocked(fileApi.download).mockResolvedValue()
    vi.mocked(fileApi.delete).mockResolvedValue()
  })

  it('renders file metadata and uploader', async () => {
    render(<FileManager workspaceId="workspace-1" canModerate={false} />)

    expect(await screen.findByText('plan.pdf')).toBeInTheDocument()
    expect(screen.getByText('2.0 KB · application/pdf')).toBeInTheDocument()
    expect(screen.getByText(/Uploaded by Alex Member/)).toBeInTheDocument()
    expect(fileApi.listWorkspace).toHaveBeenCalledWith('workspace-1', 0, 10)
  })

  it('uploads a file and displays success', async () => {
    const user = userEvent.setup()
    vi.mocked(fileApi.uploadProject).mockResolvedValue({ ...attachment, id: 'file-2', filename: 'new-plan.pdf' })
    render(<FileManager projectId="project-1" canModerate />)
    await screen.findByText('plan.pdf')
    const file = new File(['%PDF-1.7'], 'new-plan.pdf', { type: 'application/pdf' })
    await user.upload(screen.getByLabelText('Upload file'), file)

    expect(fileApi.uploadProject).toHaveBeenCalledWith('project-1', file, expect.any(Function))
    expect(await screen.findByText('new-plan.pdf uploaded successfully.')).toBeInTheDocument()
  })

  it('downloads and permits uploader deletion', async () => {
    const user = userEvent.setup()
    render(<FileManager workspaceId="workspace-1" canModerate={false} />)
    await screen.findByText('plan.pdf')
    await user.click(screen.getByRole('button', { name: 'Download' }))
    await user.click(screen.getByRole('button', { name: 'Delete' }))

    expect(fileApi.download).toHaveBeenCalledWith(attachment)
    expect(fileApi.delete).toHaveBeenCalledWith('file-1')
  })

  it('loads the next file page', async () => {
    const user = userEvent.setup()
    vi.mocked(fileApi.listWorkspace)
      .mockResolvedValueOnce({
        ...page,
        totalElements: 11,
        totalPages: 2,
        last: false,
      })
      .mockResolvedValueOnce({
        ...page,
        content: [{ ...attachment, id: 'file-2', filename: 'next.pdf' }],
        number: 1,
        first: false,
      })
    render(<FileManager workspaceId="workspace-1" canModerate={false} />)
    await screen.findByText('plan.pdf')
    await user.click(screen.getByRole('button', { name: 'Next' }))

    expect(await screen.findByText('next.pdf')).toBeInTheDocument()
    expect(fileApi.listWorkspace).toHaveBeenLastCalledWith('workspace-1', 1, 10)
  })

  it('shows loading and API errors', async () => {
    let rejectRequest: (error: Error) => void = () => undefined
    vi.mocked(fileApi.listWorkspace).mockImplementation(() => new Promise((_resolve, reject) => {
      rejectRequest = reject
    }))
    render(<FileManager workspaceId="workspace-1" canModerate />)
    expect(screen.getByRole('status')).toHaveTextContent('Loading files…')
    rejectRequest(new Error('network'))
    expect(await screen.findByRole('alert')).toHaveTextContent('Files could not be loaded.')
    await waitFor(() => expect(fileApi.listWorkspace).toHaveBeenCalled())
  })
})
