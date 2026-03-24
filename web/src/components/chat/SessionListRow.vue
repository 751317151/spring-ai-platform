<template>
  <div
    class="session-item"
    :class="{
      active: active,
      archived: archiveMode
    }"
    @click="$emit('select', session.sessionId)"
    @dblclick.stop="$emit('start-rename', session)"
  >
    <input
      v-if="isEditing"
      :ref="captureInput"
      :value="draftTitle"
      class="session-title-input"
      maxlength="50"
      @click.stop
      @input="$emit('update:draft-title', ($event.target as HTMLInputElement).value)"
      @keydown.enter.prevent="$emit('confirm-rename', session.sessionId)"
      @keydown.esc.prevent="$emit('cancel-rename')"
      @blur="$emit('confirm-rename', session.sessionId)"
    />

    <template v-else>
      <div class="session-main">
        <div class="session-title-row">
          <span v-if="isPinned" class="session-pin active">置顶</span>
          <span v-if="active" class="session-current">当前</span>
          <span class="session-title">
            <template v-for="(segment, index) in titleSegments" :key="`${session.sessionId}-${index}`">
              <mark v-if="segment.highlight" class="session-highlight">{{ segment.text }}</mark>
              <template v-else>{{ segment.text }}</template>
            </template>
          </span>
        </div>

        <div class="session-subtitle">
          <span>{{ subtitle }}</span>
          <span v-if="matchedField" class="session-match-hint">{{ matchedField }}</span>
        </div>
      </div>

      <div class="session-actions">
        <button class="session-action-btn" @click.stop="$emit('toggle-pin')">
          {{ isPinned ? '取消置顶' : '置顶' }}
        </button>
        <button class="session-action-btn" @click.stop="$emit('start-rename', session)">重命名</button>
        <button class="session-action-btn" @click.stop="$emit('toggle-archive')">
          {{ archiveMode ? '恢复' : '归档' }}
        </button>
        <button class="session-action-btn danger" @click.stop="$emit('delete')">删除</button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SessionInfo } from '@/api/types'

const props = defineProps<{
  session: SessionInfo
  active: boolean
  isEditing: boolean
  draftTitle: string
  captureInput?: (element: Element | null) => void
  keyword: string
  subtitle: string
  archiveMode?: boolean
}>()

defineEmits<{
  (event: 'select', sessionId: string): void
  (event: 'update:draft-title', value: string): void
  (event: 'confirm-rename', sessionId: string): void
  (event: 'cancel-rename'): void
  (event: 'start-rename', session: SessionInfo): void
  (event: 'toggle-pin'): void
  (event: 'toggle-archive'): void
  (event: 'delete'): void
}>()

const isPinned = computed(() => props.session.pinned === true || props.session.pinned === 'true')
const normalizedKeyword = computed(() => props.keyword.trim().toLowerCase())

const titleSegments = computed(() => {
  const title = props.session.summary || '新会话'
  const search = normalizedKeyword.value
  if (!search) return [{ text: title, highlight: false }]

  const lowerTitle = title.toLowerCase()
  const start = lowerTitle.indexOf(search)
  if (start === -1) return [{ text: title, highlight: false }]

  const end = start + search.length
  const segments = []
  if (start > 0) segments.push({ text: title.slice(0, start), highlight: false })
  segments.push({ text: title.slice(start, end), highlight: true })
  if (end < title.length) segments.push({ text: title.slice(end), highlight: false })
  return segments
})

const matchedField = computed(() => {
  const search = normalizedKeyword.value
  if (!search) return ''
  if ((props.session.summary || '').toLowerCase().includes(search)) return '标题匹配'
  if (props.session.sessionId.toLowerCase().includes(search)) return 'ID 匹配'
  return ''
})
</script>
