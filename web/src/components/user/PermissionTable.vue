<template>
  <div class="table-shell">
    <table v-if="permissions.length">
      <thead>
        <tr>
          <th>AI 助手</th>
          <th>允许角色</th>
          <th>允许部门</th>
          <th>数据范围</th>
          <th>操作范围</th>
          <th>默认 Token 配额</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="permission in permissions" :key="permission.id">
          <td>
            <div class="bot-cell">
              <div class="primary">{{ getBotLabel(permission.botType) }}</div>
              <div class="secondary">{{ permission.botType }}</div>
            </div>
          </td>
          <td>
            <div class="tag-list">
              <span v-for="role in splitCsv(permission.allowedRoles)" :key="role" class="tag">{{ role }}</span>
            </div>
          </td>
          <td>{{ permission.allowedDepartments || '不限制部门' }}</td>
          <td>{{ scopeLabels[permission.dataScope] || permission.dataScope }}</td>
          <td>{{ permission.allowedOperations || '-' }}</td>
          <td>{{ Number(permission.dailyTokenLimit || 0).toLocaleString() }}</td>
          <td>
            <span class="status-pill" :class="permission.enabled ? 'enabled' : 'disabled'">
              {{ permission.enabled ? '启用' : '停用' }}
            </span>
          </td>
          <td>
            <div class="actions">
              <button class="table-btn" @click="emit('edit', permission.id)">编辑</button>
              <button class="table-btn danger" @click="emit('delete', permission.id, permission.botType)">删除</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <EmptyState
      v-else
      icon="P"
      title="没有匹配的助手权限规则"
      description="可以调整筛选条件，或直接新建规则。"
      variant="compact"
    />
  </div>
</template>

<script setup lang="ts">
import type { BotPermission } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'

const BOT_LABELS: Record<string, string> = {
  rd: '研发助手',
  sales: '销售助手',
  hr: 'HR 助手',
  finance: '财务助手',
  'supply-chain': '供应链助手',
  qc: '质控助手',
  weather: '天气助手',
  search: '搜索助手',
  'data-analysis': '数据分析助手',
  code: '代码助手',
  mcp: 'MCP 助手',
  multi: '多智能体助手'
}

const scopeLabels: Record<string, string> = {
  ALL: '全部数据',
  DEPARTMENT: '本部门',
  SELF: '仅本人'
}

defineProps<{ permissions: BotPermission[] }>()
const emit = defineEmits<{ edit: [id: string]; delete: [id: string, botType: string] }>()

function splitCsv(value?: string): string[] {
  if (!value) return []
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

function getBotLabel(botType: string): string {
  return BOT_LABELS[botType] || botType
}
</script>

<style scoped>
.table-shell { width: 100%; }
.bot-cell { display: grid; gap: 3px; }
.primary { color: var(--text); font-weight: 600; }
.secondary { color: var(--text3); font-size: 12px; }
.tag-list { display: flex; flex-wrap: wrap; gap: 6px; }
.tag { display: inline-flex; align-items: center; padding: 6px 10px; border-radius: 999px; background: rgba(148, 163, 184, 0.12); color: var(--text2); font-size: 12px; }
.status-pill { display: inline-flex; align-items: center; padding: 6px 10px; border-radius: 999px; font-size: 12px; }
.status-pill.enabled { background: rgba(16, 185, 129, 0.14); color: #34d399; }
.status-pill.disabled { background: rgba(239, 68, 68, 0.14); color: #f87171; }
.actions { display: flex; gap: 8px; }
.table-btn { border: 1px solid rgba(148, 163, 184, 0.18); border-radius: 10px; background: rgba(15, 23, 42, 0.46); color: var(--text2); padding: 7px 12px; font-size: 12px; cursor: pointer; }
.table-btn.danger { color: #fda4af; }
</style>
