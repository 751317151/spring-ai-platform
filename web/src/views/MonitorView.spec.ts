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
  return {
    ...actual,
    useRoute: () => route,
    useRouter: () => ({ replace })
  }
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
    store.alerts = [{ level: 'ERROR', type: 'GatewayDown', message: '网关不可用', time: '2026-03-25T09:30:00Z', fingerprint: 'fp-1' }] as never
    store.toolAudits = [
      {
        id: 'tool-1',
        user_id: 'alice',
        agent_type: 'assistant',
        trace_id: 'trace-1',
        tool_name: 'search',
        success: false,
        latency_ms: 600,
        created_at: '2026-03-25T09:05:00Z'
      }
    ] as never
    store.loadMonitorData = vi.fn().mockResolvedValue(undefined) as never
    store.updateAlertWorkflow = vi.fn().mockResolvedValue(undefined) as never
    store.loadAlertWorkflowHistory = vi.fn().mockResolvedValue(undefined) as never
    return store
  }

  it('renders troubleshooting workspace cards', () => {
    seedStore()
    const wrapper = mount(MonitorView)

    expect(wrapper.text()).toContain('当前排障焦点')
    expect(wrapper.text()).toContain('优先排查动作')
    expect(wrapper.text()).toContain('失败原因聚合')
  })

  it('loads trace detail after clicking trace action', async () => {
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
    expect(wrapper.text()).toContain('Trace 详情')
  })

  it('copies troubleshooting summary from selected sample', async () => {
    seedStore()
    const wrapper = mount(MonitorView)

    await wrapper.findAll('.sample-row')[1]!.trigger('click')
    await wrapper.findAll('.sample-row-action').find((item) => item.text() === '复制排查摘要')!.trigger('click')

    expect(window.navigator.clipboard.writeText).toHaveBeenCalled()
    expect(String(vi.mocked(window.navigator.clipboard.writeText).mock.calls[0]?.[0])).toContain('监控排查摘要')
  })
})
