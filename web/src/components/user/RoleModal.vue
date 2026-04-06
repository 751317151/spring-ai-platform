<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal-shell">
      <div class="modal-header">
        <div>
          <div class="modal-title">{{ role ? '编辑角色' : '新建角色' }}</div>
          <div class="modal-subtitle">角色编码建议使用 `ROLE_` 前缀，角色说明用于前端展示和权限理解。</div>
        </div>
      </div>

      <div class="modal-body">
        <div class="modal-field">
          <label class="modal-label">角色编码</label>
          <input v-model.trim="form.roleName" class="form-input" placeholder="例如：ROLE_SUPPORT">
        </div>
        <div class="modal-field">
          <label class="modal-label">角色说明</label>
          <input v-model.trim="form.description" class="form-input" placeholder="例如：技术支持">
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn btn-ghost btn-sm" @click="emit('close')">取消</button>
        <button class="btn btn-primary btn-sm" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中...' : role ? '保存修改' : '创建角色' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { RoleOption } from '@/api/types'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ role: RoleOption | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const userStore = useUserStore()
const { showToast } = useToast()
const saving = ref(false)

const form = reactive({
  roleName: '',
  description: ''
})

watch(
  () => props.role,
  (role) => {
    form.roleName = role?.roleName || ''
    form.description = role?.description || ''
  },
  { immediate: true }
)

async function handleSave() {
  if (!form.roleName.trim()) {
    showToast('角色编码不能为空')
    return
  }

  saving.value = true
  const payload = {
    roleName: form.roleName.trim(),
    description: form.description.trim()
  }

  const success = props.role
    ? await userStore.updateRole(props.role.id, payload)
    : await userStore.createRole(payload)

  saving.value = false
  if (success) {
    emit('saved')
  } else {
    showToast(userStore.roleError || '角色保存失败')
  }
}
</script>

<style scoped>
.modal-mask { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(15, 23, 42, 0.56); backdrop-filter: blur(8px); }
.modal-shell { width: min(560px, 100%); border: 1px solid rgba(148, 163, 184, 0.24); border-radius: 24px; background: linear-gradient(180deg, rgba(15, 23, 42, 0.98), rgba(15, 23, 42, 0.92)); box-shadow: 0 24px 80px rgba(15, 23, 42, 0.36); padding: 24px; }
.modal-header { margin-bottom: 18px; }
.modal-title { color: var(--text); font-size: 20px; font-weight: 700; }
.modal-subtitle { margin-top: 6px; color: var(--text3); font-size: 13px; line-height: 1.7; }
.modal-body { display: grid; gap: 14px; }
.modal-field { display: grid; gap: 8px; }
.modal-label { color: var(--text3); font-size: 12px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
</style>
