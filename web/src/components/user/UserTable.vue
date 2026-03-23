<template>
  <table>
    <thead>
      <tr>
        <th>用户</th>
        <th>工号</th>
        <th>部门</th>
        <th>角色</th>
        <th>状态</th>
        <th>上次登录</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="!users.length">
        <td colspan="7" class="empty-cell">暂无数据</td>
      </tr>
      <tr v-for="u in users" :key="u.id">
        <td>
          <div class="user-name">
            <div class="avatar avatar-sm">{{ (u.username || '?').charAt(0).toUpperCase() }}</div>
            <span class="username">{{ u.username }}</span>
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
        <td><span class="pill" :class="u.enabled !== false ? 'green' : 'red'">{{ u.enabled !== false ? '启用' : '禁用' }}</span></td>
        <td class="last-login">{{ formatTime(u.lastLoginAt) }}</td>
        <td>
          <div class="action-list">
            <button class="btn btn-ghost btn-sm" @click="emit('edit', u.id)">编辑</button>
            <button class="btn btn-ghost btn-sm danger" @click="emit('delete', u.id, u.username)">删除</button>
          </div>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
import type { AiUser } from '@/api/types'
import { ROLE_COLORS } from '@/utils/constants'
import { formatTime } from '@/utils/format'

defineProps<{ users: AiUser[] }>()
const emit = defineEmits<{ edit: [userId: string]; delete: [userId: string, username: string] }>()
const roleColors = ROLE_COLORS
</script>

<style scoped>
.empty-cell {
  text-align: center;
  color: var(--text3);
}

.user-name {
  display: flex;
  align-items: center;
  gap: 8px;
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

.danger {
  color: #ef4444;
}
</style>
