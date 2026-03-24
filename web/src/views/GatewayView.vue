<template>
  <div class="space-admin">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">模型网关</div>
        <div class="page-title">模型服务网关</div>
        <div class="page-subtitle">统一管理模型清单、负载策略和场景路由配置。</div>
        <div class="hero-tags">
          <span class="tag">{{ gatewayStore.models.length }} 个模型</span>
          <span class="tag">{{ Object.keys(gatewayStore.sceneRoutes || {}).length }} 个场景</span>
          <span class="tag">{{ strategyLabel }}</span>
        </div>
      </div>
      <div class="page-hero-actions">
        <select v-model="strategy" class="form-select strategy-select">
          <option value="round-robin">轮询</option>
          <option value="weighted">加权路由</option>
          <option value="least-latency">最低延迟优先</option>
        </select>
        <button class="btn btn-primary" @click="handleSave">保存配置</button>
      </div>
    </div>

    <div class="card section-spacing">
      <div class="card-header">
        <div>
          <div class="card-title">模型目录</div>
          <div class="card-subtitle">查看模型状态、权重、成功率和延迟，再决定是否调整路由。</div>
        </div>
      </div>
      <div class="model-grid">
        <ModelCard v-for="m in gatewayStore.models" :key="m.id" :model="m" />
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div>
          <div class="card-title">场景路由</div>
          <div class="card-subtitle">查看每个场景的主模型以及降级链路配置。</div>
        </div>
      </div>
      <SceneRouteTable :routes="gatewayStore.sceneRoutes" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import ModelCard from '@/components/gateway/ModelCard.vue'
import SceneRouteTable from '@/components/gateway/SceneRouteTable.vue'
import { useGatewayStore } from '@/stores/gateway'
import { useToast } from '@/composables/useToast'

const gatewayStore = useGatewayStore()
const { showToast } = useToast()

const strategy = ref('round-robin')

const strategyLabel = computed(() => {
  if (strategy.value === 'weighted') return '加权路由'
  if (strategy.value === 'least-latency') return '最低延迟优先'
  return '轮询'
})

watch(() => gatewayStore.loadBalanceStrategy, (val) => {
  strategy.value = val
})

onMounted(() => {
  gatewayStore.loadGatewayData()
})

async function handleSave() {
  const ok = await gatewayStore.saveConfig(strategy.value)
  showToast(ok ? '网关配置已保存' : '网关配置保存失败')
}
</script>

<style scoped>
.section-spacing {
  margin-bottom: 16px;
}

.strategy-select {
  min-width: 220px;
  width: auto;
}
</style>
