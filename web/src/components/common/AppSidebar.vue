<template>
  <div class="sidebar">
    <div class="sidebar-logo">
      <div class="logo-icon">AI</div>
      <span class="logo-text">Enterprise AI</span>
      <span class="logo-badge">v1.0</span>
    </div>
    <div class="sidebar-section" style="margin-top: 8px">
      <router-link
        v-for="item in mainNav"
        :key="item.to"
        :to="item.to"
        class="nav-item"
        active-class="active"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" v-html="item.icon"></svg>
        {{ item.label }}
        <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
      </router-link>
    </div>
    <div class="sidebar-section">
      <div class="sidebar-section-label">平台管理</div>
      <router-link
        v-for="item in adminNav"
        :key="item.to"
        :to="item.to"
        class="nav-item"
        active-class="active"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" v-html="item.icon"></svg>
        {{ item.label }}
      </router-link>
    </div>
    <div class="sidebar-footer">
      <div class="user-card">
        <div class="avatar">{{ displayChar }}</div>
        <div class="user-info">
          <div class="user-name">{{ authStore.username || '管理员' }}</div>
          <div class="user-role">超级管理员</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const displayChar = computed(() => {
  const name = authStore.username || '管'
  return name.charAt(0)
})

const mainNav = [
  { to: '/dashboard', label: '控制台', icon: '<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>' },
  { to: '/chat', label: 'AI 助手', icon: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>', badge: '7' },
  { to: '/rag', label: '知识库', icon: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>' }
]

const adminNav = [
  { to: '/gateway', label: '模型网关', icon: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.22 4.22l2.12 2.12M17.66 17.66l2.12 2.12M2 12h3M19 12h3M4.22 19.78l2.12-2.12M17.66 6.34l2.12-2.12"/>' },
  { to: '/monitor', label: '监控告警', icon: '<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>' },
  { to: '/users', label: '权限管理', icon: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>' }
]
</script>
