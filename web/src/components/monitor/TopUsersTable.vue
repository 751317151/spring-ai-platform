<template>
  <div class="top-users-shell">
    <div v-if="topUser" class="top-users-banner">
      <div class="top-users-copy">
        <div class="top-users-title">当前最高调用用户</div>
        <div class="top-users-desc">{{ topUser.user_id }} · {{ topUser.calls }} 次调用 · {{ agentLabel(topUser.agent_type) }}</div>
      </div>
      <div class="top-users-actions">
        <button class="banner-action-btn" type="button" @click="$emit('pick-user', topUser.user_id)">筛选该用户</button>
        <button class="banner-action-btn" type="button" @click="$emit('pick-agent', topUser.agent_type)">筛选该助手</button>
      </div>
    </div>

    <table>
      <thead>
        <tr>
          <th>用户</th>
          <th>智能体</th>
          <th>调用次数</th>
          <th>平均延迟</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!users.length">
          <td colspan="4" class="empty-cell">
            <EmptyState
              icon="U"
              title="暂无高频用户数据"
              description="当用户调用量累计后，这里会展示最活跃的样本。"
              variant="compact"
            />
          </td>
        </tr>
        <tr v-for="(user, index) in users" :key="`${user.user_id}-${index}`">
          <td>
            <button class="context-link user-id" @click="$emit('pick-user', user.user_id)">
              {{ user.user_id }}
            </button>
          </td>
          <td>
            <button class="context-link" @click="$emit('pick-agent', user.agent_type)">
              {{ agentLabel(user.agent_type) }}
            </button>
          </td>
          <td><span class="mono">{{ user.calls }}</span></td>
          <td><span class="mono">{{ user.avg_latency }}ms</span></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TopUser } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'

const props = defineProps<{ users: TopUser[] }>()

defineEmits<{
  (event: 'pick-user', userId: string): void
  (event: 'pick-agent', agent: string): void
}>()

const agentNames: Record<string, string> = {
  RdAssistantAgent: '研发助手',
  SalesAssistantAgent: '销售助手',
  HrAssistantAgent: 'HR 助手',
  FinanceAssistantAgent: '财务助手',
  SupplyChainAssistantAgent: '供应链助手',
  QcAssistantAgent: '质控助手',
  WeatherAssistantAgent: '天气助手',
  SearchAssistantAgent: '搜索助手',
  DataAnalysisAssistantAgent: '数据分析助手',
  CodeAssistantAgent: '代码助手',
  McpAssistantAgent: 'MCP 助手',
  MultiAssistantAgent: '多智能体',
  assistant: '通用助手',
  writer: '写作助手'
}

function agentLabel(agent: string) {
  return agentNames[agent] || agent
}

const topUser = computed(() => props.users[0] || null)
</script>

<style scoped>
.top-users-shell {
  display: grid;
  gap: 12px;
}

.top-users-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.08), rgba(255, 255, 255, 0.02));
}

.top-users-title {
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
}

.top-users-desc {
  margin-top: 4px;
  color: var(--text3);
  font-size: 12px;
}

.top-users-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.banner-action-btn {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text2);
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition);
}

.banner-action-btn:hover {
  border-color: var(--accent);
  color: var(--accent2);
  background: var(--accent-dim);
}

.empty-cell {
  padding: 12px;
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

@media (max-width: 820px) {
  .top-users-banner {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
