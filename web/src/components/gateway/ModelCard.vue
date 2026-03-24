<template>
  <div class="model-card" :class="model.enabled ? 'online' : 'offline'">
    <div class="model-card-head">
      <div>
        <div class="model-name">{{ model.name }}</div>
        <div class="model-provider">{{ providerLabels[model.provider] || model.provider }}</div>
      </div>
      <div class="model-head-meta">
        <span class="pill" :class="model.enabled ? 'green' : 'red'">
          {{ model.enabled ? '在线' : '离线' }}
        </span>
        <span class="tag">权重 {{ model.weight || 1 }}</span>
      </div>
    </div>

    <div class="model-stats">
      <div class="model-stat">
        <div class="model-stat-val">{{ model.enabled ? (model.avgLatencyMs || 0) : '--' }}</div>
        <div class="model-stat-label">平均毫秒</div>
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

    <div v-if="model.capabilities?.length" class="capability-list">
      <span v-for="cap in model.capabilities" :key="cap" class="pill blue">{{ cap }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ModelInfo } from '@/api/types'
import { PROVIDER_LABELS } from '@/utils/constants'

defineProps<{ model: ModelInfo }>()
const providerLabels = PROVIDER_LABELS
</script>

<style scoped>
.model-head-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-end;
}

.capability-list {
  display: flex;
  gap: 4px;
  margin-top: 10px;
  flex-wrap: wrap;
}
</style>
