<template>
  <div>
    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px">
      <div>
        <div style="font-size: 15px; font-weight: 500; color: var(--text)">模型服务网关</div>
        <div style="font-size: 12px; color: var(--text3); margin-top: 2px">统一管理所有 AI 模型，支持路由、负载均衡、熔断降级</div>
      </div>
      <div style="display: flex; gap: 8px">
        <select v-model="strategy" class="form-select" style="padding: 6px 12px; font-size: 12px; width: auto">
          <option value="round-robin">负载均衡: 轮询</option>
          <option value="weighted">负载均衡: 加权</option>
          <option value="least-latency">负载均衡: 最低延迟</option>
        </select>
        <button class="btn btn-primary btn-sm" @click="handleSave">保存配置</button>
      </div>
    </div>

    <div class="model-grid">
      <ModelCard v-for="m in gatewayStore.models" :key="m.id" :model="m" />
    </div>

    <SceneRouteTable :routes="gatewayStore.sceneRoutes" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import ModelCard from '@/components/gateway/ModelCard.vue'
import SceneRouteTable from '@/components/gateway/SceneRouteTable.vue'
import { useGatewayStore } from '@/stores/gateway'
import { useToast } from '@/composables/useToast'

const gatewayStore = useGatewayStore()
const { showToast } = useToast()

const strategy = ref('round-robin')

watch(() => gatewayStore.loadBalanceStrategy, (val) => {
  strategy.value = val
})

onMounted(() => {
  gatewayStore.loadGatewayData()
})

async function handleSave() {
  const ok = await gatewayStore.saveConfig(strategy.value)
  showToast(ok ? '配置已保存' : '保存失败')
}
</script>
