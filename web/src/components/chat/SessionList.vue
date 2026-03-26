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

    <div v-if="recentVisitedSessions.length" class="session-recent-panel">
      <div class="session-recent-title">最近访问</div>
      <div class="session-recent-list">
        <button
          v-for="session in recentVisitedSessions"
          :key="session.sessionId"
          class="session-recent-chip"
          @click="handleSelect(session.sessionId)"
        >
          <span class="session-recent-name">{{ session.summary || '未命名会话' }}</span>
          <span class="session-recent-time">{{ formatUpdatedAt(session.updatedAt) }}</span>
        </button>
      </div>
    </div>

    <div v-if="selectedCount" class="session-batch-bar">
      <span class="session-batch-count">已选 {{ selectedCount }} 项</span>
      <div class="session-batch-actions">
        <button class="session-toolbar-btn" :disabled="batchOperating" @click="selectAllVisible">
          全选当前结果
        </button>
        <button class="session-toolbar-btn" :disabled="batchOperating" @click="handleBatchArchive">
          {{ batchArchiveLabel }}
        </button>
        <button class="session-toolbar-btn danger" :disabled="batchOperating" @click="handleBatchDelete">
          批量删除
        </button>
        <button class="session-toolbar-btn" :disabled="batchOperating" @click="clearSelection">清空选择</button>
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
        <strong>{{ draftSessions.length }}</strong>
        <span>有草稿</span>
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
      <div class="session-filter-row">
        <button
          v-for="filter in filters"
          :key="filter.value"
          class="session-filter-chip"
          :class="{ active: activeFilter === filter.value }"
          @click="activeFilter = filter.value"
        >
          {{ filter.label }}
          <span>{{ filter.count }}</span>
        </button>
      </div>
      <div class="session-search-meta">
        <span>{{ searchSummary }}</span>
        <div class="session-search-tools">
          <button v-if="allVisibleSessions.length" class="session-clear-search" @click="selectAllVisible">全选当前结果</button>
          <button v-if="keyword || activeFilter !== 'all'" class="session-clear-search" @click="resetFilters">重置</button>
        </div>
      </div>
    </div>

    <div
      v-if="!visibleGroups.length && (!chatStore.showArchivedSessions || !filteredArchivedSessions.length)"
      class="session-empty-search"
    >
      当前筛选条件下没有匹配的会话。
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
          :subtitle="buildSubtitle(session)"
          :selected="selectedSessionIds.has(session.sessionId)"
          :has-draft="hasDraft(session.sessionId)"
          @select="handleSelect"
          @toggle-select="toggleSelection"
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
          :subtitle="buildSubtitle(session)"
          :selected="selectedSessionIds.has(session.sessionId)"
          :has-draft="hasDraft(session.sessionId)"
          archive-mode
          @select="handleSelect"
          @toggle-select="toggleSelection"
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
import { computed, nextTick, ref, watch } from 'vue'
import type { SessionInfo } from '@/api/types'
import SessionListRow from './SessionListRow.vue'
import { useChatStore } from '@/stores/chat'
import { useToast } from '@/composables/useToast'

type FilterValue = 'all' | 'pinned' | 'draft'

const RECENT_VISITED_KEY = 'chat_recent_visited_sessions'

const chatStore = useChatStore()
const { showToast } = useToast()

const editingSessionId = ref<string | null>(null)
const draftTitle = ref('')
const titleInput = ref<HTMLInputElement | null>(null)
const keyword = ref('')
const batchOperating = ref(false)
const selectedSessionIds = ref<Set<string>>(new Set())
const activeFilter = ref<FilterValue>('all')

