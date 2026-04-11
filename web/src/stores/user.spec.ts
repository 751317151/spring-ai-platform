import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from './user'
import * as authApi from '@/api/auth'
import * as agentApi from '@/api/agent'

vi.mock('@/api/auth', () => ({
  getUsers: vi.fn(),
  getRoles: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUser: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  getRoleTokenLimits: vi.fn(),
  getUserTokenLimits: vi.fn(),
  createRoleTokenLimit: vi.fn(),
  updateRoleTokenLimit: vi.fn(),
  deleteRoleTokenLimit: vi.fn(),
  createUserTokenLimit: vi.fn(),
  updateUserTokenLimit: vi.fn(),
  deleteUserTokenLimit: vi.fn()
}))

vi.mock('@/api/agent', () => ({
  getAgentDefinitions: vi.fn(),
  createAgentDefinition: vi.fn(),
  updateAgentDefinition: vi.fn(),
  deleteAgentDefinition: vi.fn()
}))

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads users, roles and assistants successfully', async () => {
    vi.mocked(authApi.getUsers).mockResolvedValue([{ userId: 'u1', username: '张三' } as never])
    vi.mocked(authApi.getRoles).mockResolvedValue([{ id: 'r1', roleName: 'ROLE_ADMIN' } as never])
    vi.mocked(agentApi.getAgentDefinitions).mockResolvedValue([{ id: 2001, agentCode: 'rd', allowedRoles: 'ROLE_ADMIN' } as never])
    vi.mocked(authApi.getRoleTokenLimits).mockResolvedValue([])
    vi.mocked(authApi.getUserTokenLimits).mockResolvedValue([])

    const store = useUserStore()
    await store.loadAll()

    expect(store.users).toHaveLength(1)
    expect(store.roles).toHaveLength(1)
    expect(store.agentDefinitions).toHaveLength(1)
    expect(store.userError).toBe('')
    expect(store.roleError).toBe('')
    expect(store.agentDefinitionError).toBe('')
  })

  it('sets readable errors when load requests fail', async () => {
    vi.mocked(authApi.getUsers).mockRejectedValue(new Error('fail'))
    vi.mocked(authApi.getRoles).mockRejectedValue(new Error('fail'))
    vi.mocked(agentApi.getAgentDefinitions).mockRejectedValue(new Error('fail'))
    vi.mocked(authApi.getRoleTokenLimits).mockResolvedValue([])
    vi.mocked(authApi.getUserTokenLimits).mockResolvedValue([])

    const store = useUserStore()
    await store.loadAll()

    expect(store.userError).toBe('fail')
    expect(store.roleError).toBe('fail')
    expect(store.agentDefinitionError).toBe('fail')
    expect(store.users).toEqual([])
    expect(store.roles).toEqual([])
    expect(store.agentDefinitions).toEqual([])
  })

  it('reloads users after createUser succeeds', async () => {
    vi.mocked(authApi.createUser).mockResolvedValue({} as never)
    vi.mocked(authApi.getUsers).mockResolvedValue([{ userId: 'u2', username: '李四' } as never])

    const store = useUserStore()
    await expect(store.createUser({ userId: 'u2', username: '李四', password: '123456' })).resolves.toBe(true)

    expect(authApi.createUser).toHaveBeenCalled()
    expect(authApi.getUsers).toHaveBeenCalled()
    expect(store.users[0]?.username).toBe('李四')
  })

  it('reloads assistants after updateAgentDefinition succeeds', async () => {
    vi.mocked(agentApi.updateAgentDefinition).mockResolvedValue({} as never)
    vi.mocked(agentApi.getAgentDefinitions).mockResolvedValue([{ id: 2008, agentCode: 'search', allowedRoles: 'ROLE_ADMIN' } as never])

    const store = useUserStore()
    await expect(store.updateAgentDefinition('search', { allowedRoles: 'ROLE_ADMIN' })).resolves.toBe(true)

    expect(agentApi.updateAgentDefinition).toHaveBeenCalledWith('search', { allowedRoles: 'ROLE_ADMIN' })
    expect(agentApi.getAgentDefinitions).toHaveBeenCalled()
    expect(store.agentDefinitions[0]?.agentCode).toBe('search')
  })
})
