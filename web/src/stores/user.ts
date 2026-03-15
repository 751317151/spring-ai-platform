import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'
import type { AiUser, BotPermission } from '@/api/types'

export const useUserStore = defineStore('user', () => {
  const users = ref<AiUser[]>([])
  const permissions = ref<BotPermission[]>([])

  async function loadUsers() {
    try {
      const data = await authApi.getUsers()
      users.value = data || []
    } catch {
      users.value = []
    }
  }

  async function loadPermissions() {
    try {
      const data = await authApi.getPermissions()
      permissions.value = data || []
    } catch {
      permissions.value = []
    }
  }

  async function loadAll() {
    await Promise.all([loadUsers(), loadPermissions()])
  }

  async function createUser(data: Record<string, string>): Promise<boolean> {
    try {
      await authApi.createUser(data)
      await loadUsers()
      return true
    } catch {
      return false
    }
  }

  async function updateUser(id: string, data: Record<string, unknown>): Promise<boolean> {
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

  async function createPermission(data: Partial<import('@/api/types').BotPermission>): Promise<boolean> {
    try {
      await authApi.createPermission(data)
      await loadPermissions()
      return true
    } catch {
      return false
    }
  }

  async function updatePermission(id: string, data: Partial<import('@/api/types').BotPermission>): Promise<boolean> {
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
    users, permissions,
    loadUsers, loadPermissions, loadAll,
    createUser, updateUser, deleteUser,
    createPermission, updatePermission, deletePermission
  }
})
