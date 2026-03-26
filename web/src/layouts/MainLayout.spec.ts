import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import MainLayout from './MainLayout.vue'

const push = vi.fn()
const routeState = reactive({
  path: '/dashboard',
  fullPath: '/dashboard',
  meta: {
    title: '控制台'
  }
})

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({
      push
    }),
    useRoute: () => routeState
  }
})

const RouterViewStub = defineComponent({
  name: 'RouterViewStub',
  setup(_props, { slots }) {
    return () => h('div', { class: 'router-view-stub' }, slots.default?.({
      Component: defineComponent({
        name: 'RouteComponentStub',
        render() {
          return h('div', 'route-content')
        }
      }),
      route: routeState
    }))
  }
})

describe('MainLayout', () => {
  beforeEach(() => {
    localStorage.clear()
    push.mockReset()
    routeState.path = '/dashboard'
    routeState.fullPath = '/dashboard'
    routeState.meta.title = '控制台'
  })

  it('persists recent views for non-login routes', () => {
    const wrapper = mount(MainLayout, {
      global: {
        stubs: {
          AppSidebar: { template: '<div class="sidebar-stub" />' },
          AppHeader: { template: '<div class="header-stub" />' },
          ToastNotification: { template: '<div class="toast-stub" />' },
          SkeletonBlock: { template: '<div class="skeleton-stub" />' },
          RouterView: RouterViewStub,
          Transition: false,
          Suspense: false
        }
      }
    })

    const raw = localStorage.getItem('layout_recent_views')
    expect(raw).not.toBeNull()
    expect(raw).toContain('控制台')
    expect(raw).toContain('/dashboard')
    wrapper.unmount()
  })

  it('dispatches header search shortcut on ctrl+k', () => {
    const eventHandler = vi.fn()
    window.addEventListener('app:focus-header-search', eventHandler)

    const wrapper = mount(MainLayout, {
      attachTo: document.body,
      global: {
        stubs: {
          AppSidebar: { template: '<div class="sidebar-stub" />' },
          AppHeader: { template: '<div class="header-stub" />' },
          ToastNotification: { template: '<div class="toast-stub" />' },
          SkeletonBlock: { template: '<div class="skeleton-stub" />' },
          RouterView: RouterViewStub,
          Transition: false,
          Suspense: false
        }
      }
    })

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }))

    expect(eventHandler).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    window.removeEventListener('app:focus-header-search', eventHandler)
  })

  it('dispatches shortcut help event on shift+question mark', () => {
    const eventHandler = vi.fn()
    window.addEventListener('app:open-shortcut-help', eventHandler)

    const wrapper = mount(MainLayout, {
      attachTo: document.body,
      global: {
        stubs: {
          AppSidebar: { template: '<div class="sidebar-stub" />' },
          AppHeader: { template: '<div class="header-stub" />' },
          ToastNotification: { template: '<div class="toast-stub" />' },
          SkeletonBlock: { template: '<div class="skeleton-stub" />' },
          RouterView: RouterViewStub,
          Transition: false,
          Suspense: false
        }
      }
    })

    window.dispatchEvent(new KeyboardEvent('keydown', { key: '?', shiftKey: true }))

    expect(eventHandler).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    window.removeEventListener('app:open-shortcut-help', eventHandler)
  })

  it('clears route loading mask after route settles', async () => {
    vi.useFakeTimers()

    const wrapper = mount(MainLayout, {
      global: {
        stubs: {
          AppSidebar: { template: '<div class="sidebar-stub" />' },
          AppHeader: { template: '<div class="header-stub" />' },
          ToastNotification: { template: '<div class="toast-stub" />' },
          SkeletonBlock: { template: '<div class="skeleton-stub" />' },
          RouterView: RouterViewStub,
          Transition: false,
          Suspense: false
        }
      }
    })

    routeState.path = '/chat'
    routeState.fullPath = '/chat'
    routeState.meta.title = 'AI 助手'
    await nextTick()
    vi.runAllTimers()
    await nextTick()

    expect(wrapper.find('.route-loading-mask').exists()).toBe(false)
    wrapper.unmount()
    vi.useRealTimers()
  })
})
