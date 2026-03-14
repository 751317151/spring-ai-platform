<template>
  <table>
    <thead><tr><th>用户</th><th>Agent</th><th>调用次数</th><th>平均延迟</th></tr></thead>
    <tbody>
      <tr v-if="!users.length">
        <td colspan="4" style="text-align: center; color: var(--text3)">暂无数据</td>
      </tr>
      <tr v-for="(u, idx) in users" :key="idx">
        <td style="color: var(--text)">{{ u.user_id }}</td>
        <td>{{ agentLabels[u.agent_type] || u.agent_type }}</td>
        <td><span class="mono">{{ u.calls }}</span></td>
        <td><span class="mono">{{ u.avg_latency }}ms</span></td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
import type { TopUser } from '@/api/types'
import { AGENT_LABELS } from '@/utils/constants'

defineProps<{ users: TopUser[] }>()
const agentLabels = AGENT_LABELS
</script>
