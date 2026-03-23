<template>
  <div class="chat-input-area">
    <div style="display: flex; gap: 6px; margin-bottom: 8px; flex-wrap: wrap">
      <button
        v-for="prompt in QUICK_PROMPTS"
        :key="prompt.label"
        class="btn btn-ghost btn-sm"
        @click="insertPrompt(prompt.text)"
      >
        {{ prompt.label }}
      </button>
    </div>
    <div class="chat-input-wrap">
      <textarea
        ref="inputRef"
        v-model="message"
        class="chat-input"
        placeholder="输入消息，Shift+Enter 换行..."
        rows="1"
        @keydown="handleKey"
        @input="autoResize"
      ></textarea>
      <button class="send-btn" :disabled="!message.trim() || chatStore.isThinking" @click="send">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import { QUICK_PROMPTS } from '@/utils/constants'

const emit = defineEmits<{ send: [message: string] }>()
const chatStore = useChatStore()

const message = ref('')
const inputRef = ref<HTMLTextAreaElement>()

function insertPrompt(text: string) {
  message.value = text
  inputRef.value?.focus()
}

function handleKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function autoResize() {
  const el = inputRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 120)}px`
  }
}

function send() {
  const msg = message.value.trim()
  if (!msg || chatStore.isThinking) {
    return
  }
  emit('send', msg)
  message.value = ''
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }
}
</script>
