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
    localStorage.clear()
    routeState.path = '/dashboard'
  })

  it('renders only main navigation for regular users', () => {
    const authStore = useAuthStore()
    authStore.username = '张三'
    authStore.roles = 'ROLE_USER'

    const wrapper = mount(AppSidebar, {
      props: {
        collapsed: false,
        recentViews: [{ title: '最近聊天', to: '/chat' }]
      },
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :href="to"><slot /></a>'
          }
        }
      }
    })

    expect(wrapper.text()).toContain('控制台')
    expect(wrapper.text()).toContain('AI 助手')
    expect(wrapper.text()).toContain('知识库')
    expect(wrapper.text()).toContain('学习中心')
    expect(wrapper.text()).toContain('当前工作区')
    expect(wrapper.text()).toContain('最近访问')
    expect(wrapper.text()).not.toContain('平台管理')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('普通用户')
    expect(wrapper.find('.avatar').text()).toBe('张')
  })

  it('renders admin section for admin users', () => {
    const authStore = useAuthStore()
    authStore.username = 'admin'
    authStore.roles = 'ROLE_ADMIN'
    routeState.path = '/monitor'

    const wrapper = mount(AppSidebar, {
      props: {
        collapsed: false,
        recentViews: []
      },
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :href="to"><slot /></a>'
          }
        }
      }
    })

    expect(wrapper.text()).toContain('平台管理')
    expect(wrapper.text()).toContain('模型网关')
    expect(wrapper.text()).toContain('MCP 管理')
    expect(wrapper.text()).toContain('运行监控')
    expect(wrapper.text()).toContain('用户与权限')
    expect(wrapper.text()).toContain('管理员模式')
    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.find('.avatar').text()).toBe('A')
  })
})
