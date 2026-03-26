import { computed, ref } from 'vue'

export interface ToastRecord {
  id: number
  message: string
  createdAt: number
  readAt: number | null
}

const toastMessage = ref('')
const isVisible = ref(false)
const toastHistory = ref<ToastRecord[]>([])

let timer: ReturnType<typeof setTimeout> | null = null
let toastId = 0

export function useToast() {
  function showToast(message: string, duration = 2800) {
    const record: ToastRecord = {
      id: ++toastId,
      message,
      createdAt: Date.now(),
      readAt: null
    }

    toastMessage.value = message
    isVisible.value = true
    toastHistory.value = [record, ...toastHistory.value].slice(0, 30)

    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      isVisible.value = false
    }, duration)
  }

  function clearHistory() {
    toastHistory.value = []
  }

  function markRead(id: number) {
    toastHistory.value = toastHistory.value.map((item) =>
      item.id === id && item.readAt == null
        ? { ...item, readAt: Date.now() }
        : item
    )
  }

  function markAllRead() {
    const now = Date.now()
    toastHistory.value = toastHistory.value.map((item) =>
      item.readAt == null
        ? { ...item, readAt: now }
        : item
    )
  }

  const unreadCount = computed(() =>
    Math.min(
      toastHistory.value.filter((item) => item.readAt == null).length,
      99
    )
  )

  return {
    toastMessage,
    isVisible,
    toastHistory,
    unreadCount,
    showToast,
    clearHistory,
    markRead,
    markAllRead
  }
}
