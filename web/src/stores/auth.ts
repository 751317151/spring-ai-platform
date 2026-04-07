import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import type { LoginResponse } from '@/api/types'
import { useRuntimeStore } from './runtime'
import { createGuestLoginResponse, GUEST_AUTH_MODE } from '@/utils/guest-mock'

const ACCESS_TOKEN_EXPIRES_AT_KEY = 'auth_token_expires_at'
const REFRESH_TOKEN_EXPIRES_AT_KEY = 'auth_refresh_token_expires_at'

export const useAuthStore = defineStore('auth', () => {
  const runtimeStore = useRuntimeStore()
  const token = ref<string | null>(localStorage.getItem('auth_token'))
  const refreshToken = ref<string | null>(localStorage.getItem('auth_refreshToken'))
  const userId = ref(localStorage.getItem('auth_userId') || 'admin')
  const username = ref(localStorage.getItem('auth_username') || '')
  const roles = ref(localStorage.getItem('auth_roles') || '')
  const department = ref(localStorage.getItem('auth_department') || '')
  const authMode = ref(localStorage.getItem('auth_mode') || 'live')

  const isAuthenticated = computed(() => !!token.value)
  const isGuest = computed(() => authMode.value === GUEST_AUTH_MODE)

  function setExpireAt(key: string, expiresInSeconds?: number) {
    if (!expiresInSeconds || expiresInSeconds <= 0) {
      localStorage.removeItem(key)
      return
    }
    localStorage.setItem(key, String(Date.now() + expiresInSeconds * 1000))
  }

  function setAuth(data: LoginResponse, mode = 'live') {
    token.value = data.token
    refreshToken.value = data.refreshToken || null
    userId.value = data.userId
    username.value = data.username
    roles.value = data.roles
    department.value = data.department
    authMode.value = mode

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
    localStorage.setItem('auth_mode', mode)
    setExpireAt(ACCESS_TOKEN_EXPIRES_AT_KEY, data.expiresIn)
    setExpireAt(REFRESH_TOKEN_EXPIRES_AT_KEY, data.refreshExpiresIn)
    runtimeStore.setAuthMode(mode)
  }

  function clearAuth() {
    token.value = null
    refreshToken.value = null
    userId.value = 'admin'
    username.value = ''
    roles.value = ''
    department.value = ''
    authMode.value = 'live'

    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_refreshToken')
    localStorage.removeItem('auth_userId')
    localStorage.removeItem('auth_username')
    localStorage.removeItem('auth_roles')
    localStorage.removeItem('auth_department')
    localStorage.removeItem('auth_mode')
    localStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_KEY)
    localStorage.removeItem(REFRESH_TOKEN_EXPIRES_AT_KEY)
    runtimeStore.setAuthMode('')
  }

  async function login(loginUserId: string, password: string) {
    try {
      const data = await authApi.login({ userId: loginUserId, password })
      setAuth(data, 'live')
      return true
    } catch {
      throw new Error('无法连接到认证服务，请稍后重试。')
    }
  }

  async function loginAsGuest() {
    setAuth(createGuestLoginResponse(), GUEST_AUTH_MODE)
    return true
  }

  async function logout() {
    try {
      if (token.value && !isGuest.value && !token.value.startsWith('mock-token-')) {
        await authApi.logout(refreshToken.value || undefined)
      }
    } catch {
      // ignore logout errors
    } finally {
      clearAuth()
    }
  }

  return {
    token,
    refreshToken,
    userId,
    username,
    roles,
    department,
    authMode,
    isAuthenticated,
    isGuest,
    setAuth,
    clearAuth,
    login,
    loginAsGuest,
    logout
  }
})