const hasSessions = computed(() => chatStore.activeSessions.length > 0 || chatStore.archivedSessions.length > 0)
const filteredActiveSessions = computed(() => filterSessions(chatStore.activeSessions))
const filteredArchivedSessions = computed(() => filterSessions(chatStore.archivedSessions))
const allVisibleSessions = computed(() => [...filteredActiveSessions.value, ...filteredArchivedSessions.value])
const selectedSessions = computed(() => allVisibleSessions.value.filter((session) => selectedSessionIds.value.has(session.sessionId)))
const selectedCount = computed(() => selectedSessions.value.length)
const pinnedSessions = computed(() => filteredActiveSessions.value.filter((session) => isPinned(session)))
const draftSessions = computed(() => filteredActiveSessions.value.filter((session) => hasDraft(session.sessionId)))
const todaySessions = computed(() => filteredActiveSessions.value.filter((session) => !isPinned(session) && isToday(session.updatedAt)))
const yesterdaySessions = computed(() => filteredActiveSessions.value.filter((session) => !isPinned(session) && isYesterday(session.updatedAt)))
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

const filters = computed(() => [
  { value: 'all' as const, label: '全部', count: chatStore.activeSessions.length },
  { value: 'pinned' as const, label: '仅看置顶', count: chatStore.activeSessions.filter((session) => isPinned(session)).length },
  { value: 'draft' as const, label: '仅看草稿', count: chatStore.activeSessions.filter((session) => hasDraft(session.sessionId)).length }
])

const recentVisitedSessions = computed(() => {
  try {
    const raw = localStorage.getItem(RECENT_VISITED_KEY)
    const ids = raw ? (JSON.parse(raw) as string[]) : []
    return ids
      .filter((id) => id !== chatStore.currentSessionId)
      .map((id) => chatStore.sessionList.find((session) => session.sessionId === id))
      .filter((session): session is SessionInfo => Boolean(session))
      .slice(0, 4)
  } catch {
    return []
  }
})

const searchSummary = computed(() => {
  const totalMatches = filteredActiveSessions.value.length + filteredArchivedSessions.value.length
  if (!keyword.value && activeFilter.value === 'all') {
    return `共有 ${filteredActiveSessions.value.length} 个进行中的会话`
  }
  return `当前筛选命中 ${totalMatches} 项`
})

const batchArchiveLabel = computed(() => {
  if (!selectedSessions.value.length) return '批量归档'
  const allArchived = selectedSessions.value.every((session) => isArchived(session))
  const allActive = selectedSessions.value.every((session) => !isArchived(session))
  if (allArchived) return '批量恢复'
  if (allActive) return '批量归档'
  return '批量切换归档'
})

watch(
  allVisibleSessions,
  (sessions) => {
    const visibleIds = new Set(sessions.map((session) => session.sessionId))
    selectedSessionIds.value = new Set([...selectedSessionIds.value].filter((sessionId) => visibleIds.has(sessionId)))
  },
  { immediate: true }
)

function filterSessions(list: SessionInfo[]) {
  const search = keyword.value.trim().toLowerCase()
  return list.filter((session) => {
    const summary = (session.summary || '').toLowerCase()
    const matchesSearch = !search || summary.includes(search) || session.sessionId.toLowerCase().includes(search)
    if (!matchesSearch) return false
    if (activeFilter.value === 'pinned') return isPinned(session)
    if (activeFilter.value === 'draft') return hasDraft(session.sessionId)
    return true
  })
}

function hasDraft(sessionId: string) {
  return chatStore.getDraft(sessionId).trim().length > 0
}

function isPinned(session: SessionInfo) {
  return session.pinned === true || session.pinned === 'true'
}

function isArchived(session: SessionInfo) {
  return session.archived === true || session.archived === 'true'
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
  return Math.floor((currentDay - targetDay) / 86_400_000)
}

function toggleSelection(sessionId: string) {
  const next = new Set(selectedSessionIds.value)
  if (next.has(sessionId)) next.delete(sessionId)
  else next.add(sessionId)
  selectedSessionIds.value = next
}

function clearSelection() {
  selectedSessionIds.value = new Set()
}

function selectAllVisible() {
  selectedSessionIds.value = new Set(allVisibleSessions.value.map((session) => session.sessionId))
}

