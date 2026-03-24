<template>
  <div class="route-shell">
    <div class="route-summary">
      <span class="route-summary-chip">{{ routeEntries.length }} 个场景</span>
      <span class="route-summary-chip">主模型 + 兜底链路</span>
    </div>

    <table>
      <thead>
        <tr>
          <th>场景</th>
          <th>主模型</th>
          <th>兜底模型</th>
          <th>路由说明</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!routeEntries.length">
          <td colspan="4" class="empty-cell">暂无路由规则。</td>
        </tr>
        <tr v-for="[scene, models] in routeEntries" :key="scene">
          <td>{{ sceneLabels[scene] || scene }}</td>
          <td><span class="mono">{{ models[0] || '-' }}</span></td>
          <td><span class="mono">{{ models.length > 1 ? models.slice(1).join(', ') : '-' }}</span></td>
          <td><span class="route-note">由网关配置统一管理。</span></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { SCENE_LABELS } from '@/utils/constants'

const props = defineProps<{ routes: Record<string, string[]> }>()
const sceneLabels = SCENE_LABELS

const routeEntries = computed(() => Object.entries(props.routes))
</script>

<style scoped>
.route-shell {
  margin-top: 4px;
}

.route-summary {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.route-summary-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
  color: var(--text3);
  font-size: 12px;
  background: rgba(255, 255, 255, 0.03);
}

.empty-cell {
  text-align: center;
  color: var(--text3);
}

.route-note {
  font-size: 11px;
  color: var(--text3);
}
</style>
