import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import GatewayView from './GatewayView.vue'
import { useGatewayStore } from '@/stores/gateway'
import { useToast } from '@/composables/useToast'

describe('GatewayView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useToast().clearHistory()
    Object.defineProperty(window.navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true
    })
  })

  it('renders route preview and saves strategy', async () => {
    const store = useGatewayStore()
    store.models = [
      {
        id: 'gpt',
        name: 'GPT',
        provider: 'openai',
        enabled: true,
        weight: 2,
        capabilities: ['chat'],
        healthStatus: 'degraded',
        healthReason: '连续失败过多，已临时降级',
        totalCalls: 30,
        successCalls: 29,
        avgLatencyMs: 120,
        successRate: 97,
        totalEstimatedCost: 1.2345
      } as never
    ]
    store.sceneRoutes = { code: ['gpt', 'deepseek-chat'] } as never
    store.loadBalanceStrategy = 'round-robin' as never
    store.routeDecisionPreview = {
      scene: 'code',
      selectedModelId: 'gpt',
      strategy: 'round-robin',
      reason: '命中场景路由',
      fallbackTriggered: false,
      estimatedCostNote: '按每 1K Token 估算',
      candidateModels: [
        {
          id: 'gpt',
          name: 'GPT',
          provider: 'openai',
          enabled: true,
          healthy: true,
          selected: true,
          degraded: false,
          weight: 2,
          avgLatencyMs: 120,
          successRate: 97,
          promptCostPer1kTokens: 0.01,
          completionCostPer1kTokens: 0.02,
          reason: '当前场景最终选中的模型'
        }
      ]
    } as never
    store.routePreviewScene = 'code' as never
    store.routePreviewRequestedModelId = '' as never
    store.loadGatewayData = vi.fn().mockResolvedValue(undefined) as never
    store.loadRouteDecisionPreview = vi.fn().mockResolvedValue(undefined) as never
    store.saveConfig = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(GatewayView)
    await flushPromises()

    expect(wrapper.text()).toContain('模型服务网关')
    expect(wrapper.text()).toContain('路由决策预览')
    expect(wrapper.text()).toContain('代码修复')
    expect(wrapper.text()).toContain('当前场景最终选中的模型')

    await wrapper.find('.strategy-select').setValue('weighted')
    await wrapper.find('.btn-primary').trigger('click')

    expect(store.saveConfig).toHaveBeenCalledWith('weighted')
    expect(useToast().toastHistory.value[0]?.message).toBe('网关配置已保存')
  })

  it('switches sample with scene change and refreshes route preview', async () => {
    const store = useGatewayStore()
    store.models = [] as never
    store.sceneRoutes = { default: ['gpt'], code: ['gpt'] } as never
    store.loadBalanceStrategy = 'round-robin' as never
    store.routeDecisionPreview = {
      scene: 'default',
      selectedModelId: 'gpt',
      strategy: 'round-robin',
      reason: '命中默认路由',
      fallbackTriggered: false,
      candidateModels: []
    } as never
    store.routePreviewScene = 'default' as never
    store.routePreviewRequestedModelId = '' as never
    store.loadGatewayData = vi.fn().mockResolvedValue(undefined) as never
    store.loadRouteDecisionPreview = vi.fn().mockResolvedValue(undefined) as never
    store.saveConfig = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(GatewayView)
    await flushPromises()

    expect(wrapper.text()).toContain('通用问答')

    const previewSelects = wrapper.findAll('.route-preview-toolbar select')
    await previewSelects[1]!.setValue('')
    await previewSelects[0]!.setValue('code')
    await flushPromises()

    expect(vi.mocked(store.loadRouteDecisionPreview).mock.calls.at(-1)).toEqual(['code', undefined])
    expect(wrapper.text()).toContain('代码修复')
  })

  it('copies gateway overview summary', async () => {
    const store = useGatewayStore()
    store.models = [
      {
        id: 'gpt',
        name: 'GPT',
        provider: 'openai',
        enabled: true,
        weight: 2,
        capabilities: ['chat'],
        healthStatus: 'healthy',
        totalCalls: 30,
        successCalls: 29,
        avgLatencyMs: 120,
        successRate: 97,
        totalEstimatedCost: 0.12
      } as never
    ]
    store.sceneRoutes = { code: ['gpt'] } as never
    store.loadBalanceStrategy = 'round-robin' as never
    store.routeDecisionPreview = {
      scene: 'code',
      selectedModelId: 'gpt',
      strategy: 'round-robin',
      reason: '命中场景路由',
      fallbackTriggered: false,
      candidateModels: []
    } as never
    store.routePreviewScene = 'code' as never
    store.routePreviewRequestedModelId = '' as never
    store.loadGatewayData = vi.fn().mockResolvedValue(undefined) as never
    store.loadRouteDecisionPreview = vi.fn().mockResolvedValue(undefined) as never
    store.saveConfig = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(GatewayView)
    await flushPromises()
    await wrapper.findAll('button').find((item) => item.text() === '复制概览')?.trigger('click')

    expect(window.navigator.clipboard.writeText).toHaveBeenCalled()
    expect(String(vi.mocked(window.navigator.clipboard.writeText).mock.calls[0]?.[0])).toContain('模型服务网关概览')
    expect(String(vi.mocked(window.navigator.clipboard.writeText).mock.calls[0]?.[0])).toContain('当前样本')
    expect(useToast().toastHistory.value[0]?.message).toBe('已复制网关概览')
  })
})
