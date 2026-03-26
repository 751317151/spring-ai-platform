<template>
  <div class="sidebar" :class="{ collapsed }">
    <div class="sidebar-logo">
      <div class="logo-icon">AI</div>
      <span class="logo-text">AI 智能平台</span>
      <span class="logo-badge">v1.0</span>
      <button class="collapse-btn" @click="emit('toggle-collapse')">{{ collapsed ? '>' : '<' }}</button>
    </div>

    <div class="sidebar-section" style="margin-top: 8px">
      <router-link
        v-for="item in mainNav"
        :key="item.to"
        :to="item.to"
        class="nav-item"
        :title="collapsed ? item.label : undefined"
        active-class="active"
      >
        <svg
          class="nav-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          v-html="item.icon"
        ></svg>
        <span class="nav-text">{{ item.label }}</span>
        <span v-if="item.badge && !collapsed" class="nav-badge">{{ item.badge }}</span>
      </router-link>
    </div>

    <div v-if="!collapsed" class="sidebar-section">
      <div class="sidebar-focus-card">
        <div class="sidebar-focus-kicker">当前工作区</div>
        <div class="sidebar-focus-title">{{ currentNavLabel }}</div>
        <div class="sidebar-focus-meta">
          <span>最近访问 {{ recentViews.length }} 页</span>
          <span>{{ isAdmin ? '管理员模式' : '普通模式' }}</span>
        </div>
      </div>
    </div>

    <div v-if="!collapsed && recentViews.length" class="sidebar-section">
      <div class="sidebar-section-label">最近访问</div>
      <router-link
        v-for="item in recentViews"
        :key="item.to"
        :to="item.to"
        class="nav-item recent-item"
        active-class="active"
      >
        <span class="recent-dot"></span>
        <span class="nav-text">{{ item.title }}</span>
      </router-link>
    </div>

    <div v-if="isAdmin" class="sidebar-section">
      <div class="sidebar-section-label">{{ collapsed ? '管理' : '平台管理' }}</div>
      <router-link
        v-for="item in adminNav"
        :key="item.to"
        :to="item.to"
        class="nav-item"
        :title="collapsed ? item.label : undefined"
        active-class="active"
      >
        <svg
          class="nav-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          v-html="item.icon"
        ></svg>
        <span class="nav-text">{{ item.label }}</span>
      </router-link>
    </div>

    <div class="sidebar-footer">
      <div class="user-card">
        <div class="avatar">{{ displayChar }}</div>
        <div v-if="!collapsed" class="user-info">
          <div class="user-name">{{ authStore.username || '用户' }}</div>
          <div class="user-role">{{ displayRole }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

interface RecentView {
  title: string
  to: string
}

defineProps<{
  collapsed: boolean
  recentViews: RecentView[]
}>()

const emit = defineEmits<{
  (e: 'toggle-collapse'): void
}>()

const authStore = useAuthStore()
const route = useRoute()

const displayChar = computed(() => {
  const name = authStore.username || '用户'
  return name.charAt(0).toUpperCase()
})

const isAdmin = computed(() => (authStore.roles || '').includes('ROLE_ADMIN'))

const roleLabels: Record<string, string> = {
  ROLE_ADMIN: '管理员',
  ROLE_RD: '研发',
  ROLE_SALES: '销售',
  ROLE_HR: '人力',
  ROLE_FINANCE: '财务',
  ROLE_USER: '普通用户'
}

const displayRole = computed(() => {
  const roles = (authStore.roles || '').split(',').map((r: string) => r.trim()).filter(Boolean)
  if (roles.includes('ROLE_ADMIN')) return '管理员'
  for (const role of roles) {
    if (role !== 'ROLE_USER' && roleLabels[role]) return roleLabels[role]
  }
  return roleLabels[roles[0]] || '用户'
})

const mainNav = [
  {
    to: '/dashboard',
    label: '控制台',
    icon: '<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>'
  },
  {
    to: '/chat',
    label: 'AI 助手',
    icon: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    badge: '常用'
  },
  {
    to: '/rag',
    label: '知识库',
    icon: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>'
  },
  {
    to: '/learning',
    label: '学习中心',
    icon: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>'
  }
]

const adminNav = [
  {
    to: '/gateway',
    label: '模型网关',
    icon: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.22 4.22l2.12 2.12M17.66 17.66l2.12 2.12M2 12h3M19 12h3M4.22 19.78l2.12-2.12M17.66 6.34l2.12-2.12"/>'
  },
  {
    to: '/mcp',
    label: 'MCP 管理',
    icon: '<path d="M8 6h8"/><path d="M6 12h12"/><path d="M10 18h4"/><rect x="3" y="4" width="18" height="16" rx="2"/>'
  },
  {
    to: '/monitor',
    label: '运行监控',
    icon: '<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>'
  },
  {
    to: '/users',
    label: '用户与权限',
    icon: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>'
  }
]

const currentNavLabel = computed(() => {
  const matched = [...mainNav, ...adminNav].find((item) => route.path.startsWith(item.to))
  return matched?.label || '控制台'
})
</script>

<style scoped>
.sidebar-focus-card {
  display: grid;
  gap: 8px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(79, 142, 247, 0.18);
  background:
    radial-gradient(circle at top right, rgba(79, 142, 247, 0.14), transparent 34%),
    rgba(255, 255, 255, 0.03);
}

.sidebar-focus-kicker {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text3);
}

.sidebar-focus-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

.sidebar-focus-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 12px;
  color: var(--text3);
}

.sidebar.collapsed {
  width: 76px;
}

.collapse-btn {
  margin-left: auto;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text3);
  cursor: pointer;
}

.collapse-btn:hover {
  color: var(--text);
  border-color: var(--border2);
  background: var(--surface2);
}

.sidebar.collapsed .logo-text,
.sidebar.collapsed .logo-badge,
.sidebar.collapsed .nav-text,
.sidebar.collapsed .user-info {
  display: none;
}

.sidebar.collapsed .sidebar-section-label {
  text-align: center;
  padding-left: 0;
  padding-right: 0;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding-left: 0;
  padding-right: 0;
}

.recent-item {
  gap: 10px;
}

.recent-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--accent);
  flex-shrink: 0;
}
</style>
