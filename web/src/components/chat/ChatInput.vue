<template>
  <div class="chat-input-area">
    <div class="prompt-toolbar">
      <button
        v-for="prompt in QUICK_PROMPTS"
        :key="prompt.label"
        class="prompt-chip"
        @click="insertPrompt(prompt.text)"
      >
        {{ prompt.label }}
      </button>
    </div>

    <div class="chat-input-wrap">
      <div class="input-meta-rail">
        <span class="input-mode-pill">{{ chatStore.isThinking ? '思考中' : '就绪' }}</span>
        <span class="input-mode-text">回车发送，Shift + Enter 换行，`/` 聚焦输入框，`Ctrl/Cmd + Shift + N` 新建会话</span>
      </div>

      <div class="chat-input-main">
        <textarea
          ref="inputRef"
          v-model="message"
          class="chat-input"
          placeholder="请输入消息、补充上下文，或直接描述你希望助手处理的任务..."
          rows="1"
          @keydown="handleKey"
          @input="autoResize"
        ></textarea>

        <div class="chat-input-actions">
          <button v-if="chatStore.isThinking" class="btn btn-ghost btn-sm" @click="chatStore.stopStreaming()">
            停止
          </button>
          <button class="send-btn" :disabled="!message.trim() || chatStore.isThinking" @click="send">
            发送
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { QUICK_PROMPTS } from '@/utils/constants'

const emit = defineEmits<{ send: [message: string] }>()
const chatStore = useChatStore()

const message = ref('')
const inputRef = ref<HTMLTextAreaElement>()

function focusInput() {
  nextTick(() => {
    inputRef.value?.focus()
    autoResize()
  })
}

function setMessage(value: string, mode: 'replace' | 'append' = 'replace') {
  message.value = mode === 'append' && message.value ? `${message.value}${value}` : value
  chatStore.setDraft(chatStore.currentSessionId, message.value)
  focusInput()
}

function insertPrompt(text: string) {
  setMessage(text)
}

function handleKey(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

function autoResize() {
  const el = inputRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`
  }
}

function send() {
  const msg = message.value.trim()
  if (!msg || chatStore.isThinking) {
    return
  }
  emit('send', msg)
  message.value = ''
  chatStore.clearDraft(chatStore.currentSessionId)
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
    inputRef.value.focus()
  }
}

function handleFocusShortcut() {
  focusInput()
}

function syncDraftFromSession() {
  message.value = chatStore.getDraft(chatStore.currentSessionId)
  nextTick(() => autoResize())
}

defineExpose({
  focusInput,
  setMessage
})

onMounted(() => {
  syncDraftFromSession()
  window.addEventListener('app:focus-chat-input', handleFocusShortcut as EventListener)
})

onUnmounted(() => {
  window.removeEventListener('app:focus-chat-input', handleFocusShortcut as EventListener)
})

watch(() => message.value, (value) => {
  chatStore.setDraft(chatStore.currentSessionId, value)
})

watch(() => [chatStore.currentSessionId, chatStore.currentAgent], () => {
  syncDraftFromSession()
})
</script>
