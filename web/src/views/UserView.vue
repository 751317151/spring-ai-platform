<template>
  <div>
    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px">
      <div style="font-size: 15px; font-weight: 500; color: var(--text)">权限管理</div>
      <button class="btn btn-primary btn-sm" @click="showModal()">+ 新增用户</button>
    </div>

    <div class="card" style="margin-bottom: 16px">
      <div class="card-title">Bot 权限配置</div>
      <PermissionTable :permissions="userStore.permissions" />
    </div>

    <div class="card">
      <div class="card-title">用户列表</div>
      <UserTable :users="userStore.users" @edit="showModal" />
    </div>

    <UserModal
      v-if="modalVisible"
      :user-id="editingUserId"
      @close="modalVisible = false"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import UserTable from '@/components/user/UserTable.vue'
import PermissionTable from '@/components/user/PermissionTable.vue'
import UserModal from '@/components/user/UserModal.vue'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'

const userStore = useUserStore()
const { showToast } = useToast()

const modalVisible = ref(false)
const editingUserId = ref<string | null>(null)

function showModal(userId?: string) {
  editingUserId.value = userId || null
  modalVisible.value = true
}

function handleSaved() {
  modalVisible.value = false
  showToast('保存成功')
}

onMounted(() => {
  userStore.loadAll()
})
</script>
