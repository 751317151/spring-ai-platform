<template>
  <div class="page-shell">
    <section class="hero-card">
      <div>
        <div class="eyebrow">RBAC 权限管理</div>
        <h1>用户、角色、助手权限与 Token 配额</h1>
        <p>
          这一页集中管理角色目录、用户与角色、助手权限、角色配额、用户配额。
          配额优先级为：用户助手专属 > 用户总配额 > 角色助手专属 > 角色总配额 > 助手默认配额。
        </p>
      </div>
      <div class="hero-actions">
        <button class="btn btn-primary" @click="openPrimaryAction">{{ activeSection.primaryActionText }}</button>
      </div>
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
          <div class="section-subtitle">维护角色定义，并查看角色被哪些用户和助手规则引用。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="openRoleModal()">新建角色</button>
      </div>
      <div class="toolbar">
        <input v-model.trim="roleKeyword" class="form-input toolbar-input" placeholder="搜索角色编码或角色说明">
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
            <span>{{ countPermissionsByRole(role.roleName) }} 条助手规则</span>
            <span>{{ countRoleQuotaByRole(role.id) }} 条角色配额</span>
          </div>
        </article>
      </div>
    </section>

    <section v-if="activeSectionKey === 'users'" class="card section-card">
      <div class="section-header">
        <div>
          <div class="section-title">用户与角色</div>
          <div class="section-subtitle">用户登录后先获得角色，再由角色决定可访问的 AI 助手能力。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="openUserModal()">新建用户</button>
      </div>
      <div class="toolbar">
        <input v-model.trim="userKeyword" class="form-input toolbar-input" placeholder="搜索用户 ID、用户名、部门或角色">
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

    <section v-if="activeSectionKey === 'permissions'" class="card section-card">
      <div class="section-header">
        <div>
          <div class="section-title">角色与助手权限</div>
          <div class="section-subtitle">定义哪些角色可以访问哪些 AI 助手，以及对应的数据范围和操作范围。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="openPermissionModal()">新建规则</button>
      </div>
      <div class="toolbar">
        <input v-model.trim="permissionKeyword" class="form-input toolbar-input" placeholder="搜索助手、角色、部门或操作范围">
        <select v-model="permissionStatus" class="form-input toolbar-select">
          <option value="all">全部状态</option>
          <option value="enabled">仅启用</option>
          <option value="disabled">仅停用</option>
        </select>
      </div>
      <div class="toolbar-meta">共 {{ filteredPermissions.length }} / {{ userStore.permissions.length }} 条规则</div>
      <EmptyState
        v-if="userStore.loadingPermissions"
        icon="P"
        title="正在加载助手权限规则"
        description="请稍候。"
        variant="compact"
      />
      <EmptyState
        v-else-if="userStore.permissionError"
        icon="P"
        title="助手权限规则加载失败"
        :description="userStore.permissionError"
        action-text="重新加载"
        variant="compact"
        @action="userStore.loadPermissions()"
      />
      <PermissionTable
        v-else
        :permissions="filteredPermissions"
        @edit="openPermissionModal"
        @delete="handleDeletePermission"
      />
    </section>

    <section v-if="activeSectionKey === 'role-quotas'" class="card section-card">
      <div class="section-header">
        <div>
          <div class="section-title">角色 Token 配额</div>
          <div class="section-subtitle">给角色设置总配额或指定助手配额；同一用户拥有多个角色时，系统取最小值。</div>
        </div>
        <button class="btn btn-primary btn-sm" @click="openRoleTokenLimitModal()">新建角色配额</button>
      </div>
      <div class="toolbar">
        <input v-model.trim="roleTokenLimitKeyword" class="form-input toolbar-input" placeholder="搜索角色名或助手范围">
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
          <div class="section-subtitle">给特定用户设置专属配额，用于覆盖角色级或助手默认配额。</div>
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
    <PermissionModal
      v-if="permissionModalVisible"
      :permission-id="editingPermissionId"
      @close="closePermissionModal"
      @saved="handlePermissionSaved"
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
import type { RoleOption, RoleTokenLimit, RoleUsage, UserTokenLimit } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import PermissionModal from '@/components/user/PermissionModal.vue'
import PermissionTable from '@/components/user/PermissionTable.vue'
import RoleModal from '@/components/user/RoleModal.vue'
import RoleUsageDrawer from '@/components/user/RoleUsageDrawer.vue'
import TokenLimitModal from '@/components/user/TokenLimitModal.vue'
import TokenLimitTable from '@/components/user/TokenLimitTable.vue'
import UserModal from '@/components/user/UserModal.vue'
import UserTable from '@/components/user/UserTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'

