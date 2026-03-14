<template>
  <div class="chat-messages" ref="messagesRef">
    <div v-if="!chatStore.chatHistory.length" class="msg ai">
      <div class="msg-avatar ai">AI</div>
      <div>
        <div class="msg-bubble">{{ welcomeMessage }}</div>
        <div class="msg-time">刚刚</div>
      </div>
    </div>
    <MessageBubble
      v-for="(msg, idx) in chatStore.chatHistory"
      :key="idx"
      :role="msg.role"
      :content="msg.content"
    />
    <div v-if="chatStore.isThinking" class="msg ai">
      <div class="msg-avatar ai">AI</div>
      <div>
        <div class="msg-bubble">
          <div class="thinking">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import MessageBubble from './MessageBubble.vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const messagesRef = ref<HTMLElement>()

const welcomeMessage = computed(() => {
  const config = chatStore.getAgentConfig()
  return `你好！我是${config.name}，可以帮你进行${config.desc}。你可以直接把问题或代码粘贴过来。`
})

watch(
  () => chatStore.chatHistory.length,
  async () => {
    await nextTick()
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  }
)

// Also scroll on content changes (streaming)
watch(
  () => chatStore.chatHistory[chatStore.chatHistory.length - 1]?.content,
  async () => {
    await nextTick()
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  }
)
</script>
