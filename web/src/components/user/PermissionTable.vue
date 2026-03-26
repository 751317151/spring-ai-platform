<template>
  <div class="permission-table">
    <table v-if="permissionGroups.length">
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
      <tbody v-for="group in permissionGroups" :key="group.botType" class="group-body">
        <tr class="group-row">
          <td colspan="8">
            <button class="group-toggle" type="button" @click="toggleGroup(group.botType)">
              <div class="group-summary">
                <div>
                  <strong>{{ group.label }}</strong>
                  <small class="subtle-text">{{ group.botType }}</small>
                </div>
                <div class="group-metrics">
                  <span class="pill blue">{{ group.items.length }} 条规则</span>
                  <span v-if="group.riskCount" class="pill amber">{{ group.riskCount }} 条高风险</span>
                  <span class="group-arrow">{{ expandedGroups[group.botType] ? '收起' : '展开' }}</span>
                </div>
              </div>
            </button>
          </td>
        </tr>
        <tr
          v-for="p in group.items"
          v-show="expandedGroups[group.botType]"
          :key="p.id"
          :class="{ 'risk-row': riskIds.includes(p.id) }"
        >
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

    <div v-else class="empty-cell">
      <EmptyState
        icon="P"
        title="当前筛选条件下没有匹配的权限规则"
        description="可以调整角色、部门或操作关键字后重新筛选。"
        variant="compact"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { BotPermission } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import { BOT_LABELS, ROLE_COLORS, SCOPE_LABELS } from '@/utils/constants'

const props = withDefaults(defineProps<{ permissions: BotPermission[]; riskIds?: string[] }>(), {
  riskIds: () => []
})

const emit = defineEmits<{ edit: [id: string] }>()

const botLabels = BOT_LABELS
const scopeLabels = SCOPE_LABELS
const roleColors = ROLE_COLORS
const expandedGroups = reactive<Record<string, boolean>>({})

const permissionGroups = computed(() => {
  const groupMap = new Map<string, BotPermission[]>()

  props.permissions.forEach((permission) => {
    const current = groupMap.get(permission.botType) || []
    current.push(permission)
    groupMap.set(permission.botType, current)
  })

  return Array.from(groupMap.entries()).map(([botType, items]) => ({
    botType,
    label: botLabels[botType] || botType,
    items,
    riskCount: items.filter((item) => props.riskIds.includes(item.id)).length
  }))
})

watch(
  permissionGroups,
  (groups) => {
    const activeKeys = new Set(groups.map((group) => group.botType))
    Object.keys(expandedGroups).forEach((key) => {
      if (!activeKeys.has(key)) {
        delete expandedGroups[key]
      }
    })
    groups.forEach((group) => {
      if (expandedGroups[group.botType] === undefined) {
        expandedGroups[group.botType] = true
      }
    })
  },
  { immediate: true }
)

function splitRoles(roles: string | undefined): string[] {
  if (!roles) return []
  return roles.split(',').map((r) => r.trim()).filter(Boolean)
}

function toggleGroup(botType: string) {
  expandedGroups[botType] = !expandedGroups[botType]
}
</script>

<style scoped>
.permission-table {
  width: 100%;
}

.empty-cell {
  padding: 12px;
}

.group-body + .group-body .group-row td {
  border-top: 1px solid var(--border);
}

.group-row td {
  padding: 0;
  background: rgba(15, 23, 42, 0.03);
}

.group-toggle {
  width: 100%;
  border: 0;
  background: transparent;
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
}

.group-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.group-metrics {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.group-arrow {
  color: var(--text3);
  font-size: 12px;
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

.risk-row {
  background: rgba(245, 158, 11, 0.06);
}
</style>
