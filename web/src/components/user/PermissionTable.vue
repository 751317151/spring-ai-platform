<template>
  <table>
    <thead><tr><th>Bot</th><th>允许角色</th><th>允许部门</th><th>数据范围</th><th>操作权限</th><th>日 Token 限额</th><th>状态</th><th>操作</th></tr></thead>
    <tbody>
      <tr v-if="!permissions.length">
        <td colspan="8" style="text-align: center; color: var(--text3)">暂无数据</td>
      </tr>
      <tr v-for="p in permissions" :key="p.id">
        <td style="color: var(--text); font-weight: 500">{{ botLabels[p.botType] || p.botType }}</td>
        <td>
          <span v-for="role in splitRoles(p.allowedRoles)" :key="role" class="pill" :class="roleColors[role] || 'blue'" style="margin-right: 4px">
            {{ role.replace('ROLE_', '') }}
          </span>
        </td>
        <td style="color: var(--text2)">{{ p.allowedDepartments || '全部' }}</td>
        <td>{{ scopeLabels[p.dataScope] || p.dataScope }}</td>
        <td style="color: var(--text2)">{{ p.allowedOperations || '-' }}</td>
        <td><span class="mono">{{ p.dailyTokenLimit?.toLocaleString() || '-' }}</span></td>
        <td><span class="pill" :class="p.enabled ? 'green' : 'red'">{{ p.enabled ? '启用' : '禁用' }}</span></td>
        <td>
          <button class="btn btn-ghost btn-sm" @click="emit('edit', p.id)" title="编辑">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          </button>
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
  return roles.split(',').map(r => r.trim()).filter(Boolean)
}
</script>