type SectionKey = 'roles' | 'users' | 'permissions' | 'role-quotas' | 'user-quotas'

const userStore = useUserStore()
const { confirm } = useConfirm()
const { showToast } = useToast()

const sections = [
  { key: 'roles' as SectionKey, title: '角色目录', subtitle: '查看角色定义和引用情况', primaryActionText: '新建角色' },
  { key: 'users' as SectionKey, title: '用户与角色', subtitle: '维护用户账号和角色分配', primaryActionText: '新建用户' },
  { key: 'permissions' as SectionKey, title: '助手权限', subtitle: '配置角色访问 AI 助手的规则', primaryActionText: '新建规则' },
  { key: 'role-quotas' as SectionKey, title: '角色配额', subtitle: '配置角色级 Token 配额', primaryActionText: '新建角色配额' },
  { key: 'user-quotas' as SectionKey, title: '用户配额', subtitle: '配置用户级 Token 配额', primaryActionText: '新建用户配额' }
]

const activeSectionKey = ref<SectionKey>('roles')
const roleModalVisible = ref(false)
const editingRole = ref<RoleOption | null>(null)
const roleUsageVisible = ref(false)
const selectedRoleUsage = ref<RoleUsage | null>(null)
const userModalVisible = ref(false)
const editingUserId = ref<string | null>(null)
const permissionModalVisible = ref(false)
const editingPermissionId = ref<string | null>(null)
const roleTokenLimitModalVisible = ref(false)
const editingRoleTokenLimit = ref<RoleTokenLimit | null>(null)
const userTokenLimitModalVisible = ref(false)
const editingUserTokenLimit = ref<UserTokenLimit | null>(null)

const roleKeyword = ref('')
const userKeyword = ref('')
const userStatus = ref<'all' | 'enabled' | 'disabled'>('all')
const permissionKeyword = ref('')
const permissionStatus = ref<'all' | 'enabled' | 'disabled'>('all')
const roleTokenLimitKeyword = ref('')
const roleTokenLimitStatus = ref<'all' | 'enabled' | 'disabled'>('all')
const userTokenLimitKeyword = ref('')
const userTokenLimitStatus = ref<'all' | 'enabled' | 'disabled'>('all')

const activeSection = computed(() => sections.find((section) => section.key === activeSectionKey.value) || sections[0])

const filteredRoles = computed(() => {
  const keyword = roleKeyword.value.toLowerCase()
  return userStore.roles.filter((role) =>
    !keyword ||
    [role.roleName, role.description].filter(Boolean).some((value) => String(value).toLowerCase().includes(keyword))
  )
})

const filteredUsers = computed(() => {
  const keyword = userKeyword.value.toLowerCase()
  return userStore.users.filter((user) => {
    const matchesKeyword =
      !keyword ||
      [user.userId, user.username, user.department, user.roles]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))

    const enabled = user.enabled !== false
    const matchesStatus =
      userStatus.value === 'all' ||
      (userStatus.value === 'enabled' && enabled) ||
      (userStatus.value === 'disabled' && !enabled)

    return matchesKeyword && matchesStatus
  })
})

const filteredPermissions = computed(() => {
  const keyword = permissionKeyword.value.toLowerCase()
  return userStore.permissions.filter((permission) => {
    const matchesKeyword =
      !keyword ||
      [permission.botType, permission.allowedRoles, permission.allowedDepartments, permission.allowedOperations]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))

    const matchesStatus =
      permissionStatus.value === 'all' ||
      (permissionStatus.value === 'enabled' && permission.enabled) ||
      (permissionStatus.value === 'disabled' && !permission.enabled)

    return matchesKeyword && matchesStatus
  })
})

