<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal-shell">
      <div class="modal-header">
        <div>
          <div class="modal-title">{{ isEdit ? '编辑助手权限规则' : '新建助手权限规则' }}</div>
          <div class="modal-subtitle">定义哪些角色可以使用某个 AI 助手，以及可用的数据范围和操作范围。</div>
        </div>
      </div>

      <div class="modal-body">
        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">AI 助手</label>
            <input v-if="!isEdit" v-model.trim="form.botType" class="form-input" placeholder="例如：rd / multi / search">
            <div v-else class="readonly-box">{{ getBotLabel(form.botType) }}</div>
          </div>
          <div class="modal-field">
            <label class="modal-label">数据范围</label>
            <select v-model="form.dataScope" class="form-input">
              <option value="ALL">全部数据</option>
              <option value="DEPARTMENT">本部门</option>
              <option value="SELF">仅本人</option>
            </select>
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">允许角色</label>
          <div class="choice-grid">
            <label v-for="role in roleOptions" :key="role.roleName" class="choice-item">
              <input v-model="selectedRoles" type="checkbox" :value="role.roleName">
              <div class="choice-card" :class="{ active: selectedRoles.includes(role.roleName) }">
                <strong>{{ role.roleName }}</strong>
                <span>{{ role.description || '未填写角色说明' }}</span>
              </div>
            </label>
          </div>
        </div>

        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">允许部门</label>
            <input v-model.trim="form.allowedDepartments" class="form-input" placeholder="多个部门用英文逗号分隔，留空表示不限制">
          </div>
          <div class="modal-field">
            <label class="modal-label">默认每日 Token 配额</label>
            <input v-model.number="form.dailyTokenLimit" class="form-input" type="number" min="0" step="1000">
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">允许操作</label>
          <div class="operation-grid">
            <label v-for="operation in operationOptions" :key="operation" class="operation-chip">
              <input v-model="selectedOperations" type="checkbox" :value="operation">
              <span :class="{ active: selectedOperations.includes(operation) }">{{ operation }}</span>
            </label>
          </div>
        </div>

        <label class="switch-row">
          <input v-model="form.enabled" type="checkbox">
          <span>启用这条权限规则</span>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { getPermission } from '@/api/auth'
import type { RoleOption } from '@/api/types'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'

const BOT_LABELS: Record<string, string> = {
  rd: '研发助手',
  sales: '销售助手',
  hr: 'HR 助手',
  finance: '财务助手',
  'supply-chain': '供应链助手',
  qc: '质控助手',
  weather: '天气助手',
  search: '搜索助手',
  'data-analysis': '数据分析助手',
  code: '代码助手',
  mcp: 'MCP 助手',
  multi: '多智能体助手'
}

const operationOptions = ['READ', 'WRITE', 'APPROVE']
const props = defineProps<{ permissionId: string | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const userStore = useUserStore()
const { showToast } = useToast()
const saving = ref(false)
const selectedRoles = ref<string[]>([])
const selectedOperations = ref<string[]>(['READ', 'WRITE'])
const form = reactive({ botType: '', allowedDepartments: '', dataScope: 'DEPARTMENT', dailyTokenLimit: 100000, enabled: true })

const isEdit = computed(() => Boolean(props.permissionId))
const roleOptions = computed<RoleOption[]>(() => userStore.roles)

onMounted(async () => {
  if (!userStore.roles.length) {
    await userStore.loadRoles()
  }
  if (!props.permissionId) return

  try {
    const permission = await getPermission(props.permissionId)
    form.botType = permission.botType || ''
    form.allowedDepartments = permission.allowedDepartments || ''
    form.dataScope = permission.dataScope || 'DEPARTMENT'
    form.dailyTokenLimit = permission.dailyTokenLimit ?? 100000
    form.enabled = permission.enabled ?? true
    selectedRoles.value = splitCsv(permission.allowedRoles)
    selectedOperations.value = splitCsv(permission.allowedOperations)
  } catch (error) {
    showToast(error instanceof Error ? error.message : '权限规则详情加载失败')
  }
})

async function handleSave() {
  if (!form.botType.trim()) return showToast('AI 助手标识不能为空')
  if (!selectedRoles.value.length) return showToast('请至少选择一个角色')
  if (!selectedOperations.value.length) return showToast('请至少选择一个操作')

  saving.value = true
  const payload = {
    botType: form.botType.trim(),
    allowedRoles: selectedRoles.value.join(','),
    allowedDepartments: form.allowedDepartments.trim(),
    dataScope: form.dataScope,
    allowedOperations: selectedOperations.value.join(','),
    dailyTokenLimit: form.dailyTokenLimit,
    enabled: form.enabled
  }
  const success = isEdit.value ? await userStore.updatePermission(props.permissionId!, payload) : await userStore.createPermission(payload)
  saving.value = false

  if (success) {
    emit('saved')
  } else {
    showToast(userStore.permissionError || '权限规则保存失败')
  }
}

function splitCsv(value?: string): string[] {
  if (!value) return []
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

function getBotLabel(botType: string): string {
  return BOT_LABELS[botType] || botType
}
</script>

<style scoped>
.modal-mask { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(15, 23, 42, 0.56); backdrop-filter: blur(8px); }
.modal-shell { width: min(760px, 100%); border: 1px solid rgba(148, 163, 184, 0.24); border-radius: 24px; background: linear-gradient(180deg, rgba(15, 23, 42, 0.98), rgba(15, 23, 42, 0.92)); box-shadow: 0 24px 80px rgba(15, 23, 42, 0.36); padding: 24px; }
.modal-header { margin-bottom: 18px; }
.modal-title { color: var(--text); font-size: 20px; font-weight: 700; }
.modal-subtitle { margin-top: 6px; color: var(--text3); font-size: 13px; }
.modal-body { display: grid; gap: 14px; }
.modal-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.modal-field { display: grid; gap: 8px; }
.modal-label { color: var(--text3); font-size: 12px; }
.readonly-box { padding: 11px 14px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 14px; background: rgba(15, 23, 42, 0.58); color: var(--text2); }
.choice-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.choice-item { display: block; cursor: pointer; }
.choice-item input { display: none; }
.choice-card { display: grid; gap: 6px; padding: 14px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 16px; background: rgba(15, 23, 42, 0.54); }
.choice-card strong { color: var(--text); font-size: 13px; }
.choice-card span { color: var(--text3); font-size: 12px; line-height: 1.6; }
.choice-card.active { border-color: rgba(52, 211, 153, 0.48); background: rgba(6, 78, 59, 0.66); transform: translateY(-1px); }
.operation-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.operation-chip input { display: none; }
.operation-chip span { display: inline-flex; align-items: center; padding: 10px 14px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 999px; background: rgba(15, 23, 42, 0.54); color: var(--text2); font-size: 12px; cursor: pointer; }
.operation-chip span.active { border-color: rgba(56, 189, 248, 0.42); background: rgba(8, 47, 73, 0.68); color: var(--text); }
.switch-row { display: inline-flex; align-items: center; gap: 10px; color: var(--text2); font-size: 13px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
@media (max-width: 720px) { .modal-grid, .choice-grid { grid-template-columns: 1fr; } }
</style>
