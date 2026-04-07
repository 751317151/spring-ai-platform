import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useMonitorStore } from './monitor'
import * as monitorApi from '@/api/monitor'
import * as gatewayApi from '@/api/gateway'

vi.mock('@/config/app-config', () => ({
  DEMO_MODE_ENABLED: false,
  ENABLE_SCREEN_MOCK: false
}))

vi.mock('@/api/monitor', () => ({
  getOverview: vi.fn(),
  getScreenSnapshot: vi.fn(),
  getHourlyStats: vi.fn(),
  getByAgent: vi.fn(),
  getAuditLogs: vi.fn(),
  getTokenTopUsers: vi.fn(),
  getAlerts: vi.fn(),
  getByModel: vi.fn(),
  getSlowRequests: vi.fn(),
  getFailureSamples: vi.fn(),
  getFeedbackOverview: vi.fn(),
  getRecentFeedback: vi.fn(),
  getRecentEvidenceFeedback: vi.fn(),
  downloadExport: vi.fn(),
  toCsvBlob: vi.fn(() => new Blob(['ok'], { type: 'text/csv' }))
}))

vi.mock('@/api/gateway', () => ({
  getModels: vi.fn()
}))

describe('monitor store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads monitor data successfully', async () => {
    vi.mocked(monitorApi.getOverview).mockResolvedValue({ totalRequests: 1 } as never)
    vi.mocked(monitorApi.getTokenTopUsers).mockResolvedValue([{ user_id: 'u1', agent_type: 'rd', calls: 2, avg_latency: 50 }] as never)
    vi.mocked(monitorApi.getAlerts).mockResolvedValue({ activeAlerts: 0, alerts: [] } as never)
    vi.mocked(monitorApi.getHourlyStats).mockResolvedValue([{ hour: 1, total: 2, errors: 0, avg_latency: 10, p50: 8, p95: 20 }] as never)
    vi.mocked(monitorApi.getByAgent).mockResolvedValue([{ agent_type: 'rd', count: 2 }] as never)
    vi.mocked(monitorApi.getByModel).mockResolvedValue([{ model_id: 'gpt', count: 2 }] as never)
    vi.mocked(monitorApi.getSlowRequests).mockResolvedValue([] as never)
    vi.mocked(monitorApi.getFailureSamples).mockResolvedValue([] as never)
    vi.mocked(monitorApi.getFeedbackOverview).mockResolvedValue({ totalCount: 0, positiveCount: 0, negativeCount: 0, positiveRate: 0 } as never)
    vi.mocked(monitorApi.getRecentFeedback).mockResolvedValue([] as never)
    vi.mocked(monitorApi.getRecentEvidenceFeedback).mockResolvedValue([] as never)
    vi.mocked(gatewayApi.getModels).mockResolvedValue({ models: [{ id: 'gpt', name: 'GPT', provider: 'openai', enabled: true, weight: 1, capabilities: [], totalCalls: 2, successCalls: 2, avgLatencyMs: 10, successRate: 100 }], count: 1, sceneRoutes: { chat: ['gpt'] }, loadBalanceStrategy: 'round-robin' } as never)

    const store = useMonitorStore()
    await store.loadMonitorData()

    expect(store.error).toBe('')
    expect(store.overview?.totalRequests).toBe(1)
    expect(store.topUsers).toHaveLength(1)
    expect(store.models).toHaveLength(1)
    expect(store.gatewayLoadBalanceStrategy).toBe('round-robin')
  })

  it('sets readable error when monitor data load fails', async () => {
    vi.mocked(monitorApi.getOverview).mockRejectedValue(new Error('boom'))
    vi.mocked(monitorApi.getTokenTopUsers).mockResolvedValue([] as never)
    vi.mocked(monitorApi.getAlerts).mockResolvedValue({ activeAlerts: 0, alerts: [] } as never)
    vi.mocked(monitorApi.getHourlyStats).mockResolvedValue([] as never)
    vi.mocked(monitorApi.getByAgent).mockResolvedValue([] as never)
    vi.mocked(monitorApi.getByModel).mockResolvedValue([] as never)
    vi.mocked(monitorApi.getSlowRequests).mockResolvedValue([] as never)
    vi.mocked(monitorApi.getFailureSamples).mockResolvedValue([] as never)
    vi.mocked(monitorApi.getFeedbackOverview).mockResolvedValue({ totalCount: 0, positiveCount: 0, negativeCount: 0, positiveRate: 0 } as never)
    vi.mocked(monitorApi.getRecentFeedback).mockResolvedValue([] as never)
    vi.mocked(monitorApi.getRecentEvidenceFeedback).mockResolvedValue([] as never)
    vi.mocked(gatewayApi.getModels).mockResolvedValue({ models: [], count: 0, sceneRoutes: {}, loadBalanceStrategy: '' } as never)

    const store = useMonitorStore()
    await store.loadMonitorData()

    expect(store.error).toBe('boom')
    expect(store.loading).toBe(false)
  })

  it('loads screen data from backend without merging frontend mock data', async () => {
    vi.mocked(monitorApi.getScreenSnapshot).mockResolvedValue({
      overview: {
        totalRequests: 5,
        errorRequests: 1,
        successRate: 0.8,
        avgLatencyMs: 120,
        p95LatencyMs: 220,
        p99LatencyMs: 320,
        totalPromptTokens: 100,
        totalCompletionTokens: 50,
        totalTokens: 150,
        activeRequests: 2
      },
      hourlyStats: [],
      agentStats: [],
      topUsers: [],
      regionHeat: [],
      failureSamples: [],
      feedbackOverview: {
        totalCount: 0,
        positiveCount: 0,
        negativeCount: 0,
        positiveRate: 0
      },
      alerts: {
        activeAlerts: 0,
        alerts: []
      }
    } as never)
    vi.mocked(gatewayApi.getModels).mockResolvedValue({
      models: [],
      count: 0,
      sceneRoutes: {},
      loadBalanceStrategy: ''
    } as never)

    const store = useMonitorStore()
    await store.loadScreenData()

    expect(store.error).toBe('')
    expect(store.screenSnapshot?.overview.totalRequests).toBe(5)
    expect(store.screenSnapshot?.regionHeat).toEqual([])
    expect(store.topUsers).toEqual([])
  })
})
