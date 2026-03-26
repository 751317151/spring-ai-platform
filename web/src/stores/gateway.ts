import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as gatewayApi from '@/api/gateway'
import type { GatewayRouteDecisionPreview, ModelInfo } from '@/api/types'

export const useGatewayStore = defineStore('gateway', () => {
  const models = ref<ModelInfo[]>([])
  const sceneRoutes = ref<Record<string, string[]>>({})
  const loadBalanceStrategy = ref('round-robin')
  const routeDecisionPreview = ref<GatewayRouteDecisionPreview | null>(null)
  const routePreviewScene = ref('default')
  const routePreviewRequestedModelId = ref('')

  const availableScenes = computed(() => {
    const scenes = Object.keys(sceneRoutes.value || {})
    return scenes.length ? scenes : ['default']
  })

  async function loadGatewayData() {
    try {
      const data = await gatewayApi.getModels()
      models.value = data.models || []
      sceneRoutes.value = data.sceneRoutes || {}
      loadBalanceStrategy.value = data.loadBalanceStrategy || 'round-robin'
      const firstScene = Object.keys(sceneRoutes.value || {})[0] || 'default'
      routePreviewScene.value = routePreviewScene.value || firstScene
      await loadRouteDecisionPreview(routePreviewScene.value, routePreviewRequestedModelId.value || undefined)
    } catch {
      // fallback empty
    }
  }

  async function loadRouteDecisionPreview(scene = routePreviewScene.value, requestedModelId?: string) {
    try {
      routePreviewScene.value = scene || 'default'
      routePreviewRequestedModelId.value = requestedModelId || ''
      routeDecisionPreview.value = await gatewayApi.getRouteDecisionPreview(routePreviewScene.value, requestedModelId)
      return routeDecisionPreview.value
    } catch {
      routeDecisionPreview.value = null
      return null
    }
  }

  async function saveConfig(strategy: string) {
    try {
      await gatewayApi.updateLoadBalance(strategy)
      loadBalanceStrategy.value = strategy
      return true
    } catch {
      return false
    }
  }

  return {
    models,
    sceneRoutes,
    loadBalanceStrategy,
    routeDecisionPreview,
    routePreviewScene,
    routePreviewRequestedModelId,
    availableScenes,
    loadGatewayData,
    loadRouteDecisionPreview,
    saveConfig
  }
})
