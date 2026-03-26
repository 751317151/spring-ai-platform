import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from './user'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth', () => ({
  getUsers: vi.fn(),
  getPermissions: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUser: vi.fn(),
  createPermission: vi.fn(),
  updatePermission: vi.fn(),
  deletePermission: vi.fn()
}))

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads users and permissions successfully', async () => {
    vi.mocked(authApi.getUsers).mockResolvedValue([{ id: 'u1', username: '张三' } as never])
    vi.mocked(authApi.getPermissions).mockResolvedValue([{ id: 'p1', botType: 'rd' } as never])

    const store = useUserStore()
    await store.loadAll()

    expect(store.users).toHaveLength(1)
    expect(store.permissions).toHaveLength(1)
    expect(store.userError).toBe('')
    expect(store.permissionError).toBe('')
  })

  it('sets readable errors when load requests fail', async () => {
    vi.mocked(authApi.getUsers).mockRejectedValue(new Error('fail'))
    vi.mocked(authApi.getPermissions).mockRejectedValue(new Error('fail'))

    const store = useUserStore()
    await store.loadAll()

    expect(store.userError).toBe('用户列表加载失败，请稍后重试。')
    expect(store.permissionError).toBe('权限规则加载失败，请稍后重试。')
    expect(store.users).toEqual([])
    expect(store.permissions).toEqual([])
  })

  it('reloads users after createUser succeeds', async () => {
    vi.mocked(authApi.createUser).mockResolvedValue({} as never)
    vi.mocked(authApi.getUsers).mockResolvedValue([{ id: 'u2', username: '李四' } as never])

    const store = useUserStore()
    await expect(store.createUser({ username: '李四', password: '123456' })).resolves.toBe(true)

    expect(authApi.createUser).toHaveBeenCalled()
    expect(authApi.getUsers).toHaveBeenCalled()
    expect(store.users[0]?.username).toBe('李四')
  })

  it('reloads permissions after updatePermission succeeds', async () => {
    vi.mocked(authApi.updatePermission).mockResolvedValue({} as never)
    vi.mocked(authApi.getPermissions).mockResolvedValue([{ id: 'p2', botType: 'search' } as never])

    const store = useUserStore()
    await expect(store.updatePermission('p2', { botType: 'search' })).resolves.toBe(true)

    expect(authApi.updatePermission).toHaveBeenCalledWith('p2', { botType: 'search' })
    expect(authApi.getPermissions).toHaveBeenCalled()
    expect(store.permissions[0]?.botType).toBe('search')
  })
})
