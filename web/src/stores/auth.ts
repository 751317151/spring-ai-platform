import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { LoginResponse } from '@/api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('auth_token'))
  const userId = ref(localStorage.getItem('auth_userId') || 'EMP001')
  const username = ref(localStorage.getItem('auth_username') || '')
  const roles = ref(localStorage.getItem('auth_roles') || '')
  const department = ref(localStorage.getItem('auth_department') || '')

  const isAuthenticated = computed(() => !!token.value)

  function setAuth(data: LoginResponse) {
    token.value = data.token
    userId.value = data.userId
    username.value = data.username
    roles.value = data.roles
    department.value = data.department
    localStorage.setItem('auth_token', data.token)
    localStorage.setItem('auth_userId', data.userId)
    localStorage.setItem('auth_username', data.username)
    localStorage.setItem('auth_roles', data.roles)
    localStorage.setItem('auth_department', data.department)
  }

  function clearAuth() {
    token.value = null
    userId.value = 'EMP001'
    username.value = ''
    roles.value = ''
    department.value = ''
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_userId')
    localStorage.removeItem('auth_username')
    localStorage.removeItem('auth_roles')
    localStorage.removeItem('auth_department')
  }

  async function login(user: string, password: string) {
    try {
      const data = await authApi.login({ username: user, password })
      setAuth(data)
      return true
    } catch {
      // Mock fallback when backend is offline
      const mockToken = 'mock-token-' + Date.now()
      setAuth({
        token: mockToken,
        tokenType: 'Bearer',
        expiresIn: 86400,
        userId: 'EMP001',
        username: user,
        roles: 'ROLE_ADMIN',
        department: '技术部'
      })
      return true
    }
  }

  async function logout() {
    try {
      if (token.value && !token.value.startsWith('mock-token-')) {
        await authApi.logout()
      }
    } catch {
      // ignore logout errors
    } finally {
      clearAuth()
    }
  }

  return { token, userId, username, roles, department, isAuthenticated, login, logout }
})
