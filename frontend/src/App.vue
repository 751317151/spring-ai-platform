<template>
  <div class="layout">
    <aside class="sidebar">
      <h1>Spring AI 管理台</h1>
      <nav>
        <RouterLink to="/dashboard">监控总览</RouterLink>
        <RouterLink to="/models">模型网关</RouterLink>
        <RouterLink to="/rag">RAG 知识库</RouterLink>
        <RouterLink to="/agent">Agent 调度</RouterLink>
      </nav>
      <div class="auth-box">
        <input v-model="user" placeholder="用户名" />
        <input v-model="pass" type="password" placeholder="密码" />
        <button @click="saveAuth">保存认证</button>
      </div>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const user = ref(localStorage.getItem('api-user') || 'ai-admin')
const pass = ref(localStorage.getItem('api-pass') || 'ai-admin-pass')

const saveAuth = () => {
  localStorage.setItem('api-user', user.value)
  localStorage.setItem('api-pass', pass.value)
  alert('认证信息已保存')
}
</script>
