<template>
  <div class="app-shell" :class="{ 'app-shell-collapsed': sidebarCollapsed }">
    <AppSidebar :collapsed="sidebarCollapsed" @toggle-collapse="toggleSidebar" />
    <div class="app-main">
      <AppHeader :collapsed="sidebarCollapsed" @toggle-collapse="toggleSidebar" />
      <main class="app-content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <Suspense>
              <component :is="Component" />
              <template #fallback>
                <div class="route-fallback card">
                  <div class="route-fallback-kicker">页面准备中</div>
                  <div class="route-fallback-title">正在加载当前工作区</div>
                  <SkeletonBlock :count="3" :height="84" variant="grid" :min-width="220" />
                </div>
              </template>
            </Suspense>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
  <ToastNotification />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSidebar from '@/components/common/AppSidebar.vue'
import AppHeader from '@/components/common/AppHeader.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import ToastNotification from '@/components/common/ToastNotification.vue'

const route = useRoute()
const router = useRouter()
const sidebarCollapsed = ref(localStorage.getItem('layout_sidebar_collapsed') === '1')

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem('layout_sidebar_collapsed', sidebarCollapsed.value ? '1' : '0')
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
    return
  }

  if (event.altKey) {
    if (event.key === '1') {
      event.preventDefault()
      router.push('/chat')
      return
    }
    if (event.key === '2') {
      event.preventDefault()
      router.push('/rag')
      return
    }
    if (event.key === '3') {
      event.preventDefault()
      router.push('/dashboard')
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalShortcuts)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalShortcuts)
})
</script>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.08), transparent 26%),
    radial-gradient(circle at bottom right, rgba(16, 185, 129, 0.08), transparent 24%),
    var(--bg);
}

.app-shell-collapsed {
  grid-template-columns: 72px minmax(0, 1fr);
}

.app-main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.app-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 18px 20px 22px;
  overflow-y: auto;
  overflow-x: hidden;
}

.route-fallback {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: 22px;
}

.route-fallback-kicker {
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text3);
}

.route-fallback-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

@media (max-width: 960px) {
  .app-shell,
  .app-shell-collapsed {
    grid-template-columns: 1fr;
  }

  .app-content {
    padding: 14px 14px 18px;
  }
}
</style>
