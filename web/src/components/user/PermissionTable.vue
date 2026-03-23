<template>
  <table>
    <thead>
      <tr>
        <th>Bot</th>
        <th>允许角色</th>
        <th>允许部门</th>
        <th>数据范围</th>
        <th>操作权限</th>
        <th>日 Token 限额</th>
        <th>状态</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="!permissions.length">
        <td colspan="8" class="empty-cell">暂无数据</td>
      </tr>
      <tr v-for="p in permissions" :key="p.id">
        <td class="bot-name">{{ botLabels[p.botType] || p.botType }}</td>
        <td>
          <span v-for="role in splitRoles(p.allowedRoles)" :key="role" class="pill role-pill" :class="roleColors[role] || 'blue'">
            {{ role.replace('ROLE_', '') }}
          </span>
        </td>
        <td>{{ p.allowedDepartments || '全部' }}</td>
        <td>{{ scopeLabels[p.dataScope] || p.dataScope }}</td>
        <td>{{ p.allowedOperations || '-' }}</td>
        <td><span class="mono">{{ p.dailyTokenLimit?.toLocaleString() || '-' }}</span></td>
        <td><span class="pill" :class="p.enabled ? 'green' : 'red'">{{ p.enabled ? '启用' : '禁用' }}</span></td>
        <td>
          <button class="btn btn-ghost btn-sm" @click="emit('edit', p.id)">编辑</button>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
import type { BotPermission } from '@/api/types'
import { BOT_LABELS, SCOPE_LABELS, ROLE_COLORS } from '@/utils/constants'

defineProps<{ permissions: BotPermission[] }>()
const emit = defineEmits<{ edit: [id: string] }>()

const botLabels = BOT_LABELS
const scopeLabels = SCOPE_LABELS
const roleColors = ROLE_COLORS

function splitRoles(roles: string | undefined): string[] {
  if (!roles) return []
  return roles.split(',').map((r) => r.trim()).filter(Boolean)
}
</script>

<style scoped>
.empty-cell {
  text-align: center;
  color: var(--text3);
}

.bot-name {
  color: var(--text);
  font-weight: 500;
}

.role-pill {
  margin-right: 4px;
}
</style>
