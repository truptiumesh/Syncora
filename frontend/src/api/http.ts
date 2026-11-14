import axios from 'axios'

let accessToken: string | null = null
const unauthorizedHandlers = new Set<() => void>()

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function subscribeToUnauthorized(handler: () => void) {
  unauthorizedHandlers.add(handler)
  return () => {
    unauthorizedHandlers.delete(handler)
  }
}

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

export const apiBaseUrl = (configuredApiBaseUrl
  || (import.meta.env.DEV ? 'http://localhost:8080' : window.location.origin))
  .replace(/\/+$/, '')

export const http = axios.create({
  baseURL: apiBaseUrl,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

http.interceptors.response.use(undefined, (error: unknown) => {
  if (
    axios.isAxiosError(error)
    && error.response?.status === 401
    && accessToken
    && !/\/api\/v1\/auth\/(?:login|register)(?:\?|$)/.test(error.config?.url ?? '')
  ) {
    setAccessToken(null)
    unauthorizedHandlers.forEach((handler) => handler())
  }
  return Promise.reject(error)
})
