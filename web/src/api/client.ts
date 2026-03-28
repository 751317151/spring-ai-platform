import axios from 'axios'
import type { LoginResponse, Result } from './types'
import router from '@/router'

const client = axios.create({
  timeout: 30000
})

let refreshPromise: Promise<LoginResponse | null> | null = null

function getRefreshToken(): string | null {
  return localStorage.getItem('auth_refreshToken')
}

function applyAuth(data: LoginResponse) {
  localStorage.setItem('auth_token', data.token)
  if (data.refreshToken) {
    localStorage.setItem('auth_refreshToken', data.refreshToken)
  }
  localStorage.setItem('auth_userId', data.userId)
  localStorage.setItem('auth_username', data.username)
  localStorage.setItem('auth_roles', data.roles)
  localStorage.setItem('auth_department', data.department)
}

function clearAuth() {
  localStorage.removeItem('auth_token')
  localStorage.removeItem('auth_refreshToken')
  localStorage.removeItem('auth_userId')
  localStorage.removeItem('auth_username')
  localStorage.removeItem('auth_roles')
  localStorage.removeItem('auth_department')
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

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token')
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
        const error = new Error(data.message || '请求失败')
        ;(error as Error & { responseCode?: number; responseError?: unknown; responseTraceId?: string }).responseCode = data.code
        ;(error as Error & { responseCode?: number; responseError?: unknown; responseTraceId?: string }).responseError = data.error
        ;(error as Error & { responseCode?: number; responseError?: unknown; responseTraceId?: string }).responseTraceId = data.traceId
        return Promise.reject(error)
      }
      return data.data
    }
    return data
  },
  async (error) => {
    const originalRequest = error.config
    if (error.response?.status === 401 && !originalRequest?._retry) {
      originalRequest._retry = true
      if (!refreshPromise) {
        refreshPromise = refreshAccessToken().finally(() => {
          refreshPromise = null
        })
      }
      const refreshed = await refreshPromise
      if (refreshed?.token) {
        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers.Authorization = `Bearer ${refreshed.token}`
        return client(originalRequest)
      }

      await redirectToLogin()
      return Promise.reject(error)
    }

    if (error.response?.status === 401) {
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
  redirectToLogin
}
