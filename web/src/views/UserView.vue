<template>
  <div class="page-shell">
    <section class="hero-card">
      <div>
        <div class="eyebrow">RBAC 管理</div>
        <h1>用户、角色、助手与 Token 配额</h1>
        <p>
          这一页统一管理角色目录、用户账号、助手定义、角色配额和用户配额。普通助手直接在助手上配置允许角色和默认 Token 配额，不再单独维护权限规则表。
        </p>
      </div>
      <button class="btn btn-primary" @click="openPrimaryAction">{{ activeSection.primaryActionText }}</button>
    </section>

    <section class="card section-card">
      <div class="section-tabs">
        <button
          v-for="section in sections"
          :key="section.key"
          class="section-tab"
          :class="{ active: activeSectionKey === section.key }"
          @click="activeSectionKey = section.key"
        >
          <span class="tab-title">{{ section.title }}</span>
          <span class="tab-subtitle">{{ section.subtitle }}</span>
        </button>
      </div>
    </section>

    <section v-if="activeSectionKey === 'roles'" class="card section-card">
      <div class="section-header">
        <div>
          <div class="section-title">角色目录</div>
          <div class="section-subtitle">维护角色定义，并查看角色被哪些用户和助手引用。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="openRoleModal()">新建角色</button>
      </div>
      <div class="toolbar">
        <input v-model.trim="roleKeyword" class="form-input toolbar-input" placeholder="搜索角色名称或描述">
      </div>
      <EmptyState
        v-if="userStore.loadingRoles"
        icon="R"
        title="正在加载角色目录"
        description="请稍候。"
        variant="compact"
      />
      <EmptyState
        v-else-if="userStore.roleError"
        icon="R"
        title="角色目录加载失败"
        :description="userStore.roleError"
        action-text="重新加载"
        variant="compact"
        @action="userStore.loadRoles()"
      />
      <div v-else class="role-grid">
        <article v-for="role in filteredRoles" :key="role.id" class="role-card">
          <div class="role-top">
            <div>
              <div class="role-name">{{ role.roleName }}</div>
              <div class="role-desc">{{ role.description || '未填写角色说明' }}</div>
            </div>
            <div class="role-actions">
              <button class="table-btn" @click="openRoleUsage(role)">详情</button>
              <button class="table-btn" @click="openRoleModal(role)">编辑</button>
              <button class="table-btn danger" @click="handleDeleteRole(role)">删除</button>
            </div>
          </div>
          <div class="role-metrics">
            <span>{{ countUsersByRole(role.roleName) }} 个用户</span>
            <span>{{ countAssistantsByRole(role.roleName) }} 个助手</span>
            <span>{{ countRoleQuotaByRole(role.id) }} 条角色配额</span>
          </div>
        </article>
      </div>
    </section>

    <section v-if="activeSectionKey === 'users'" class="card section-card">
      <div class="section-header">
        <div>
          <div class="section-title">用户与角色</div>
          <div class="section-subtitle">用户登录后先获得角色，再由角色决定可访问的助手。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="openUserModal()">新建用户</button>
      </div>
      <div class="toolbar">
        <input v-model.trim="userKeyword" class="form-input toolbar-input" placeholder="搜索用户、部门或角色">
        <select v-model="userStatus" class="form-input toolbar-select">
          <option value="all">全部状态</option>
          <option value="enabled">仅启用</option>
          <option value="disabled">仅停用</option>
        </select>
      </div>
      <div class="toolbar-meta">共 {{ filteredUsers.length }} / {{ userStore.users.length }} 个用户</div>
      <EmptyState
        v-if="userStore.loadingUsers"
        icon="U"
        title="正在加载用户列表"
        description="请稍候。"
        variant="compact"
      />
      <EmptyState
        v-else-if="userStore.userError"
        icon="U"
        title="用户列表加载失败"
        :description="userStore.userError"
        action-text="重新加载"
        variant="compact"
        @action="userStore.loadUsers()"
      />
      <UserTable v-else :users="filteredUsers" @edit="openUserModal" @delete="handleDeleteUser" />
    </section>

    <section v-if="activeSectionKey === 'agent-definitions'" class="card section-card">
      <div class="section-header">
        <div>
          <div class="section-title">助手</div>
          <div class="section-subtitle">助手直接维护系统提示词、允许角色和默认 Token 配额。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="openAgentDefinitionModal()">新建助手</button>
      </div>
      <div class="toolbar">
        <input v-model.trim="agentDefinitionKeyword" class="form-input toolbar-input" placeholder="搜索编码、名称或描述">
        <select v-model="agentDefinitionStatus" class="form-input toolbar-select">
          <option value="all">全部状态</option>
          <option value="enabled">仅启用</option>
          <option value="disabled">仅停用</option>
        </select>
      </div>
      <div class="toolbar-meta">共 {{ filteredAgentDefinitions.length }} / {{ userStore.agentDefinitions.length }} 个助手</div>
      <EmptyState
        v-if="userStore.loadingAgentDefinitions"
        icon="AI"
        title="正在加载助手列表"
        description="请稍候。"
        variant="compact"
      />
      <EmptyState
        v-else-if="userStore.agentDefinitionError"
        icon="AI"
        title="助手列表加载失败"
        :description="userStore.agentDefinitionError"
        action-text="重新加载"
        variant="compact"
        @action="userStore.loadAgentDefinitions()"
      />
      <div v-else class="definition-grid">
        <article v-for="definition in filteredAgentDefinitions" :key="definition.id || definition.agentCode" class="definition-card">
          <div class="definition-head">
            <div class="definition-badge" :style="{ background: definition.color || '#6b7280' }">
              {{ definition.icon || 'AI' }}
            </div>
            <div class="definition-main">
              <div class="definition-title-row">
                <div class="role-name">{{ definition.agentName }}</div>
                <div class="chips">
                  <span v-if="definition.systemDefined" class="chip chip-special">特殊助手</span>
                  <span class="chip" :class="{ disabled: !definition.enabled }">
                    {{ definition.enabled ? '已启用' : '已停用' }}
                  </span>
                </div>
              </div>
              <div class="definition-code">{{ definition.agentCode }}</div>
              <div class="role-desc">{{ definition.description || '未填写助手说明' }}</div>
            </div>
          </div>
          <div class="role-metrics">
            <span>默认模型 {{ definition.defaultModel || 'auto' }}</span>
            <span>排序 {{ definition.sortOrder ?? 0 }}</span>
            <span>每日 Token {{ Number(definition.dailyTokenLimit || 0).toLocaleString() }}</span>
          </div>
          <div class="tag-list">
            <span v-for="role in splitCsv(definition.allowedRoles)" :key="role" class="tag">{{ role }}</span>
          </div>
          <div class="role-actions">
            <button class="table-btn" @click="openAgentDefinitionModal(definition)">编辑</button>
            <button v-if="!definition.systemDefined" class="table-btn danger" @click="handleDeleteAgentDefinition(definition)">
              删除
            </button>
          </div>
        </article>
      </div>
    </section>

    <section v-if="activeSectionKey === 'role-quotas'" class="card section-card">
      <div class="section-header">
        <div>
          <div class="section-title">角色 Token 配额</div>
          <div class="section-subtitle">给角色配置总配额或指定助手配额。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="openRoleTokenLimitModal()">新建角色配额</button>
      </div>
      <div class="toolbar">
        <input v-model.trim="roleTokenLimitKeyword" class="form-input toolbar-input" placeholder="搜索角色名称或助手范围">
        <select v-model="roleTokenLimitStatus" class="form-input toolbar-select">
          <option value="all">全部状态</option>
          <option value="enabled">仅启用</option>
          <option value="disabled">仅停用</option>
        </select>
      </div>
      <div class="toolbar-meta">共 {{ filteredRoleTokenLimits.length }} / {{ userStore.roleTokenLimits.length }} 条规则</div>
      <EmptyState
        v-if="userStore.loadingRoleTokenLimits"
        icon="Q"
        title="正在加载角色配额规则"
        description="请稍候。"
        variant="compact"
      />
      <EmptyState
        v-else-if="userStore.roleTokenLimitError"
        icon="Q"
        title="角色配额规则加载失败"
        :description="userStore.roleTokenLimitError"
        action-text="重新加载"
        variant="compact"
        @action="userStore.loadRoleTokenLimits()"
      />
      <TokenLimitTable
        v-else
        target-type="role"
        :items="filteredRoleTokenLimits"
        @edit="openRoleTokenLimitModalById"
        @delete="handleDeleteRoleTokenLimit"
      />
    </section>

    <section v-if="activeSectionKey === 'user-quotas'" class="card section-card">
      <div class="section-header">
        <div>
          <div class="section-title">用户 Token 配额</div>
          <div class="section-subtitle">给特定用户配置覆盖角色默认值的专属配额。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="openUserTokenLimitModal()">新建用户配额</button>
      </div>
      <div class="toolbar">
        <input v-model.trim="userTokenLimitKeyword" class="form-input toolbar-input" placeholder="搜索用户、部门或助手范围">
        <select v-model="userTokenLimitStatus" class="form-input toolbar-select">
          <option value="all">全部状态</option>
          <option value="enabled">仅启用</option>
          <option value="disabled">仅停用</option>
        </select>
      </div>
      <div class="toolbar-meta">共 {{ filteredUserTokenLimits.length }} / {{ userStore.userTokenLimits.length }} 条规则</div>
      <EmptyState
        v-if="userStore.loadingUserTokenLimits"
        icon="Q"
        title="正在加载用户配额规则"
        description="请稍候。"
        variant="compact"
      />
      <EmptyState
        v-else-if="userStore.userTokenLimitError"
        icon="Q"
        title="用户配额规则加载失败"
        :description="userStore.userTokenLimitError"
        action-text="重新加载"
        variant="compact"
        @action="userStore.loadUserTokenLimits()"
      />
      <TokenLimitTable
        v-else
        target-type="user"
        :items="filteredUserTokenLimits"
        @edit="openUserTokenLimitModalById"
        @delete="handleDeleteUserTokenLimit"
      />
    </section>

    <RoleModal v-if="roleModalVisible" :role="editingRole" @close="closeRoleModal" @saved="handleRoleSaved" />
    <RoleUsageDrawer v-if="roleUsageVisible && selectedRoleUsage" :usage="selectedRoleUsage" @close="closeRoleUsage" />
    <UserModal v-if="userModalVisible" :user-id="editingUserId" @close="closeUserModal" @saved="handleUserSaved" />
    <AgentDefinitionModal
      v-if="agentDefinitionModalVisible"
      :definition="editingAgentDefinition"
      @close="closeAgentDefinitionModal"
      @saved="handleAgentDefinitionSaved"
    />
    <TokenLimitModal
      v-if="roleTokenLimitModalVisible"
      target-type="role"
      :edit-item="editingRoleTokenLimit"
      @close="closeRoleTokenLimitModal"
      @saved="handleRoleTokenLimitSaved"
    />
    <TokenLimitModal
      v-if="userTokenLimitModalVisible"
      target-type="user"
      :edit-item="editingUserTokenLimit"
      @close="closeUserTokenLimitModal"
      @saved="handleUserTokenLimitSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getRoleUsage } from '@/api/auth'
