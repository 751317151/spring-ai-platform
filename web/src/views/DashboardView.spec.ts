import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import DashboardView from './DashboardView.vue'
import { useMonitorStore } from '@/stores/monitor'

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

vi.mock('vue-chartjs', () => ({
  Line: {
    name: 'Line',
    template: '<div class="chart-line" />'
  },
  Doughnut: {
    name: 'Doughnut',
    template: '<div class="chart-doughnut" />'
  }
}))

describe('DashboardView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    push.mockReset()
  })

  it('renders overview cards and jumps to monitor with agent context', async () => {
    const store = useMonitorStore()
    store.overview = {
      totalRequests: 120,
      totalTokens: 4000,
      avgLatencyMs: 180,
      successRate: 0.98
    } as never
    store.agentStats = [
      { agent_type: 'assistant', count: 16, avg_latency: 240, errors: 3 }
    ] as never
    store.models = [
      { id: 'gpt', name: 'GPT', provider: 'openai', enabled: true, weight: 1, capabilities: [], totalCalls: 24, successCalls: 23, avgLatencyMs: 150, successRate: 95 } as never
    ]
    store.auditLogs = [
      { id: '1', user_id: 'alice', agent_type: 'assistant', success: true, created_at: '2026-03-25T10:00:00Z' } as never
    ]
    store.hourlyStats = [
      { hour: 10, total: 12, errors: 1 } as never
    ]
    store.loadDashboardData = vi.fn().mockResolvedValue(undefined) as never
    store.startRealtimeUpdates = vi.fn() as never
    store.stopRealtimeUpdates = vi.fn() as never

    const wrapper = mount(DashboardView)

    expect(wrapper.text()).toContain('平台总览')
    expect(wrapper.text()).toContain('当前最慢助手链路')
    expect(wrapper.text()).toContain('模型排名')

    const jumpButton = wrapper.findAll('.summary-link')[0]
    await jumpButton.trigger('click')

    expect(push).toHaveBeenCalledWith({ name: 'monitor', query: { agent: 'assistant' } })
  })

  it('jumps to chat from quick action tile', async () => {
    const store = useMonitorStore()
    store.loadDashboardData = vi.fn().mockResolvedValue(undefined) as never
    store.startRealtimeUpdates = vi.fn() as never
    store.stopRealtimeUpdates = vi.fn() as never

    const wrapper = mount(DashboardView)
    await wrapper.findAll('.quick-action-item')[0]!.trigger('click')

    expect(push).toHaveBeenCalledWith({ path: '/chat', query: { source: 'dashboard' } })
  })

  it('opens rag page with failed status context', async () => {
    const store = useMonitorStore()
    store.auditLogs = [
      { id: '1', user_id: 'alice', agent_type: 'assistant', success: false, created_at: '2026-03-25T10:00:00Z' } as never
    ]
    store.loadDashboardData = vi.fn().mockResolvedValue(undefined) as never
    store.startRealtimeUpdates = vi.fn() as never
    store.stopRealtimeUpdates = vi.fn() as never

    const wrapper = mount(DashboardView)
    await wrapper.findAll('.quick-action-item')[2]!.trigger('click')

    expect(push).toHaveBeenCalledWith({ path: '/rag', query: { status: 'FAILED', source: 'dashboard' } })
  })

  it('opens rag page from rag summary card', async () => {
    const store = useMonitorStore()
    store.auditLogs = []
    store.loadDashboardData = vi.fn().mockResolvedValue(undefined) as never
    store.startRealtimeUpdates = vi.fn() as never
    store.stopRealtimeUpdates = vi.fn() as never

    const wrapper = mount(DashboardView)
    const ragSummaryLink = wrapper.findAll('.summary-card')[2]!.get('.summary-link')
    await ragSummaryLink.trigger('click')

    expect(push).toHaveBeenCalledWith({ path: '/rag', query: { status: 'PROCESSING', source: 'dashboard' } })
  })

  it('renders focus actions and opens top priority suggestion', async () => {
    const store = useMonitorStore()
    store.agentStats = [
      { agent_type: 'assistant', count: 16, avg_latency: 240, errors: 3 }
    ] as never
    store.auditLogs = [
      { id: '1', user_id: 'alice', agent_type: 'assistant', success: false, created_at: '2026-03-25T10:00:00Z' } as never
    ]
    store.loadDashboardData = vi.fn().mockResolvedValue(undefined) as never
    store.startRealtimeUpdates = vi.fn() as never
    store.stopRealtimeUpdates = vi.fn() as never

    const wrapper = mount(DashboardView)
    expect(wrapper.text()).toContain('今日处理建议')

    await wrapper.findAll('.focus-item')[0]!.trigger('click')

    expect(push).toHaveBeenCalledWith({ path: '/rag', query: { status: 'FAILED', source: 'dashboard' } })
  })
})
