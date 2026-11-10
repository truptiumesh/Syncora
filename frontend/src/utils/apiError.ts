import axios from 'axios'
import type { ApiErrorResponse } from '../types/auth'

export function apiErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    if (!error.response) return 'Unable to connect to SYNCORA. Check the API URL and server connection, then try again.'
    if (error.response.status >= 500) return 'The server could not complete your request. Please try again.'
    return error.response.data?.message ?? `The request failed (HTTP ${error.response.status}).`
  }
  return fallback
}

export function apiErrorDetails(error: unknown, fallback: string) {
  const message = apiErrorMessage(error, fallback)
  if (!axios.isAxiosError<ApiErrorResponse>(error) || !error.response) return message

  const { status, data } = error.response
  const method = error.config?.method?.toUpperCase()
  const path = data.path || error.config?.url
  const request = [method, path].filter(Boolean).join(' ')
  return `${message} (HTTP ${status}${request ? ` · ${request}` : ''})`
}
