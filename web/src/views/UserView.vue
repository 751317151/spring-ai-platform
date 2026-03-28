<template>
  <div class="space-admin">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">管理台</div>
        <div class="page-title">用户与权限</div>
        <div class="page-subtitle">在一个工作台中统一管理用户账号、角色访问范围和助手权限边界。</div>
        <div class="hero-tags">
          <span class="tag">{{ userStore.permissions.length }} 条权限规则</span>
          <span class="tag">{{ userStore.users.length }} 个用户</span>
          <span class="tag">支持批量操作、筛选和统一确认</span>
        </div>
      </div>
      <div class="page-hero-actions">
        <button class="btn btn-ghost btn-sm" @click="copyOverview">复制概览</button>
        <button class="btn btn-primary" @click="showPermModal()">新建权限</button>
        <button class="btn btn-ghost" @click="showUserModal()">新建用户</button>
      </div>
    </div>

    <div v-if="recentActionLabel" class="recent-action-banner section-card">
      <span class="recent-action-tag">最近操作</span>
      <span class="recent-action-copy">{{ recentActionLabel }}</span>
    </div>

    <div class="summary-grid section-card">
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">启用用户</div>
        <div class="summary-value">{{ enabledUserCount }}</div>
        <div class="summary-subtitle">当前处于启用状态的账号数量</div>
      </div>
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">停用用户</div>
        <div class="summary-value">{{ disabledUserCount }}</div>
        <div class="summary-subtitle">可结合筛选快速检查异常账号</div>
      </div>
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">管理员</div>
        <div class="summary-value">{{ adminUserCount }}</div>
        <div class="summary-subtitle">包含 `ROLE_ADMIN` 的用户数量</div>
      </div>
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">当前筛选结果</div>
        <div class="summary-value">{{ filteredUsers.length }}</div>
        <div class="summary-subtitle">当前用户筛选命中的结果数</div>
      </div>
    </div>

    <div class="card section-card risk-card">
      <div class="card-header">
        <div>
          <div class="card-title">权限风险摘要</div>
          <div class="card-subtitle">先看规则边界是否过宽，再决定是否需要拆分角色或收紧访问范围。</div>
        </div>
        <div class="filter-tabs filter-pill-group">
          <button class="filter-tab filter-pill" :class="{ active: permissionRiskFilter === 'all' }" @click="permissionRiskFilter = 'all'">全部规则</button>
          <button class="filter-tab filter-pill" :class="{ active: permissionRiskFilter === 'risky' }" @click="permissionRiskFilter = 'risky'">仅看高风险</button>
        </div>
      </div>
      <div class="risk-grid">
        <button class="risk-item interactive-metric-card elevated-summary-card" type="button" @click="applyRiskShortcut('scope')">
          <div class="risk-label">全量数据规则</div>
          <div class="risk-value">{{ allDataScopePermissions }}</div>
          <div class="risk-desc">`dataScope = ALL` 的权限规则数量</div>
        </button>
        <button class="risk-item interactive-metric-card elevated-summary-card" type="button" @click="applyRiskShortcut('department')">
          <div class="risk-label">开放全部部门</div>
          <div class="risk-value">{{ unrestrictedDepartmentPermissions }}</div>
          <div class="risk-desc">未限制部门范围的权限规则数量</div>
        </button>
        <button class="risk-item interactive-metric-card elevated-summary-card" type="button" @click="applyRiskShortcut('admin')">
          <div class="risk-label">管理员覆盖</div>
          <div class="risk-value">{{ adminCoveredPermissions }}</div>
          <div class="risk-desc">允许 `ROLE_ADMIN` 访问的权限规则数量</div>
        </button>
      </div>
      <div class="risk-density">
        <span>高风险规则 {{ riskyPermissionRatio }}</span>
        <span>已启用高风险规则 {{ riskyEnabledPermissions }}</span>
      </div>
    </div>

    <div class="summary-grid section-card">
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">角色分布</div>
        <div class="distribution-list">
          <div v-for="item in topRoleDistribution" :key="item.label" class="distribution-row">
            <span>{{ item.label }}</span>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </div>
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">部门分布</div>
        <div class="distribution-list">
          <div v-for="item in topDepartmentDistribution" :key="item.label" class="distribution-row">
            <span>{{ item.label }}</span>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </div>
    </div>

    <div class="card section-card">
      <div class="card-header">
        <div>
          <div class="card-title">助手权限规则</div>
          <div class="card-subtitle">按助手、角色、部门和允许操作维度控制访问边界。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="showPermModal()">+ 新建权限</button>
      </div>

      <div class="filters">
        <input v-model="permissionKeyword" class="form-input filter-input" placeholder="搜索助手、角色、部门或操作">
        <select v-model="permissionStatus" class="form-input filter-select">
          <option value="all">全部状态</option>
          <option value="enabled">仅看启用</option>
          <option value="disabled">仅看停用</option>
        </select>
        <button v-if="hasPermissionFilters" class="btn btn-ghost btn-sm" @click="resetPermissionFilters">重置筛选</button>
      </div>

      <div class="filter-summary">
        当前显示 {{ filteredPermissions.length }} / {{ userStore.permissions.length }} 条权限规则
        <span v-if="permissionRiskFilter === 'risky'">，当前仅展示高风险规则</span>
      </div>

      <SkeletonBlock v-if="userStore.loadingPermissions" :count="3" :height="52" />
      <EmptyState
        v-else-if="userStore.permissionError"
        icon="P"
        badge="规则状态"
        title="权限规则加载失败"
        :description="userStore.permissionError"
        action-text="重新加载"
        @action="userStore.loadPermissions()"
      />
      <PermissionTable v-else :permissions="filteredPermissions" :risk-ids="riskPermissionIds" @edit="showPermModal" />
    </div>

    <div class="card">
      <div class="card-header">
        <div>
          <div class="card-title">用户目录</div>
          <div class="card-subtitle">按状态、角色和关键词筛选用户，提升账号管理效率。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="showUserModal()">+ 新建用户</button>
      </div>

      <div class="filters">
        <input v-model="userKeyword" class="form-input filter-input" placeholder="搜索用户ID、用户名、工号、部门或角色">
        <select v-model="userStatus" class="form-input filter-select">
          <option value="all">全部状态</option>
          <option value="enabled">仅看启用</option>
          <option value="disabled">仅看停用</option>
        </select>
        <select v-model="userRole" class="form-input filter-select">
          <option value="all">全部角色</option>
          <option value="ROLE_ADMIN">ROLE_ADMIN</option>
          <option value="ROLE_RD">ROLE_RD</option>
          <option value="ROLE_SALES">ROLE_SALES</option>
          <option value="ROLE_HR">ROLE_HR</option>
          <option value="ROLE_FINANCE">ROLE_FINANCE</option>
          <option value="ROLE_USER">ROLE_USER</option>
        </select>
        <button v-if="hasUserFilters" class="btn btn-ghost btn-sm" @click="resetUserFilters">重置筛选</button>
      </div>

      <div class="filter-summary">
        当前显示 {{ filteredUsers.length }} / {{ userStore.users.length }} 个用户
        <span v-if="selectedUserIds.length">，已选择 {{ selectedUserIds.length }} 个</span>
      </div>

      <div v-if="selectedUserIds.length" class="bulk-toolbar">
        <div class="bulk-info">已选择 {{ selectedUserIds.length }} 个用户</div>
        <div class="bulk-actions">
          <button class="btn btn-ghost btn-sm" @click="selectedUserIds = []">清空选择</button>
          <button class="btn btn-danger btn-sm" @click="handleBatchDeleteUsers">批量删除</button>
        </div>
      </div>

      <SkeletonBlock v-if="userStore.loadingUsers" :count="4" :height="52" />
      <EmptyState
        v-else-if="userStore.userError"
        icon="U"
        badge="用户状态"
        title="用户加载失败"
        :description="userStore.userError"
        action-text="重新加载"
        @action="userStore.loadUsers()"
      />
      <UserTable
        v-else
        :users="filteredUsers"
        :selected-ids="selectedUserIds"
        :highlighted-user-id="highlightedUserId"
        @update:selected-ids="selectedUserIds = $event"
        @reset-filters="resetUserFilters"
        @edit="showUserModal"
        @inspect="inspectUser"
        @delete="handleDeleteUser"
      />

      <div v-if="inspectedUser" class="user-insight-panel">
        <div class="card-header">
          <div>
            <div class="card-title">用户使用概览</div>
            <div class="card-subtitle">聚合最近登录、今日 Token 使用和近期助手调用情况。</div>
          </div>
          <button class="btn btn-ghost btn-sm" @click="closeUserInsight">关闭</button>
        </div>

        <div v-if="userUsageLoading" class="filter-summary">正在加载用户使用概览...</div>
        <EmptyState
          v-else-if="userUsageError"
          icon="I"
          badge="用户概览"
          title="用户使用概览加载失败"
          :description="userUsageError"
          action-text="重试"
          @action="inspectUser(inspectingUserId)"
        />
        <template v-else>
          <div class="summary-grid insight-grid">
            <div class="card summary-card">
              <div class="summary-label">最近登录</div>
              <div class="summary-value">{{ inspectedUser.lastLoginAt ? formatTime(inspectedUser.lastLoginAt) : '暂无记录' }}</div>
              <div class="summary-subtitle">{{ inspectedUser.username }} / {{ inspectedUser.userId }}</div>
            </div>
            <div class="card summary-card">
              <div class="summary-label">今日 Token</div>
              <div class="summary-value">{{ userTokenUsage?.tokensUsed ?? 0 }}</div>
              <div class="summary-subtitle">{{ userTokenUsage?.date || '当天' }}</div>
            </div>
            <div class="card summary-card">
              <div class="summary-label">近期调用</div>
              <div class="summary-value">{{ userRecentLogs.length }}</div>
              <div class="summary-subtitle">最近 5 条审计记录</div>
            </div>
          </div>

          <div class="summary-grid insight-grid">
            <div class="card summary-card">
              <div class="summary-label">最近使用助手</div>
              <div class="distribution-list">
                <div v-for="item in inspectedTopAgents" :key="item.label" class="distribution-row">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.count }}</strong>
                </div>
                <div v-if="!inspectedTopAgents.length" class="distribution-row">
                  <span>暂无调用记录</span>
                  <strong>0</strong>
                </div>
              </div>
            </div>
            <div class="card summary-card insight-log-card">
              <div class="summary-label">最近调用记录</div>
              <div class="insight-log-list">
                <div v-for="item in userRecentLogs" :key="item.id" class="insight-log-item">
                  <div class="insight-log-title">{{ item.agent_type || 'unknown' }} / {{ item.model_id || '未记录模型' }}</div>
                  <div class="insight-log-meta">{{ item.success ? '成功' : '失败' }} · {{ item.latency_ms }}ms · {{ formatTime(item.created_at) }}</div>
                </div>
                <div v-if="!userRecentLogs.length" class="insight-log-item">
                  <div class="insight-log-title">暂无调用记录</div>
                  <div class="insight-log-meta">该用户最近没有可展示的审计日志。</div>
                </div>
              </div>
            </div>
          </div>

          <div class="insight-actions">
            <button class="btn btn-ghost btn-sm" @click="copyInspectedUserSummary">复制用户摘要</button>
            <button
              v-if="inspectedUser?.department"
              class="btn btn-ghost btn-sm"
              @click="applyDepartmentShortcut(inspectedUser.department)"
            >
              筛选同部门用户
            </button>
          </div>
        </template>
      </div>
    </div>

    <UserModal
      v-if="userModalVisible"
      :user-id="editingUserId"
      @close="userModalVisible = false"
      @saved="handleUserSaved"
    />

    <PermissionModal
      v-if="permModalVisible"
      :permission-id="editingPermId"
      @close="permModalVisible = false"
      @saved="handlePermSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { getAuditLogs, getTokenUsage } from '@/api/monitor'
