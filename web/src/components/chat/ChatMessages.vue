<template>
  <div class="chat-messages" ref="messagesRef" @scroll="handleScroll">
    <div v-if="!chatStore.chatHistory.length" class="chat-empty-state">
      <div class="chat-empty-icon">{{ chatStore.getAgentConfig().icon }}</div>
      <div class="chat-empty-title">{{ chatStore.getAgentConfig().name }}</div>
      <div class="chat-empty-desc">{{ welcomeMessage }}</div>

      <div class="onboarding-panel">
        <div class="onboarding-title">开始使用</div>
        <div class="onboarding-steps">
          <div class="onboarding-step">
            <span>1</span>
            <div>从左侧选择最符合当前任务的助手。</div>
          </div>
          <div class="onboarding-step">
            <span>2</span>
            <div>尽量直接地说明目标、上下文和约束条件。</div>
          </div>
          <div class="onboarding-step">
            <span>3</span>
            <div>尽量在同一会话中继续追问，以保持上下文连续。</div>
          </div>
        </div>
      </div>

      <div class="chat-empty-prompts">
        <button
          v-for="prompt in QUICK_PROMPTS"
          :key="prompt.label"
          class="chat-empty-prompt"
          @click="$emit('use-prompt', prompt.text)"
        >
          <span>{{ prompt.label }}</span>
          <small>{{ prompt.text }}</small>
        </button>
      </div>
    </div>

    <template v-else>
      <MessageBubble
        v-for="(msg, idx) in chatStore.chatHistory"
        :key="idx"
        :role="msg.role"
        :content="msg.content"
        :response-id="msg.responseId"
        :feedback="msg.feedback"
        @feedback="handleFeedback(idx, $event)"
        @insert-prompt="$emit('insert-prompt', $event)"
      />

      <div v-if="followUpSuggestions.length || recentPrompts.length" class="chat-followup-panel">
        <div v-if="followUpSuggestions.length" class="followup-block">
          <div class="followup-title">推荐追问</div>
          <div class="followup-chip-list">
            <button
              v-for="item in followUpSuggestions"
              :key="item"
              class="followup-chip"
              @click="usePrompt(item, true)"
            >
              {{ item }}
            </button>
          </div>
        </div>

        <div v-if="recentPrompts.length" class="followup-block">
          <div class="followup-title">最近追问</div>
          <div class="followup-chip-list">
            <button
              v-for="item in recentPrompts"
              :key="item"
              class="followup-chip recent"
              @click="usePrompt(item, false)"
            >
              {{ item }}
            </button>
          </div>
        </div>
      </div>
    </template>

    <div v-if="chatStore.isThinking" class="msg ai">
      <div class="msg-avatar ai">AI</div>
      <div>
        <div class="msg-bubble">
          <div class="thinking">
            <span></span><span></span><span></span>
          </div>
        </div>
        <div class="msg-time">正在生成回复...</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import MessageBubble from './MessageBubble.vue'
import { useChatStore } from '@/stores/chat'
import { useToast } from '@/composables/useToast'
import { QUICK_PROMPTS } from '@/utils/constants'

const emit = defineEmits<{
  (e: 'use-prompt', value: string): void
  (e: 'insert-prompt', value: string): void
}>()

const chatStore = useChatStore()
const messagesRef = ref<HTMLElement>()
const { showToast } = useToast()
const recentPrompts = ref<string[]>([])
const RECENT_FOLLOWUP_KEY = 'chat_recent_followups'
const SCROLL_POSITION_KEY = 'chat_session_scroll_positions'
const scrollPositions = ref<Record<string, number>>({})
const restoringScroll = ref(false)

const welcomeMessage = computed(() => {
  const config = chatStore.getAgentConfig()
  return `你正在与 ${config.name} 对话，可以使用下方推荐提示词，或直接描述任务。`
})

const lastAssistantMessage = computed(() => {
  return [...chatStore.chatHistory].reverse().find((item) => item.role === 'assistant')?.content || ''
})

const followUpSuggestions = computed(() => {
  const excerpt = compactContent(lastAssistantMessage.value, 96)
  if (!excerpt) {
    return []
  }
  return [
    `请展开说明这一点：${excerpt}`,
    `请把这段回答整理成分步骤计划：${excerpt}`,
    '这里还需要关注哪些风险或边界情况？'
  ]
})

async function handleFeedback(messageIndex: number, feedback: 'up' | 'down') {
  try {
    const saved = await chatStore.submitFeedback(messageIndex, feedback)
    if (saved) {
      showToast(feedback === 'up' ? '已记录正向反馈' : '已记录负向反馈')
    }
  } catch {
    showToast('提交反馈失败')
  }
}

