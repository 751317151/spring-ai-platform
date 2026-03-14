<template>
  <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); z-index: 1000; display: flex; align-items: center; justify-content: center" @click.self="emit('close')">
    <div style="background: var(--surface); border: 1px solid var(--border); border-radius: var(--r2); padding: 24px; width: 400px; max-width: 90vw">
      <div style="font-size: 14px; font-weight: 500; color: var(--text); margin-bottom: 16px">
        {{ userId ? '编辑用户' : '新增用户' }}
      </div>
      <div style="margin-bottom: 10px">
        <label style="font-size: 11px; color: var(--text3); display: block; margin-bottom: 4px">用户名</label>
        <input class="form-input" v-model="form.username" placeholder="username" style="width: 100%">
      </div>
      <div style="margin-bottom: 10px">
        <label style="font-size: 11px; color: var(--text3); display: block; margin-bottom: 4px">密码</label>
        <input class="form-input" v-model="form.password" type="password" placeholder="password" style="width: 100%">
      </div>
      <div style="margin-bottom: 10px">
        <label style="font-size: 11px; color: var(--text3); display: block; margin-bottom: 4px">工号</label>
        <input class="form-input" v-model="form.employeeId" placeholder="EMP001" style="width: 100%">
      </div>
      <div style="margin-bottom: 10px">
        <label style="font-size: 11px; color: var(--text3); display: block; margin-bottom: 4px">部门</label>
        <input class="form-input" v-model="form.department" placeholder="研发中心" style="width: 100%">
      </div>
      <div style="margin-bottom: 10px">
        <label style="font-size: 11px; color: var(--text3); display: block; margin-bottom: 4px">角色 (逗号分隔)</label>
        <input class="form-input" v-model="form.roles" placeholder="ROLE_RD,ROLE_USER" style="width: 100%">
      </div>
      <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px">
        <button class="btn btn-ghost btn-sm" @click="emit('close')">取消</button>
        <button class="btn btn-primary btn-sm" @click="handleSave">保存</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
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