import type { AuditLog, TokenUsage } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import PermissionModal from '@/components/user/PermissionModal.vue'
import PermissionTable from '@/components/user/PermissionTable.vue'
import UserModal from '@/components/user/UserModal.vue'
import UserTable from '@/components/user/UserTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const { showToast } = useToast()
const { confirm } = useConfirm()

const userModalVisible = ref(false)
const editingUserId = ref<string | null>(null)
const permModalVisible = ref(false)
const editingPermId = ref<string | null>(null)
const userKeyword = ref('')
const userStatus = ref<'all' | 'enabled' | 'disabled'>('all')
const userRole = ref('all')
const permissionKeyword = ref('')
const permissionStatus = ref<'all' | 'enabled' | 'disabled'>('all')
const selectedUserIds = ref<string[]>([])
const permissionRiskFilter = ref<'all' | 'risky'>('all')
const highlightedUserId = ref('')
const inspectingUserId = ref('')
const userUsageLoading = ref(false)
const userUsageError = ref('')
const userTokenUsage = ref<TokenUsage | null>(null)
const userRecentLogs = ref<AuditLog[]>([])
const recentActionLabel = ref('')

const hasUserFilters = computed(() => Boolean(userKeyword.value || userStatus.value !== 'all' || userRole.value !== 'all'))
const hasPermissionFilters = computed(() => Boolean(permissionKeyword.value || permissionStatus.value !== 'all' || permissionRiskFilter.value !== 'all'))
const enabledUserCount = computed(() => userStore.users.filter((user) => user.enabled !== false).length)
const disabledUserCount = computed(() => userStore.users.filter((user) => user.enabled === false).length)
const adminUserCount = computed(() =>
  userStore.users.filter((user) =>
    (user.roles || '')
      .split(',')
      .map((item) => item.trim())
      .includes('ROLE_ADMIN')
  ).length
)
const allDataScopePermissions = computed(() => userStore.permissions.filter((item) => item.dataScope === 'ALL').length)
const unrestrictedDepartmentPermissions = computed(() => userStore.permissions.filter((item) => !item.allowedDepartments?.trim()).length)
const adminCoveredPermissions = computed(() =>
  userStore.permissions.filter((item) =>
    (item.allowedRoles || '')
      .split(',')
      .map((role) => role.trim())
      .includes('ROLE_ADMIN')
  ).length
)
const topRoleDistribution = computed(() => {
  const counts = new Map<string, number>()
  userStore.users.forEach((user) => {
    const roles = (user.roles || '')
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
    if (!roles.length) {
      counts.set('未配置角色', (counts.get('未配置角色') || 0) + 1)
      return
    }
    roles.forEach((role) => {
      counts.set(role, (counts.get(role) || 0) + 1)
    })
  })
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 4)
    .map(([label, count]) => ({ label, count }))
})
const topDepartmentDistribution = computed(() => {
  const counts = new Map<string, number>()
  userStore.users.forEach((user) => {
    const department = user.department?.trim() || '未分配部门'
    counts.set(department, (counts.get(department) || 0) + 1)
  })
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 4)
    .map(([label, count]) => ({ label, count }))
})
const riskPermissionIds = computed(() =>
  userStore.permissions
    .filter((item) =>
      item.dataScope === 'ALL' ||
      !item.allowedDepartments?.trim() ||
      (item.allowedRoles || '')
        .split(',')
        .map((role) => role.trim())
        .includes('ROLE_ADMIN')
    )
    .map((item) => item.id)
)
const riskyEnabledPermissions = computed(() =>
  userStore.permissions.filter((item) => riskPermissionIds.value.includes(item.id) && item.enabled).length
)
const riskyPermissionRatio = computed(() => {
  if (!userStore.permissions.length) return '0%'
  return `${Math.round((riskPermissionIds.value.length / userStore.permissions.length) * 100)}%`
})
const inspectedUser = computed(() => userStore.users.find((item) => item.userId === inspectingUserId.value) || null)
const inspectedTopAgents = computed(() => {
  const counts = new Map<string, number>()
  userRecentLogs.value.forEach((item) => {
    const label = item.agent_type || 'unknown'
    counts.set(label, (counts.get(label) || 0) + 1)
  })
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3)
    .map(([label, count]) => ({ label, count }))
})

