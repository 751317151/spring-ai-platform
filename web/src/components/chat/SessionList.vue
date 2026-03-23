<template>
  <div v-if="chatStore.activeSessions.length > 0 || chatStore.archivedSessions.length > 0" class="session-list">
    <div class="session-list-label">
      <span>对话列表</span>
      <div class="session-list-actions">
        <button class="session-toolbar-btn" @click="chatStore.toggleArchivedVisibility()">
          {{ chatStore.showArchivedSessions ? '隐藏归档' : `查看归档(${chatStore.archivedSessions.length})` }}
        </button>
        <button class="btn-new-chat" @click="chatStore.createNewSession()">+ 新对话</button>
      </div>
    </div>

    <div
      v-for="session in chatStore.activeSessions"
      :key="session.sessionId"
      class="session-item"
      :class="{ active: chatStore.currentSessionId === session.sessionId }"
      @click="handleSelect(session.sessionId)"
      @dblclick.stop="startRename(session)"
    >
      <input
        v-if="editingSessionId === session.sessionId"
        ref="titleInput"
        v-model="draftTitle"
        class="session-title-input"
        maxlength="50"
        @click.stop
        @keydown.enter.prevent="confirmRename(session.sessionId)"
        @keydown.esc.prevent="cancelRename"
        @blur="confirmRename(session.sessionId)"
      />
      <template v-else>
        <span class="session-pin" :class="{ active: isPinned(session) }">{{ isPinned(session) ? '置顶' : '' }}</span>
        <span class="session-title">{{ session.summary || '新对话' }}</span>
        <button class="session-action-btn" @click.stop="chatStore.togglePinSession(session.sessionId)">
          {{ isPinned(session) ? '取消置顶' : '置顶' }}
        </button>
        <button class="session-action-btn" @click.stop="startRename(session)">重命名</button>
        <button class="session-action-btn" @click.stop="chatStore.toggleArchiveSession(session.sessionId)">归档</button>
        <button class="session-action-btn danger" @click.stop="handleDelete(session.sessionId)">删除</button>
      </template>
    </div>

    <div v-if="chatStore.showArchivedSessions && chatStore.archivedSessions.length > 0" class="session-archived-block">
      <div class="session-archived-title">已归档</div>
      <div
        v-for="session in chatStore.archivedSessions"
        :key="session.sessionId"
        class="session-item archived"
        :class="{ active: chatStore.currentSessionId === session.sessionId }"
        @click="handleSelect(session.sessionId)"
        @dblclick.stop="startRename(session)"
      >
        <input
          v-if="editingSessionId === session.sessionId"
          ref="titleInput"
          v-model="draftTitle"
          class="session-title-input"
          maxlength="50"
          @click.stop
          @keydown.enter.prevent="confirmRename(session.sessionId)"
          @keydown.esc.prevent="cancelRename"
          @blur="confirmRename(session.sessionId)"
        />
        <template v-else>
          <span class="session-pin" :class="{ active: isPinned(session) }">{{ isPinned(session) ? '置顶' : '' }}</span>
          <span class="session-title">{{ session.summary || '新对话' }}</span>
          <button class="session-action-btn" @click.stop="chatStore.togglePinSession(session.sessionId)">
            {{ isPinned(session) ? '取消置顶' : '置顶' }}
          </button>
          <button class="session-action-btn" @click.stop="startRename(session)">重命名</button>
          <button class="session-action-btn" @click.stop="chatStore.toggleArchiveSession(session.sessionId)">取消归档</button>
          <button class="session-action-btn danger" @click.stop="handleDelete(session.sessionId)">删除</button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import type { SessionInfo } from '@/api/types'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()

const editingSessionId = ref<string | null>(null)
const draftTitle = ref('')
const titleInput = ref<HTMLInputElement | null>(null)

function isPinned(session: SessionInfo) {
  return session.pinned === true || session.pinned === 'true'
}

function handleSelect(sessionId: string) {
  if (editingSessionId.value === sessionId) {
    return
  }
  chatStore.switchSession(sessionId)
}

function startRename(session: SessionInfo) {
  editingSessionId.value = session.sessionId
  draftTitle.value = session.summary || '新对话'
  nextTick(() => titleInput.value?.focus())
}

function cancelRename() {
  editingSessionId.value = null
  draftTitle.value = ''
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
}
</script>

<style scoped>
.session-list-actions {
  display: flex;
  gap: 6px;
}

.session-toolbar-btn {
  border: none;
  background: transparent;
  color: var(--text3);
  font-size: 11px;
  cursor: pointer;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.session-item.archived {
  opacity: 0.78;
}

.session-archived-block {
  margin-top: 8px;
  border-top: 1px dashed var(--border);
  padding-top: 8px;
}

.session-archived-title {
  padding: 4px 8px;
  font-size: 11px;
  color: var(--text3);
}

.session-pin {
  min-width: 28px;
  font-size: 10px;
  color: transparent;
}

.session-pin.active {
  color: var(--accent);
}

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-title-input {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
  padding: 6px 8px;
  color: var(--text);
}

.session-action-btn {
  border: none;
  background: transparent;
  color: var(--text3);
  font-size: 11px;
  cursor: pointer;
}

.session-action-btn.danger {
  color: #b42318;
}
</style>
