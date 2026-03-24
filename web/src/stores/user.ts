import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'
import type { AiUser, BotPermission, BotPermissionUpsertRequest, UserUpsertRequest } from '@/api/types'

export const useUserStore = defineStore('user', () => {
  const users = ref<AiUser[]>([])
  const permissions = ref<BotPermission[]>([])
  const loadingUsers = ref(false)
  const loadingPermissions = ref(false)
  const userError = ref('')
  const permissionError = ref('')

  async function loadUsers() {
    loadingUsers.value = true
    userError.value = ''
    try {
      const data = await authApi.getUsers()
      users.value = data || []
    } catch {
      users.value = []
      userError.value = '用户列表加载失败，请稍后重试。'
    } finally {
      loadingUsers.value = false
    }
  }

  async function loadPermissions() {
    loadingPermissions.value = true
    permissionError.value = ''
    try {
      const data = await authApi.getPermissions()
      permissions.value = data || []
    } catch {
      permissions.value = []
      permissionError.value = '权限规则加载失败，请稍后重试。'
    } finally {
      loadingPermissions.value = false
    }
  }

  async function loadAll() {
    await Promise.all([loadUsers(), loadPermissions()])
  }

  async function createUser(data: UserUpsertRequest): Promise<boolean> {
    try {
      await authApi.createUser(data)
      await loadUsers()
      return true
    } catch {
      return false
    }
  }

  async function updateUser(id: string, data: UserUpsertRequest): Promise<boolean> {
    try {
      await authApi.updateUser(id, data)
      await loadUsers()
      return true
    } catch {
      return false
    }
  }

  async function deleteUser(id: string): Promise<boolean> {
    try {
      await authApi.deleteUser(id)
      await loadUsers()
      return true
    } catch {
      return false
    }
  }

  async function createPermission(data: BotPermissionUpsertRequest): Promise<boolean> {
    try {
      await authApi.createPermission(data)
      await loadPermissions()
      return true
    } catch {
      return false
    }
  }

  async function updatePermission(id: string, data: BotPermissionUpsertRequest): Promise<boolean> {
    try {
      await authApi.updatePermission(id, data)
      await loadPermissions()
      return true
    } catch {
      return false
    }
  }

  async function deletePermission(id: string): Promise<boolean> {
    try {
      await authApi.deletePermission(id)
      await loadPermissions()
      return true
    } catch {
      return false
    }
  }

  return {
    users,
    permissions,
    loadingUsers,
    loadingPermissions,
    userError,
    permissionError,
    loadUsers,
    loadPermissions,
    loadAll,
    createUser,
    updateUser,
    deleteUser,
    createPermission,
    updatePermission,
    deletePermission
  }
})
