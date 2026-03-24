<template>
  <div class="msg" :class="role">
    <div class="msg-avatar" :class="role === 'user' ? 'user-av' : 'ai'">
      {{ role === 'user' ? 'U' : 'AI' }}
    </div>
    <div class="msg-content-wrap">
      <div class="msg-header-line">
        <span class="msg-role-label">{{ role === 'user' ? '用户' : '助手' }}</span>
        <span class="msg-time">{{ timeLabel }}</span>
      </div>
      <div class="msg-bubble" v-html="formattedContent"></div>
      <div class="msg-meta">
        <button v-if="content" class="msg-text-btn" @click="copyContent">复制</button>
        <button v-if="content" class="msg-text-btn" @click="quoteContent">引用</button>
        <button v-if="role === 'assistant' && content" class="msg-text-btn" @click="followUp">继续追问</button>
        <div v-if="showFeedback" class="msg-actions">
          <button class="feedback-btn" :class="{ active: feedback === 'up' }" @click="$emit('feedback', 'up')">
            有帮助
          </button>
          <button class="feedback-btn" :class="{ active: feedback === 'down' }" @click="$emit('feedback', 'down')">
            不准确
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatMarkdown } from '@/utils/format'
import { useToast } from '@/composables/useToast'

const props = defineProps<{
  role: 'user' | 'assistant'
  content: string
  responseId?: string
  feedback?: 'up' | 'down' | null
}>()

const emit = defineEmits<{
  (e: 'feedback', value: 'up' | 'down'): void
  (e: 'insert-prompt', value: string): void
}>()

const { showToast } = useToast()

const formattedContent = computed(() => formatMarkdown(props.content || ''))
const showFeedback = computed(() => props.role === 'assistant' && !!props.responseId && !!props.content)
const timeLabel = computed(() => {
  const now = new Date()
  return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
})

async function copyContent() {
  try {
    await navigator.clipboard.writeText(props.content || '')
    showToast('消息已复制')
  } catch {
    showToast('复制消息失败')
  }
}

function quoteContent() {
  emit('insert-prompt', `> ${compactContent(props.content)}\n\n`)
}

function followUp() {
  emit('insert-prompt', `基于这段回答，继续帮我推进：${compactContent(props.content, 160)}`)
}

function compactContent(value: string, maxLength = 120) {
  const normalized = (value || '').replace(/\s+/g, ' ').trim()
  if (normalized.length <= maxLength) {
    return normalized
  }
  return `${normalized.slice(0, maxLength)}...`
}
</script>

<style scoped>
.msg-content-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.msg-header-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.msg-role-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text2);
  letter-spacing: 0.04em;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.msg-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.msg-text-btn,
.feedback-btn {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text3);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition);
}

.msg-text-btn:hover,
.feedback-btn:hover {
  color: var(--text);
  border-color: var(--border2);
  background: var(--surface2);
}

.feedback-btn.active {
  border-color: var(--accent);
  color: var(--accent2);
  background: rgba(59, 130, 246, 0.08);
}
</style>
