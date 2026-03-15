<template>
  <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); z-index: 1000; display: flex; align-items: center; justify-content: center" @click.self="emit('close')">
    <div style="background: var(--surface); border: 1px solid var(--border); border-radius: var(--r2); padding: 24px; width: 480px; max-width: 90vw">
      <div style="font-size: 14px; font-weight: 500; color: var(--text); margin-bottom: 16px">
        {{ permissionId ? '编辑 Bot 权限' : '新增 Bot 权限' }}
      </div>

      <div style="margin-bottom: 10px">
        <label class="modal-label">Bot 类型</label>
        <input v-if="!permissionId" class="form-input" v-model="form.botType" placeholder="rd / sales / hr / ..." style="width: 100%">
        <div v-else style="padding: 6px 10px; background: var(--bg); border: 1px solid var(--border); border-radius: 6px; font-size: 12px; color: var(--text2)">
          {{ botLabels[form.botType] || form.botType }}
        </div>
      </div>

      <div style="margin-bottom: 10px">
        <label class="modal-label">允许角色</label>
        <div style="display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 6px">
          <label v-for="role in availableRoles" :key="role" class="role-checkbox">
            <input type="checkbox" :value="role" v-model="selectedRoles">
            <span class="pill" :class="[roleColors[role] || 'blue', { 'pill-inactive': !selectedRoles.includes(role) }]" style="cursor: pointer; font-size: 11px">
              {{ role.replace('ROLE_', '') }}
            </span>
          </label>
        </div>
      </div>

      <div style="margin-bottom: 10px">
        <label class="modal-label">允许部门 (逗号分隔，留空=全部)</label>
        <input class="form-input" v-model="form.allowedDepartments" placeholder="研发中心,销售部" style="width: 100%">
      </div>

      <div style="display: flex; gap: 12px; margin-bottom: 10px">
        <div style="flex: 1">
          <label class="modal-label">数据范围</label>
          <select class="form-input" v-model="form.dataScope" style="width: 100%">
            <option value="ALL">全部数据</option>
            <option value="DEPARTMENT">本部门</option>
            <option value="SELF">仅本人</option>
          </select>
        </div>
        <div style="flex: 1">
          <label class="modal-label">日 Token 限额</label>
          <input class="form-input" type="number" v-model.number="form.dailyTokenLimit" min="0" step="10000" style="width: 100%">
        </div>
      </div>

      <div style="margin-bottom: 10px">
        <label class="modal-label">允许操作</label>
        <div style="display: flex; gap: 8px">
          <label v-for="op in availableOperations" :key="op" class="role-checkbox">
            <input type="checkbox" :value="op" v-model="selectedOperations">
            <span class="pill" :class="{ 'pill-inactive': !selectedOperations.includes(op) }" style="cursor: pointer; font-size: 11px">{{ op }}</span>
          </label>
        </div>
      </div>

      <div style="margin-bottom: 10px; display: flex; align-items: center; gap: 8px">
        <label class="modal-label" style="margin-bottom: 0">启用状态</label>
        <button class="pill" :class="form.enabled ? 'green' : 'red'" style="cursor: pointer; border: none; font-size: 11px" @click="form.enabled = !form.enabled">
          {{ form.enabled ? '启用' : '禁用' }}
        </button>
      </div>

      <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px">
        <button class="btn btn-ghost btn-sm" @click="emit('close')">取消</button>
        <button class="btn btn-primary btn-sm" @click="handleSave">保存</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { getPermission } from '@/api/auth'
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
      selectedRoles.value = perm.allowedRoles ? perm.allowedRoles.split(',').map(r => r.trim()).filter(Boolean) : []
      selectedOperations.value = perm.allowedOperations ? perm.allowedOperations.split(',').map(o => o.trim()).filter(Boolean) : []
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
.modal-label {
  font-size: 11px;
  color: var(--text3);
  display: block;
  margin-bottom: 4px;
}
.role-checkbox {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}
.role-checkbox input[type="checkbox"] {
  display: none;
}
.pill-inactive {
  opacity: 0.35;
}
</style>
