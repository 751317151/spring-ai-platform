<template>
  <div class="table-shell">
    <table v-if="users.length">
      <thead>
        <tr>
          <th>用户</th>
          <th>部门</th>
          <th>省市</th>
          <th>角色</th>
          <th>状态</th>
          <th>最近登录</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.userId">
          <td>
            <div class="user-cell">
              <div class="avatar">{{ (user.username || user.userId || '?').slice(0, 1).toUpperCase() }}</div>
              <div>
                <div class="primary">{{ user.username }}</div>
                <div class="secondary">{{ user.userId }}</div>
              </div>
            </div>
          </td>
          <td>{{ user.department || '-' }}</td>
          <td>{{ formatLocation(user.province, user.city) }}</td>
          <td>
            <div class="tag-list">
              <span v-for="role in splitCsv(user.roles)" :key="role" class="tag">{{ role }}</span>
            </div>
          </td>
          <td>
            <span class="status-pill" :class="user.enabled !== false ? 'enabled' : 'disabled'">
              {{ user.enabled !== false ? '启用' : '停用' }}
            </span>
          </td>
          <td>{{ formatTime(user.lastLoginAt) }}</td>
          <td>
            <div class="actions">
              <button class="table-btn" @click="emit('edit', user.userId)">编辑</button>
              <button class="table-btn danger" @click="emit('delete', user.userId, user.username)">删除</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <EmptyState
      v-else
      icon="U"
      title="没有匹配的用户"
      description="可以调整筛选条件，或直接新建用户。"
      variant="compact"
    />
  </div>
</template>

<script setup lang="ts">
import type { AiUser } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import { formatTime } from '@/utils/format'

defineProps<{ users: AiUser[] }>()

const emit = defineEmits<{ edit: [userId: string]; delete: [userId: string, username: string] }>()

function splitCsv(value?: string): string[] {
  if (!value) return []
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

function formatLocation(province?: string, city?: string): string {
  const values = [province, city].map((item) => item?.trim()).filter(Boolean)
  return values.length ? values.join(' / ') : '-'
}
</script>

<style scoped>
.table-shell { width: 100%; }
.user-cell { display: flex; align-items: center; gap: 12px; }
.avatar { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 12px; background: linear-gradient(135deg, rgba(56, 189, 248, 0.18), rgba(14, 165, 233, 0.28)); color: var(--text); font-weight: 700; }
.primary { color: var(--text); font-weight: 600; }
.secondary { margin-top: 2px; color: var(--text3); font-size: 12px; }
.tag-list { display: flex; flex-wrap: wrap; gap: 6px; }
.tag { display: inline-flex; align-items: center; padding: 6px 10px; border-radius: 999px; background: rgba(148, 163, 184, 0.12); color: var(--text2); font-size: 12px; }
.status-pill { display: inline-flex; align-items: center; padding: 6px 10px; border-radius: 999px; font-size: 12px; }
.status-pill.enabled { background: rgba(16, 185, 129, 0.14); color: #34d399; }
.status-pill.disabled { background: rgba(239, 68, 68, 0.14); color: #f87171; }
.actions { display: flex; gap: 8px; }
.table-btn { border: 1px solid rgba(148, 163, 184, 0.18); border-radius: 10px; background: rgba(15, 23, 42, 0.46); color: var(--text2); padding: 7px 12px; font-size: 12px; cursor: pointer; }
.table-btn.danger { color: #fda4af; }
</style>
