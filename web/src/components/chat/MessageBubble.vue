<template>
  <div class="msg" :class="role">
    <div class="msg-avatar" :class="role === 'user' ? 'user-av' : 'ai'">
      {{ role === 'user' ? 'U' : 'AI' }}
    </div>
    <div>
      <div class="msg-bubble" v-html="formattedContent"></div>
      <div class="msg-time">{{ timeLabel }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatMarkdown } from '@/utils/format'

const props = defineProps<{
  role: 'user' | 'assistant'
  content: string
}>()

const formattedContent = computed(() => formatMarkdown(props.content || ''))
const timeLabel = computed(() => {
  const now = new Date()
  return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
})
</script>
