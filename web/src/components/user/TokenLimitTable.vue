<template>
  <div class="table-shell">
    <table v-if="items.length">
      <thead>
        <tr>
          <th>{{ targetType === 'role' ? '角色' : '用户' }}</th>
          <th>助手范围</th>
          <th>每日 Token 配额</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td>
            <div class="primary-cell">
              <div class="primary">{{ getPrimaryTitle(item) }}</div>
              <div class="secondary">{{ getSecondaryText(item) }}</div>
            </div>
          </td>
          <td>{{ item.botType || '全部助手' }}</td>
          <td>{{ Number(item.dailyTokenLimit || 0).toLocaleString() }}</td>
          <td>
            <span class="status-pill" :class="item.enabled ? 'enabled' : 'disabled'">
              {{ item.enabled ? '启用' : '停用' }}
            </span>
          </td>
          <td>
            <div class="actions">
              <button class="table-btn" @click="emit('edit', item.id)">编辑</button>
              <button class="table-btn danger" @click="emit('delete', item.id, getPrimaryTitle(item))">删除</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <EmptyState
      v-else
      icon="Q"
      :title="targetType === 'role' ? '没有匹配的角色配额规则' : '没有匹配的用户配额规则'"
      description="可以调整筛选条件，或直接新建规则。"
      variant="compact"
    />
  </div>
</template>

<script setup lang="ts">
import type { RoleTokenLimit, UserTokenLimit } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'

type TokenLimitItem = RoleTokenLimit | UserTokenLimit

const props = defineProps<{
  targetType: 'role' | 'user'
  items: TokenLimitItem[]
}>()

const emit = defineEmits<{
  edit: [id: string]
  delete: [id: string, label: string]
}>()

function getPrimaryTitle(item: TokenLimitItem): string {
  if (props.targetType === 'role') {
    return (item as RoleTokenLimit).roleName
  }
  return (item as UserTokenLimit).username || (item as UserTokenLimit).userId
}

function getSecondaryText(item: TokenLimitItem): string {
  if (props.targetType === 'role') {
    return (item as RoleTokenLimit).roleDescription || '未填写角色说明'
  }
  const userItem = item as UserTokenLimit
  return [userItem.userId, userItem.department].filter(Boolean).join(' · ') || '未填写部门信息'
}
</script>

<style scoped>
.table-shell {
  width: 100%;
}

.primary-cell {
  display: grid;
  gap: 3px;
}

.primary {
  color: var(--text);
  font-weight: 600;
}

.secondary {
  color: var(--text3);
  font-size: 12px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.status-pill.enabled {
  background: rgba(16, 185, 129, 0.14);
  color: #34d399;
}

.status-pill.disabled {
  background: rgba(239, 68, 68, 0.14);
  color: #f87171;
}

.actions {
  display: flex;
  gap: 8px;
}

.table-btn {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.46);
  color: var(--text2);
  padding: 7px 12px;
  font-size: 12px;
  cursor: pointer;
}

.table-btn.danger {
  color: #fda4af;
}
</style>
