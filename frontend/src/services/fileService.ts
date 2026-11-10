import { http } from '../api/http'
import type { FileAttachment, FileAttachmentPage } from '../types/file'

export const fileApi = {
  async listWorkspace(workspaceId: string, page = 0, size = 10) {
    const response = await http.get<FileAttachmentPage>(`/api/v1/workspaces/${workspaceId}/files`, {
      params: { page, size },
    })
    return response.data
  },

  async listProject(projectId: string, page = 0, size = 10) {
    const response = await http.get<FileAttachmentPage>(`/api/v1/projects/${projectId}/files`, {
      params: { page, size },
    })
    return response.data
  },

  async uploadWorkspace(workspaceId: string, file: File, onProgress: (progress: number) => void) {
    return upload(`/api/v1/workspaces/${workspaceId}/files`, file, onProgress)
  },

  async uploadProject(projectId: string, file: File, onProgress: (progress: number) => void) {
    return upload(`/api/v1/projects/${projectId}/files`, file, onProgress)
  },

  async download(file: FileAttachment) {
    const response = await http.get<Blob>(`/api/v1/files/${file.id}/download`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = file.filename
    document.body.append(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  },

  async delete(fileId: string) {
    await http.delete(`/api/v1/files/${fileId}`)
  },
}

async function upload(
  endpoint: string,
  file: File,
  onProgress: (progress: number) => void,
) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<FileAttachment>(endpoint, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (event.total) onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
  return response.data
}
