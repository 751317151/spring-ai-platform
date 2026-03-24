<template>
  <div v-if="hasSessions" class="session-list">
    <div class="session-list-label">
      <span>会话列表</span>
      <div class="session-list-actions">
        <button class="session-toolbar-btn" @click="chatStore.toggleArchivedVisibility()">
          {{ chatStore.showArchivedSessions ? '隐藏归档' : `归档（${chatStore.archivedSessions.length}）` }}
        </button>
        <button class="btn-new-chat" @click="chatStore.createNewSession()">+ 新建</button>
      </div>
    </div>

    <div class="session-overview">
      <div class="session-overview-item">
        <strong>{{ filteredActiveSessions.length }}</strong>
        <span>进行中</span>
      </div>
      <div class="session-overview-item">
        <strong>{{ pinnedSessions.length }}</strong>
        <span>置顶</span>
      </div>
      <div class="session-overview-item">
        <strong>{{ filteredArchivedSessions.length }}</strong>
        <span>已归档</span>
      </div>
    </div>

    <div class="session-search-wrap">
      <input
        v-model.trim="keyword"
        class="session-search"
        type="text"
        placeholder="按标题或会话 ID 搜索"
      />
      <div class="session-search-meta">
        <span>{{ searchSummary }}</span>
        <button v-if="keyword" class="session-clear-search" @click="keyword = ''">清空</button>
      </div>
    </div>

    <div
      v-if="!visibleGroups.length && (!chatStore.showArchivedSessions || !filteredArchivedSessions.length)"
      class="session-empty-search"
    >
      当前搜索条件下没有匹配的会话。
    </div>

    <template v-else>
      <div v-for="group in visibleGroups" :key="group.key" class="session-group">
        <div class="session-group-title">
          <span>{{ group.label }}</span>
          <span class="session-group-count">{{ group.sessions.length }}</span>
        </div>

        <SessionListRow
          v-for="session in group.sessions"
          :key="session.sessionId"
          :session="session"
          :active="chatStore.currentSessionId === session.sessionId"
          :is-editing="editingSessionId === session.sessionId"
          :draft-title="draftTitle"
          :capture-input="setTitleInput"
          :keyword="keyword"
          :subtitle="formatUpdatedAt(session.updatedAt)"
          @select="handleSelect"
          @update:draft-title="draftTitle = $event"
          @confirm-rename="confirmRename"
          @cancel-rename="cancelRename"
          @start-rename="startRename"
          @toggle-pin="chatStore.togglePinSession(session.sessionId)"
          @toggle-archive="chatStore.toggleArchiveSession(session.sessionId)"
          @delete="handleDelete(session.sessionId)"
        />
      </div>

      <div v-if="chatStore.showArchivedSessions && filteredArchivedSessions.length > 0" class="session-archived-block">
        <div class="session-archived-title">
          <span>已归档</span>
          <span class="session-group-count">{{ filteredArchivedSessions.length }}</span>
        </div>

        <SessionListRow
          v-for="session in filteredArchivedSessions"
          :key="session.sessionId"
          :session="session"
          :active="chatStore.currentSessionId === session.sessionId"
          :is-editing="editingSessionId === session.sessionId"
          :draft-title="draftTitle"
          :capture-input="setTitleInput"
          :keyword="keyword"
          :subtitle="formatUpdatedAt(session.updatedAt)"
          archive-mode
          @select="handleSelect"
          @update:draft-title="draftTitle = $event"
          @confirm-rename="confirmRename"
          @cancel-rename="cancelRename"
          @start-rename="startRename"
          @toggle-pin="chatStore.togglePinSession(session.sessionId)"
          @toggle-archive="chatStore.toggleArchiveSession(session.sessionId)"
          @delete="handleDelete(session.sessionId)"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import type { SessionInfo } from '@/api/types'
import SessionListRow from './SessionListRow.vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const editingSessionId = ref<string | null>(null)
const draftTitle = ref('')
const titleInput = ref<HTMLInputElement | null>(null)
const keyword = ref('')

