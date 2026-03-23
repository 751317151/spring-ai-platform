<template>
  <div>
    <div class="page-title">权限管理</div>

    <div class="card section-card">
      <div class="section-head">
        <div class="card-title">Bot 权限配置</div>
        <button class="btn btn-primary btn-sm" @click="showPermModal()">+ 新增权限</button>
      </div>

      <div class="filters">
        <input v-model="permissionKeyword" class="form-input filter-input" placeholder="搜索 Bot、角色、部门">
        <select v-model="permissionStatus" class="form-input filter-select">
          <option value="all">全部状态</option>
          <option value="enabled">仅启用</option>
          <option value="disabled">仅禁用</option>
        </select>
      </div>

      <PermissionTable :permissions="filteredPermissions" @edit="showPermModal" />
    </div>

    <div class="card">
      <div class="section-head">
        <div class="card-title">用户列表</div>
        <button class="btn btn-primary btn-sm" @click="showUserModal()">+ 新增用户</button>
      </div>

      <div class="filters">
        <input v-model="userKeyword" class="form-input filter-input" placeholder="搜索用户名、工号、部门、角色">
        <select v-model="userStatus" class="form-input filter-select">
          <option value="all">全部状态</option>
          <option value="enabled">仅启用</option>
          <option value="disabled">仅禁用</option>
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

      <UserTable :users="filteredUsers" @edit="showUserModal" @delete="handleDeleteUser" />
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
import { computed, onMounted, ref } from 'vue'
import UserTable from '@/components/user/UserTable.vue'
import PermissionTable from '@/components/user/PermissionTable.vue'
import UserModal from '@/components/user/UserModal.vue'
import PermissionModal from '@/components/user/PermissionModal.vue'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'

const userStore = useUserStore()
const { showToast } = useToast()

const userModalVisible = ref(false)
const editingUserId = ref<string | null>(null)
const permModalVisible = ref(false)
const editingPermId = ref<string | null>(null)
const userKeyword = ref('')
const userStatus = ref<'all' | 'enabled' | 'disabled'>('all')
const userRole = ref('all')
const permissionKeyword = ref('')
const permissionStatus = ref<'all' | 'enabled' | 'disabled'>('all')

const filteredUsers = computed(() => {
  const keyword = userKeyword.value.trim().toLowerCase()
  return userStore.users.filter((user) => {
    const enabled = user.enabled !== false
    const matchesStatus =
      userStatus.value === 'all' ||
      (userStatus.value === 'enabled' && enabled) ||
      (userStatus.value === 'disabled' && !enabled)
    const matchesRole = userRole.value === 'all' || (user.roles || '').split(',').map((it) => it.trim()).includes(userRole.value)
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
  showToast('权限配置保存成功')
}

async function handleDeleteUser(id: string, name: string) {
  if (!confirm(`确定删除用户 "${name}"？此操作不可恢复。`)) return
  const ok = await userStore.deleteUser(id)
  if (ok) {
    showToast('用户已删除')
  }
}

onMounted(() => {
  userStore.loadAll()
})
</script>

<style scoped>
.page-title {
  margin-bottom: 16px;
  font-size: 15px;
  font-weight: 500;
  color: var(--text);
}

.section-card {
  margin-bottom: 16px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
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
</style>
