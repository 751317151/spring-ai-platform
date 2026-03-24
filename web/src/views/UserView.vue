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
        <button class="btn btn-primary" @click="showPermModal()">新建权限</button>
        <button class="btn btn-ghost" @click="showUserModal()">新建用户</button>
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
      </div>

      <div v-if="userStore.loadingPermissions" class="section-loading">
        <div v-for="idx in 3" :key="idx" class="section-loading-row skeleton"></div>
      </div>
      <EmptyState
        v-else-if="userStore.permissionError"
        icon="P"
        title="权限规则加载失败"
        :description="userStore.permissionError"
        action-text="重新加载"
        @action="userStore.loadPermissions()"
      />
      <PermissionTable v-else :permissions="filteredPermissions" @edit="showPermModal" />
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
        <input v-model="userKeyword" class="form-input filter-input" placeholder="搜索用户名、工号、部门或角色">
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
      </div>

      <div v-if="selectedUserIds.length" class="bulk-toolbar">
        <div class="bulk-info">已选择 {{ selectedUserIds.length }} 个用户</div>
        <div class="bulk-actions">
          <button class="btn btn-ghost btn-sm" @click="selectedUserIds = []">清空选择</button>
          <button class="btn btn-danger btn-sm" @click="handleBatchDeleteUsers">批量删除</button>
        </div>
      </div>

      <div v-if="userStore.loadingUsers" class="section-loading">
        <div v-for="idx in 4" :key="idx" class="section-loading-row skeleton"></div>
      </div>
      <EmptyState
        v-else-if="userStore.userError"
        icon="U"
        title="用户加载失败"
        :description="userStore.userError"
        action-text="重新加载"
        @action="userStore.loadUsers()"
      />
      <UserTable
        v-else
        :users="filteredUsers"
        :selected-ids="selectedUserIds"
        @update:selected-ids="selectedUserIds = $event"
        @edit="showUserModal"
        @delete="handleDeleteUser"
      />
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
import { computed, onMounted, ref, watch } from 'vue'
import UserTable from '@/components/user/UserTable.vue'
import PermissionTable from '@/components/user/PermissionTable.vue'
import UserModal from '@/components/user/UserModal.vue'
import PermissionModal from '@/components/user/PermissionModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'

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
      [user.username, user.employeeId, user.department, user.roles]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    return matchesStatus && matchesRole && matchesKeyword
  })
})

const filteredPermissions = computed(() => {
  const keyword = permissionKeyword.value.trim().toLowerCase()
  return userStore.permissions.filter((permission) => {
    const matchesStatus =
      permissionStatus.value === 'all' ||
      (permissionStatus.value === 'enabled' && permission.enabled) ||
      (permissionStatus.value === 'disabled' && !permission.enabled)
    const matchesKeyword =
      !keyword ||
      [permission.botType, permission.allowedRoles, permission.allowedDepartments, permission.allowedOperations]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    return matchesStatus && matchesKeyword
  })
})

watch(filteredUsers, (users) => {
  const allowed = new Set(users.map((user) => user.id))
  selectedUserIds.value = selectedUserIds.value.filter((id) => allowed.has(id))
})

function showUserModal(userId?: string) {
  editingUserId.value = userId || null
  userModalVisible.value = true
}

function showPermModal(permId?: string) {
  editingPermId.value = permId || null
  permModalVisible.value = true
}

function handleUserSaved() {
  userModalVisible.value = false
  showToast('用户保存成功')
}

function handlePermSaved() {
  permModalVisible.value = false
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
    showToast('用户已删除')
  }
}

async function handleBatchDeleteUsers() {
  const names = filteredUsers.value
    .filter((user) => selectedUserIds.value.includes(user.id))
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
  showToast(
    successCount === results.length
      ? `已删除 ${successCount} 个用户`
      : `已删除 ${successCount} 个用户，部分失败`
  )
}

onMounted(() => {
  userStore.loadAll()
})
</script>

<style scoped>
.section-card {
  margin-bottom: 16px;
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

.bulk-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  background: var(--surface2);
  border-radius: 12px;
}

.bulk-info {
  font-size: 13px;
  color: var(--text);
  font-weight: 500;
}

.bulk-actions {
  display: flex;
  gap: 8px;
}

.section-loading {
  display: grid;
  gap: 10px;
}

.section-loading-row {
  height: 52px;
  border-radius: 12px;
}
</style>