function usePrompt(value: string, save = true) {
  if (save) {
    saveRecentPrompt(value)
  }
  emit('insert-prompt', value)
}

function saveRecentPrompt(value: string) {
  const normalized = value.trim()
  if (!normalized) {
    return
  }
  recentPrompts.value = [normalized, ...recentPrompts.value.filter((item) => item !== normalized)].slice(0, 6)
  window.sessionStorage.setItem(RECENT_FOLLOWUP_KEY, JSON.stringify(recentPrompts.value))
}

function compactContent(value: string, maxLength = 120) {
  const normalized = value.replace(/\s+/g, ' ').trim()
  if (normalized.length <= maxLength) {
    return normalized
  }
  return `${normalized.slice(0, maxLength)}...`
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function getSessionScrollKey() {
  return `${chatStore.currentAgent}:${chatStore.currentSessionId || 'draft'}`
}

function isNearBottom() {
  const el = messagesRef.value
  if (!el) {
    return true
  }
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function saveScrollPosition() {
  const el = messagesRef.value
  if (!el) {
    return
  }
  scrollPositions.value[getSessionScrollKey()] = el.scrollTop
  window.sessionStorage.setItem(SCROLL_POSITION_KEY, JSON.stringify(scrollPositions.value))
}

async function restoreScrollPosition() {
  await nextTick()
  const el = messagesRef.value
  if (!el) {
    return
  }

  const saved = scrollPositions.value[getSessionScrollKey()]
  if (typeof saved === 'number') {
    el.scrollTop = saved
    return
  }

  if (chatStore.chatHistory.length > 0 || chatStore.isThinking) {
    el.scrollTop = el.scrollHeight
    return
  }

  el.scrollTop = 0
}

function handleScroll() {
  if (restoringScroll.value) {
    return
  }
  saveScrollPosition()
}

watch(
  () => `${chatStore.currentAgent}:${chatStore.currentSessionId || ''}`,
  async (_value, oldValue) => {
    if (oldValue && messagesRef.value) {
      scrollPositions.value[oldValue] = messagesRef.value.scrollTop
    }
    restoringScroll.value = true
    await restoreScrollPosition()
    restoringScroll.value = false
  },
  { immediate: true }
)

watch(
  () => chatStore.chatHistory.length,
  async () => {
    if (restoringScroll.value) {
      return
    }
    if (isNearBottom()) {
      await scrollToBottom()
      saveScrollPosition()
      return
    }
    saveScrollPosition()
  }
)

watch(
  () => chatStore.chatHistory[chatStore.chatHistory.length - 1]?.content,
  async () => {
    if (restoringScroll.value) {
      return
    }
    if (isNearBottom()) {
      await scrollToBottom()
      saveScrollPosition()
      return
    }
    saveScrollPosition()
  }
)

watch(
  () => chatStore.isThinking,
  async () => {
    if (restoringScroll.value) {
      return
    }
    if (isNearBottom()) {
      await scrollToBottom()
      saveScrollPosition()
      return
    }
    saveScrollPosition()
  }
)

onMounted(() => {
  try {
    const stored = window.sessionStorage.getItem(RECENT_FOLLOWUP_KEY)
    recentPrompts.value = stored ? JSON.parse(stored) : []
  } catch {
    recentPrompts.value = []
  }

  try {
    const stored = window.sessionStorage.getItem(SCROLL_POSITION_KEY)
    scrollPositions.value = stored ? JSON.parse(stored) : {}
  } catch {
    scrollPositions.value = {}
  }

  restoreScrollPosition()
})
</script>

<style scoped>
.onboarding-panel {
  width: 100%;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  border-radius: 16px;
  padding: 14px 16px;
}

.onboarding-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 10px;
}

.onboarding-steps {
  display: grid;
  gap: 10px;
}

.onboarding-step {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 10px;
  align-items: start;
  color: var(--text2);
  font-size: 12px;
  line-height: 1.6;
}

.onboarding-step span {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-dim);
  color: var(--accent2);
  font-weight: 600;
}

.chat-followup-panel {
  display: grid;
  gap: 12px;
  padding-top: 4px;
}

.followup-block {
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.03);
}

.followup-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 10px;
  letter-spacing: 0.04em;
}

.followup-chip-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.followup-chip {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text2);
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition);
}

.followup-chip:hover {
  color: var(--text);
  background: var(--surface2);
  border-color: var(--border2);
}
</style>
