<template>
  <table>
    <thead>
      <tr>
        <th>用户</th>
        <th>助手</th>
        <th>调用次数</th>
        <th>平均延迟</th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="!users.length">
        <td colspan="4" class="empty-cell">暂无高频用户数据。</td>
      </tr>
      <tr v-for="(user, index) in users" :key="`${user.user_id}-${index}`">
        <td>
          <button class="context-link user-id" @click="$emit('pick-user', user.user_id)">
            {{ user.user_id }}
          </button>
        </td>
        <td>
          <button class="context-link" @click="$emit('pick-agent', user.agent_type)">
            {{ agentLabels[user.agent_type] || user.agent_type }}
          </button>
        </td>
        <td><span class="mono">{{ user.calls }}</span></td>
        <td><span class="mono">{{ user.avg_latency }}ms</span></td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
import type { TopUser } from '@/api/types'
import { AGENT_LABELS } from '@/utils/constants'

defineProps<{ users: TopUser[] }>()

defineEmits<{
  (event: 'pick-user', userId: string): void
  (event: 'pick-agent', agent: string): void
}>()

const agentLabels = AGENT_LABELS
</script>

<style scoped>
.empty-cell {
  text-align: center;
  color: var(--text3);
}

.context-link {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0;
  font: inherit;
  text-align: left;
}

.context-link:hover {
  color: var(--accent);
}

.user-id {
  color: var(--text);
  font-weight: 500;
}
</style>
