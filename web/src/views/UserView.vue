<template>
  <div>
    <div style="margin-bottom: 16px">
      <div style="font-size: 15px; font-weight: 500; color: var(--text)">权限管理</div>
    </div>

    <div class="card" style="margin-bottom: 16px">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px">
        <div class="card-title" style="margin-bottom: 0">Bot 权限配置</div>
        <button class="btn btn-primary btn-sm" @click="showPermModal()">+ 新增权限</button>
      </div>
      <PermissionTable :permissions="userStore.permissions" @edit="showPermModal" />
    </div>

    <div class="card">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px">
        <div class="card-title" style="margin-bottom: 0">用户列表</div>
        <button class="btn btn-primary btn-sm" @click="showUserModal()">+ 新增用户</button>
      </div>
      <UserTable :users="userStore.users" @edit="showUserModal" @delete="handleDeleteUser" />
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
import { ref, onMounted } from 'vue'
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
