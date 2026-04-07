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
      <div class="login-sub">可使用账号登录，也可直接进入游客模式体验完整前端演示。</div>

      <div class="login-tips">
        <span class="login-tip">对话工作台</span>
        <span class="login-tip">知识库问答</span>
        <span class="login-tip">本地 Mock 演示</span>
      </div>

      <div class="form-group">
        <label class="form-label">用户ID</label>
        <input
          v-model="userId"
          class="form-input"
          type="text"
          placeholder="admin"
          @keyup.enter="handleLogin"
        >
      </div>

      <div class="form-group">
        <label class="form-label">密码</label>
        <input
          v-model="password"
          class="form-input"
          type="password"
          placeholder="请输入密码"
          @keyup.enter="handleLogin"
        >
      </div>

      <div v-if="errorMsg" class="login-error">
        {{ errorMsg }}
      </div>

      <div class="login-actions">
        <button class="btn btn-primary login-submit" :disabled="isLoading" @click="handleLogin">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3" />
          </svg>
          {{ isLoading ? '登录中...' : '账号登录' }}
        </button>

        <button class="btn btn-ghost login-guest" :disabled="isLoading" @click="handleGuestLogin">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 12a5 5 0 1 0-5-5 5 5 0 0 0 5 5Zm-8 9a8 8 0 0 1 16 0" />
          </svg>
          游客模式
        </button>
      </div>

      <div class="guest-note">
        游客模式不会请求后端服务，聊天、知识库和仪表盘数据都由前端本地 mock 生成。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const userId = ref('admin')
const password = ref('admin123')
const isLoading = ref(false)
const errorMsg = ref('')

function resolveRedirect() {
  return typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
}

async function handleLogin() {
  if (isLoading.value) {
    return
  }
  isLoading.value = true
  errorMsg.value = ''
  try {
    await authStore.login(userId.value, password.value)
    router.replace(resolveRedirect())
  } catch (error: unknown) {
    errorMsg.value = error instanceof Error ? error.message : '登录失败，请稍后重试。'
  } finally {
    isLoading.value = false
  }
}

async function handleGuestLogin() {
  if (isLoading.value) {
    return
  }
  isLoading.value = true
  errorMsg.value = ''
  try {
    await authStore.loginAsGuest()
    router.replace(resolveRedirect())
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

.login-actions {
  display: grid;
  gap: 10px;
}

.login-submit,
.login-guest {
  width: 100%;
  justify-content: center;
}

.guest-note {
  margin-top: 12px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.7;
}
</style>
