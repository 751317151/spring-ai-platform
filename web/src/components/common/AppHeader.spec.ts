import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import AppHeader from './AppHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const push = vi.fn()

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({
      push
    }),
    useRoute: () => ({
      path: '/dashboard',
      fullPath: '/dashboard',
      meta: {
        title: '控制台'
      }
    })
  }
})

describe('AppHeader', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-26T09:00:00'))
    setActivePinia(createPinia())
    localStorage.clear()
    push.mockReset()
    useToast().clearHistory()
  })

  it('shows admin quick actions and quick jump entries', async () => {
    const authStore = useAuthStore()
    authStore.username = '管理员'
    authStore.roles = 'ROLE_ADMIN'

    const wrapper = mount(AppHeader, {
      props: {
        collapsed: false,
        recentViews: [{ title: '知识库', to: '/rag' }]
      }
    })

    expect(wrapper.text()).toContain('运行监控')

    await wrapper.find('.header-search-input').trigger('focus')
    expect(wrapper.text()).toContain('模型网关')
    expect(wrapper.text()).toContain('MCP 管理')
    expect(wrapper.text()).toContain('用户与权限')
  })

  it('opens notice center and supports mark-all-read', async () => {
    const authStore = useAuthStore()
    authStore.username = '运营同学'
    authStore.roles = 'ROLE_USER'

    const { showToast, toastHistory, unreadCount } = useToast()
    showToast('上传任务已完成', 1)
    vi.setSystemTime(new Date('2026-03-25T09:00:00'))
    showToast('索引重建已提交', 1)
    vi.setSystemTime(new Date('2026-03-26T09:00:00'))

    const wrapper = mount(AppHeader, {
      props: {
        collapsed: false,
        recentViews: []
      }
    })

    await wrapper.find('.notice-btn').trigger('click')
    expect(wrapper.text()).toContain('通知中心')
    expect(wrapper.text()).toContain('上传任务已完成')
    expect(wrapper.text()).toContain('索引重建已提交')
    expect(wrapper.text()).toContain('今天')
    expect(wrapper.text()).toContain('昨天')
    expect(wrapper.find('.notice-badge').text()).toBe(String(unreadCount.value))

    await wrapper.findAll('.notice-clear')[0]!.trigger('click')
    await nextTick()

    expect(unreadCount.value).toBe(0)
    expect(toastHistory.value.every((item) => item.readAt != null)).toBe(true)
    expect(wrapper.text()).toContain('已读')
  })

  it('clears notice history', async () => {
    const authStore = useAuthStore()
    authStore.username = '测试用户'
    authStore.roles = 'ROLE_USER'

    const { showToast, toastHistory } = useToast()
    showToast('保存成功', 1)

    const wrapper = mount(AppHeader, {
      props: {
        collapsed: false,
        recentViews: []
      }
    })

    await wrapper.find('.notice-btn').trigger('click')
    await wrapper.findAll('.notice-clear')[1]!.trigger('click')
    await nextTick()

    expect(toastHistory.value).toHaveLength(0)
    expect(wrapper.text()).not.toContain('通知中心')
  })

  it('opens shortcut help from the shortcut button', async () => {
    const authStore = useAuthStore()
    authStore.username = '测试用户'
    authStore.roles = 'ROLE_USER'

    const wrapper = mount(AppHeader, {
      props: {
        collapsed: false,
        recentViews: []
      },
      attachTo: document.body
    })

    await wrapper.findAll('.header-btn').find((node) => node.text() === '快捷键')?.trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('快捷键帮助')
    expect(wrapper.text()).toContain('打开快速跳转')
    expect(wrapper.text()).toContain('Shift')

    wrapper.unmount()
  })

  it('shows mobile trays for recent views and quick actions', async () => {
    const authStore = useAuthStore()
    authStore.username = '测试用户'
    authStore.roles = 'ROLE_ADMIN'

    const wrapper = mount(AppHeader, {
      props: {
        collapsed: false,
        recentViews: [{ title: '知识库', to: '/rag' }]
      },
      attachTo: document.body
    })

    await wrapper.findAll('.mobile-entry-btn')[1]!.trigger('click')
    expect(wrapper.text()).toContain('最近访问')
    expect(wrapper.text()).toContain('继续你刚刚处理过的页面')

    await wrapper.findAll('.mobile-entry-btn')[2]!.trigger('click')
    expect(wrapper.text()).toContain('常用入口')
    expect(wrapper.text()).toContain('通知中心')

    wrapper.unmount()
  })
})
