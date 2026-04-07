import { beforeEach, describe, expect, it } from 'vitest'
import router from './index'

describe('router guards', () => {
  beforeEach(async () => {
    localStorage.clear()
    await router.replace('/login')
  })

  it('redirects unauthenticated users to login with redirect query', async () => {
    await router.push('/chat')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/chat')
  })

  it('redirects unauthenticated users from learning center to login', async () => {
    await router.push('/learning')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/learning')
  })

  it('allows authenticated users to access learning center', async () => {
    localStorage.setItem('auth_token', 'token-1')

    await router.push('/learning')

    expect(router.currentRoute.value.name).toBe('learning')
  })

  it('redirects authenticated users away from login', async () => {
    localStorage.setItem('auth_token', 'token-1')

    await router.push('/login?redirect=%2Frag')

    expect(router.currentRoute.value.fullPath).toBe('/rag')
  })

  it('blocks non-admin users from admin routes', async () => {
    localStorage.setItem('auth_token', 'token-1')
    localStorage.setItem('auth_roles', 'ROLE_USER')

    await router.push('/monitor')

    expect(router.currentRoute.value.name).toBe('chat')
  })

  it('allows admin users to access screen route', async () => {
    localStorage.setItem('auth_token', 'token-1')
    localStorage.setItem('auth_roles', 'ROLE_ADMIN')

    await router.push('/screen')

    expect(router.currentRoute.value.name).toBe('screen')
  })
})