const filteredRoleTokenLimits = computed(() => {
  const keyword = roleTokenLimitKeyword.value.toLowerCase()
  return userStore.roleTokenLimits.filter((item) => {
    const matchesKeyword =
      !keyword ||
      [item.roleName, item.roleDescription, item.botType]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    const matchesStatus =
      roleTokenLimitStatus.value === 'all' ||
      (roleTokenLimitStatus.value === 'enabled' && item.enabled) ||
      (roleTokenLimitStatus.value === 'disabled' && !item.enabled)
    return matchesKeyword && matchesStatus
  })
})

const filteredUserTokenLimits = computed(() => {
  const keyword = userTokenLimitKeyword.value.toLowerCase()
  return userStore.userTokenLimits.filter((item) => {
    const matchesKeyword =
      !keyword ||
      [item.userId, item.username, item.department, item.botType]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    const matchesStatus =
      userTokenLimitStatus.value === 'all' ||
      (userTokenLimitStatus.value === 'enabled' && item.enabled) ||
      (userTokenLimitStatus.value === 'disabled' && !item.enabled)
    return matchesKeyword && matchesStatus
  })
})

onMounted(() => {
  userStore.loadAll()
})

function openPrimaryAction() {
  if (activeSectionKey.value === 'roles') return openRoleModal()
  if (activeSectionKey.value === 'users') return openUserModal()
  if (activeSectionKey.value === 'permissions') return openPermissionModal()
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
  try {
    selectedRoleUsage.value = await getRoleUsage(role.id)
    roleUsageVisible.value = true
  } catch (error) {
    showToast(error instanceof Error ? error.message : '角色引用详情加载失败')
  }
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

function openPermissionModal(permissionId?: string) {
  editingPermissionId.value = permissionId || null
  permissionModalVisible.value = true
}

function closePermissionModal() {
  permissionModalVisible.value = false
  editingPermissionId.value = null
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
  showToast('角色保存成功')
}

function handleUserSaved(userId: string) {
  closeUserModal()
  showToast(`用户 ${userId} 保存成功`)
}

function handlePermissionSaved() {
  closePermissionModal()
  showToast('助手权限规则保存成功')
}

function handleRoleTokenLimitSaved() {
  closeRoleTokenLimitModal()
  showToast('角色配额规则保存成功')
}

function handleUserTokenLimitSaved() {
  closeUserTokenLimitModal()
  showToast('用户配额规则保存成功')
}

async function handleDeleteRole(role: RoleOption) {
  try {
    const usage = await getRoleUsage(role.id)
    if (usage.userCount > 0 || usage.permissionCount > 0 || countRoleQuotaByRole(role.id) > 0) {
      selectedRoleUsage.value = usage
      roleUsageVisible.value = true
      showToast(`角色 ${role.roleName} 仍被引用，请先解除关联后再删除`)
      return
    }
  } catch (error) {
    showToast(error instanceof Error ? error.message : '角色引用信息加载失败')
    return
  }

  const accepted = await confirm({
    title: '删除角色',
    description: `确认删除角色“${role.roleName}”吗？`,
    confirmText: '删除',
    intent: 'danger'
  })
  if (!accepted) return

  const success = await userStore.deleteRole(role.id)
  showToast(success ? `角色 ${role.roleName} 已删除` : userStore.roleError || '删除角色失败')
}

async function handleDeleteUser(userId: string, username: string) {
  const accepted = await confirm({
    title: '删除用户',
    description: `确认删除用户“${username || userId}”吗？`,
    confirmText: '删除',
    intent: 'danger'
  })
  if (!accepted) return

  const success = await userStore.deleteUser(userId)
  showToast(success ? `用户 ${username || userId} 已删除` : userStore.userError || '删除用户失败')
}

async function handleDeletePermission(id: string, botType: string) {
  const accepted = await confirm({
    title: '删除助手权限规则',
    description: `确认删除助手“${botType}”的权限规则吗？`,
    confirmText: '删除',
    intent: 'danger'
  })
  if (!accepted) return

  const success = await userStore.deletePermission(id)
  showToast(success ? `助手 ${botType} 的权限规则已删除` : userStore.permissionError || '删除助手权限规则失败')
}

async function handleDeleteRoleTokenLimit(id: string, label: string) {
  const accepted = await confirm({
    title: '删除角色配额规则',
    description: `确认删除“${label}”的角色配额规则吗？`,
    confirmText: '删除',
    intent: 'danger'
  })
  if (!accepted) return

  const success = await userStore.deleteRoleTokenLimit(id)
  showToast(success ? `角色 ${label} 的配额规则已删除` : userStore.roleTokenLimitError || '删除角色配额规则失败')
}

async function handleDeleteUserTokenLimit(id: string, label: string) {
  const accepted = await confirm({
    title: '删除用户配额规则',
    description: `确认删除“${label}”的用户配额规则吗？`,
    confirmText: '删除',
    intent: 'danger'
  })
  if (!accepted) return

  const success = await userStore.deleteUserTokenLimit(id)
  showToast(success ? `用户 ${label} 的配额规则已删除` : userStore.userTokenLimitError || '删除用户配额规则失败')
}

function countUsersByRole(roleName: string): number {
  return userStore.users.filter((user) => splitCsv(user.roles).includes(roleName)).length
}

function countPermissionsByRole(roleName: string): number {
  return userStore.permissions.filter((permission) => splitCsv(permission.allowedRoles).includes(roleName)).length
}

function countRoleQuotaByRole(roleId: string): number {
  return userStore.roleTokenLimits.filter((item) => item.roleId === roleId).length
}

function splitCsv(value?: string): string[] {
  if (!value) return []
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}
</script>

<style scoped>
.page-shell { display: grid; gap: 20px; }
.hero-card {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(56, 189, 248, 0.16), transparent 32%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.96), rgba(15, 23, 42, 0.84));
}
.hero-card h1 { margin: 6px 0 10px; color: var(--text); font-size: 28px; }
.hero-card p { max-width: 760px; color: var(--text3); line-height: 1.8; }
.eyebrow { color: #38bdf8; font-size: 12px; font-weight: 700; letter-spacing: 0.1em; }
.hero-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.section-card { padding: 20px; }
.section-tabs { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.section-tab {
  display: grid; gap: 4px; text-align: left; padding: 16px; border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px; background: rgba(15, 23, 42, 0.36); cursor: pointer;
}
.section-tab.active { border-color: rgba(56, 189, 248, 0.34); background: rgba(8, 47, 73, 0.6); transform: translateY(-1px); }
.tab-title { color: var(--text); font-size: 14px; font-weight: 700; }
.tab-subtitle { color: var(--text3); font-size: 12px; line-height: 1.6; }
.section-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.section-title { color: var(--text); font-size: 18px; font-weight: 700; }
.section-subtitle { margin-top: 6px; color: var(--text3); font-size: 13px; line-height: 1.7; }
.role-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 14px; }
.role-card { display: grid; gap: 12px; padding: 18px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 20px; background: rgba(15, 23, 42, 0.42); }
.role-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.role-name { color: var(--text); font-size: 16px; font-weight: 700; }
.role-desc { margin-top: 6px; color: var(--text3); font-size: 13px; line-height: 1.7; }
.role-actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.role-metrics { display: flex; gap: 10px; flex-wrap: wrap; color: var(--text2); font-size: 12px; }
.role-metrics span { padding: 6px 10px; border-radius: 999px; background: rgba(148, 163, 184, 0.12); }
.toolbar { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.toolbar-input { flex: 1; min-width: 240px; }
.toolbar-select { min-width: 136px; }
.toolbar-meta { margin-bottom: 12px; color: var(--text3); font-size: 12px; }
.table-btn {
  border: 1px solid rgba(148, 163, 184, 0.18); border-radius: 10px; background: rgba(15, 23, 42, 0.46);
  color: var(--text2); padding: 7px 12px; font-size: 12px; cursor: pointer;
}
.table-btn.danger { color: #fda4af; }
@media (max-width: 1280px) { .section-tabs { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px) {
  .hero-card, .section-header, .role-top { flex-direction: column; align-items: stretch; }
  .section-tabs { grid-template-columns: 1fr; }
}
</style>