function resetFilters() {
  keyword.value = ''
  activeFilter.value = 'all'
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
  if (title) {
    await chatStore.renameSession(sessionId, title)
  }
  cancelRename()
}

async function handleDelete(sessionId: string) {
  await chatStore.deleteSession(sessionId)
  if (selectedSessionIds.value.has(sessionId)) {
    const next = new Set(selectedSessionIds.value)
    next.delete(sessionId)
    selectedSessionIds.value = next
  }
}

async function handleBatchArchive() {
  if (!selectedSessions.value.length || batchOperating.value) return
  const actionLabel = batchArchiveLabel.value
  batchOperating.value = true
  try {
    const sessions = [...selectedSessions.value]
    for (const session of sessions) {
      await chatStore.toggleArchiveSession(session.sessionId)
    }
    showToast(`${actionLabel.replace('批量', '')}完成`)
    clearSelection()
  } finally {
    batchOperating.value = false
  }
}

async function handleBatchDelete() {
  if (!selectedSessions.value.length || batchOperating.value) return
  batchOperating.value = true
  try {
    const sessions = [...selectedSessions.value]
    for (const session of sessions) {
      await chatStore.deleteSession(session.sessionId)
    }
    showToast(`已删除 ${sessions.length} 个会话`)
    clearSelection()
  } finally {
    batchOperating.value = false
  }
}

function setTitleInput(element: Element | null) {
  titleInput.value = element as HTMLInputElement | null
}

function formatUpdatedAt(updatedAt?: string) {
  if (!updatedAt) return '刚刚'
  const timestamp = Number(updatedAt)
  if (Number.isNaN(timestamp)) return updatedAt
  const date = new Date(timestamp)
  const hour = date.getHours().toString().padStart(2, '0')
  const minute = date.getMinutes().toString().padStart(2, '0')
  if (isToday(updatedAt)) return `今天 ${hour}:${minute}`
  if (isYesterday(updatedAt)) return `昨天 ${hour}:${minute}`
  return `${date.getMonth() + 1}/${date.getDate()} ${hour}:${minute}`
}

function buildSubtitle(session: SessionInfo) {
  return hasDraft(session.sessionId)
    ? `${formatUpdatedAt(session.updatedAt)} · 有草稿`
    : formatUpdatedAt(session.updatedAt)
}
</script>

<style scoped>
.session-recent-panel {
  margin: 0 4px 12px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: rgba(79, 142, 247, 0.05);
}

.session-recent-title {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--text2);
}

.session-recent-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.session-recent-chip {
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--surface);
  text-align: left;
  cursor: pointer;
}

.session-recent-name {
  font-size: 12px;
  color: var(--text);
}

.session-recent-time {
  font-size: 11px;
  color: var(--text3);
}

.session-batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  margin: 0 4px 10px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: rgba(59, 130, 246, 0.06);
}

.session-batch-count {
  font-size: 12px;
  color: var(--text2);
}

.session-batch-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.session-toolbar-btn.danger {
  color: #dc2626;
}

.session-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 0 4px 10px;
}

.session-overview-item {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--surface2);
  text-align: center;
}

.session-search-wrap {
  display: grid;
  gap: 8px;
  margin: 0 4px 10px;
}

.session-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.session-filter-chip {
  border: 1px solid var(--border);
  border-radius: 999px;
  background: transparent;
  color: var(--text2);
  padding: 4px 10px;
  cursor: pointer;
}

.session-filter-chip.active {
  border-color: rgba(79, 142, 247, 0.28);
  background: rgba(79, 142, 247, 0.1);
  color: var(--text);
}

.session-filter-chip span {
  margin-left: 4px;
  font-size: 11px;
  color: var(--text3);
}

.session-search-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--text3);
}

.session-search-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-empty-search {
  margin: 0 4px;
  padding: 12px;
  border-radius: 12px;
  border: 1px dashed var(--border);
  color: var(--text3);
  font-size: 12px;
}

@media (max-width: 720px) {
  .session-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .session-search-meta,
  .session-batch-bar {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
