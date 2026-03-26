<template>
  <div class="model-card" :class="model.enabled ? 'online' : 'offline'">
    <div class="model-card-head">
      <div>
        <div class="model-name">{{ model.name }}</div>
        <div class="model-provider">{{ providerLabels[model.provider] || model.provider }}</div>
      </div>
      <div class="model-head-meta">
        <span class="pill" :class="statusClass">{{ statusLabel }}</span>
        <span class="tag">权重 {{ model.weight || 1 }}</span>
      </div>
    </div>

    <div class="model-stats">
      <div class="model-stat">
        <div class="model-stat-val">{{ model.enabled ? (model.avgLatencyMs || 0) : '--' }}</div>
        <div class="model-stat-label">平均延迟</div>
      </div>
      <div class="model-stat">
        <div class="model-stat-val">{{ model.enabled ? `${model.successRate || 100}%` : '--' }}</div>
        <div class="model-stat-label">成功率</div>
      </div>
      <div class="model-stat">
        <div class="model-stat-val">{{ (model.totalCalls || 0).toLocaleString() }}</div>
        <div class="model-stat-label">总调用数</div>
      </div>
    </div>

    <div class="health-note">{{ healthDescription }}</div>

    <div v-if="model.capabilities?.length" class="capability-list">
      <span v-for="cap in model.capabilities" :key="cap" class="pill blue">{{ cap }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ModelInfo } from '@/api/types'
import { PROVIDER_LABELS } from '@/utils/constants'

const props = defineProps<{ model: ModelInfo }>()
const providerLabels = PROVIDER_LABELS

const statusLabel = computed(() => {
  if (!props.model.enabled) return '离线'
  if (props.model.healthStatus === 'degraded') return '已降级'
  return '在线'
})

const statusClass = computed(() => {
  if (!props.model.enabled) return 'red'
  if (props.model.healthStatus === 'degraded') return 'amber'
  return 'green'
})

const healthDescription = computed(() => {
  if (!props.model.enabled) return '当前模型未参与路由。'
  if (props.model.healthStatus === 'degraded') {
    return props.model.healthReason || '模型短时失败较多，已临时降级。'
  }
  if (props.model.consecutiveFailures) {
    return `最近连续失败 ${props.model.consecutiveFailures} 次，仍在观察。`
  }
  return '当前健康状态正常，可继续参与路由。'
})
</script>

<style scoped>
.model-head-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-end;
}

.health-note {
  margin-top: 10px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}

.capability-list {
  display: flex;
  gap: 4px;
  margin-top: 10px;
  flex-wrap: wrap;
}
</style>
