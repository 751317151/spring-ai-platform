import axios from 'axios'
import type { LoginResponse, Result } from './types'
import router from '@/router'

const client = axios.create({
  timeout: 30000
})

const ACCESS_TOKEN_EXPIRES_AT_KEY = 'auth_token_expires_at'
const REFRESH_TOKEN_EXPIRES_AT_KEY = 'auth_refresh_token_expires_at'
const ACCESS_TOKEN_REFRESH_BUFFER_MS = 60 * 1000

let refreshPromise: Promise<LoginResponse | null> | null = null

type RetryableRequest = {
  _retry?: boolean
  headers?: Record<string, string>
  url?: string
}

function getRefreshToken(): string | null {
  return localStorage.getItem('auth_refreshToken')
}

function getExpireAt(key: string): number | null {
  const value = localStorage.getItem(key)
  if (!value) {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function setExpireAt(key: string, expiresInSeconds?: number) {
  if (!expiresInSeconds || expiresInSeconds <= 0) {
    localStorage.removeItem(key)
    return
  }
  localStorage.setItem(key, String(Date.now() + expiresInSeconds * 1000))
}

function isExpiringSoon(expiresAt: number | null, bufferMs: number): boolean {
  if (!expiresAt) {
    return false
  }
  return expiresAt - Date.now() <= bufferMs
}

function isRefreshRequest(url?: string): boolean {
  return !!url && url.includes('/api/v1/auth/refresh')
}

function isAuthRequest(url?: string): boolean {
  return !!url && url.includes('/api/v1/auth/')
}

function createBusinessError(data: Result<unknown>, config?: unknown): Error {
  const error = new Error(data.message || '请求失败')
  ;(error as Error & {
    config?: unknown
    responseCode?: number
    responseError?: unknown
    responseTraceId?: string
  }).config = config
  ;(error as Error & { responseCode?: number }).responseCode = data.code
  ;(error as Error & { responseError?: unknown }).responseError = data.error
  ;(error as Error & { responseTraceId?: string }).responseTraceId = data.traceId
  return error
}

function applyAuth(data: LoginResponse) {
  localStorage.setItem('auth_token', data.token)
  if (data.refreshToken) {
    localStorage.setItem('auth_refreshToken', data.refreshToken)
  } else {
    localStorage.removeItem('auth_refreshToken')
  }
  localStorage.setItem('auth_userId', data.userId)
  localStorage.setItem('auth_username', data.username)
  localStorage.setItem('auth_roles', data.roles)
  localStorage.setItem('auth_department', data.department)
  setExpireAt(ACCESS_TOKEN_EXPIRES_AT_KEY, data.expiresIn)
  setExpireAt(REFRESH_TOKEN_EXPIRES_AT_KEY, data.refreshExpiresIn)
}

function clearAuth() {
  localStorage.removeItem('auth_token')
  localStorage.removeItem('auth_refreshToken')
  localStorage.removeItem('auth_userId')
  localStorage.removeItem('auth_username')
  localStorage.removeItem('auth_roles')
  localStorage.removeItem('auth_department')
  localStorage.removeItem('auth_mode')
  localStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_KEY)
  localStorage.removeItem(REFRESH_TOKEN_EXPIRES_AT_KEY)
}

async function refreshAccessToken(): Promise<LoginResponse | null> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return null
  }

  const response = await axios.post<Result<LoginResponse>>('/api/v1/auth/refresh', { refreshToken })
  const payload = response.data
  if (!payload || payload.code !== 200 || !payload.data?.token) {
    return null
  }
  applyAuth(payload.data)
  return payload.data
}

async function runRefreshFlow(): Promise<LoginResponse | null> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function redirectToLogin() {
  clearAuth()
  if (router.currentRoute.value.name !== 'login') {
    await router.replace({
      name: 'login',
      query: {
        redirect: `${window.location.pathname}${window.location.search}${window.location.hash}`
      }
    })
  }
}

async function retryWithRefresh(originalRequest?: RetryableRequest): Promise<LoginResponse | null> {
  if (!originalRequest || originalRequest._retry || isRefreshRequest(originalRequest.url)) {
    return null
  }
  originalRequest._retry = true
  return runRefreshFlow()
}

export async function ensureValidAccessToken(): Promise<string | null> {
  const token = localStorage.getItem('auth_token')
  if (!token) {
    return null
  }

  const accessExpiresAt = getExpireAt(ACCESS_TOKEN_EXPIRES_AT_KEY)
  if (!isExpiringSoon(accessExpiresAt, ACCESS_TOKEN_REFRESH_BUFFER_MS)) {
    return token
  }

  const refreshExpiresAt = getExpireAt(REFRESH_TOKEN_EXPIRES_AT_KEY)
  if (refreshExpiresAt && refreshExpiresAt <= Date.now()) {
    await redirectToLogin()
    return null
  }

  const refreshed = await runRefreshFlow()
  if (refreshed?.token) {
    return refreshed.token
  }

  await redirectToLogin()
  return null
}

client.interceptors.request.use(async (config) => {
  if (isAuthRequest(config.url)) {
    const token = localStorage.getItem('auth_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  }

  const token = await ensureValidAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && typeof data.code === 'number') {
      if (data.code !== 200) {
        return Promise.reject(createBusinessError(data, response.config))
      }
      return data.data
    }
    return data
  },
  async (error) => {
    const originalRequest = error.config as RetryableRequest | undefined
    const businessCode = (error as Error & { responseCode?: number }).responseCode
    const isUnauthorized = error.response?.status === 401 || businessCode === 401

    if (isUnauthorized) {
      const refreshed = await retryWithRefresh(originalRequest)
      if (refreshed?.token && originalRequest) {
        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers.Authorization = `Bearer ${refreshed.token}`
        return client(originalRequest)
      }
      await redirectToLogin()
    }

    return Promise.reject(error)
  }
)

export default client

export function getAuthToken(): string | null {
  return localStorage.getItem('auth_token')
}

export function buildHeaders(extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }
  const token = getAuthToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (extra) {
    Object.assign(headers, extra)
  }
  return headers
}

export const __clientTestUtils = {
  applyAuth,
  clearAuth,
  redirectToLogin,
  getExpireAt,
  refreshAccessToken
}
