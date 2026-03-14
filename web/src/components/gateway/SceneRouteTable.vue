<template>
  <div class="card" style="margin-top: 16px">
    <div class="card-title">场景路由规则</div>
    <table>
      <thead><tr><th>场景</th><th>优先模型</th><th>降级模型</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-if="!routeEntries.length">
          <td colspan="4" style="text-align: center; color: var(--text3)">暂无数据</td>
        </tr>
        <tr v-for="[scene, models] in routeEntries" :key="scene">
          <td>{{ sceneLabels[scene] || scene }}</td>
          <td><span class="mono">{{ models[0] || '-' }}</span></td>
          <td><span class="mono">{{ models.length > 1 ? models.slice(1).join(', ') : '-' }}</span></td>
          <td><span style="font-size: 11px; color: var(--text3)">配置文件管理</span></td>
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
