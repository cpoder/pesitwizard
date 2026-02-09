import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

export interface ApiClientConfig {
  /** Base URL for API requests (default: '/api/v1') */
  baseURL?: string
  /** Request timeout in milliseconds (default: 30000) */
  timeout?: number
  /** Authentication strategy to apply to each request */
  authStrategy?: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig
  /** Callback when an auth error (401/403) is received */
  onAuthError?: (status: number) => void
}

/**
 * Creates a configured axios instance with CSRF handling, auth strategy,
 * and error interceptors. Shared between client-ui and admin-ui.
 */
export function createApiClient(options: ApiClientConfig = {}): AxiosInstance {
  const {
    baseURL = '/api/v1',
    timeout = 30000,
    authStrategy,
    onAuthError,
  } = options

  const client = axios.create({
    baseURL,
    timeout,
    headers: {
      'Content-Type': 'application/json',
    },
    withCredentials: true,
    xsrfCookieName: 'XSRF-TOKEN',
    xsrfHeaderName: 'X-XSRF-TOKEN',
  })

  if (authStrategy) {
    client.interceptors.request.use(authStrategy)
  }

  if (onAuthError) {
    client.interceptors.response.use(
      (response) => response,
      (error) => {
        const status = error.response?.status
        if (status === 401 || status === 403) {
          onAuthError(status)
        }
        return Promise.reject(error)
      }
    )
  }

  return client
}

/** Auth strategy: API key from sessionStorage */
export function apiKeyAuth(storageKey = 'pesitwizard-api-key') {
  return (config: InternalAxiosRequestConfig) => {
    const apiKey = sessionStorage.getItem(storageKey)
    if (apiKey) {
      config.headers['X-API-Key'] = apiKey
    }
    return config
  }
}

/** Auth strategy: Basic auth from sessionStorage */
export function basicAuth(storageKey = 'auth_token') {
  return (config: InternalAxiosRequestConfig) => {
    const token = sessionStorage.getItem(storageKey)
    if (token) {
      config.headers.Authorization = `Basic ${token}`
    }
    return config
  }
}
