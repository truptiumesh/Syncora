import axios, { AxiosError, AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it } from 'vitest'
import type { ApiErrorResponse } from '../types/auth'
import { apiErrorMessage } from './apiError'

const config: InternalAxiosRequestConfig = {
  url: '/api/v1/auth/login',
  method: 'post',
  headers: new AxiosHeaders(),
}

function responseError(status: number, message: string) {
  const response: AxiosResponse<ApiErrorResponse> = {
    data: {
      timestamp: '',
      status,
      error: '',
      message,
      path: '/api/v1/auth/login',
      validationErrors: {},
    },
    status,
    statusText: '',
    headers: new AxiosHeaders(),
    config,
  }
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, undefined, response)
}

describe('apiErrorMessage', () => {
  it('uses the safe API message for authentication errors', () => {
    expect(apiErrorMessage(responseError(401, 'Email or password is incorrect.'), 'Sign-in failed.'))
      .toBe('Email or password is incorrect.')
  })

  it('does not expose server details from failed responses', () => {
    expect(apiErrorMessage(responseError(500, 'Database host and query details'), 'Request failed.'))
      .toBe('The server could not complete your request. Please try again.')
  })

  it('distinguishes a network failure from an HTTP error', () => {
    const networkError = new AxiosError('Network Error', 'ERR_NETWORK', config)
    expect(axios.isAxiosError(networkError)).toBe(true)
    expect(apiErrorMessage(networkError, 'Request failed.'))
      .toBe('Unable to connect to SYNCORA. Check the API URL and server connection, then try again.')
  })
})