const filteredUsers = computed(() => {
  const keyword = userKeyword.value.trim().toLowerCase()
  return userStore.users.filter((user) => {
    const enabled = user.enabled !== false
    const matchesStatus =
      userStatus.value === 'all' ||
      (userStatus.value === 'enabled' && enabled) ||
      (userStatus.value === 'disabled' && !enabled)
    const matchesRole =
      userRole.value === 'all' ||
      (user.roles || '')
        .split(',')
        .map((item) => item.trim())
        .includes(userRole.value)
    const matchesKeyword =
      !keyword ||
      [user.userId, user.username, user.employeeId, user.department, user.roles]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    return matchesStatus && matchesRole && matchesKeyword
  })
})

const filteredPermissions = computed(() => {
  const keyword = permissionKeyword.value.trim().toLowerCase()
  return userStore.permissions
    .filter((permission) => {
      const matchesStatus =
        permissionStatus.value === 'all' ||
        (permissionStatus.value === 'enabled' && permission.enabled) ||
        (permissionStatus.value === 'disabled' && !permission.enabled)
      const matchesKeyword =
        !keyword ||
        [permission.botType, permission.allowedRoles, permission.allowedDepartments, permission.allowedOperations]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(keyword))
      const matchesRisk = permissionRiskFilter.value === 'all' || riskPermissionIds.value.includes(permission.id)
      return matchesStatus && matchesKeyword && matchesRisk
    })
    .sort((a, b) => Number(riskPermissionIds.value.includes(b.id)) - Number(riskPermissionIds.value.includes(a.id)))
})

