<template>
  <header class="header">
    <div class="header-left">
      <button class="header-btn" type="button" @click="emit('toggle-collapse')">
        {{ collapsed ? '展开' : '收起' }}
      </button>
      <div class="header-copy">
        <div class="header-kicker">当前工作区</div>
        <div class="header-title">{{ currentTitle }}</div>
      </div>
    </div>

    <div class="header-right">
      <div v-if="authStore.isGuest" class="guest-badge">游客模式</div>

      <div class="header-user">
        <div class="header-user-name">{{ authStore.username || '当前用户' }}</div>
        <div class="header-user-role">{{ displayRole }}</div>
      </div>

      <button class="header-btn danger" type="button" @click="handleLogout">退出登录</button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-collapse'): void
}>()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isAdmin = computed(() => (authStore.roles || '').includes('ROLE_ADMIN'))
const currentTitle = computed(() => (route.meta.title as string) || 'AI 助手')
const displayRole = computed(() => {
  if (authStore.isGuest) return '游客体验'
  if (isAdmin.value) return '管理员'
  if ((authStore.roles || '').includes('ROLE_RD')) return '研发'
  if ((authStore.roles || '').includes('ROLE_SALES')) return '销售'
  if ((authStore.roles || '').includes('ROLE_HR')) return '人力'
  if ((authStore.roles || '').includes('ROLE_FINANCE')) return '财务'
  return '平台用户'
})

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px 12px;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.header-copy,
.header-user {
  min-width: 0;
}

.header-kicker {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text3);
}

.header-title {
  margin-top: 2px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}

.guest-badge {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.14);
  border: 1px solid rgba(16, 185, 129, 0.26);
  color: #9ae6b4;
  font-size: 12px;
  font-weight: 600;
}

.header-user {
  text-align: right;
}

.header-user-name {
  color: var(--text);
  font-size: 13px;
  font-weight: 700;
}

.header-user-role {
  margin-top: 2px;
  color: var(--text3);
  font-size: 11px;
}

.header-btn {
  height: 36px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
  cursor: pointer;
  transition: border-color var(--transition), background var(--transition), transform var(--transition);
}

.header-btn:hover {
  transform: translateY(-1px);
  border-color: rgba(59, 130, 246, 0.24);
  background: rgba(255, 255, 255, 0.06);
}

.header-btn.danger:hover {
  border-color: rgba(239, 68, 68, 0.24);
}

@media (max-width: 720px) {
  .header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
