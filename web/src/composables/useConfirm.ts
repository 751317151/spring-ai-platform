import { ref } from 'vue'

type ConfirmIntent = 'default' | 'danger'

interface ConfirmOptions {
  title?: string
  description?: string
  confirmText?: string
  cancelText?: string
  intent?: ConfirmIntent
}

const visible = ref(false)
const title = ref('请确认操作')
const description = ref('')
const confirmText = ref('确认')
const cancelText = ref('取消')
const intent = ref<ConfirmIntent>('default')
let resolver: ((value: boolean) => void) | null = null

export function useConfirm() {
  function confirm(options: ConfirmOptions = {}): Promise<boolean> {
    visible.value = true
    title.value = options.title || '请确认操作'
    description.value = options.description || ''
    confirmText.value = options.confirmText || '确认'
    cancelText.value = options.cancelText || '取消'
    intent.value = options.intent || 'default'

    return new Promise((resolve) => {
      resolver = resolve
    })
  }

  function resolveConfirm(result: boolean) {
    visible.value = false
    resolver?.(result)
    resolver = null
  }

  return {
    visible,
    title,
    description,
    confirmText,
    cancelText,
    intent,
    confirm,
    resolveConfirm
  }
}
