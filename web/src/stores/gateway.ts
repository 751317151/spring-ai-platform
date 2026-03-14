import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as gatewayApi from '@/api/gateway'
import type { ModelInfo } from '@/api/types'

export const useGatewayStore = defineStore('gateway', () => {
  const models = ref<ModelInfo[]>([])
  const sceneRoutes = ref<Record<string, string[]>>({})
  const loadBalanceStrategy = ref('round-robin')

  async function loadGatewayData() {
    try {
      const data = await gatewayApi.getModels()
      models.value = data.models || []
      sceneRoutes.value = data.sceneRoutes || {}
      loadBalanceStrategy.value = data.loadBalanceStrategy || 'round-robin'
    } catch {
      // fallback empty
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

  return { models, sceneRoutes, loadBalanceStrategy, loadGatewayData, saveConfig }
})
