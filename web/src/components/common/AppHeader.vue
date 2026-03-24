<template>
  <div class="header">
    <div class="header-left">
      <button class="header-btn" @click="emit('toggle-collapse')">
        {{ collapsed ? '展开' : '收起' }}
      </button>

      <div class="breadcrumb">
        <span>AI 平台</span>
        <span class="breadcrumb-sep">/</span>
        <span class="breadcrumb-cur">{{ currentTitle }}</span>
      </div>

      <div class="header-search-shell" @keydown.esc="closeQuickJump">
        <input
          ref="searchRef"
          v-model.trim="searchKeyword"
          class="header-search-input"
          type="text"
          placeholder="快速跳转：页面、最近访问、常用功能"
          @focus="openQuickJump"
        >
        <span class="header-search-kbd">Ctrl/Cmd + K</span>

        <div v-if="showQuickJump" class="quick-jump-panel">
          <div class="quick-jump-section">
            <div class="quick-jump-label">快速跳转</div>
            <button
              v-for="item in filteredQuickJumpItems"
              :key="item.to"
              class="quick-jump-item"
              @mousedown.prevent="goTo(item.to)"
            >
              <span class="quick-jump-title">{{ item.title }}</span>
              <span class="quick-jump-meta">{{ item.hint }}</span>
            </button>
            <div v-if="!filteredQuickJumpItems.length" class="quick-jump-empty">没有匹配的页面。</div>
          </div>
        </div>
      </div>

      <div v-if="recentViews.length" class="recent-links">
        <button
          v-for="item in recentViews.slice(0, 3)"
          :key="item.to"
          class="recent-link"
          @click="router.push(item.to)"
        >
          {{ item.title }}
        </button>
      </div>
    </div>

    <div class="header-right">
      <div class="service-status">
        <div class="status-dot"></div>
        <span>核心服务在线</span>
      </div>

      <button
        v-for="item in quickActions"
        :key="item.to"
        class="header-btn"
        @click="router.push(item.to)"
      >
        {{ item.label }}
      </button>

      <button class="header-btn user-shortcut">
        {{ authStore.username || '当前用户' }}
      </button>
      <button class="header-btn danger" @click="handleLogout">退出登录</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

interface RecentView {
  title: string
  to: string
}

interface QuickJumpItem {
  title: string
  to: string
  hint: string
}

const props = defineProps<{
  collapsed: boolean
  recentViews: RecentView[]
}>()

const emit = defineEmits<{
  (e: 'toggle-collapse'): void
}>()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const searchRef = ref<HTMLInputElement | null>(null)
const searchKeyword = ref('')
const showQuickJump = ref(false)

const currentTitle = computed(() => (route.meta.title as string) || '控制台')

const quickActions = computed(() => {
  const items = [
    { label: 'AI 助手', to: '/chat' },
    { label: '知识库', to: '/rag' }
  ]
  if ((authStore.roles || '').includes('ROLE_ADMIN')) {
    items.push({ label: '运行监控', to: '/monitor' })
  }
  return items.filter((item) => item.to !== route.path).slice(0, 3)
})

const quickJumpItems = computed<QuickJumpItem[]>(() => {
  const base: QuickJumpItem[] = [
    { title: '控制台', to: '/dashboard', hint: '总览与今日指标' },
    { title: 'AI 助手', to: '/chat', hint: '对话工作台' },
    { title: '知识库', to: '/rag', hint: 'RAG 工作流与文档管理' }
  ]

  if ((authStore.roles || '').includes('ROLE_ADMIN')) {
    base.push(
      { title: '运行监控', to: '/monitor', hint: '延迟、失败和反馈分析' },
      { title: '模型网关', to: '/gateway', hint: '模型路由和提供商配置' },
      { title: 'MCP 管理', to: '/mcp', hint: '工具集成与运行状态' },
      { title: '用户与权限', to: '/users', hint: '用户、角色和 Bot 访问规则' }
    )
  }

  const recent = props.recentViews.map((item) => ({
    title: item.title,
    to: item.to,
    hint: '最近访问'
  }))

  const deduped = new Map<string, QuickJumpItem>()
  ;[...recent, ...base].forEach((item) => {
    if (!deduped.has(item.to)) {
      deduped.set(item.to, item)
    }
  })

  return [...deduped.values()].filter((item) => item.to !== route.fullPath)
})

const filteredQuickJumpItems = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return quickJumpItems.value.slice(0, 7)
  }
  return quickJumpItems.value
    .filter((item) => `${item.title} ${item.hint} ${item.to}`.toLowerCase().includes(keyword))
    .slice(0, 7)
})

function openQuickJump() {
  showQuickJump.value = true
}

function closeQuickJump() {
  showQuickJump.value = false
}

function focusQuickJump() {
  nextTick(() => {
    searchRef.value?.focus()
    searchRef.value?.select()
    showQuickJump.value = true
  })
}

function goTo(path: string) {
  searchKeyword.value = ''
  closeQuickJump()
  router.push(path)
}

function handleGlobalClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Node)) {
    return
  }
  if (!searchRef.value?.closest('.header-search-shell')?.contains(target)) {
    closeQuickJump()
  }
}

function handleFocusSearchShortcut() {
  focusQuickJump()
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

onMounted(() => {
  window.addEventListener('app:focus-header-search', handleFocusSearchShortcut as EventListener)
  window.addEventListener('click', handleGlobalClick)
})

onUnmounted(() => {
  window.removeEventListener('app:focus-header-search', handleFocusSearchShortcut as EventListener)
  window.removeEventListener('click', handleGlobalClick)
})
</script>

<style scoped>
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.header-search-shell {
  position: relative;
  min-width: 260px;
  max-width: 420px;
  width: 100%;
}

.header-search-input {
  width: 100%;
  height: 34px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--surface2);
  color: var(--text);
  padding: 0 112px 0 12px;
  outline: none;
}

.header-search-input:focus {
  border-color: var(--accent);
}

.header-search-kbd {
  position: absolute;
  top: 50%;
  right: 10px;
  transform: translateY(-50%);
  font-size: 11px;
  color: var(--text3);
}

.quick-jump-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.12);
  padding: 12px;
  z-index: 20;
}

.quick-jump-section {
  display: grid;
  gap: 6px;
}

.quick-jump-label {
  font-size: 11px;
  color: var(--text3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding: 0 4px 4px;
}

.quick-jump-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text);
  border-radius: 12px;
  padding: 10px 12px;
  cursor: pointer;
  text-align: left;
}

.quick-jump-item:hover {
  background: var(--surface2);
  border-color: var(--border);
}

.quick-jump-title {
  font-size: 13px;
  font-weight: 500;
}

.quick-jump-meta {
  font-size: 11px;
  color: var(--text3);
}

.quick-jump-empty {
  color: var(--text3);
  font-size: 12px;
  padding: 10px 12px;
}

.recent-links {
  display: flex;
  gap: 8px;
  min-width: 0;
}

.recent-link {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text3);
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 11px;
  cursor: pointer;
}

.recent-link:hover {
  color: var(--text);
  border-color: var(--accent);
}

.service-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text3);
}

.user-shortcut {
  max-width: 140px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-btn.danger {
  color: var(--red);
}

@media (max-width: 1100px) {
  .recent-links {
    display: none;
  }

  .header-search-shell {
    min-width: 220px;
  }

  .header-search-input {
    padding-right: 96px;
  }
}
</style>
