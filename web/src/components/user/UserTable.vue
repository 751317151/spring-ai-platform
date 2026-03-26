<template>
  <table>
    <thead>
      <tr>
        <th class="check-col">
          <input
            type="checkbox"
            :checked="allSelected"
            :indeterminate.prop="indeterminate"
            @change="toggleAll(($event.target as HTMLInputElement).checked)"
          >
        </th>
        <th>用户</th>
        <th>工号</th>
        <th>部门</th>
        <th>角色</th>
        <th>状态</th>
        <th>最后登录</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="!users.length">
        <td colspan="8" class="empty-cell">
          <EmptyState
            icon="U"
            title="当前筛选条件下没有匹配的用户"
            description="可以调整状态、角色或关键词筛选条件后重试。"
            action-text="清空筛选"
            variant="compact"
            @action="emit('reset-filters')"
          />
        </td>
      </tr>
      <tr v-for="u in users" :id="`user-row-${u.id}`" :key="u.id" :class="{ highlighted: highlightedUserId === u.id }">
        <td class="check-col">
          <input
            type="checkbox"
            :checked="selectedIds.includes(u.id)"
            @change="toggleOne(u.id, ($event.target as HTMLInputElement).checked)"
          >
        </td>
        <td>
          <div class="user-name">
            <div class="avatar avatar-sm">{{ (u.username || '?').charAt(0).toUpperCase() }}</div>
            <div class="user-main">
              <span class="username">{{ u.username }}</span>
              <small class="subtle-text">{{ u.id }}</small>
            </div>
          </div>
        </td>
        <td><span class="mono">{{ u.employeeId || '-' }}</span></td>
        <td>{{ u.department || '-' }}</td>
        <td>
          <div class="role-list">
            <span
              v-for="role in (u.roles || '').split(',').map((item) => item.trim()).filter(Boolean)"
              :key="role"
              class="pill"
              :class="roleColors[role] || 'blue'"
            >
              {{ role }}
            </span>
          </div>
        </td>
        <td>
          <span class="pill" :class="u.enabled !== false ? 'green' : 'red'">
            {{ u.enabled !== false ? '启用' : '停用' }}
          </span>
        </td>
        <td class="last-login">{{ formatTime(u.lastLoginAt) }}</td>
        <td>
          <div class="action-list">
            <button class="table-action-btn" @click="emit('edit', u.id)">编辑</button>
            <button class="table-action-btn" @click="emit('inspect', u.id)">概览</button>
            <button class="table-action-btn danger" @click="emit('delete', u.id, u.username)">删除</button>
          </div>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiUser } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import { ROLE_COLORS } from '@/utils/constants'
import { formatTime } from '@/utils/format'

const props = defineProps<{
  users: AiUser[]
  selectedIds: string[]
  highlightedUserId?: string
}>()

const emit = defineEmits<{
  (e: 'edit', userId: string): void
  (e: 'inspect', userId: string): void
  (e: 'delete', userId: string, username: string): void
  (e: 'update:selectedIds', ids: string[]): void
  (e: 'reset-filters'): void
}>()

const roleColors = ROLE_COLORS

const allSelected = computed(() => props.users.length > 0 && props.users.every((user) => props.selectedIds.includes(user.id)))
const indeterminate = computed(() => props.selectedIds.length > 0 && !allSelected.value)

function toggleAll(checked: boolean) {
  emit('update:selectedIds', checked ? props.users.map((user) => user.id) : [])
}

function toggleOne(id: string, checked: boolean) {
  if (checked) {
    emit('update:selectedIds', [...props.selectedIds, id])
  } else {
    emit('update:selectedIds', props.selectedIds.filter((item) => item !== id))
  }
}
</script>

<style scoped>
.check-col {
  width: 42px;
}

.empty-cell {
  padding: 12px;
}

.user-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.username {
  color: var(--text);
  font-weight: 500;
}

.role-list {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.last-login {
  font-size: 11px;
  color: var(--text3);
}

.action-list {
  display: flex;
  gap: 6px;
}

.table-action-btn {
  padding: 4px 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: transparent;
  color: var(--text2);
  font-size: 11px;
  cursor: pointer;
  transition: all var(--transition);
}

.table-action-btn:hover {
  background: var(--surface2);
  color: var(--text);
  border-color: var(--border2);
}

.table-action-btn.danger {
  color: #ef4444;
}

.table-action-btn.danger:hover {
  border-color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

tbody tr.highlighted {
  background: rgba(59, 130, 246, 0.08);
  box-shadow: inset 0 0 0 1px rgba(59, 130, 246, 0.25);
}
</style>