import type { AgentDefinition, RoleOption, RoleTokenLimit, RoleUsage, UserTokenLimit } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import AgentDefinitionModal from '@/components/user/AgentDefinitionModal.vue'
import RoleModal from '@/components/user/RoleModal.vue'
import RoleUsageDrawer from '@/components/user/RoleUsageDrawer.vue'
import TokenLimitModal from '@/components/user/TokenLimitModal.vue'
import TokenLimitTable from '@/components/user/TokenLimitTable.vue'
import UserModal from '@/components/user/UserModal.vue'
import UserTable from '@/components/user/UserTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'

type SectionKey = 'roles' | 'users' | 'agent-definitions' | 'role-quotas' | 'user-quotas'

const sections = [
  { key: 'roles' as SectionKey, title: '角色目录', subtitle: '查看角色定义和引用情况', primaryActionText: '新建角色' },
  { key: 'users' as SectionKey, title: '用户与角色', subtitle: '维护用户账号和角色分配', primaryActionText: '新建用户' },
  { key: 'agent-definitions' as SectionKey, title: '助手', subtitle: '统一管理助手定义、角色和配额', primaryActionText: '新建助手' },
  { key: 'role-quotas' as SectionKey, title: '角色配额', subtitle: '配置角色级 Token 配额', primaryActionText: '新建角色配额' },
  { key: 'user-quotas' as SectionKey, title: '用户配额', subtitle: '配置用户级 Token 配额', primaryActionText: '新建用户配额' }
]