const hasSessions = computed(() => chatStore.activeSessions.length > 0 || chatStore.archivedSessions.length > 0)
const filteredActiveSessions = computed(() => filterSessions(chatStore.activeSessions))
const filteredArchivedSessions = computed(() => filterSessions(chatStore.archivedSessions))
const pinnedSessions = computed(() => filteredActiveSessions.value.filter((session) => isPinned(session)))
const todaySessions = computed(() => filteredActiveSessions.value.filter((session) => !isPinned(session) && isToday(session.updatedAt)))
const yesterdaySessions = computed(() =>
  filteredActiveSessions.value.filter((session) => !isPinned(session) && isYesterday(session.updatedAt))
)
const earlierSessions = computed(() =>
  filteredActiveSessions.value.filter((session) => !isPinned(session) && !isToday(session.updatedAt) && !isYesterday(session.updatedAt))
)

const visibleGroups = computed(() =>
  [
    { key: 'pinned', label: '置顶', sessions: pinnedSessions.value },
    { key: 'today', label: '今天', sessions: todaySessions.value },
    { key: 'yesterday', label: '昨天', sessions: yesterdaySessions.value },
    { key: 'earlier', label: '更早', sessions: earlierSessions.value }
  ].filter((group) => group.sessions.length > 0)
)

const searchSummary = computed(() => {
  if (!keyword.value) {
    return `共有 ${filteredActiveSessions.value.length} 个进行中的会话`
  }
  const totalMatches = filteredActiveSessions.value.length + filteredArchivedSessions.value.length
  return `搜索“${keyword.value}”匹配到 ${totalMatches} 项`
})

function filterSessions(list: SessionInfo[]) {
  const search = keyword.value.trim().toLowerCase()
  if (!search) return list
  return list.filter((session) => {
    const summary = (session.summary || '').toLowerCase()
    return summary.includes(search) || session.sessionId.toLowerCase().includes(search)
  })
}

function isPinned(session: SessionInfo) {
  return session.pinned === true || session.pinned === 'true'
}

function isToday(updatedAt?: string) {
  return getDayOffset(updatedAt) === 0
}

function isYesterday(updatedAt?: string) {
  return getDayOffset(updatedAt) === 1
}

function getDayOffset(updatedAt?: string) {
  const timestamp = Number(updatedAt || 0)
  if (!timestamp) return 0
  const date = new Date(timestamp)
  const now = new Date()
  const targetDay = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  const currentDay = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  return Math.floor((currentDay - targetDay) / 86400000)
}

function handleSelect(sessionId: string) {
  if (editingSessionId.value === sessionId) return
  chatStore.switchSession(sessionId)
}

function startRename(session: SessionInfo) {
  editingSessionId.value = session.sessionId
  draftTitle.value = session.summary || '新会话'
  nextTick(() => titleInput.value?.focus())
}

function cancelRename() {
  editingSessionId.value = null
  draftTitle.value = ''
  titleInput.value = null
}

async function confirmRename(sessionId: string) {
  const title = draftTitle.value.trim()
  if (title) await chatStore.renameSession(sessionId, title)
  cancelRename()
}

async function handleDelete(sessionId: string) {
  await chatStore.deleteSession(sessionId)
}

function setTitleInput(element: Element | null) {
  titleInput.value = element as HTMLInputElement | null
}

function formatUpdatedAt(updatedAt?: string) {
  if (!updatedAt) return '刚刚'
  const timestamp = Number(updatedAt)
  if (Number.isNaN(timestamp)) return updatedAt
  const date = new Date(timestamp)
  if (isToday(updatedAt)) return `今天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  if (isYesterday(updatedAt)) return `昨天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}
</script>

<style scoped>
.session-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 0 4px 10px;
}

.session-overview-item {
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.02);
}

.session-overview-item strong {
  display: block;
  color: var(--text);
  font-size: 14px;
  line-height: 1;
}

.session-overview-item span {
  display: block;
  margin-top: 6px;
  color: var(--text3);
  font-size: 11px;
}

.session-group {
  margin-top: 8px;
}

.session-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 6px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text3);
}

.session-group-count {
  min-width: 22px;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--text3);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
}

.session-empty-search {
  padding: 14px 12px;
  text-align: center;
  color: var(--text3);
  font-size: 12px;
  border: 1px dashed var(--border);
  border-radius: 12px;
}

.session-search-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  padding: 0 4px;
  color: var(--text3);
  font-size: 11px;
}

.session-clear-search {
  border: none;
  background: transparent;
  color: var(--accent2);
  cursor: pointer;
  font-size: 11px;
}

.session-archived-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
