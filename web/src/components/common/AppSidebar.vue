<template>
  <aside class="sidebar" :class="{ collapsed }">
    <div class="sidebar-head">
      <div class="brand-mark">AI</div>
      <div v-if="!collapsed" class="brand-copy">
        <div class="brand-title">AI 智能平台</div>
        <div class="brand-subtitle">Work Console</div>
      </div>
      <button class="collapse-btn" type="button" @click="emit('toggle-collapse')">
        {{ collapsed ? '>' : '<' }}
      </button>
    </div>

    <nav class="sidebar-nav">
      <div class="sidebar-group">
        <div v-if="!collapsed" class="group-label">核心工作区</div>
        <router-link
          v-for="item in primaryNav"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: isRouteActive(item.to) }"
          :title="collapsed ? item.label : undefined"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span v-if="!collapsed" class="nav-copy">
            <span class="nav-label">{{ item.label }}</span>
            <span class="nav-desc">{{ item.desc }}</span>
          </span>
        </router-link>
      </div>

      <div v-if="isAdmin" class="sidebar-group">
        <div v-if="!collapsed" class="group-label">管理入口</div>
        <router-link
          v-for="item in adminNav"
          :key="item.to"
          :to="item.to"
          class="nav-item nav-item-secondary"
          :class="{ active: isRouteActive(item.to) }"
          :title="collapsed ? item.label : undefined"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span v-if="!collapsed" class="nav-copy">
            <span class="nav-label">{{ item.label }}</span>
            <span class="nav-desc">{{ item.desc }}</span>
          </span>
        </router-link>
      </div>
    </nav>

    <div class="sidebar-foot">
      <div class="user-badge">{{ displayChar }}</div>
      <div v-if="!collapsed" class="user-copy">
        <div class="user-name">{{ authStore.username || '用户' }}</div>
        <div class="user-role">{{ displayRole }}</div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-collapse'): void
}>()

const authStore = useAuthStore()
const route = useRoute()

const isAdmin = computed(() => (authStore.roles || '').includes('ROLE_ADMIN'))

const displayChar = computed(() => {
  const name = authStore.username || '用户'
  return name.slice(0, 1).toUpperCase()
})

const displayRole = computed(() => {
  const roles = (authStore.roles || '').split(',').map((item) => item.trim()).filter(Boolean)
  if (roles.includes('ROLE_ADMIN')) return '管理员'
  if (roles.includes('ROLE_RD')) return '研发'
  if (roles.includes('ROLE_SALES')) return '销售'
  if (roles.includes('ROLE_HR')) return '人力'
  if (roles.includes('ROLE_FINANCE')) return '财务'
  return '平台用户'
})

const primaryNav = [
  { to: '/chat', label: 'AI 助手', desc: '直接进入对话工作区', icon: 'AI' },
  { to: '/rag', label: '知识库', desc: '选库并围绕知识提问', icon: 'KB' },
  { to: '/dashboard', label: '工作台', desc: '查看常用入口与概要', icon: 'WS' },
  { to: '/learning', label: '学习中心', desc: '沉淀常用内容与笔记', icon: 'LC' }
]

const adminNav = [
  { to: '/screen', label: '大屏指挥台', desc: '全屏展示平台运行核心指标', icon: 'SC' },
  { to: '/monitor', label: '运行监控', desc: '查看系统状态与审计', icon: 'MO' },
  { to: '/gateway', label: '模型网关', desc: '管理模型路由与健康', icon: 'GW' },
  { to: '/agents', label: 'Agent 工作台', desc: '智能体执行与治理', icon: 'AG' },
  { to: '/mcp', label: 'MCP 管理', desc: '工具接入与可用态', icon: 'MC' },
  { to: '/users', label: '用户权限', desc: '账号与角色管理', icon: 'UR' }
]

function isRouteActive(target: string) {
  if (target === '/rag') {
    return route.path === '/rag' || route.path.startsWith('/rag/')
  }
  return route.path === target
}
</script>

<style scoped>
.sidebar {
  width: 100%;
  max-width: 248px;
  height: 100vh;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 14px 12px;
  border-right: 1px solid var(--border);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.01)),
    rgba(7, 12, 24, 0.82);
  backdrop-filter: blur(14px);
  overflow: hidden;
}

.sidebar.collapsed {
  max-width: 72px;
  padding-inline: 10px;
}

.sidebar.collapsed .sidebar-head,
.sidebar.collapsed .sidebar-foot {
  justify-content: center;
  flex-direction: column;
  gap: 8px;
}

.sidebar-head,
.sidebar-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.sidebar-head {
  margin-bottom: 18px;
}

.sidebar.collapsed .sidebar-head {
  margin-bottom: 14px;
}

.brand-mark,
.user-badge {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  flex: none;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.9), rgba(16, 185, 129, 0.8));
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.22);
}

.brand-copy,
.user-copy {
  min-width: 0;
  overflow: hidden;
}

.brand-title,
.user-name {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand-subtitle,
.user-role {
  margin-top: 2px;
  color: var(--text3);
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.collapse-btn {
  margin-left: auto;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text2);
  cursor: pointer;
}

.sidebar.collapsed .collapse-btn {
  margin-left: 0;
  width: 36px;
  height: 28px;
  border-radius: 9px;
}

.sidebar-nav {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: grid;
  gap: 18px;
  padding-right: 6px;
  scrollbar-gutter: stable;
}

.sidebar-group {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.group-label {
  padding: 0 8px;
  color: var(--text3);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  min-width: 0;
  padding: 10px 12px;
  border-radius: 18px;
  border: 1px solid transparent;
  color: inherit;
  text-decoration: none;
  transition: transform var(--transition), border-color var(--transition), background var(--transition);
}

.nav-item:hover {
  transform: translateY(-1px);
  border-color: rgba(59, 130, 246, 0.2);
  background: rgba(255, 255, 255, 0.04);
}

.nav-item.active {
  border-color: rgba(59, 130, 246, 0.24);
  background:
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.18), transparent 42%),
    rgba(255, 255, 255, 0.06);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.nav-item-secondary.active {
  border-color: rgba(148, 163, 184, 0.22);
}

.nav-icon {
  width: 32px;
  height: 32px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  flex: none;
  color: var(--text);
  background: rgba(255, 255, 255, 0.05);
  font-size: 11px;
  font-weight: 700;
}

.nav-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
  overflow: hidden;
}

.nav-label {
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-desc {
  color: var(--text3);
  font-size: 11px;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-foot {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
}

.sidebar.collapsed .sidebar-foot {
  margin-top: 12px;
  padding-top: 12px;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding-inline: 0;
  width: 100%;
  min-height: 50px;
  border-radius: 16px;
}

.sidebar.collapsed .sidebar-nav {
  padding-right: 0;
  gap: 14px;
}

.sidebar.collapsed .nav-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  font-size: 10px;
}

.sidebar.collapsed .sidebar-group {
  justify-items: center;
  gap: 6px;
}

@media (max-width: 960px) {
  .sidebar,
  .sidebar.collapsed {
    height: auto;
    min-height: auto;
    padding: 10px 12px;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }

  .sidebar-nav {
    overflow-x: auto;
    overflow-y: hidden;
    display: flex;
    gap: 10px;
    padding-bottom: 6px;
    padding-right: 0;
  }

  .sidebar-group {
    display: flex;
    flex-direction: row;
    align-items: stretch;
    gap: 10px;
  }

  .nav-item {
    min-width: 180px;
  }

  .sidebar-foot {
    display: none;
  }
}
</style>
