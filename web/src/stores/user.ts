import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'
import * as agentApi from '@/api/agent'
import type {
  AgentDefinition,
  AgentDefinitionUpsertRequest,
  AiUser,
  RoleOption,
  RoleTokenLimit,
  RoleTokenLimitUpsertRequest,
  RoleUpsertRequest,
  UserTokenLimit,
  UserTokenLimitUpsertRequest,
  UserUpsertRequest
} from '@/api/types'

export const useUserStore = defineStore('user', () => {
  const users = ref<AiUser[]>([])
  const roles = ref<RoleOption[]>([])
  const agentDefinitions = ref<AgentDefinition[]>([])
  const roleTokenLimits = ref<RoleTokenLimit[]>([])
  const userTokenLimits = ref<UserTokenLimit[]>([])

  const loadingUsers = ref(false)
  const loadingRoles = ref(false)
  const loadingAgentDefinitions = ref(false)
  const loadingRoleTokenLimits = ref(false)
  const loadingUserTokenLimits = ref(false)

  const userError = ref('')
  const roleError = ref('')
  const agentDefinitionError = ref('')
  const roleTokenLimitError = ref('')
  const userTokenLimitError = ref('')

  async function loadUsers() {
    loadingUsers.value = true
    userError.value = ''
    try {
      users.value = (await authApi.getUsers()) || []
    } catch (error) {
      users.value = []
      userError.value = error instanceof Error ? error.message : '用户列表加载失败，请稍后重试'
    } finally {
      loadingUsers.value = false
    }
  }

  async function loadRoles() {
    loadingRoles.value = true
    roleError.value = ''
    try {
      roles.value = (await authApi.getRoles()) || []
    } catch (error) {
      roles.value = []
      roleError.value = error instanceof Error ? error.message : '角色目录加载失败，请稍后重试'
    } finally {
      loadingRoles.value = false
    }
  }

  async function loadAgentDefinitions() {
    loadingAgentDefinitions.value = true
    agentDefinitionError.value = ''
    try {
      agentDefinitions.value = (await agentApi.getAgentDefinitions()) || []
    } catch (error) {
      agentDefinitions.value = []
      agentDefinitionError.value = error instanceof Error ? error.message : '助手定义加载失败，请稍后重试'
    } finally {
      loadingAgentDefinitions.value = false
    }
  }

  async function loadRoleTokenLimits() {
    loadingRoleTokenLimits.value = true
    roleTokenLimitError.value = ''
    try {
      roleTokenLimits.value = (await authApi.getRoleTokenLimits()) || []
    } catch (error) {
      roleTokenLimits.value = []
      roleTokenLimitError.value = error instanceof Error ? error.message : '角色配额规则加载失败，请稍后重试'
    } finally {
      loadingRoleTokenLimits.value = false
    }
  }

  async function loadUserTokenLimits() {
    loadingUserTokenLimits.value = true
    userTokenLimitError.value = ''
    try {
      userTokenLimits.value = (await authApi.getUserTokenLimits()) || []
    } catch (error) {
      userTokenLimits.value = []
      userTokenLimitError.value = error instanceof Error ? error.message : '用户配额规则加载失败，请稍后重试'
    } finally {
      loadingUserTokenLimits.value = false
    }
  }

  async function loadAll() {
    await Promise.all([
      loadUsers(),
      loadRoles(),
      loadAgentDefinitions(),
      loadRoleTokenLimits(),
      loadUserTokenLimits()
    ])
  }

  async function createRole(data: RoleUpsertRequest): Promise<boolean> {
    try {
      await authApi.createRole(data)
      await loadRoles()
      return true
    } catch (error) {
      roleError.value = error instanceof Error ? error.message : '新建角色失败'
      return false
    }
  }

  async function updateRole(id: string, data: RoleUpsertRequest): Promise<boolean> {
    try {
      await authApi.updateRole(id, data)
      await Promise.all([loadRoles(), loadUsers(), loadAgentDefinitions()])
      return true
    } catch (error) {
      roleError.value = error instanceof Error ? error.message : '更新角色失败'
      return false
    }
  }

  async function deleteRole(id: string): Promise<boolean> {
    try {
      await authApi.deleteRole(id)
      await Promise.all([loadRoles(), loadUsers(), loadAgentDefinitions()])
      return true
    } catch (error) {
      roleError.value = error instanceof Error ? error.message : '删除角色失败'
      return false
    }
  }

  async function createUser(data: UserUpsertRequest): Promise<boolean> {
    try {
      await authApi.createUser(data)
      await loadUsers()
      return true
    } catch (error) {
      userError.value = error instanceof Error ? error.message : '新建用户失败'
      return false
    }
  }

  async function updateUser(id: string, data: UserUpsertRequest): Promise<boolean> {
    try {
      await authApi.updateUser(id, data)
      await loadUsers()
      return true
    } catch (error) {
      userError.value = error instanceof Error ? error.message : '更新用户失败'
      return false
    }
  }

  async function deleteUser(id: string): Promise<boolean> {
    try {
      await authApi.deleteUser(id)
      await loadUsers()
      return true
    } catch (error) {
      userError.value = error instanceof Error ? error.message : '删除用户失败'
      return false
    }
  }

  async function createAgentDefinition(data: AgentDefinitionUpsertRequest): Promise<boolean> {
    try {
      await agentApi.createAgentDefinition(data)
      await loadAgentDefinitions()
      return true
    } catch (error) {
      agentDefinitionError.value = error instanceof Error ? error.message : '新建助手定义失败'
      return false
    }
  }

  async function updateAgentDefinition(agentCode: string, data: AgentDefinitionUpsertRequest): Promise<boolean> {
    try {
      await agentApi.updateAgentDefinition(agentCode, data)
      await loadAgentDefinitions()
      return true
    } catch (error) {
      agentDefinitionError.value = error instanceof Error ? error.message : '更新助手定义失败'
      return false
    }
  }

  async function deleteAgentDefinition(agentCode: string): Promise<boolean> {
    try {
      await agentApi.deleteAgentDefinition(agentCode)
      await loadAgentDefinitions()
      return true
    } catch (error) {
      agentDefinitionError.value = error instanceof Error ? error.message : '删除助手定义失败'
      return false
    }
  }

  async function createRoleTokenLimit(data: RoleTokenLimitUpsertRequest): Promise<boolean> {
    try {
      await authApi.createRoleTokenLimit(data)
      await loadRoleTokenLimits()
      return true
    } catch (error) {
      roleTokenLimitError.value = error instanceof Error ? error.message : '新建角色配额规则失败'
      return false
    }
  }

  async function updateRoleTokenLimit(id: string, data: RoleTokenLimitUpsertRequest): Promise<boolean> {
    try {
      await authApi.updateRoleTokenLimit(id, data)
      await loadRoleTokenLimits()
      return true
    } catch (error) {
      roleTokenLimitError.value = error instanceof Error ? error.message : '更新角色配额规则失败'
      return false
    }
  }

  async function deleteRoleTokenLimit(id: string): Promise<boolean> {
    try {
      await authApi.deleteRoleTokenLimit(id)
      await loadRoleTokenLimits()
      return true
    } catch (error) {
      roleTokenLimitError.value = error instanceof Error ? error.message : '删除角色配额规则失败'
      return false
    }
  }

  async function createUserTokenLimit(data: UserTokenLimitUpsertRequest): Promise<boolean> {
    try {
      await authApi.createUserTokenLimit(data)
      await loadUserTokenLimits()
      return true
    } catch (error) {
      userTokenLimitError.value = error instanceof Error ? error.message : '新建用户配额规则失败'
      return false
    }
  }

  async function updateUserTokenLimit(id: string, data: UserTokenLimitUpsertRequest): Promise<boolean> {
    try {
      await authApi.updateUserTokenLimit(id, data)
      await loadUserTokenLimits()
      return true
    } catch (error) {
      userTokenLimitError.value = error instanceof Error ? error.message : '更新用户配额规则失败'
      return false
    }
  }

  async function deleteUserTokenLimit(id: string): Promise<boolean> {
    try {
      await authApi.deleteUserTokenLimit(id)
      await loadUserTokenLimits()
      return true
    } catch (error) {
      userTokenLimitError.value = error instanceof Error ? error.message : '删除用户配额规则失败'
      return false
    }
  }

  return {
    users,
    roles,
    agentDefinitions,
    roleTokenLimits,
    userTokenLimits,
    loadingUsers,
    loadingRoles,
    loadingAgentDefinitions,
    loadingRoleTokenLimits,
    loadingUserTokenLimits,
    userError,
    roleError,
    agentDefinitionError,
    roleTokenLimitError,
    userTokenLimitError,
    loadUsers,
    loadRoles,
    loadAgentDefinitions,
    loadRoleTokenLimits,
    loadUserTokenLimits,
    loadAll,
    createRole,
    updateRole,
    deleteRole,
    createUser,
    updateUser,
    deleteUser,
    createAgentDefinition,
    updateAgentDefinition,
    deleteAgentDefinition,
    createRoleTokenLimit,
    updateRoleTokenLimit,
    deleteRoleTokenLimit,
    createUserTokenLimit,
    updateUserTokenLimit,
    deleteUserTokenLimit
  }
})
