import axios from 'axios'
import type { ApiErrorResponse } from '../types/auth'

export function apiErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.message ?? 'Unable to reach the server. Please try again.'
  }
  return fallback
}
