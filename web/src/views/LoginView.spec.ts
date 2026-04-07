import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { reactive } from 'vue'
import LoginView from './LoginView.vue'
import { useAuthStore } from '@/stores/auth'

const replace = vi.fn()
const route = reactive({
  query: {} as Record<string, string>
})

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({
      replace
    }),
    useRoute: () => route
  }
})

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    replace.mockReset()
    route.query = {}
  })

  it('logs in and redirects to query redirect path', async () => {
    route.query = { redirect: '/chat' }
    const authStore = useAuthStore()
    authStore.login = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(LoginView)
    await wrapper.find('.login-submit').trigger('click')

    expect(authStore.login).toHaveBeenCalledWith('admin', 'admin123')
    expect(replace).toHaveBeenCalledWith('/chat')
  })

  it('supports guest login', async () => {
    route.query = { redirect: '/chat' }
    const authStore = useAuthStore()
    authStore.loginAsGuest = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(LoginView)
    await wrapper.find('.login-guest').trigger('click')

    expect(authStore.loginAsGuest).toHaveBeenCalled()
    expect(replace).toHaveBeenCalledWith('/chat')
  })

  it('shows error message when login fails', async () => {
    const authStore = useAuthStore()
    authStore.login = vi.fn().mockRejectedValue(new Error('认证失败')) as never

    const wrapper = mount(LoginView)
    await wrapper.find('.login-submit').trigger('click')

    expect(wrapper.text()).toContain('认证失败')
  })
})
