import { ref } from 'vue'

const toastMessage = ref('')
const isVisible = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null

export function useToast() {
  function showToast(message: string, duration = 2800) {
    toastMessage.value = message
    isVisible.value = true
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      isVisible.value = false
    }, duration)
  }

  return { toastMessage, isVisible, showToast }
}
