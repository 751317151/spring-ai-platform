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
        <router-view />
      </div>
    </div>
  </div>
  <ToastNotification />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSidebar from '@/components/common/AppSidebar.vue'
import AppHeader from '@/components/common/AppHeader.vue'
import ToastNotification from '@/components/common/ToastNotification.vue'

interface RecentView {
  title: string
  to: string
}

const route = useRoute()
const router = useRouter()
const sidebarCollapsed = ref(localStorage.getItem('layout_sidebar_collapsed') === '1')
const recentViews = ref<RecentView[]>([])

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

function dispatchShortcut(name: 'app:new-chat' | 'app:focus-chat-input' | 'app:focus-header-search') {
  window.dispatchEvent(new CustomEvent(name))
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

watch(sidebarCollapsed, (value) => {
  localStorage.setItem('layout_sidebar_collapsed', value ? '1' : '0')
}, { immediate: true })

loadRecentViews()

watch(
  () => route.fullPath,
  () => {
    const title = typeof route.meta.title === 'string' ? route.meta.title : ''
    if (!title || route.path === '/login') {
      return
    }

    recentViews.value = [
      { title, to: route.fullPath },
      ...recentViews.value.filter((item) => item.to !== route.fullPath)
    ].slice(0, 6)

    persistRecentViews()
  },
  { immediate: true }
)

onMounted(() => {
  window.addEventListener('keydown', handleGlobalShortcuts)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalShortcuts)
})
</script>
