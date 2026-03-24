<template>
  <table>
    <thead>
      <tr>
        <th>助手类型</th>
        <th>允许角色</th>
        <th>允许部门</th>
        <th>数据范围</th>
        <th>允许操作</th>
        <th>每日令牌限额</th>
        <th>状态</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="!permissions.length">
        <td colspan="8" class="empty-cell">当前筛选条件下没有匹配的权限规则。</td>
      </tr>
      <tr v-for="p in permissions" :key="p.id">
        <td>
          <div class="bot-cell">
            <span class="bot-name">{{ botLabels[p.botType] || p.botType }}</span>
            <small class="subtle-text">{{ p.botType }}</small>
          </div>
        </td>
        <td>
          <div class="role-list">
            <span
              v-for="role in splitRoles(p.allowedRoles)"
              :key="role"
              class="pill role-pill"
              :class="roleColors[role] || 'blue'"
            >
              {{ role.replace('ROLE_', '') }}
            </span>
          </div>
        </td>
        <td>{{ p.allowedDepartments || '全部部门' }}</td>
        <td>{{ scopeLabels[p.dataScope] || p.dataScope }}</td>
        <td class="ops-cell">{{ p.allowedOperations || '-' }}</td>
        <td><span class="mono">{{ p.dailyTokenLimit?.toLocaleString() || '-' }}</span></td>
        <td><span class="pill" :class="p.enabled ? 'green' : 'red'">{{ p.enabled ? '启用' : '停用' }}</span></td>
        <td>
          <button class="table-action-btn" @click="emit('edit', p.id)">编辑</button>
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

.bot-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bot-name {
  color: var(--text);
  font-weight: 500;
}

.role-list {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.role-pill {
  margin-right: 0;
}

.ops-cell {
  max-width: 240px;
  white-space: normal;
  word-break: break-word;
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
</style>
