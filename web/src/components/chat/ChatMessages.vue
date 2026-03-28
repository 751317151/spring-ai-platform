<template>
  <div class="chat-messages" ref="messagesRef" @scroll="handleScroll">
    <div v-if="!chatStore.chatHistory.length" class="chat-empty-state">
      <div class="chat-empty-hero">
        <div class="chat-empty-icon">{{ chatStore.getAgentConfig().icon }}</div>
        <div class="chat-empty-copy">
          <div class="chat-empty-title">{{ chatStore.getAgentConfig().name }}</div>
          <div class="chat-empty-desc">{{ welcomeMessage }}</div>
        </div>
      </div>

      <div class="onboarding-panel">
        <div class="onboarding-title">开始使用</div>
        <div class="onboarding-steps">
          <div class="onboarding-step">
            <span>1</span>
            <div>先从左侧选择最适合当前任务的助手角色。</div>
          </div>
          <div class="onboarding-step">
            <span>2</span>
            <div>尽量直接说明目标、上下文和限制条件，回答会更稳定。</div>
          </div>
          <div class="onboarding-step">
            <span>3</span>
            <div>在同一会话里持续追问，可以保留上下文并逐步推进结果。</div>
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
        :trace-id="msg.traceId"
        :feedback="msg.feedback"
        :session-config-snapshot="msg.sessionConfigSnapshot"
        :agent-type="chatStore.currentAgent"
        :session-id="chatStore.currentSessionId || undefined"
        :session-summary="chatStore.sessionList.find((item) => item.sessionId === chatStore.currentSessionId)?.summary"
        :message-index="idx"
        :highlighted="highlightedMessageIndex === idx"
        :derived-from="msg.derivedFrom"
        @feedback="handleFeedback(idx, $event)"
        @insert-prompt="$emit('insert-prompt', $event)"
        @branch-session="$emit('branch-session', idx)"
        @continue-response="$emit('continue-response', idx)"
        @regenerate-response="$emit('regenerate-response', idx)"
        @open-trace="$emit('open-trace', $event)"
      />

      <div v-if="followUpSuggestions.length || recentPrompts.length" class="chat-followup-panel">
        <div v-if="followUpSuggestions.length" class="followup-block">
          <div class="followup-title">推荐追问</div>
          <div class="followup-chip-list">
            <button v-for="item in followUpSuggestions" :key="item" class="followup-chip" @click="usePrompt(item, true)">
              {{ item }}
            </button>
          </div>
        </div>

        <div v-if="recentPrompts.length" class="followup-block recent-block">
          <div class="followup-title">最近追问</div>
          <div class="followup-chip-list">
            <button v-for="item in recentPrompts" :key="item" class="followup-chip recent" @click="usePrompt(item, false)">
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
import { useToast } from '@/composables/useToast'
import { useChatStore } from '@/stores/chat'
import { QUICK_PROMPTS } from '@/utils/constants'
import MessageBubble from './MessageBubble.vue'

const emit = defineEmits<{
  (e: 'use-prompt', value: string): void
  (e: 'insert-prompt', value: string): void
  (e: 'branch-session', messageIndex: number): void
  (e: 'continue-response', messageIndex: number): void
  (e: 'regenerate-response', messageIndex: number): void
  (e: 'open-trace', traceId: string): void
}>()

const props = withDefaults(defineProps<{
  highlightedMessageIndex?: number | null
}>(), {
  highlightedMessageIndex: null
})

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
  return `你正在与 ${config.name} 对话。可以直接描述任务，也可以先点下面的快捷提示。`
})

const lastAssistantMessage = computed(() => [...chatStore.chatHistory].reverse().find((item) => item.role === 'assistant')?.content || '')

const followUpSuggestions = computed(() => {
  const excerpt = compactContent(lastAssistantMessage.value, 96)
  if (!excerpt) {
    return []
  }
  return [
    `请展开说明这一点：${excerpt}`,
    `请把这段回答整理成分步骤计划：${excerpt}`,
    '这里还需要关注哪些风险、边界条件或后续动作？'
  ]
})

async function handleFeedback(messageIndex: number, feedback: 'up' | 'down') {
  try {
    const saved = await chatStore.submitFeedback(messageIndex, feedback)
    if (saved) {
      showToast(feedback === 'up' ? '已记录正向反馈' : '已记录改进反馈')
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

async function scrollToHighlightedMessage() {
  await nextTick()
  const el = messagesRef.value
  if (!el || props.highlightedMessageIndex === null || props.highlightedMessageIndex === undefined) {
    return
  }
  const target = el.querySelector<HTMLElement>(`.msg[data-message-index="${props.highlightedMessageIndex}"]`)
  if (target && typeof target.scrollIntoView === 'function') {
    target.scrollIntoView({ block: 'center', behavior: 'auto' })
  }
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
    await scrollToHighlightedMessage()
    restoringScroll.value = false
  },
  { immediate: true }
)

watch(
  () => props.highlightedMessageIndex,
  async () => {
    await scrollToHighlightedMessage()
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
  scrollToHighlightedMessage()
})
</script>

<style scoped>
.chat-empty-hero {
  display: flex;
  gap: 16px;
  align-items: center;
}

.chat-empty-copy {
  display: grid;
  gap: 6px;
}

.onboarding-panel {
  width: 100%;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02));
  border-radius: 20px;
  padding: 16px 18px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

.onboarding-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 12px;
}

.onboarding-steps {
  display: grid;
  gap: 12px;
}

.onboarding-step {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 12px;
  align-items: start;
  color: var(--text2);
  font-size: 12px;
  line-height: 1.7;
}

.onboarding-step span {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(79, 142, 247, 0.16), rgba(79, 142, 247, 0.08));
  color: var(--accent2);
  font-weight: 700;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.chat-followup-panel {
  display: grid;
  gap: 12px;
  padding-top: 6px;
}

.followup-block {
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 14px 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02));
}

.recent-block {
  background: linear-gradient(180deg, rgba(79, 142, 247, 0.05), rgba(255, 255, 255, 0.02));
}

.followup-title {
  font-size: 12px;
  font-weight: 700;
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
  padding: 7px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition);
}

.followup-chip:hover {
  color: var(--text);
  background: var(--surface2);
  border-color: var(--border2);
}

@media (max-width: 820px) {
  .chat-empty-hero {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