const userStore = useUserStore()
const { confirm } = useConfirm()
const { showToast } = useToast()

const activeSectionKey = ref<SectionKey>('roles')
const roleModalVisible = ref(false)
const editingRole = ref<RoleOption | null>(null)
const roleUsageVisible = ref(false)
const selectedRoleUsage = ref<RoleUsage | null>(null)
const userModalVisible = ref(false)
const editingUserId = ref<string | null>(null)
const agentDefinitionModalVisible = ref(false)
const editingAgentDefinition = ref<AgentDefinition | null>(null)
const roleTokenLimitModalVisible = ref(false)
const editingRoleTokenLimit = ref<RoleTokenLimit | null>(null)
const userTokenLimitModalVisible = ref(false)
const editingUserTokenLimit = ref<UserTokenLimit | null>(null)

const roleKeyword = ref('')
const userKeyword = ref('')
const userStatus = ref<'all' | 'enabled' | 'disabled'>('all')
const agentDefinitionKeyword = ref('')
const agentDefinitionStatus = ref<'all' | 'enabled' | 'disabled'>('all')
const roleTokenLimitKeyword = ref('')
const roleTokenLimitStatus = ref<'all' | 'enabled' | 'disabled'>('all')
const userTokenLimitKeyword = ref('')
const userTokenLimitStatus = ref<'all' | 'enabled' | 'disabled'>('all')

