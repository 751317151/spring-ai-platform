<template>
  <div class="header">
    <div class="breadcrumb">
      <span>Enterprise AI</span>
      <span class="breadcrumb-sep">/</span>
      <span class="breadcrumb-cur">{{ currentTitle }}</span>
    </div>
    <div class="header-right">
      <div style="display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text3)">
        <div class="status-dot"></div>
        <span>5 个服务在线</span>
      </div>
      <button class="header-btn" @click="showToast('请前往模型网关页面管理 API 配置')">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14M5 5a10 10 0 0 0 0 14"/></svg>
        API 配置
      </button>
      <button class="header-btn" style="color: var(--red)" @click="handleLogout">退出</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { showToast } = useToast()

const currentTitle = computed(() => (route.meta.title as string) || '控制台')

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>
