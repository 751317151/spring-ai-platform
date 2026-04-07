import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn()
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('stores auth payload into state and localStorage after login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: 'token-1',
      refreshToken: 'refresh-1',
      tokenType: 'Bearer',
      expiresIn: 3600,
      refreshExpiresIn: 7200,
      userId: 'user-1',
      username: '测试用户',
      roles: 'ROLE_ADMIN',
      department: '研发中心'
    })

    const store = useAuthStore()
    await expect(store.login('admin', '123456')).resolves.toBe(true)

    expect(store.token).toBe('token-1')
    expect(store.refreshToken).toBe('refresh-1')
    expect(store.username).toBe('测试用户')
    expect(localStorage.getItem('auth_token')).toBe('token-1')
    expect(localStorage.getItem('auth_roles')).toBe('ROLE_ADMIN')
    expect(Number(localStorage.getItem('auth_token_expires_at'))).toBeGreaterThan(Date.now())
    expect(Number(localStorage.getItem('auth_refresh_token_expires_at'))).toBeGreaterThan(Date.now())
  })

  it('supports guest login without backend request', async () => {
    const store = useAuthStore()

    await expect(store.loginAsGuest()).resolves.toBe(true)

    expect(store.isGuest).toBe(true)
    expect(store.userId).toBe('guest')
    expect(localStorage.getItem('auth_mode')).toBe('guest')
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('clears state and storage when logout completes', async () => {
    const store = useAuthStore()
    store.setAuth({
      token: 'token-1',
      refreshToken: 'refresh-1',
      tokenType: 'Bearer',
      expiresIn: 3600,
      refreshExpiresIn: 7200,
      userId: 'user-1',
      username: '测试用户',
      roles: 'ROLE_USER',
      department: '运营'
    })

    await store.logout()

    expect(store.token).toBeNull()
    expect(store.refreshToken).toBeNull()
    expect(store.username).toBe('')
    expect(localStorage.getItem('auth_token')).toBeNull()
    expect(localStorage.getItem('auth_token_expires_at')).toBeNull()
    expect(localStorage.getItem('auth_refresh_token_expires_at')).toBeNull()
  })

  it('throws readable message when login request fails', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('network error'))

    const store = useAuthStore()
    await expect(store.login('admin', 'wrong')).rejects.toThrow('无法连接到认证服务，请稍后重试。')
  })
})
