<template>
  <div class="msg" :class="role">
    <div class="msg-avatar" :class="role === 'user' ? 'user-av' : 'ai'">
      {{ role === 'user' ? 'U' : 'AI' }}
    </div>
    <div>
      <div class="msg-bubble" v-html="formattedContent"></div>
      <div class="msg-meta">
        <div class="msg-time">{{ timeLabel }}</div>
        <div v-if="showFeedback" class="msg-actions">
          <button
            class="feedback-btn"
            :class="{ active: feedback === 'up' }"
            @click="$emit('feedback', 'up')"
          >
            👍
          </button>
          <button
            class="feedback-btn"
            :class="{ active: feedback === 'down' }"
            @click="$emit('feedback', 'down')"
          >
            👎
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatMarkdown } from '@/utils/format'

const props = defineProps<{
  role: 'user' | 'assistant'
  content: string
  responseId?: string
  feedback?: 'up' | 'down' | null
}>()

defineEmits<{
  (e: 'feedback', value: 'up' | 'down'): void
}>()

const formattedContent = computed(() => formatMarkdown(props.content || ''))
const showFeedback = computed(() => props.role === 'assistant' && !!props.responseId && !!props.content)
const timeLabel = computed(() => {
  const now = new Date()
  return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
})
</script>

<style scoped>
.msg-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}

.msg-actions {
  display: flex;
  gap: 6px;
}

.feedback-btn {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text3);
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  cursor: pointer;
}

.feedback-btn.active {
  border-color: var(--brand);
  color: var(--brand);
  background: rgba(59, 130, 246, 0.08);
}
</style>