watch(filteredUsers, (users) => {
  const allowed = new Set(users.map((user) => user.userId))
  selectedUserIds.value = selectedUserIds.value.filter((id) => allowed.has(id))
})

function resetUserFilters() {
  userKeyword.value = ''
  userStatus.value = 'all'
  userRole.value = 'all'
}

function resetPermissionFilters() {
  permissionKeyword.value = ''
  permissionStatus.value = 'all'
  permissionRiskFilter.value = 'all'
}

function applyRiskShortcut(type: 'scope' | 'department' | 'admin') {
  permissionRiskFilter.value = 'risky'
  if (type === 'scope') {
    permissionKeyword.value = 'ALL'
    return
  }
  if (type === 'department') {
    permissionKeyword.value = ''
    return
  }
  permissionKeyword.value = 'ROLE_ADMIN'
}

async function copyOverview() {
  const lines = [
    '用户与权限概览',
    `用户总数：${userStore.users.length}`,
    `启用用户：${enabledUserCount.value}`,
    `停用用户：${disabledUserCount.value}`,
    `管理员：${adminUserCount.value}`,
    `权限规则：${userStore.permissions.length}`,
    `高风险规则占比：${riskyPermissionRatio.value}`,
    `已启用高风险规则：${riskyEnabledPermissions.value}`
  ]
  if (topRoleDistribution.value.length) {
    lines.push('角色分布：')
    topRoleDistribution.value.forEach((item) => lines.push(`- ${item.label}: ${item.count}`))
  }
  if (topDepartmentDistribution.value.length) {
    lines.push('部门分布：')
    topDepartmentDistribution.value.forEach((item) => lines.push(`- ${item.label}: ${item.count}`))
  }
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    setRecentAction('已复制用户与权限概览。')
    showToast('已复制用户与权限概览')
  } catch {
    showToast('复制用户与权限概览失败')
  }
}

