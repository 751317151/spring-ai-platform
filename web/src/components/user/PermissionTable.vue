<template>
  <table>
    <thead><tr><th>Bot</th><th>允许角色</th><th>数据范围</th><th>操作权限</th><th>日 Token 限额</th><th>状态</th></tr></thead>
    <tbody>
      <tr v-if="!permissions.length">
        <td colspan="6" style="text-align: center; color: var(--text3)">暂无数据</td>
      </tr>
      <tr v-for="p in permissions" :key="p.id">
        <td style="color: var(--text); font-weight: 500">{{ botLabels[p.botType] || p.botType }}</td>
        <td>{{ p.allowedRoles }}</td>
        <td>{{ scopeLabels[p.dataScope] || p.dataScope }}</td>
        <td>{{ p.allowedOperations }}</td>
        <td><span class="mono">{{ p.dailyTokenLimit?.toLocaleString() || '—' }}</span></td>
        <td><span class="pill" :class="p.enabled ? 'green' : 'red'">{{ p.enabled ? '启用' : '禁用' }}</span></td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
import type { BotPermission } from '@/api/types'
import { BOT_LABELS, SCOPE_LABELS } from '@/utils/constants'

defineProps<{ permissions: BotPermission[] }>()
const botLabels = BOT_LABELS
const scopeLabels = SCOPE_LABELS
</script>
