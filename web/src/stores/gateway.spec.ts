import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useGatewayStore } from './gateway'
import * as gatewayApi from '@/api/gateway'

vi.mock('@/api/gateway', () => ({
  getModels: vi.fn(),
  updateLoadBalance: vi.fn(),
  getRouteDecisionPreview: vi.fn()
}))

describe('gateway store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads models and route config', async () => {
    vi.mocked(gatewayApi.getModels).mockResolvedValue({
      models: [{ id: 'gpt-4.1', name: 'GPT-4.1' }],
      sceneRoutes: { chat: ['gpt-4.1'] },
      loadBalanceStrategy: 'weighted'
    } as never)
    vi.mocked(gatewayApi.getRouteDecisionPreview).mockResolvedValue({
      scene: 'chat',
      selectedModelId: 'gpt-4.1',
      strategy: 'weighted',
      reason: '命中场景路由',
      fallbackTriggered: false,
      candidateModels: []
    } as never)

    const store = useGatewayStore()
    await store.loadGatewayData()

    expect(store.models).toHaveLength(1)
    expect(store.sceneRoutes.chat).toEqual(['gpt-4.1'])
    expect(store.loadBalanceStrategy).toBe('weighted')
    expect(store.routeDecisionPreview?.selectedModelId).toBe('gpt-4.1')
  })

  it('updates strategy after saveConfig succeeds', async () => {
    vi.mocked(gatewayApi.updateLoadBalance).mockResolvedValue({} as never)

    const store = useGatewayStore()
    const result = await store.saveConfig('least-latency')

    expect(result).toBe(true)
    expect(store.loadBalanceStrategy).toBe('least-latency')
  })
})
