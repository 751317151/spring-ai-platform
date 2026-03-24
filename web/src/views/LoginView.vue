<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">
        <div class="logo-icon">AI</div>
        <div>
          <div class="login-brand">AI 智能平台</div>
          <div class="login-version">平台 v1.0</div>
        </div>
      </div>

      <div class="login-title">欢迎回来</div>
      <div class="login-sub">登录后继续进入你的 AI 工作台。</div>

      <div class="login-tips">
        <span class="login-tip">对话工作台</span>
        <span class="login-tip">知识库管理</span>
        <span class="login-tip">平台管理</span>
      </div>

      <div class="form-group">
        <label class="form-label">用户名</label>
        <input class="form-input" type="text" v-model="username" placeholder="admin" @keyup.enter="handleLogin">
      </div>
      <div class="form-group">
        <label class="form-label">密码</label>
        <input class="form-input" type="password" v-model="password" placeholder="请输入密码" @keyup.enter="handleLogin">
      </div>

      <div v-if="errorMsg" class="login-error">
        {{ errorMsg }}
      </div>

      <button class="btn btn-primary login-submit" @click="handleLogin" :disabled="isLoading">
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
    errorMsg.value = e instanceof Error ? e.message : '登录失败，请稍后重试。'
  } finally {
    isLoading.value = false
  }
}
</script>

<style scoped>
.login-brand {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.login-version {
  font-size: 11px;
  color: var(--text3);
}

.login-tips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 0 0 16px;
}

.login-tip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
  color: var(--text3);
  font-size: 12px;
  background: rgba(255, 255, 255, 0.03);
}

.login-error {
  margin-bottom: 16px;
  padding: 8px 12px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 6px;
  font-size: 12px;
  color: #ef4444;
}

.login-submit {
  width: 100%;
  justify-content: center;
}
</style>
