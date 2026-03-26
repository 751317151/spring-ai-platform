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
            <EmptyState
              v-if="!filteredQuickJumpItems.length"
              icon="K"
              title="没有匹配的页面"
              description="可以换一个关键词，或者直接使用最近访问和常用入口。"
              variant="compact"
              align="left"
            />
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

      <div class="mobile-entry-row">
        <button class="mobile-entry-btn" type="button" @click="focusQuickJump">
          搜索
        </button>
        <button
          v-if="recentViews.length"
          class="mobile-entry-btn"
          type="button"
          @click="toggleMobileRecentTray"
        >
          最近访问
        </button>
        <button class="mobile-entry-btn" type="button" @click="toggleMobileActionTray">
          常用入口
        </button>
      </div>
    </div>

    <div class="header-right">
      <div class="service-status">
        <div class="status-dot"></div>
        <span>核心服务在线</span>
      </div>

      <button v-for="item in quickActions" :key="item.to" class="header-btn" @click="router.push(item.to)">
        {{ item.label }}
      </button>

      <button class="header-btn" @click="openShortcutHelp">快捷键</button>

      <div class="notice-shell" @keydown.esc="closeNoticeCenter">
        <button class="header-btn notice-btn" @click="toggleNoticeCenter">
          通知
          <span v-if="unreadCount" class="notice-badge">{{ unreadCount }}</span>
        </button>

        <div v-if="showNoticeCenter" class="notice-panel">
          <div class="notice-head">
            <div>
              <div class="notice-title">通知中心</div>
              <div class="notice-subtitle">展示最近的系统提示和操作反馈。</div>
            </div>
            <div class="notice-head-actions">
              <button class="notice-clear" :disabled="!unreadCount" @click="markAllRead">
                全部已读
              </button>
              <button class="notice-clear" :disabled="!toastHistory.length" @click="handleClearHistory">
                清空
              </button>
            </div>
          </div>

          <div v-if="toastHistory.length" class="notice-list">
            <section v-for="group in groupedNotices" :key="group.label" class="notice-group">
              <div class="notice-group-title">{{ group.label }}</div>
              <div class="notice-group-list">
                <button
                  v-for="item in group.items"
                  :key="item.id"
                  class="notice-item"
                  :class="{ unread: item.readAt == null }"
                  @click="handleRead(item.id)"
                >
                  <div class="notice-item-top">
                    <div class="notice-item-time">{{ formatTime(item.createdAt) }}</div>
                    <span v-if="item.readAt == null" class="notice-item-dot"></span>
                    <span v-else class="notice-item-read">已读</span>
                  </div>
                  <div class="notice-item-text">{{ item.message }}</div>
                </button>
              </div>
            </section>
          </div>

          <EmptyState
            v-else
            icon="N"
            title="暂无通知"
            description="上传结果、操作反馈和系统提示会集中显示在这里。"
            variant="compact"
            align="left"
          />
        </div>
      </div>

      <button class="header-btn user-shortcut">
        {{ authStore.username || '当前用户' }}
      </button>
      <button class="header-btn danger" @click="handleLogout">退出登录</button>
    </div>
  </div>

  <div v-if="showMobileRecentTray" class="mobile-tray">
    <div class="mobile-tray-head">
      <div>
        <div class="mobile-tray-title">最近访问</div>
        <div class="mobile-tray-subtitle">继续你刚刚处理过的页面。</div>
      </div>
      <button class="mobile-tray-close" type="button" @click="showMobileRecentTray = false">关闭</button>
    </div>
    <div class="mobile-tray-list">
      <button
        v-for="item in recentViews"
        :key="item.to"
        class="mobile-tray-item"
        type="button"
        @click="goTo(item.to)"
      >
        <span class="mobile-tray-item-title">{{ item.title }}</span>
        <span class="mobile-tray-item-meta">{{ item.to }}</span>
      </button>
    </div>
  </div>

  <div v-if="showMobileActionTray" class="mobile-tray">
    <div class="mobile-tray-head">
      <div>
        <div class="mobile-tray-title">常用入口</div>
        <div class="mobile-tray-subtitle">在移动端保留高频跳转与效率操作。</div>
      </div>
      <button class="mobile-tray-close" type="button" @click="showMobileActionTray = false">关闭</button>
    </div>
    <div class="mobile-tray-list">
      <button
        v-for="item in quickActions"
        :key="item.to"
        class="mobile-tray-item"
        type="button"
        @click="goTo(item.to)"
      >
        <span class="mobile-tray-item-title">{{ item.label }}</span>
        <span class="mobile-tray-item-meta">快速打开</span>
      </button>
      <button class="mobile-tray-item" type="button" @click="openShortcutHelp">
        <span class="mobile-tray-item-title">快捷键帮助</span>
        <span class="mobile-tray-item-meta">查看全局操作说明</span>
      </button>
      <button class="mobile-tray-item" type="button" @click="toggleNoticeCenter">
        <span class="mobile-tray-item-title">通知中心</span>
        <span class="mobile-tray-item-meta">
          {{ unreadCount ? `还有 ${unreadCount} 条未读消息` : '查看最近系统通知' }}
        </span>
      </button>
    </div>
  </div>

  <div v-if="showShortcutHelp" class="shortcut-help-mask" @click.self="closeShortcutHelp">
    <div class="shortcut-help-panel">
      <div class="shortcut-help-head">
        <div>
          <div class="shortcut-help-title">快捷键帮助</div>
          <div class="shortcut-help-subtitle">用键盘完成高频跳转和聊天操作。</div>
        </div>
        <button class="shortcut-help-close" @click="closeShortcutHelp">关闭</button>
      </div>

      <div class="shortcut-help-list">
        <div v-for="item in shortcutItems" :key="item.label" class="shortcut-help-item">
          <div class="shortcut-help-text">
            <div class="shortcut-help-label">{{ item.label }}</div>
            <div class="shortcut-help-desc">{{ item.description }}</div>
          </div>
          <div class="shortcut-help-keys">
            <span v-for="key in item.keys" :key="key" class="shortcut-key">{{ key }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EmptyState from '@/components/common/EmptyState.vue'
import { useToast, type ToastRecord } from '@/composables/useToast'
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

interface ShortcutItem {
  label: string
  description: string
  keys: string[]
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
const { toastHistory, unreadCount, clearHistory, markRead, markAllRead } = useToast()

const searchRef = ref<HTMLInputElement | null>(null)
const searchKeyword = ref('')
const showQuickJump = ref(false)
const showNoticeCenter = ref(false)
const showShortcutHelp = ref(false)
const showMobileRecentTray = ref(false)
const showMobileActionTray = ref(false)

const isAdmin = computed(() => (authStore.roles || '').includes('ROLE_ADMIN'))
const currentTitle = computed(() => (route.meta.title as string) || '控制台')

const quickActions = computed(() => {
  const items = [
    { label: 'AI 助手', to: '/chat' },
    { label: '知识库', to: '/rag' }
  ]

  if (isAdmin.value) {
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

  if (isAdmin.value) {
    base.push(
      { title: '运行监控', to: '/monitor', hint: '延迟、失败和反馈分析' },
      { title: '模型网关', to: '/gateway', hint: '模型路由和供应商配置' },
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

const groupedNotices = computed(() => {
  const groups = new Map<string, ToastRecord[]>()
  toastHistory.value.forEach((item) => {
    const label = getNoticeGroupLabel(item.createdAt)
    const current = groups.get(label) || []
    current.push(item)
    groups.set(label, current)
  })
  return [...groups.entries()].map(([label, items]) => ({ label, items }))
})

const shortcutItems = computed<ShortcutItem[]>(() => {
  const items: ShortcutItem[] = [
    {
      label: '打开快速跳转',
      description: '搜索页面、最近访问和常用入口。',
      keys: ['Ctrl/Cmd', 'K']
    },
    {
      label: '打开快捷键帮助',
      description: '查看全局快捷键说明。',
      keys: ['Shift', '?']
    },
    {
      label: '跳转控制台',
      description: '快速回到总览页。',
      keys: ['Alt', '1']
    },
    {
      label: '跳转 AI 助手',
      description: '打开聊天工作区。',
      keys: ['Alt', '2']
    },
    {
      label: '跳转知识库',
      description: '打开知识库管理页。',
      keys: ['Alt', '3']
    },
    {
      label: '跳转运行监控',
      description: '管理员可直接进入监控页面。',
      keys: ['Alt', '4']
    }
  ]

  if (route.path === '/chat') {
    items.push(
      {
        label: '新建会话',
        description: '在聊天页快速开启新对话。',
        keys: ['Ctrl/Cmd', 'Shift', 'N']
      },
      {
        label: '聚焦输入框',
        description: '将光标移动到聊天输入框。',
        keys: ['/']
      }
    )
  }

  return items
})

function openQuickJump() {
  showQuickJump.value = true
  showNoticeCenter.value = false
  showShortcutHelp.value = false
  showMobileRecentTray.value = false
  showMobileActionTray.value = false
}

function closeQuickJump() {
  showQuickJump.value = false
}

function focusQuickJump() {
  nextTick(() => {
    searchRef.value?.focus()
    searchRef.value?.select()
    showQuickJump.value = true
    showNoticeCenter.value = false
    showShortcutHelp.value = false
  })
}

function goTo(path: string) {
  searchKeyword.value = ''
  closeQuickJump()
  showMobileRecentTray.value = false
  showMobileActionTray.value = false
  router.push(path)
}

function toggleNoticeCenter() {
  showNoticeCenter.value = !showNoticeCenter.value
  if (showNoticeCenter.value) {
    closeQuickJump()
    closeShortcutHelp()
    showMobileRecentTray.value = false
    showMobileActionTray.value = false
  }
}

function closeNoticeCenter() {
  showNoticeCenter.value = false
}

function openShortcutHelp() {
  showShortcutHelp.value = true
  closeQuickJump()
  closeNoticeCenter()
  showMobileRecentTray.value = false
  showMobileActionTray.value = false
}

function closeShortcutHelp() {
  showShortcutHelp.value = false
}

function toggleMobileRecentTray() {
  showMobileRecentTray.value = !showMobileRecentTray.value
  if (showMobileRecentTray.value) {
    closeQuickJump()
    closeNoticeCenter()
    closeShortcutHelp()
    showMobileActionTray.value = false
  }
}

function toggleMobileActionTray() {
  showMobileActionTray.value = !showMobileActionTray.value
  if (showMobileActionTray.value) {
    closeQuickJump()
    closeNoticeCenter()
    closeShortcutHelp()
    showMobileRecentTray.value = false
  }
}

function handleRead(id: number) {
  markRead(id)
}

function handleClearHistory() {
  clearHistory()
  closeNoticeCenter()
}

function formatTime(value: number) {
  return new Date(value).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function getNoticeGroupLabel(value: number) {
  const now = new Date()
  const target = new Date(value)
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const yesterday = today - 24 * 60 * 60 * 1000
  const targetDay = new Date(target.getFullYear(), target.getMonth(), target.getDate()).getTime()

  if (targetDay === today) return '今天'
  if (targetDay === yesterday) return '昨天'
  return '更早'
}

function handleGlobalClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Node)) return
  if (!searchRef.value?.closest('.header-search-shell')?.contains(target)) {
    closeQuickJump()
  }
  const noticeShell = document.querySelector('.notice-shell')
  if (noticeShell && !noticeShell.contains(target)) {
    closeNoticeCenter()
  }
}

function handleFocusSearchShortcut() {
  focusQuickJump()
}

function handleOpenShortcutHelp() {
  openShortcutHelp()
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

onMounted(() => {
  window.addEventListener('app:focus-header-search', handleFocusSearchShortcut as EventListener)
  window.addEventListener('app:open-shortcut-help', handleOpenShortcutHelp as EventListener)
  window.addEventListener('click', handleGlobalClick)
})

onUnmounted(() => {
  window.removeEventListener('app:focus-header-search', handleFocusSearchShortcut as EventListener)
  window.removeEventListener('app:open-shortcut-help', handleOpenShortcutHelp as EventListener)
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

.recent-links {
  display: flex;
  gap: 8px;
  min-width: 0;
}

.mobile-entry-row,
.mobile-tray {
  display: none;
}

.notice-shell {
  position: relative;
}

.notice-btn {
  position: relative;
}

.notice-badge {
  margin-left: 6px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.notice-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 360px;
  max-height: 460px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.12);
  z-index: 20;
}

.notice-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 14px 10px;
  border-bottom: 1px solid var(--border);
}

.notice-head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.notice-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
}

.notice-subtitle {
  margin-top: 4px;
  font-size: 11px;
  color: var(--text3);
}

.notice-clear {
  border: none;
  background: transparent;
  color: var(--accent2);
  cursor: pointer;
  font-size: 12px;
}

.notice-clear:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.notice-list {
  max-height: 390px;
  overflow: auto;
  display: grid;
  gap: 12px;
  padding: 12px;
}

.notice-group {
  display: grid;
  gap: 8px;
}

.notice-group-title {
  font-size: 11px;
  color: var(--text3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding: 0 4px;
}

.notice-group-list {
  display: grid;
  gap: 8px;
}

.notice-item {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px 12px;
  background: var(--surface2);
  text-align: left;
  cursor: pointer;
}

.notice-item.unread {
  border-color: rgba(79, 142, 247, 0.28);
  background: rgba(79, 142, 247, 0.07);
}

.notice-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.notice-item-time,
.notice-item-read {
  font-size: 11px;
  color: var(--text3);
}

.notice-item-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #ef4444;
  flex: none;
}

.notice-item-text {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text);
  line-height: 1.5;
}

.shortcut-help-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.28);
  backdrop-filter: blur(8px);
}

.shortcut-help-panel {
  width: min(760px, 100%);
  max-height: min(80vh, 760px);
  overflow: auto;
  border-radius: 24px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background:
    radial-gradient(circle at top right, rgba(79, 142, 247, 0.15), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.98));
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.22);
}

