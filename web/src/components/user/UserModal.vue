<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal-shell">
      <div class="modal-header">
        <div>
          <div class="modal-title">{{ userId ? '编辑用户' : '新建用户' }}</div>
          <div class="modal-subtitle">维护账号信息、所属部门和角色分配。</div>
        </div>
      </div>

      <div class="modal-body">
        <div class="modal-field">
          <label class="modal-label">用户名</label>
          <input class="form-input" v-model="form.username" placeholder="请输入用户名" :disabled="Boolean(userId)">
        </div>

        <div class="modal-field">
          <label class="modal-label">{{ userId ? '新密码' : '密码' }}</label>
          <input class="form-input" v-model="form.password" type="password" :placeholder="userId ? '留空则保持当前密码不变' : '请输入密码'">
        </div>

        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">工号</label>
            <input class="form-input" v-model="form.employeeId" placeholder="EMP001">
          </div>
          <div class="modal-field">
            <label class="modal-label">部门</label>
            <input class="form-input" v-model="form.department" placeholder="例如：研发中心">
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">角色</label>
          <input class="form-input" v-model="form.roles" placeholder="ROLE_RD,ROLE_USER">
          <div class="modal-help">请使用英文逗号分隔角色编码，例如 `ROLE_ADMIN,ROLE_USER`。</div>
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn btn-ghost btn-sm" @click="emit('close')">取消</button>
        <button class="btn btn-primary btn-sm" @click="handleSave">{{ userId ? '保存修改' : '创建用户' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useUserStore } from '@/stores/user'
import { getUser } from '@/api/auth'

const props = defineProps<{ userId: string | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const userStore = useUserStore()

const form = reactive({
  username: '',
  password: '',
  employeeId: '',
  department: '',
  roles: ''
})

onMounted(async () => {
  if (props.userId) {
    try {
      const user = await getUser(props.userId)
      form.username = user.username || ''
      form.employeeId = user.employeeId || ''
      form.department = user.department || ''
      form.roles = user.roles || ''
    } catch {
      // ignore
    }
  }
})

async function handleSave() {
  if (props.userId) {
    const data: Record<string, unknown> = {
      department: form.department,
      employeeId: form.employeeId,
      roles: form.roles
    }
    if (form.password) data.password = form.password
    const ok = await userStore.updateUser(props.userId, data)
    if (ok) emit('saved')
  } else {
    const ok = await userStore.createUser({
      username: form.username,
      password: form.password,
      employeeId: form.employeeId,
      department: form.department,
      roles: form.roles
    })
    if (ok) emit('saved')
  }
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
