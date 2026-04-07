import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import AppSidebar from './AppSidebar.vue'
import { useAuthStore } from '@/stores/auth'

const routeState = {
  path: '/dashboard'
}

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => routeState
  }
})

describe('AppSidebar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeState.path = '/dashboard'
  })

  it('renders main navigation for regular users', () => {
    const authStore = useAuthStore()
    authStore.username = '张三'
    authStore.roles = 'ROLE_USER'

    const wrapper = mount(AppSidebar, {
      props: { collapsed: false },
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :href="to"><slot /></a>'
          }
        }
      }
    })

    expect(wrapper.text()).toContain('AI 智能平台')
    expect(wrapper.text()).toContain('AI 助手')
    expect(wrapper.text()).toContain('知识库')
    expect(wrapper.text()).toContain('工作台')
    expect(wrapper.text()).toContain('学习中心')
    expect(wrapper.text()).not.toContain('大屏指挥台')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('平台用户')
    expect(wrapper.find('.user-badge').text()).toBe('张')
  })

  it('renders admin navigation including screen entry', () => {
    const authStore = useAuthStore()
    authStore.username = 'admin'
    authStore.roles = 'ROLE_ADMIN'
    routeState.path = '/screen'

    const wrapper = mount(AppSidebar, {
      props: { collapsed: false },
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :href="to"><slot /></a>'
          }
        }
      }
    })

    expect(wrapper.text()).toContain('大屏指挥台')
    expect(wrapper.text()).toContain('运行监控')
    expect(wrapper.text()).toContain('模型网关')
    expect(wrapper.text()).toContain('MCP 管理')
    expect(wrapper.text()).toContain('用户权限')
    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.find('.user-badge').text()).toBe('A')
  })
})