.shortcut-help-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
}

.shortcut-help-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
}

.shortcut-help-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text2);
}

.shortcut-help-close {
  min-width: 72px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.78);
  color: var(--text);
  cursor: pointer;
}

.shortcut-help-list {
  display: grid;
  gap: 12px;
  padding: 18px 24px 24px;
}

.shortcut-help-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.68);
}

.shortcut-help-text {
  display: grid;
  gap: 4px;
}

.shortcut-help-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.shortcut-help-desc {
  font-size: 12px;
  color: var(--text2);
}

.shortcut-help-keys {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.shortcut-key {
  min-width: 32px;
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: rgba(248, 250, 252, 0.95);
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
  text-align: center;
  box-shadow: inset 0 -2px 0 rgba(148, 163, 184, 0.18);
}

@media (max-width: 960px) {
  .header-search-shell,
  .recent-links {
    display: none;
  }

  .mobile-entry-row {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .mobile-entry-btn {
    height: 32px;
    padding: 0 12px;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: var(--surface2);
    color: var(--text2);
    font-size: 12px;
    cursor: pointer;
  }

  .mobile-tray {
    display: grid;
    gap: 12px;
    margin-top: 12px;
    padding: 14px;
    border-radius: 18px;
    border: 1px solid var(--border);
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02));
  }

  .mobile-tray-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .mobile-tray-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--text);
  }

  .mobile-tray-subtitle {
    margin-top: 4px;
    font-size: 12px;
    color: var(--text3);
  }

  .mobile-tray-close {
    min-width: 56px;
    height: 30px;
    border-radius: 10px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--text2);
    cursor: pointer;
  }

  .mobile-tray-list {
    display: grid;
    gap: 8px;
  }

  .mobile-tray-item {
    display: grid;
    gap: 4px;
    width: 100%;
    padding: 12px;
    border-radius: 14px;
    border: 1px solid var(--border);
    background: var(--surface2);
    color: var(--text);
    text-align: left;
    cursor: pointer;
  }

  .mobile-tray-item-title {
    font-size: 13px;
    font-weight: 600;
  }

  .mobile-tray-item-meta {
    font-size: 11px;
    color: var(--text3);
  }
}

@media (max-width: 720px) {
  .notice-panel {
    width: min(92vw, 360px);
  }

  .notice-head,
  .shortcut-help-head,
  .shortcut-help-item {
    flex-direction: column;
    align-items: stretch;
  }

  .notice-head-actions,
  .shortcut-help-keys {
    justify-content: flex-start;
  }
}
</style>
