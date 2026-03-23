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
      :response-id="msg.responseId"
      :feedback="msg.feedback"
      @feedback="handleFeedback(idx, $event)"
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
import { useToast } from '@/composables/useToast'

const chatStore = useChatStore()
const messagesRef = ref<HTMLElement>()
const { showToast } = useToast()

const welcomeMessage = computed(() => {
  const config = chatStore.getAgentConfig()
  return `你好，我是 ${config.name}，可以帮你处理 ${config.desc}。你可以直接开始提问。`
})

async function handleFeedback(messageIndex: number, feedback: 'up' | 'down') {
  try {
    const saved = await chatStore.submitFeedback(messageIndex, feedback)
    if (saved) {
      showToast(feedback === 'up' ? '已记录正向反馈' : '已记录负向反馈')
    }
  } catch {
    showToast('反馈提交失败，请稍后重试')
  }
}

watch(
  () => chatStore.chatHistory.length,
  async () => {
    await nextTick()
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  }
)

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
