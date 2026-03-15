<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">
        <div class="logo-icon">AI</div>
        <div>
          <div style="font-size: 14px; font-weight: 500; color: var(--text)">Enterprise AI</div>
          <div style="font-size: 11px; color: var(--text3)">Platform v1.0</div>
        </div>
      </div>
      <div class="login-title">欢迎回来</div>
      <div class="login-sub">登录企业 AI 平台</div>
      <div class="form-group">
        <label class="form-label">用户名</label>
        <input class="form-input" type="text" v-model="username" placeholder="admin" @keyup.enter="handleLogin">
      </div>
      <div class="form-group">
        <label class="form-label">密码</label>
        <input class="form-input" type="password" v-model="password" placeholder="••••••••" @keyup.enter="handleLogin">
      </div>
      <div v-if="errorMsg" style="margin-bottom: 16px; padding: 8px 12px; background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2); border-radius: 6px; font-size: 12px; color: #ef4444">
        {{ errorMsg }}
      </div>
      <button class="btn btn-primary" style="width: 100%; justify-content: center" @click="handleLogin" :disabled="isLoading">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3"/></svg>
        {{ isLoading ? '登录中...' : '登录' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const username = ref('admin')
const password = ref('admin123')
const isLoading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  if (isLoading.value) return
  isLoading.value = true
  errorMsg.value = ''
  try {
    await authStore.login(username.value, password.value)
    router.push('/dashboard')
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : '登录失败，请重试'
  } finally {
    isLoading.value = false
  }
}
</script>
