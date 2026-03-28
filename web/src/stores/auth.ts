import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import type { LoginResponse } from '@/api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('auth_token'))
  const refreshToken = ref<string | null>(localStorage.getItem('auth_refreshToken'))
  const userId = ref(localStorage.getItem('auth_userId') || 'admin')
  const username = ref(localStorage.getItem('auth_username') || '')
  const roles = ref(localStorage.getItem('auth_roles') || '')
  const department = ref(localStorage.getItem('auth_department') || '')

  const isAuthenticated = computed(() => !!token.value)

  function setAuth(data: LoginResponse) {
    token.value = data.token
    refreshToken.value = data.refreshToken || null
    userId.value = data.userId
    username.value = data.username
    roles.value = data.roles
    department.value = data.department
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
  }

  function clearAuth() {
    token.value = null
    refreshToken.value = null
    userId.value = 'admin'
    username.value = ''
    roles.value = ''
    department.value = ''
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_refreshToken')
    localStorage.removeItem('auth_userId')
    localStorage.removeItem('auth_username')
    localStorage.removeItem('auth_roles')
    localStorage.removeItem('auth_department')
  }

  async function login(loginUserId: string, password: string) {
    try {
      const data = await authApi.login({ userId: loginUserId, password })
      setAuth(data)
      return true
    } catch {
      throw new Error('无法连接到认证服务，请稍后重试。')
    }
  }

  async function logout() {
    try {
      if (token.value && !token.value.startsWith('mock-token-')) {
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
    isAuthenticated,
    setAuth,
    clearAuth,
    login,
    logout
  }
})
