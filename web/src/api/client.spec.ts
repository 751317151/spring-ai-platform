import { beforeEach, describe, expect, it, vi } from 'vitest'

const replaceMock = vi.fn()

vi.mock('@/router', () => ({
  default: {
    currentRoute: {
      value: {
        name: 'dashboard'
      }
    },
    replace: replaceMock
  }
}))

describe('api client helpers', async () => {
  const clientModule = await import('./client')

  beforeEach(() => {
    localStorage.clear()
    replaceMock.mockReset()
    window.history.replaceState({}, '', '/chat?tab=history#section')
  })

  it('buildHeaders injects auth token and extra headers', () => {
    localStorage.setItem('auth_token', 'token-123')

    expect(clientModule.buildHeaders({ 'X-Test': 'yes' })).toEqual({
      'Content-Type': 'application/json',
      Authorization: 'Bearer token-123',
      'X-Test': 'yes'
    })
  })

  it('redirectToLogin clears auth storage and keeps redirect target', async () => {
    localStorage.setItem('auth_token', 'token-123')
    localStorage.setItem('auth_refreshToken', 'refresh-123')
    localStorage.setItem('auth_userId', 'user-1')

    await clientModule.__clientTestUtils.redirectToLogin()

    expect(localStorage.getItem('auth_token')).toBeNull()
    expect(localStorage.getItem('auth_refreshToken')).toBeNull()
    expect(replaceMock).toHaveBeenCalledWith({
      name: 'login',
      query: {
        redirect: '/chat?tab=history#section'
      }
    })
  })

  it('applyAuth writes login payload into localStorage', () => {
    clientModule.__clientTestUtils.applyAuth({
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

    expect(localStorage.getItem('auth_token')).toBe('token-1')
    expect(localStorage.getItem('auth_refreshToken')).toBe('refresh-1')
    expect(localStorage.getItem('auth_username')).toBe('测试用户')
  })
})
