<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal-shell permission-shell">
      <div class="modal-header">
        <div>
          <div class="modal-title">{{ permissionId ? '编辑助手权限' : '新建助手权限' }}</div>
          <div class="modal-subtitle">定义该助手可访问的角色、部门、操作类型和数据范围。</div>
        </div>
      </div>

      <div class="modal-body">
        <div class="modal-field">
          <label class="modal-label">助手类型</label>
          <input
            v-if="!permissionId"
            class="form-input"
            v-model="form.botType"
            placeholder="例如：rd / sales / hr"
          >
          <div v-else class="readonly-field">
            {{ botLabels[form.botType] || form.botType }}
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">允许角色</label>
          <div class="choice-grid">
            <label v-for="role in availableRoles" :key="role" class="choice-item">
              <input type="checkbox" :value="role" v-model="selectedRoles">
              <span class="pill" :class="[roleColors[role] || 'blue', { 'pill-inactive': !selectedRoles.includes(role) }]">
                {{ role.replace('ROLE_', '') }}
              </span>
            </label>
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">允许部门</label>
          <input class="form-input" v-model="form.allowedDepartments" placeholder="例如：研发中心,销售团队">
          <div class="modal-help">留空表示允许全部部门。</div>
        </div>

        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">数据范围</label>
            <select class="form-input" v-model="form.dataScope">
              <option value="ALL">全部数据</option>
              <option value="DEPARTMENT">仅本部门</option>
              <option value="SELF">仅本人</option>
            </select>
          </div>
          <div class="modal-field">
            <label class="modal-label">每日 Token 限额</label>
            <input class="form-input" type="number" v-model.number="form.dailyTokenLimit" min="0" step="10000">
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">允许操作</label>
          <div class="choice-grid">
            <label v-for="op in availableOperations" :key="op" class="choice-item">
              <input type="checkbox" :value="op" v-model="selectedOperations">
              <span class="pill" :class="{ 'pill-inactive': !selectedOperations.includes(op) }">{{ op }}</span>
            </label>
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">状态</label>
          <button class="status-toggle" :class="form.enabled ? 'green' : 'red'" @click="form.enabled = !form.enabled">
            {{ form.enabled ? '启用' : '停用' }}
          </button>
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn btn-ghost btn-sm" @click="emit('close')">取消</button>
        <button class="btn btn-primary btn-sm" @click="handleSave">{{ permissionId ? '保存修改' : '创建权限' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getPermission } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { BOT_LABELS, ROLE_COLORS } from '@/utils/constants'

const props = defineProps<{ permissionId: string | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const userStore = useUserStore()
const botLabels = BOT_LABELS
const roleColors = ROLE_COLORS

const availableRoles = ['ROLE_ADMIN', 'ROLE_RD', 'ROLE_SALES', 'ROLE_HR', 'ROLE_FINANCE', 'ROLE_USER']
const availableOperations = ['READ', 'WRITE', 'APPROVE']

const form = reactive({
  botType: '',
  allowedDepartments: '',
  dataScope: 'DEPARTMENT',
  dailyTokenLimit: 100000,
  enabled: true
})

const selectedRoles = ref<string[]>(['ROLE_ADMIN'])
const selectedOperations = ref<string[]>(['READ', 'WRITE'])

onMounted(async () => {
  if (props.permissionId) {
    try {
      const perm = await getPermission(props.permissionId)
      form.botType = perm.botType || ''
      form.allowedDepartments = perm.allowedDepartments || ''
      form.dataScope = perm.dataScope || 'DEPARTMENT'
      form.dailyTokenLimit = perm.dailyTokenLimit ?? 100000
      form.enabled = perm.enabled ?? true
      selectedRoles.value = perm.allowedRoles ? perm.allowedRoles.split(',').map((r) => r.trim()).filter(Boolean) : []
      selectedOperations.value = perm.allowedOperations ? perm.allowedOperations.split(',').map((o) => o.trim()).filter(Boolean) : []
    } catch {
      // ignore
    }
  }
})

async function handleSave() {
  const data = {
    botType: form.botType,
    allowedRoles: selectedRoles.value.join(','),
    allowedDepartments: form.allowedDepartments || null,
    dataScope: form.dataScope,
    allowedOperations: selectedOperations.value.join(','),
    dailyTokenLimit: form.dailyTokenLimit,
    enabled: form.enabled
  }

  let ok: boolean
  if (props.permissionId) {
    ok = await userStore.updatePermission(props.permissionId, data)
  } else {
    if (!data.botType.trim()) return
    ok = await userStore.createPermission(data)
  }
  if (ok) emit('saved')
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal-shell {
  width: min(440px, 100%);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r2);
  padding: 24px;
}

.permission-shell {
  width: min(520px, 100%);
}

.modal-header {
  margin-bottom: 16px;
}

.modal-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.modal-subtitle {
  margin-top: 6px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}

.modal-body {
  display: grid;
  gap: 12px;
}

.modal-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.modal-field {
  display: grid;
  gap: 6px;
}

.modal-label {
  font-size: 11px;
  color: var(--text3);
}

.modal-help {
  color: var(--text3);
  font-size: 11px;
}

.readonly-field {
  padding: 10px 12px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 10px;
  font-size: 12px;
  color: var(--text2);
}

.choice-grid {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.choice-item {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}

.choice-item input[type='checkbox'] {
  display: none;
}

.pill-inactive {
  opacity: 0.35;
}

.status-toggle {
  width: fit-content;
  border: none;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}

.status-toggle.green {
  background: rgba(16, 185, 129, 0.14);
  color: #059669;
}

.status-toggle.red {
  background: rgba(239, 68, 68, 0.14);
  color: #dc2626;
}

.modal-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 18px;
}

@media (max-width: 640px) {
  .modal-grid {
    grid-template-columns: 1fr;
  }
}
</style>