const activeSection = computed(() => sections.find((section) => section.key === activeSectionKey.value) || sections[0])

const filteredRoles = computed(() => {
  const keyword = roleKeyword.value.toLowerCase()
  return userStore.roles.filter((role) =>
    !keyword || [role.roleName, role.description].filter(Boolean).some((value) => String(value).toLowerCase().includes(keyword))
  )
})

const filteredUsers = computed(() => {
  const keyword = userKeyword.value.toLowerCase()
  return userStore.users.filter((user) => {
    const enabled = Boolean(user.enabled)
    const statusMatched =
      userStatus.value === 'all' ||
      (userStatus.value === 'enabled' && enabled) ||
      (userStatus.value === 'disabled' && !enabled)
    const keywordMatched =
      !keyword ||
      [user.userId, user.username, user.department, user.roles].filter(Boolean).some((value) => String(value).toLowerCase().includes(keyword))
    return statusMatched && keywordMatched
  })
})

const filteredAgentDefinitions = computed(() => {
  const keyword = agentDefinitionKeyword.value.toLowerCase()
  return userStore.agentDefinitions.filter((definition) => {
    const enabled = Boolean(definition.enabled)
    const statusMatched =
      agentDefinitionStatus.value === 'all' ||
      (agentDefinitionStatus.value === 'enabled' && enabled) ||
      (agentDefinitionStatus.value === 'disabled' && !enabled)
    const keywordMatched =
      !keyword ||
      [definition.agentCode, definition.agentName, definition.description, definition.allowedRoles]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    return statusMatched && keywordMatched
  })
})

const filteredRoleTokenLimits = computed(() => filterTokenLimits(userStore.roleTokenLimits, roleTokenLimitKeyword.value, roleTokenLimitStatus.value))
const filteredUserTokenLimits = computed(() => filterTokenLimits(userStore.userTokenLimits, userTokenLimitKeyword.value, userTokenLimitStatus.value))

function filterTokenLimits<T extends { roleName?: string; username?: string; department?: string; botType?: string; enabled: boolean }>(
  items: T[],
  keywordValue: string,
  statusValue: 'all' | 'enabled' | 'disabled'
) {
  const keyword = keywordValue.toLowerCase()
  return items.filter((item) => {
    const enabled = Boolean(item.enabled)
    const statusMatched =
      statusValue === 'all' ||
      (statusValue === 'enabled' && enabled) ||
      (statusValue === 'disabled' && !enabled)
    const keywordMatched =
      !keyword || [item.roleName, item.username, item.department, item.botType].filter(Boolean).some((value) => String(value).toLowerCase().includes(keyword))
    return statusMatched && keywordMatched
  })
}

