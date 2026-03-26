import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { reactive } from 'vue'
import MonitorView from './MonitorView.vue'
import { useMonitorStore } from '@/stores/monitor'

const { replace, getTraceDetail } = vi.hoisted(() => ({
  replace: vi.fn(),
  getTraceDetail: vi.fn()
}))

const route = reactive({ query: {} as Record<string, string> })

vi.mock('@/api/monitor', () => ({ getTraceDetail }))
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return { ...actual, useRoute: () => route, useRouter: () => ({ replace }) }
  it('shows recent action banner after sample actions', async () => {
    seedStore()
    const wrapper = mount(MonitorView)

    const rows = wrapper.findAll('.sample-row')
    await rows[1]!.trigger('click')

    const summaryAction = wrapper.findAll('.sample-row-action').find((item) => item.text() === '复制排查摘要')
    await summaryAction!.trigger('click')

    expect(wrapper.text()).toContain('最近操作')
  })
})

describe('MonitorView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-25T10:00:00Z'))
    setActivePinia(createPinia())
    replace.mockReset()
    getTraceDetail.mockReset()
    route.query = {}
    Object.defineProperty(window, 'prompt', {
      value: vi.fn().mockReturnValue('已确认处理中'),
      configurable: true
    })
    Object.defineProperty(window.navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true
    })
  })

  function seedStore() {
    const store = useMonitorStore()
    store.loading = false
    store.error = ''
    store.overview = {
      totalRequests: 100,
      errorRequests: 5,
      successRate: 0.95,
      avgLatencyMs: 120,
      p95LatencyMs: 320,
      p99LatencyMs: 500,
      totalPromptTokens: 1000,
      totalCompletionTokens: 500,
      totalTokens: 1500,
      activeRequests: 7
    } as never
    store.agentStats = [{ agent_type: 'assistant', count: 12, avg_latency: 180, errors: 2 }] as never
    store.topUsers = [{ user_id: 'alice', agent_type: 'assistant', calls: 8, avg_latency: 220 }] as never
    store.slowRequests = [
      {
        id: 'slow-1',
        user_id: 'alice',
        agent_type: 'assistant',
        model_id: 'gpt',
        trace_id: 'trace-1',
        latency_ms: 900,
        success: true,
        created_at: '2026-03-25T09:00:00Z'
      }
    ] as never
    store.failureSamples = [
      {
        id: 'fail-1',
        user_id: 'bob',
        agent_type: 'writer',
        model_id: 'gpt',
        error_message: 'boom',
        latency_ms: 400,
        session_id: 'sess-1',
        trace_id: 'trace-2',
        created_at: '2026-03-25T09:10:00Z'
      }
    ] as never
    store.recentFeedback = [
      {
        responseId: 'resp-1',
        userId: 'alice',
        sourceType: 'agent',
        agentType: 'assistant',
        feedback: 'down',
        createdAt: '2026-03-25T09:20:00Z'
      }
    ] as never
    store.recentEvidenceFeedback = [] as never
    store.alerts = [{ level: 'ERROR', type: 'GatewayDown', message: '网关不可用', time: '2026-03-25T09:30:00Z', fingerprint: 'fp-1' }] as never
    store.models = [{ id: 'gpt', name: 'GPT', provider: 'openai', enabled: true, weight: 1, capabilities: [], totalCalls: 12, successCalls: 11, avgLatencyMs: 150, successRate: 0.92 }] as never
    store.loadMonitorData = vi.fn().mockResolvedValue(undefined) as never
    store.updateAlertWorkflow = vi.fn().mockResolvedValue(undefined) as never
    store.loadAlertWorkflowHistory = vi.fn().mockResolvedValue(undefined) as never
    return store
  }

  it('渲染中文摘要并支持助手筛选', async () => {
    seedStore()
    const wrapper = mount(MonitorView)

    expect(wrapper.text()).toContain('运行监控')
    expect(wrapper.text()).toContain('风险最高的助手')
    expect(wrapper.text()).toContain('GatewayDown')

    await wrapper.findAll('.context-link').find((item) => item.text().includes('通用助手'))!.trigger('click')
    expect(replace).toHaveBeenCalledWith({ name: 'monitor', query: { agent: 'assistant' } })
  })

  it('同步时间范围到路由', async () => {
    seedStore()
    const wrapper = mount(MonitorView)

    await wrapper.findAll('.range-chip').find((item) => item.text().includes('近 7 天'))!.trigger('click')
    expect(replace).toHaveBeenCalledWith({ name: 'monitor', query: { range: '7d' } })
  })

  it('点击 Trace 后加载详情面板', async () => {
    seedStore()
    getTraceDetail.mockResolvedValue({
      id: 'slow-1',
      trace_id: 'trace-1',
      user_id: 'alice',
      agent_type: 'assistant',
      model_id: 'gpt',
      success: true,
      latency_ms: 900,
      created_at: '2026-03-25T09:00:00Z',
      user_message: '你好',
      ai_response: '已收到'
    })

    const wrapper = mount(MonitorView)
    await wrapper.findAll('.mini-action').find((item) => item.text() === 'Trace')!.trigger('click')
    await flushPromises()

    expect(replace).toHaveBeenCalledWith({ name: 'monitor', query: { traceId: 'trace-1' } })
    expect(getTraceDetail).toHaveBeenCalledWith('trace-1')
    expect(wrapper.find('.trace-detail-panel').text()).toContain('用户输入')
    expect(wrapper.find('.trace-detail-panel').text()).toContain('已收到')
  })

  it('清空筛选后移除 trace 详情', async () => {
    seedStore()
    getTraceDetail.mockResolvedValue({
      id: 'slow-1',
      trace_id: 'trace-1',
      user_id: 'alice',
      agent_type: 'assistant',
      model_id: 'gpt',
      success: true,
      latency_ms: 900,
      created_at: '2026-03-25T09:00:00Z'
    })
    route.query = { traceId: 'trace-1' }

    const wrapper = mount(MonitorView)
    await flushPromises()

    expect(wrapper.find('.trace-detail-panel').exists()).toBe(true)
    replace.mockClear()
    await wrapper.get('.context-card .btn').trigger('click')
    expect(replace).toHaveBeenCalledWith({ name: 'monitor', query: {} })
  })

  it('支持更新告警流转状态', async () => {
    const store = seedStore()
    const wrapper = mount(MonitorView)

    const alertAction = wrapper.findAll('.alert-action-btn').find((item) => item.text() === '确认')
    await alertAction!.trigger('click')

    expect(store.updateAlertWorkflow).toHaveBeenCalledWith('fp-1', 'acknowledged')
  })

  it('点击样本行后展示详情面板', async () => {
    seedStore()
    const wrapper = mount(MonitorView)

    const rows = wrapper.findAll('.sample-row')
    await rows[0]!.trigger('click')
    expect(wrapper.find('.sample-detail-panel').text()).toContain('慢请求样本')
    expect(wrapper.find('.sample-detail-panel').text()).toContain('alice')
    expect(wrapper.find('.sample-detail-panel').text()).toContain('优先查看 Trace 详情')

    await rows[1]!.trigger('click')
    expect(wrapper.find('.sample-detail-panel').text()).toContain('失败样本')
    expect(wrapper.find('.sample-detail-panel').text()).toContain('boom')
    expect(wrapper.find('.sample-detail-panel').text()).toContain('sess-1')
  })

  it('supports copying sample troubleshooting summary', async () => {
    seedStore()
    const wrapper = mount(MonitorView)

    const rows = wrapper.findAll('.sample-row')
    await rows[1]!.trigger('click')

    const summaryAction = wrapper.findAll('.sample-row-action').find((item) => item.text() === '复制排查摘要')
    await summaryAction!.trigger('click')

    expect(window.navigator.clipboard.writeText).toHaveBeenCalled()
    const payload = String(vi.mocked(window.navigator.clipboard.writeText).mock.calls[0]?.[0])
    expect(payload).toContain('监控排查摘要')
    expect(payload).toContain('用户：bob')
    expect(payload).toContain('助手：写作助手')
    expect(payload).toContain('Trace：trace-2')
    expect(payload).toContain('排查建议')
  })
})
