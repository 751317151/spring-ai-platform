<template>
  <table>
    <thead><tr><th>用户</th><th>工号</th><th>部门</th><th>角色</th><th>状态</th><th>上次登录</th><th>操作</th></tr></thead>
    <tbody>
      <tr v-if="!users.length">
        <td colspan="7" style="text-align: center; color: var(--text3)">暂无数据</td>
      </tr>
      <tr v-for="u in users" :key="u.id">
        <td>
          <div style="display: flex; align-items: center; gap: 8px">
            <div class="avatar" style="width: 24px; height: 24px; font-size: 10px">{{ (u.username || '?').charAt(0).toUpperCase() }}</div>
            <span style="color: var(--text); font-weight: 500">{{ u.username }}</span>
          </div>
        </td>
        <td><span class="mono">{{ u.employeeId || '—' }}</span></td>
        <td>{{ u.department || '—' }}</td>
        <td>
          <div style="display: flex; gap: 4px; flex-wrap: wrap">
            <span
              v-for="role in (u.roles || '').split(',')"
              :key="role"
              class="pill"
              :class="roleColors[role.trim()] || 'blue'"
            >
              {{ role.trim() }}
            </span>
          </div>
        </td>
        <td><span class="pill" :class="u.enabled !== false ? 'green' : 'red'">{{ u.enabled !== false ? '活跃' : '禁用' }}</span></td>
        <td style="font-size: 11px; color: var(--text3)">{{ formatTime(u.lastLoginAt) }}</td>
        <td><button class="btn btn-ghost btn-sm" @click="emit('edit', u.id)">编辑</button></td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
import type { AiUser } from '@/api/types'
import { ROLE_COLORS } from '@/utils/constants'
import { formatTime } from '@/utils/format'

defineProps<{ users: AiUser[] }>()
const emit = defineEmits<{ edit: [userId: string] }>()
const roleColors = ROLE_COLORS
</script>
