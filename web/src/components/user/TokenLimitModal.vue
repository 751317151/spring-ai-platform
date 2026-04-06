<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal-shell">
      <div class="modal-header">
        <div>
          <div class="modal-title">{{ isEdit ? `编辑${targetLabel}配额规则` : `新建${targetLabel}配额规则` }}</div>
          <div class="modal-subtitle">
            更具体的规则优先生效：用户助手专属配额 > 用户总配额 > 角色助手专属配额 > 角色总配额 > 助手默认配额。
          </div>
        </div>
      </div>

      <div class="modal-body">
        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">{{ targetLabel }}</label>
            <select v-model="selectedTarget" class="form-input">
              <option value="" disabled>请选择{{ targetLabel }}</option>
              <option v-for="option in options" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>
          <div class="modal-field">
            <label class="modal-label">助手范围</label>
            <select v-model="form.botType" class="form-input">
              <option value="">全部助手</option>
              <option v-for="bot in botOptions" :key="bot.value" :value="bot.value">
                {{ bot.label }}
              </option>
            </select>
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">每日 Token 配额</label>
          <input v-model.number="form.dailyTokenLimit" class="form-input" type="number" min="0" step="1000">
        </div>

        <label class="switch-row">
          <input v-model="form.enabled" type="checkbox">
          <span>启用这条配额规则</span>
        </label>
      </div>

      <div class="modal-actions">
        <button class="btn btn-ghost btn-sm" @click="emit('close')">取消</button>
        <button class="btn btn-primary btn-sm" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中...' : isEdit ? '保存修改' : '创建规则' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { RoleTokenLimit, UserTokenLimit } from '@/api/types'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'

type TokenLimitItem = RoleTokenLimit | UserTokenLimit

interface SelectOption {
  value: string
  label: string
}

const BOT_OPTIONS: SelectOption[] = [
  { value: 'rd', label: '研发助手' },
  { value: 'sales', label: '销售助手' },
  { value: 'hr', label: 'HR 助手' },
  { value: 'finance', label: '财务助手' },
  { value: 'supply-chain', label: '供应链助手' },
  { value: 'qc', label: '质控助手' },
  { value: 'weather', label: '天气助手' },
  { value: 'search', label: '搜索助手' },
  { value: 'data-analysis', label: '数据分析助手' },
  { value: 'code', label: '代码助手' },
  { value: 'mcp', label: 'MCP 助手' },
  { value: 'multi', label: '多智能体助手' }
]

const props = defineProps<{
  targetType: 'role' | 'user'
  editItem: TokenLimitItem | null
}>()

const emit = defineEmits<{ close: []; saved: [] }>()

const userStore = useUserStore()
const { showToast } = useToast()

const saving = ref(false)
const selectedTarget = ref('')
const form = reactive({
  botType: '',
  dailyTokenLimit: 100000,
  enabled: true
})

const targetLabel = computed(() => (props.targetType === 'role' ? '角色' : '用户'))
const isEdit = computed(() => Boolean(props.editItem))
const botOptions = BOT_OPTIONS

const options = computed<SelectOption[]>(() => {
  if (props.targetType === 'role') {
    return userStore.roles.map((role) => ({
      value: role.id,
      label: `${role.roleName}${role.description ? ` · ${role.description}` : ''}`
    }))
  }
  return userStore.users.map((user) => ({
    value: user.userId,
    label: `${user.username || user.userId}${user.department ? ` · ${user.department}` : ''}`
  }))
})

watch(
  () => props.editItem,
  (item) => {
    if (!item) {
      selectedTarget.value = ''
      form.botType = ''
      form.dailyTokenLimit = 100000
      form.enabled = true
      return
    }
    if (props.targetType === 'role') {
      selectedTarget.value = (item as RoleTokenLimit).roleId
    } else {
      selectedTarget.value = (item as UserTokenLimit).userId
    }
    form.botType = item.botType || ''
    form.dailyTokenLimit = item.dailyTokenLimit ?? 100000
    form.enabled = item.enabled ?? true
  },
  { immediate: true }
)

onMounted(async () => {
  if (!userStore.roles.length) {
    await userStore.loadRoles()
  }
  if (!userStore.users.length) {
    await userStore.loadUsers()
  }
})

async function handleSave() {
  if (!selectedTarget.value) {
    showToast(`请选择${targetLabel.value}`)
    return
  }
  if (form.dailyTokenLimit < 0 || Number.isNaN(form.dailyTokenLimit)) {
    showToast('每日 Token 配额不能小于 0')
    return
  }

  saving.value = true
  const success = props.targetType === 'role'
    ? await saveRoleTokenLimit()
    : await saveUserTokenLimit()
  saving.value = false

  if (success) {
    emit('saved')
    return
  }

  showToast(
    props.targetType === 'role'
      ? userStore.roleTokenLimitError || '角色配额规则保存失败'
      : userStore.userTokenLimitError || '用户配额规则保存失败'
  )
}

async function saveRoleTokenLimit(): Promise<boolean> {
  const payload = {
    roleId: selectedTarget.value,
    botType: form.botType || undefined,
    dailyTokenLimit: form.dailyTokenLimit,
    enabled: form.enabled
  }
  if (isEdit.value) {
    return userStore.updateRoleTokenLimit((props.editItem as RoleTokenLimit).id, payload)
  }
  return userStore.createRoleTokenLimit(payload)
}

async function saveUserTokenLimit(): Promise<boolean> {
  const payload = {
    userId: selectedTarget.value,
    botType: form.botType || undefined,
    dailyTokenLimit: form.dailyTokenLimit,
    enabled: form.enabled
  }
  if (isEdit.value) {
    return userStore.updateUserTokenLimit((props.editItem as UserTokenLimit).id, payload)
  }
  return userStore.createUserTokenLimit(payload)
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.56);
  backdrop-filter: blur(8px);
}

.modal-shell {
  width: min(620px, 100%);
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.98), rgba(15, 23, 42, 0.92));
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.36);
  padding: 24px;
}

.modal-header {
  margin-bottom: 18px;
}

.modal-title {
  color: var(--text);
  font-size: 20px;
  font-weight: 700;
}

.modal-subtitle {
  margin-top: 6px;
  color: var(--text3);
  font-size: 13px;
  line-height: 1.7;
}

.modal-body {
  display: grid;
  gap: 14px;
}

.modal-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.modal-field {
  display: grid;
  gap: 8px;
}

.modal-label {
  color: var(--text3);
  font-size: 12px;
}

.switch-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text2);
  font-size: 13px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

@media (max-width: 720px) {
  .modal-grid {
    grid-template-columns: 1fr;
  }
}
</style>