async function copyInspectedUserSummary() {
  if (!inspectedUser.value) return
  const lines = [
    '用户使用摘要',
    `用户：${inspectedUser.value.username} / ${inspectedUser.value.userId}`,
    `部门：${inspectedUser.value.department || '未分配'}`,
    `角色：${inspectedUser.value.roles || '未配置'}`,
    `最近登录：${inspectedUser.value.lastLoginAt ? formatTime(inspectedUser.value.lastLoginAt) : '暂无记录'}`,
    `今日 Token：${userTokenUsage.value?.tokensUsed ?? 0}`,
    `近期审计记录：${userRecentLogs.value.length}`
  ]
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    setRecentAction(`已复制 ${inspectedUser.value.username} 的使用摘要。`)
    showToast('已复制用户摘要')
  } catch {
    showToast('复制用户摘要失败')
  }
}

function applyDepartmentShortcut(department: string) {
  userKeyword.value = department
  userStatus.value = 'all'
  userRole.value = 'all'
  setRecentAction(`已按 ${department} 筛选同部门用户。`)
  showToast(`已筛选 ${department} 用户`)
}

function showUserModal(userId?: string) {
  editingUserId.value = userId || null
  userModalVisible.value = true
}

function showPermModal(permId?: string) {
  editingPermId.value = permId || null
  permModalVisible.value = true
}

async function inspectUser(userId: string) {
  inspectingUserId.value = userId
  userUsageLoading.value = true
  userUsageError.value = ''
  try {
    const [tokenUsage, logs] = await Promise.all([
      getTokenUsage(userId).catch(() => null),
      getAuditLogs(5, userId).catch(() => [])
    ])
    userTokenUsage.value = tokenUsage
    userRecentLogs.value = logs
    const username = userStore.users.find((item) => item.userId === userId)?.username || userId
    setRecentAction(`已加载 ${username} 的使用概览。`)
  } catch (error) {
    userUsageError.value = error instanceof Error ? error.message : '用户使用概览加载失败'
    userTokenUsage.value = null
    userRecentLogs.value = []
  } finally {
    userUsageLoading.value = false
  }
}

function closeUserInsight() {
  inspectingUserId.value = ''
  userUsageError.value = ''
  userTokenUsage.value = null
  userRecentLogs.value = []
}

