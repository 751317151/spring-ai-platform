import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import DashboardView from './DashboardView.vue'
import { useMonitorStore } from '@/stores/monitor'
import { useAuthStore } from '@/stores/auth'
import { useRagStore } from '@/stores/rag'

const push = vi.fn()

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({
      push
    })
  }
})

describe('DashboardView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    push.mockReset()
  })

  it('renders core entry cards for regular users', () => {
    const monitorStore = useMonitorStore()
    const ragStore = useRagStore()
    const authStore = useAuthStore()

    authStore.roles = 'ROLE_USER'
    monitorStore.overview = {
      totalRequests: 120,
      errorRequests: 2,
      successRate: 0.98,
      avgLatencyMs: 180,
      p95LatencyMs: 240,
      p99LatencyMs: 360,
      totalPromptTokens: 1200,
      totalCompletionTokens: 2800,
      totalTokens: 4000,
      activeRequests: 3
    } as never
    ragStore.knowledgeBases = [
      { id: 'kb-1', name: '知识库', documentCount: 3 } as never
    ]
    ragStore.loadKnowledgeBases = vi.fn().mockResolvedValue(undefined) as never
    monitorStore.loadDashboardData = vi.fn().mockResolvedValue(undefined) as never

    const wrapper = mount(DashboardView)

    expect(wrapper.text()).toContain('从主任务开始')
    expect(wrapper.text()).toContain('AI 助手')
    expect(wrapper.text()).toContain('知识库')
    expect(wrapper.text()).not.toContain('查看大屏')
  })

  it('shows screen entry for admin users', () => {
    const monitorStore = useMonitorStore()
    const ragStore = useRagStore()
    const authStore = useAuthStore()

    authStore.roles = 'ROLE_ADMIN'
    ragStore.loadKnowledgeBases = vi.fn().mockResolvedValue(undefined) as never
    monitorStore.loadDashboardData = vi.fn().mockResolvedValue(undefined) as never

    const wrapper = mount(DashboardView)

    expect(wrapper.text()).toContain('大屏指挥台')
    expect(wrapper.text()).toContain('查看大屏')
  })

  it('navigates to screen when admin clicks the button', async () => {
    const monitorStore = useMonitorStore()
    const ragStore = useRagStore()
    const authStore = useAuthStore()

    authStore.roles = 'ROLE_ADMIN'
    ragStore.loadKnowledgeBases = vi.fn().mockResolvedValue(undefined) as never
    monitorStore.loadDashboardData = vi.fn().mockResolvedValue(undefined) as never

    const wrapper = mount(DashboardView)
    const buttons = wrapper.findAll('button')
    const screenButton = buttons.find((item) => item.text() === '查看大屏')

    await screenButton?.trigger('click')

    expect(push).toHaveBeenCalledWith('/screen')
  })
})
