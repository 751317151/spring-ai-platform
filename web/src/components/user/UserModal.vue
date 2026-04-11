<template>
  <div class="modal-mask">
    <div class="modal-shell">
      <div class="modal-header">
        <div>
          <div class="modal-title">{{ isEdit ? '编辑用户' : '新建用户' }}</div>
          <div class="modal-subtitle">维护用户基础信息，并为用户分配角色与省市归属。</div>
        </div>
      </div>

      <div class="modal-body">
        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">用户 ID</label>
            <input
              v-model.trim="form.userId"
              class="form-input"
              :disabled="isEdit"
              placeholder="例如：admin / rd_user"
            >
          </div>
          <div class="modal-field">
            <label class="modal-label">用户名</label>
            <input v-model.trim="form.username" class="form-input" placeholder="请输入显示名称">
          </div>
        </div>

        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">{{ isEdit ? '新密码' : '登录密码' }}</label>
            <input
              v-model.trim="form.password"
              class="form-input"
              type="password"
              :placeholder="isEdit ? '留空表示不修改密码' : '请输入登录密码'"
            >
          </div>
          <div class="modal-field">
            <label class="modal-label">工号</label>
            <input v-model.trim="form.employeeId" class="form-input" placeholder="例如：EMP0001">
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">所属部门</label>
          <input v-model.trim="form.department" class="form-input" placeholder="例如：系统管理 / 研发中心">
        </div>

        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">省份</label>
            <input v-model.trim="form.province" class="form-input" placeholder="例如：北京 / 广东 / 浙江">
          </div>
          <div class="modal-field">
            <label class="modal-label">城市</label>
            <input v-model.trim="form.city" class="form-input" placeholder="例如：北京 / 深圳 / 杭州">
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">角色分配</label>
          <div class="role-grid">
            <label v-for="role in roleOptions" :key="role.roleName" class="role-option">
              <input v-model="selectedRoles" type="checkbox" :value="role.roleName">
              <div class="role-card" :class="{ active: selectedRoles.includes(role.roleName) }">
                <strong>{{ role.roleName }}</strong>
                <span>{{ role.description || '未填写角色说明' }}</span>
              </div>
            </label>
          </div>
        </div>

        <label class="switch-row">
          <input v-model="form.enabled" type="checkbox">
          <span>启用该用户</span>
        </label>
      </div>

      <div class="modal-actions">
        <button class="btn btn-ghost btn-sm" @click="emit('close')">取消</button>
        <button class="btn btn-primary btn-sm" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中...' : isEdit ? '保存修改' : '创建用户' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getUser } from '@/api/auth'
import type { RoleOption } from '@/api/types'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ userId: string | null }>()
const emit = defineEmits<{ close: []; saved: [userId: string] }>()

const userStore = useUserStore()
const { showToast } = useToast()
const saving = ref(false)
const selectedRoles = ref<string[]>([])
const form = reactive({
  userId: '',
  username: '',
  password: '',
  employeeId: '',
  department: '',
  province: '',
  city: '',
  enabled: true
})

const isEdit = computed(() => Boolean(props.userId))
const roleOptions = computed<RoleOption[]>(() => userStore.roles)

onMounted(async () => {
  if (!userStore.roles.length) {
    await userStore.loadRoles()
  }
  if (!props.userId) {
    return
  }

  try {
    const user = await getUser(props.userId)
    form.userId = user.userId || ''
    form.username = user.username || ''
    form.employeeId = user.employeeId || ''
    form.department = user.department || ''
    form.province = user.province || ''
    form.city = user.city || ''
    form.enabled = user.enabled !== false
    selectedRoles.value = splitCsv(user.roles)
  } catch (error) {
    showToast(error instanceof Error ? error.message : '用户详情加载失败')
  }
})

async function handleSave() {
  if (!form.userId.trim()) return showToast('用户 ID 不能为空')
  if (!form.username.trim()) return showToast('用户名不能为空')
  if (!isEdit.value && !form.password.trim()) return showToast('新建用户时必须填写密码')
  if (!selectedRoles.value.length) return showToast('请至少选择一个角色')

  saving.value = true
  const payload = {
    userId: form.userId.trim(),
    username: form.username.trim(),
    password: form.password.trim() || undefined,
    employeeId: form.employeeId.trim(),
    department: form.department.trim(),
    province: form.province.trim(),
    city: form.city.trim(),
    roles: selectedRoles.value.join(','),
    enabled: String(form.enabled)
  }
  const success = isEdit.value
    ? await userStore.updateUser(props.userId!, payload)
    : await userStore.createUser(payload)
  saving.value = false

  if (success) {
    emit('saved', form.userId.trim())
  } else {
    showToast(userStore.userError || '用户保存失败')
  }
}

function splitCsv(value?: string): string[] {
  if (!value) return []
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}
</script>

<style scoped>
.modal-mask { position: fixed; inset: 0; z-index: 1600; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(15, 23, 42, 0.56); backdrop-filter: blur(8px); }
.modal-shell { width: min(720px, 100%); max-height: calc(100vh - 40px); display: flex; flex-direction: column; overflow: hidden; border: 1px solid rgba(148, 163, 184, 0.24); border-radius: 24px; background: linear-gradient(180deg, rgba(15, 23, 42, 0.98), rgba(15, 23, 42, 0.92)); box-shadow: 0 24px 80px rgba(15, 23, 42, 0.36); }
.modal-header { margin-bottom: 18px; padding: 24px 24px 0; flex-shrink: 0; }
.modal-title { color: var(--text); font-size: 20px; font-weight: 700; }
.modal-subtitle { margin-top: 6px; color: var(--text3); font-size: 13px; }
.modal-body { display: grid; gap: 14px; overflow-y: auto; min-height: 0; padding: 0 24px 24px; }
.modal-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.modal-field { display: grid; gap: 8px; }
.modal-label { color: var(--text3); font-size: 12px; }
.role-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.role-option { display: block; cursor: pointer; }
.role-option input { display: none; }
.role-card { display: grid; gap: 6px; padding: 14px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 16px; background: rgba(15, 23, 42, 0.54); }
.role-card strong { color: var(--text); font-size: 13px; }
.role-card span { color: var(--text3); font-size: 12px; line-height: 1.6; }
.role-card.active { border-color: rgba(56, 189, 248, 0.48); background: rgba(8, 47, 73, 0.72); transform: translateY(-1px); }
.switch-row { display: inline-flex; align-items: center; gap: 10px; color: var(--text2); font-size: 13px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: auto; padding: 16px 24px 24px; flex-shrink: 0; border-top: 1px solid rgba(148, 163, 184, 0.12); background: linear-gradient(180deg, rgba(15, 23, 42, 0.72), rgba(15, 23, 42, 0.96)); }
@media (max-width: 720px) { .modal-mask { padding: 12px; } .modal-shell { max-height: calc(100vh - 24px); } .modal-grid, .role-grid { grid-template-columns: 1fr; } .modal-header, .modal-body, .modal-actions { padding-left: 16px; padding-right: 16px; } .modal-header { padding-top: 18px; } .modal-actions { padding-bottom: 18px; } }
</style>