async function scrollToUserRow(userId: string) {
  await nextTick()
  const row = document.getElementById(`user-row-${userId}`)
  if (!row) return
  row.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function clearUserHighlightLater() {
  window.setTimeout(() => {
    highlightedUserId.value = ''
  }, 2400)
}

async function handleUserSaved(userId: string) {
  userModalVisible.value = false
  resetUserFilters()
  highlightedUserId.value = userId
  await scrollToUserRow(userId)
  await inspectUser(userId)
  clearUserHighlightLater()
  setRecentAction('已保存用户并刷新列表。')
  showToast('用户保存成功')
}

function handlePermSaved() {
  permModalVisible.value = false
  setRecentAction('已保存权限规则。')
  showToast('权限规则保存成功')
}

async function handleDeleteUser(id: string, name: string) {
  const accepted = await confirm({
    title: '删除用户',
    description: `确认删除“${name}”吗？该操作不可撤销。`,
    confirmText: '删除',
    intent: 'danger'
  })
  if (!accepted) return

  const ok = await userStore.deleteUser(id)
  if (ok) {
    selectedUserIds.value = selectedUserIds.value.filter((item) => item !== id)
    setRecentAction(`已删除用户 ${name}。`)
    showToast('用户已删除')
  }
}

async function handleBatchDeleteUsers() {
  const names = filteredUsers.value
    .filter((user) => selectedUserIds.value.includes(user.userId))
    .map((user) => user.username)
    .slice(0, 5)
  const preview = names.length ? `，包括：${names.join('、')}` : ''
  const accepted = await confirm({
    title: '批量删除用户',
    description: `确认删除已选择的 ${selectedUserIds.value.length} 个用户吗？${preview}`,
    confirmText: '删除',
    intent: 'danger'
  })
  if (!accepted) return

  const results = await Promise.all(selectedUserIds.value.map((id) => userStore.deleteUser(id)))
  const successCount = results.filter(Boolean).length
  selectedUserIds.value = []
  setRecentAction(`已完成批量删除，成功 ${successCount} 个用户。`)
  showToast(
    successCount === results.length
      ? `已删除 ${successCount} 个用户`
      : `已删除 ${successCount} 个用户，部分失败`
  )
}

onMounted(() => {
  userStore.loadAll()
})

function setRecentAction(message: string) {
  recentActionLabel.value = `${new Date().toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })} · ${message}`
}
</script>

<style scoped>
.section-card {
  margin-bottom: 16px;
}

.recent-action-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.08), rgba(14, 165, 233, 0.04));
}

.recent-action-tag {
  color: var(--accent2);
  font-size: 12px;
  font-weight: 700;
}

.recent-action-copy {
  color: var(--text2);
  font-size: 13px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.risk-card {
  overflow: hidden;
}

.summary-card {
  padding: 16px;
}

.summary-label {
  margin-bottom: 8px;
  color: var(--text3);
  font-size: 12px;
}

.summary-value {
  color: var(--text);
  font-size: 22px;
  font-weight: 700;
}

.summary-subtitle {
  margin-top: 8px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}

.distribution-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.distribution-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(148, 163, 184, 0.08);
  color: var(--text2);
}

.distribution-row strong {
  color: var(--text);
  font-size: 16px;
}

.risk-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.risk-item {
  appearance: none;
  text-align: left;
  cursor: pointer;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  transition: transform var(--transition), border-color var(--transition), background var(--transition);
}

.risk-item:hover {
  transform: translateY(-2px);
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.08);
}

.risk-density {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
  color: #b45309;
  font-size: 12px;
}

.risk-label {
  color: var(--text3);
  font-size: 12px;
  margin-bottom: 8px;
}

.risk-value {
  color: var(--text);
  font-size: 24px;
  font-weight: 700;
}

.risk-desc {
  margin-top: 8px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}

.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.filter-input {
  min-width: 260px;
  flex: 1;
}

.filter-select {
  width: auto;
  min-width: 140px;
}

.filter-summary {
  margin-bottom: 12px;
  color: var(--text3);
  font-size: 12px;
}

.user-insight-panel {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.insight-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.insight-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 12px;
}

.insight-log-card {
  min-height: 100%;
}

.insight-log-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.insight-log-item {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(148, 163, 184, 0.08);
}

.insight-log-title {
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
}

.insight-log-meta {
  margin-top: 6px;
  color: var(--text3);
  font-size: 12px;
}

.bulk-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, rgba(148, 163, 184, 0.05), rgba(255, 255, 255, 0.02));
  border-radius: 16px;
}

.bulk-info {
  font-size: 13px;
  color: var(--text);
  font-weight: 500;
}

.bulk-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .risk-grid {
    grid-template-columns: 1fr;
  }

  .insight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
