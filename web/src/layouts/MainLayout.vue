<template>
  <div class="app" :class="{ 'app-collapsed': sidebarCollapsed }">
    <AppSidebar
      :collapsed="sidebarCollapsed"
      :recent-views="recentViews"
      @toggle-collapse="toggleSidebar"
    />
    <div class="main">
      <AppHeader
        :collapsed="sidebarCollapsed"
        :recent-views="recentViews"
        @toggle-collapse="toggleSidebar"
      />
      <div class="content">
        <div v-if="routePending" class="route-loading-mask" aria-live="polite">
          <div class="route-loading-card">
            <div class="route-loading-label">页面加载中</div>
            <div class="route-loading-title">正在切换到目标页面，请稍候。</div>
            <SkeletonBlock :count="3" :height="88" variant="grid" :min-width="220" />
          </div>
        </div>

        <router-view v-slot="{ Component, route: currentRoute }">
          <transition name="page-fade" mode="out-in">
            <Suspense @pending="handleViewPending" @resolve="handleViewResolved">
              <component :is="Component" :key="currentRoute.fullPath" />
              <template #fallback>
                <div class="route-fallback-shell">
                  <div class="route-fallback-head">
                    <div class="route-fallback-kicker">页面准备中</div>
                    <div class="route-fallback-copy">正在挂载视图和数据面板。</div>
                  </div>
                  <SkeletonBlock :count="4" :height="92" variant="grid" :min-width="240" />
                </div>
              </template>
            </Suspense>
          </transition>
        </router-view>
      </div>
    </div>
  </div>
  <ToastNotification />
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSidebar from '@/components/common/AppSidebar.vue'
import AppHeader from '@/components/common/AppHeader.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import ToastNotification from '@/components/common/ToastNotification.vue'

interface RecentView {
  title: string
  to: string
}

const route = useRoute()
const router = useRouter()
const sidebarCollapsed = ref(localStorage.getItem('layout_sidebar_collapsed') === '1')
const recentViews = ref<RecentView[]>([])
const routePending = ref(false)

let routePendingStartedAt = 0
let routePendingTimer: ReturnType<typeof setTimeout> | null = null
let routeSettledTimer: ReturnType<typeof setTimeout> | null = null

function loadRecentViews() {
  try {
    const raw = localStorage.getItem('layout_recent_views')
    recentViews.value = raw ? JSON.parse(raw) : []
  } catch {
    recentViews.value = []
  }
}

function persistRecentViews() {
  localStorage.setItem('layout_recent_views', JSON.stringify(recentViews.value))
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function isEditableTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) {
    return false
  }
  const tag = target.tagName.toLowerCase()
  return tag === 'input' || tag === 'textarea' || target.isContentEditable
}

function dispatchShortcut(name: 'app:new-chat' | 'app:focus-chat-input' | 'app:focus-header-search' | 'app:open-shortcut-help') {
  window.dispatchEvent(new CustomEvent(name))
}

function clearRoutePendingTimer() {
  if (routePendingTimer) {
    clearTimeout(routePendingTimer)
    routePendingTimer = null
  }
  if (routeSettledTimer) {
    clearTimeout(routeSettledTimer)
    routeSettledTimer = null
  }
}

function beginRoutePending() {
  clearRoutePendingTimer()
  routePendingStartedAt = Date.now()
  routePending.value = true
}

function finishRoutePending() {
  clearRoutePendingTimer()
  const elapsed = Date.now() - routePendingStartedAt
  const delay = Math.max(0, 180 - elapsed)
  routePendingTimer = window.setTimeout(() => {
    routePending.value = false
    routePendingTimer = null
  }, delay)
}

function handleGlobalShortcuts(event: KeyboardEvent) {
  if (isEditableTarget(event.target)) {
    return
  }

  if (event.altKey) {
    if (event.key === '1') {
      event.preventDefault()
      router.push('/dashboard')
      return
    }
    if (event.key === '2') {
      event.preventDefault()
      router.push('/chat')
      return
    }
    if (event.key === '3') {
      event.preventDefault()
      router.push('/rag')
      return
    }
    if (event.key === '4') {
      event.preventDefault()
      router.push('/monitor')
      return
    }
  }

  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    dispatchShortcut('app:focus-header-search')
    return
  }

  if (event.shiftKey && event.key === '?') {
    event.preventDefault()
    dispatchShortcut('app:open-shortcut-help')
    return
  }

  if (route.path === '/chat' && (event.ctrlKey || event.metaKey) && event.shiftKey && event.key.toLowerCase() === 'n') {
    event.preventDefault()
    dispatchShortcut('app:new-chat')
    return
  }

  if (route.path === '/chat' && event.key === '/') {
    event.preventDefault()
    dispatchShortcut('app:focus-chat-input')
  }
}

function handleViewPending() {
  beginRoutePending()
}

function handleViewResolved() {
  finishRoutePending()
}

function scheduleRouteSettled() {
  if (routeSettledTimer) {
    clearTimeout(routeSettledTimer)
  }
  routeSettledTimer = window.setTimeout(() => {
    routeSettledTimer = null
    finishRoutePending()
  }, 0)
}

watch(sidebarCollapsed, (value) => {
  localStorage.setItem('layout_sidebar_collapsed', value ? '1' : '0')
}, { immediate: true })

loadRecentViews()

watch(
  () => route.fullPath,
  async () => {
    beginRoutePending()

    const title = typeof route.meta.title === 'string' ? route.meta.title : ''
    if (!title || route.path === '/login') {
      await nextTick()
      scheduleRouteSettled()
      return
    }

    recentViews.value = [
      { title, to: route.fullPath },
      ...recentViews.value.filter((item) => item.to !== route.fullPath)
    ].slice(0, 6)

    persistRecentViews()

    await nextTick()
    scheduleRouteSettled()
  },
  { immediate: true }
)

onMounted(() => {
  window.addEventListener('keydown', handleGlobalShortcuts)
})

onUnmounted(() => {
  clearRoutePendingTimer()
  window.removeEventListener('keydown', handleGlobalShortcuts)
})
</script>

<style scoped>
.route-loading-mask {
  position: sticky;
  top: 0;
  z-index: 5;
  margin-bottom: 16px;
  pointer-events: none;
}

.route-loading-card,
.route-fallback-shell {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: 20px;
  background:
    radial-gradient(circle at top right, rgba(79, 142, 247, 0.12), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02));
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.08);
}

.route-loading-label,
.route-fallback-kicker {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text3);
}

.route-loading-title,
.route-fallback-copy {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}
</style>
