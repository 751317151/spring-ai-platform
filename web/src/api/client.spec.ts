import { beforeEach, describe, expect, it, vi } from 'vitest'

const replaceMock = vi.fn()
const axiosPostMock = vi.fn()

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

vi.mock('axios', async () => {
  const actual = await vi.importActual<typeof import('axios')>('axios')
  return {
    ...actual,
    default: {
      ...actual.default,
      create: actual.default.create,
      post: axiosPostMock
    }
  }
})

describe('api client helpers', async () => {
  const clientModule = await import('./client')

  beforeEach(() => {
    localStorage.clear()
    replaceMock.mockReset()
    axiosPostMock.mockReset()
    vi.useRealTimers()
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

  it('applyAuth writes login payload and expiry timestamps into localStorage', () => {
    const now = Date.now()
    vi.useFakeTimers()
    vi.setSystemTime(now)

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
    expect(clientModule.__clientTestUtils.getExpireAt('auth_token_expires_at')).toBe(now + 3600 * 1000)
    expect(clientModule.__clientTestUtils.getExpireAt('auth_refresh_token_expires_at')).toBe(now + 7200 * 1000)
  })

  it('ensureValidAccessToken refreshes when access token is about to expire', async () => {
    const now = Date.now()
    vi.useFakeTimers()
    vi.setSystemTime(now)
    localStorage.setItem('auth_token', 'token-old')
    localStorage.setItem('auth_refreshToken', 'refresh-old')
    localStorage.setItem('auth_token_expires_at', String(now + 30_000))
    localStorage.setItem('auth_refresh_token_expires_at', String(now + 3_600_000))
    axiosPostMock.mockResolvedValue({
      data: {
        code: 200,
        data: {
          token: 'token-new',
          refreshToken: 'refresh-new',
          tokenType: 'Bearer',
          expiresIn: 3600,
          refreshExpiresIn: 7200,
          userId: 'user-1',
          username: '测试用户',
          roles: 'ROLE_ADMIN',
          department: '研发中心'
        }
      }
    })

    await expect(clientModule.ensureValidAccessToken()).resolves.toBe('token-new')
    expect(axiosPostMock).toHaveBeenCalledWith('/api/v1/auth/refresh', { refreshToken: 'refresh-old' })
    expect(localStorage.getItem('auth_token')).toBe('token-new')
  })

  it('ensureValidAccessToken redirects to login after refresh token expires', async () => {
    const now = Date.now()
    vi.useFakeTimers()
    vi.setSystemTime(now)
    localStorage.setItem('auth_token', 'token-old')
    localStorage.setItem('auth_refreshToken', 'refresh-old')
    localStorage.setItem('auth_token_expires_at', String(now + 30_000))
    localStorage.setItem('auth_refresh_token_expires_at', String(now - 1_000))

    await expect(clientModule.ensureValidAccessToken()).resolves.toBeNull()
    expect(replaceMock).toHaveBeenCalledWith({
      name: 'login',
      query: {
        redirect: '/chat?tab=history#section'
      }
    })
  })
})
