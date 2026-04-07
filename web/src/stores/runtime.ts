import { computed, reactive } from 'vue'
import { defineStore } from 'pinia'
import { DEMO_MODE_ENABLED } from '@/config/app-config'
import { GUEST_AUTH_MODE } from '@/utils/guest-mock'

export type BackendServiceKey = 'chat' | 'rag'

interface BackendStatus {
  available: boolean
  message: string
  updatedAt: number | null
}

const DEFAULT_MESSAGES: Record<BackendServiceKey, string> = {
  chat: '智能助手服务暂不可用，页面不会自动回退到模拟回答。',
  rag: '知识库服务暂不可用，页面不会自动回退到模拟答案。'
}

function createInitialStatus(): Record<BackendServiceKey, BackendStatus> {
  return {
    chat: { available: true, message: '', updatedAt: null },
    rag: { available: true, message: '', updatedAt: null }
  }
}

export const useRuntimeStore = defineStore('runtime', () => {
  const services = reactive(createInitialStatus())
  const authMode = computed(() => localStorage.getItem('auth_mode') || 'live')
  const demoMode = computed(() => DEMO_MODE_ENABLED || authMode.value === GUEST_AUTH_MODE)

  function setAuthMode(mode: string) {
    if (mode) {
      localStorage.setItem('auth_mode', mode)
      return
    }
    localStorage.removeItem('auth_mode')
  }

  function markServiceAvailable(service: BackendServiceKey) {
    services[service].available = true
    services[service].message = ''
    services[service].updatedAt = Date.now()
  }

  function markServiceUnavailable(service: BackendServiceKey, message?: string) {
    services[service].available = false
    services[service].message = message || DEFAULT_MESSAGES[service]
    services[service].updatedAt = Date.now()
  }

  function getServiceStatus(service: BackendServiceKey): BackendStatus {
    return services[service]
  }

  return {
    demoMode,
    authMode,
    services,
    setAuthMode,
    markServiceAvailable,
    markServiceUnavailable,
    getServiceStatus
  }
})