function splitCsv(value?: string): string[] {
  if (!value) return []
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

function countUsersByRole(roleName: string) {
  return userStore.users.filter((user) => splitCsv(user.roles).includes(roleName)).length
}

function countAssistantsByRole(roleName: string) {
  return userStore.agentDefinitions.filter((definition) => splitCsv(definition.allowedRoles).includes(roleName)).length
}

function countRoleQuotaByRole(roleId: string) {
  return userStore.roleTokenLimits.filter((item) => item.roleId === roleId).length
}

function openPrimaryAction() {
  if (activeSectionKey.value === 'roles') return openRoleModal()
  if (activeSectionKey.value === 'users') return openUserModal()
  if (activeSectionKey.value === 'agent-definitions') return openAgentDefinitionModal()
  if (activeSectionKey.value === 'role-quotas') return openRoleTokenLimitModal()
  openUserTokenLimitModal()
}

function openRoleModal(role?: RoleOption) {
  editingRole.value = role || null
  roleModalVisible.value = true
}

function closeRoleModal() {
  roleModalVisible.value = false
  editingRole.value = null
}

async function openRoleUsage(role: RoleOption) {
  selectedRoleUsage.value = await getRoleUsage(role.id)
  roleUsageVisible.value = true
}

function closeRoleUsage() {
  roleUsageVisible.value = false
  selectedRoleUsage.value = null
}

function openUserModal(userId?: string) {
  editingUserId.value = userId || null
  userModalVisible.value = true
}

function closeUserModal() {
  userModalVisible.value = false
  editingUserId.value = null
}

function openAgentDefinitionModal(definition?: AgentDefinition) {
  editingAgentDefinition.value = definition || null
  agentDefinitionModalVisible.value = true
}

function closeAgentDefinitionModal() {
  agentDefinitionModalVisible.value = false
  editingAgentDefinition.value = null
}

function openRoleTokenLimitModal() {
  editingRoleTokenLimit.value = null
  roleTokenLimitModalVisible.value = true
}

function openRoleTokenLimitModalById(id: string) {
  editingRoleTokenLimit.value = userStore.roleTokenLimits.find((item) => item.id === id) || null
  roleTokenLimitModalVisible.value = true
}

function closeRoleTokenLimitModal() {
  roleTokenLimitModalVisible.value = false
  editingRoleTokenLimit.value = null
}

function openUserTokenLimitModal() {
  editingUserTokenLimit.value = null
  userTokenLimitModalVisible.value = true
}

function openUserTokenLimitModalById(id: string) {
  editingUserTokenLimit.value = userStore.userTokenLimits.find((item) => item.id === id) || null
  userTokenLimitModalVisible.value = true
}

function closeUserTokenLimitModal() {
  userTokenLimitModalVisible.value = false
  editingUserTokenLimit.value = null
}

function handleRoleSaved() {
  closeRoleModal()
  void userStore.loadRoles()
}

function handleUserSaved() {
  closeUserModal()
  void userStore.loadUsers()
}

function handleAgentDefinitionSaved() {
  closeAgentDefinitionModal()
  void userStore.loadAgentDefinitions()
}

function handleRoleTokenLimitSaved() {
  closeRoleTokenLimitModal()
  void userStore.loadRoleTokenLimits()
}

function handleUserTokenLimitSaved() {
  closeUserTokenLimitModal()
  void userStore.loadUserTokenLimits()
}

async function handleDeleteRole(role: RoleOption) {
  const usage = await getRoleUsage(role.id)
  if (usage.userCount > 0 || usage.permissionCount > 0) {
    showToast(`角色 ${role.roleName} 仍被引用，请先解除关联后再删除`)
    return
  }
  if (!(await confirm(`确认删除角色 ${role.roleName} 吗？`))) {
    return
  }
  const success = await userStore.deleteRole(role.id)
  if (!success) {
    showToast(userStore.roleError || '删除角色失败')
  }
}

async function handleDeleteUser(userId: string) {
  if (!(await confirm(`确认删除用户 ${userId} 吗？`))) {
    return
  }
  const success = await userStore.deleteUser(userId)
  if (!success) {
    showToast(userStore.userError || '删除用户失败')
  }
}

async function handleDeleteAgentDefinition(definition: AgentDefinition) {
  if (definition.systemDefined) {
    showToast(`特殊助手 ${definition.agentCode} 不允许删除`)
    return
  }
  if (!(await confirm(`确认删除助手 ${definition.agentCode} 吗？`))) {
    return
  }
  const success = await userStore.deleteAgentDefinition(definition.agentCode)
  if (!success) {
    showToast(userStore.agentDefinitionError || '删除助手失败')
  }
}

async function handleDeleteRoleTokenLimit(id: string) {
  if (!(await confirm('确认删除这条角色配额规则吗？'))) {
    return
  }
  const success = await userStore.deleteRoleTokenLimit(id)
  if (!success) {
    showToast(userStore.roleTokenLimitError || '删除角色配额规则失败')
  }
}

async function handleDeleteUserTokenLimit(id: string) {
  if (!(await confirm('确认删除这条用户配额规则吗？'))) {
    return
  }
  const success = await userStore.deleteUserTokenLimit(id)
  if (!success) {
    showToast(userStore.userTokenLimitError || '删除用户配额规则失败')
  }
}

onMounted(() => {
  void userStore.loadAll()
})
</script>

<style scoped>
.page-shell { display: grid; gap: 20px; }
.hero-card, .section-card { padding: 24px; border-radius: 24px; }
.hero-card { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; background: linear-gradient(135deg, rgba(14, 116, 144, 0.16), rgba(15, 23, 42, 0.94)); border: 1px solid rgba(125, 211, 252, 0.18); }
.eyebrow { color: #67e8f9; font-size: 12px; text-transform: uppercase; letter-spacing: 0.14em; }
.hero-card h1 { margin: 6px 0 10px; font-size: 28px; }
.hero-card p { max-width: 820px; color: var(--text3); line-height: 1.7; }
.section-card { background: rgba(15, 23, 42, 0.82); border: 1px solid rgba(148, 163, 184, 0.16); }
.section-tabs { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.section-tab { display: grid; gap: 6px; padding: 16px; border-radius: 18px; border: 1px solid rgba(148, 163, 184, 0.16); background: rgba(30, 41, 59, 0.46); text-align: left; }
.section-tab.active { border-color: rgba(56, 189, 248, 0.42); background: rgba(8, 47, 73, 0.76); }
.tab-title { color: var(--text); font-weight: 600; }
.tab-subtitle { color: var(--text3); font-size: 12px; }
.section-header, .role-top, .definition-head { display: flex; justify-content: space-between; gap: 16px; }
.section-header { align-items: flex-start; margin-bottom: 16px; }
.section-title { font-size: 18px; font-weight: 700; }
.section-subtitle { margin-top: 6px; color: var(--text3); line-height: 1.6; }
.toolbar { display: flex; gap: 12px; margin-bottom: 12px; }
.toolbar-input { flex: 1; }
.toolbar-select { width: 180px; }
.toolbar-meta { margin-bottom: 16px; color: var(--text3); font-size: 13px; }
.role-grid, .definition-grid { display: grid; gap: 14px; }
.role-card, .definition-card { padding: 18px; border-radius: 20px; background: rgba(30, 41, 59, 0.48); border: 1px solid rgba(148, 163, 184, 0.14); }
.role-name { font-size: 16px; font-weight: 700; }
.role-desc { margin-top: 6px; color: var(--text3); line-height: 1.6; }
.role-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.role-metrics { display: flex; gap: 14px; flex-wrap: wrap; margin-top: 14px; color: var(--text3); font-size: 13px; }
.definition-badge { width: 52px; height: 52px; border-radius: 16px; display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 700; flex: 0 0 52px; }
.definition-main { flex: 1; }
.definition-title-row { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
.definition-code { margin-top: 6px; color: #7dd3fc; font-size: 13px; }
.chips, .tag-list { display: flex; gap: 8px; flex-wrap: wrap; }
.chip { padding: 4px 10px; border-radius: 999px; background: rgba(34, 197, 94, 0.14); color: #bbf7d0; font-size: 12px; }
.chip.disabled { background: rgba(239, 68, 68, 0.14); color: #fecaca; }
.chip-special { background: rgba(249, 115, 22, 0.14); color: #fdba74; }
.tag { display: inline-flex; align-items: center; padding: 6px 10px; border-radius: 999px; background: rgba(148, 163, 184, 0.12); color: var(--text2); font-size: 12px; }
@media (max-width: 960px) { .section-tabs { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px) {
  .hero-card, .section-header, .role-top, .definition-head, .definition-title-row, .toolbar { flex-direction: column; }
  .section-tabs { grid-template-columns: 1fr; }
  .toolbar-select { width: 100%; }
}
</style>
