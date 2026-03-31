<template>
  <div
    class="session-item"
    :class="{
      active,
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
          <span class="session-title">
            <template v-for="(segment, index) in titleSegments" :key="`${session.sessionId}-${index}`">
              <mark v-if="segment.highlight" class="session-highlight">{{ segment.text }}</mark>
              <template v-else>{{ segment.text }}</template>
            </template>
          </span>
        </div>

        <div class="session-subtitle">
          <span>{{ subtitle }}</span>
        </div>
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
  selected?: boolean
  hasDraft?: boolean
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
  (event: 'toggle-select', sessionId: string): void
}>()

const normalizedKeyword = computed(() => props.keyword.trim().toLowerCase())

const titleSegments = computed(() => {
  const title = props.session.summary || '新对话'
  const search = normalizedKeyword.value
  if (!search) return [{ text: title, highlight: false }]

  const lowerTitle = title.toLowerCase()
  const start = lowerTitle.indexOf(search)
  if (start === -1) return [{ text: title, highlight: false }]

  const end = start + search.length
  const segments: Array<{ text: string; highlight: boolean }> = []
  if (start > 0) segments.push({ text: title.slice(0, start), highlight: false })
  segments.push({ text: title.slice(start, end), highlight: true })
  if (end < title.length) segments.push({ text: title.slice(end), highlight: false })
  return segments
})
</script>

<style scoped>
.session-item {
  display: flex;
  align-items: flex-start;
  gap: 0;
}

.session-main {
  min-width: 0;
  flex: 1;
}

.session-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.session-title {
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  word-break: break-word;
}

.session-subtitle {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
  font-size: 11px;
  color: var(--text3);
}
</style>
