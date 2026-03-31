<template>
  <div class="chat-page">
    <aside class="chat-sidebar">
      <div class="sidebar-card assistant-panel" ref="assistantPanelRef">
        <div class="assistant-panel-copy">
          <div class="panel-kicker">当前助手</div>
          <div class="assistant-title-row">
            <span
              class="assistant-icon"
              :style="{ background: `${agentConfig.color}22`, color: agentConfig.color }"
            >
              {{ agentConfig.icon }}
            </span>
            <div class="assistant-copy">
              <div class="assistant-name">{{ agentConfig.name }}</div>
              <div class="assistant-desc">{{ agentConfig.desc || '选择一个助手开始对话' }}</div>
            </div>
          </div>
        </div>

        <button
          class="assistant-trigger"
          type="button"
          :disabled="chatStore.isThinking"
          @click="toggleAgentMenu"
        >
          <span>切换助手</span>
          <span class="assistant-trigger-value">{{ currentAgentShortDesc }}</span>
          <span class="assistant-trigger-arrow">{{ showAgentMenu ? '收起' : '展开' }}</span>
        </button>

        <div v-if="showAgentMenu" class="assistant-menu">
          <button
            v-for="agent in chatStore.agentList"
            :key="agent.type"
            class="assistant-option"
            :class="{ active: chatStore.currentAgent === agent.type }"
            type="button"
            @click="handleSelectAgent(agent.type)"
          >
            <span
              class="assistant-option-icon"
              :style="{ background: `${agent.color}22`, color: agent.color }"
            >
              {{ agent.icon }}
            </span>
            <span class="assistant-option-copy">
              <span class="assistant-option-name">{{ agent.name }}</span>
              <span class="assistant-option-desc">{{ agent.desc || '智能助手' }}</span>
            </span>
          </button>
        </div>
      </div>

      <div class="sidebar-card sessions-panel">
        <div class="panel-head">
          <div>
            <div class="panel-kicker">会话</div>
            <div class="panel-title">{{ currentSessionLabel }}</div>
          </div>
          <button class="panel-action" type="button" @click="createNewChat">新建</button>
        </div>

        <div class="sessions-scroll">
          <SessionList />
        </div>
      </div>
    </aside>

    <section class="chat-main">
      <header class="chat-main-head">
        <div class="chat-main-copy">
          <div class="chat-main-title">{{ agentConfig.name }}</div>
          <div class="chat-main-subtitle">
            {{ currentSessionLabel }}
            <span class="chat-main-divider">/</span>
            {{ chatStore.isThinking ? '正在回复' : '可以直接开始提问' }}
          </div>
        </div>

        <div class="chat-main-actions">
          <button class="head-btn" type="button" @click="focusChatInput">聚焦输入</button>
          <button class="head-btn" type="button" @click="handleClearChat">清空会话</button>
        </div>
      </header>

      <div class="chat-main-body">
        <ChatMessages
          @use-prompt="handleInsertPrompt"
          @insert-prompt="handleInsertPrompt"
          @branch-session="handleHiddenFeature"
          @continue-response="handleHiddenFeature"
          @regenerate-response="handleHiddenFeature"
          @open-trace="handleHiddenFeature"
        />
      </div>

      <footer class="chat-main-input">
        <ChatInput ref="chatInputRef" @send="handleSend" />
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import type { AgentType } from '@/api/types'
import ChatInput from '@/components/chat/ChatInput.vue'
import ChatMessages from '@/components/chat/ChatMessages.vue'
import SessionList from '@/components/chat/SessionList.vue'
import { useToast } from '@/composables/useToast'
import { useChatStore } from '@/stores/chat'

interface ChatInputExpose {
  focusInput: () => void
  setMessage: (value: string, mode?: 'replace' | 'append') => void
}

const chatStore = useChatStore()
const { showToast } = useToast()

const chatInputRef = ref<ChatInputExpose | null>(null)
const assistantPanelRef = ref<HTMLElement | null>(null)
const showAgentMenu = ref(false)

const agentConfig = computed(() => chatStore.getAgentConfig())
const currentSessionLabel = computed(() => {
  const current = chatStore.sessionList.find((item) => item.sessionId === chatStore.currentSessionId)
  return current?.summary || '新对话'
})
const currentAgentShortDesc = computed(() => agentConfig.value.desc || agentConfig.value.name)

async function bootstrap() {
  await chatStore.loadAvailableBots()
  await chatStore.loadSessions()
}

async function handleSelectAgent(type: AgentType) {
  showAgentMenu.value = false
  if (type === chatStore.currentAgent) {
    return
  }
  await chatStore.selectAgent(type)
}

function toggleAgentMenu() {
  showAgentMenu.value = !showAgentMenu.value
}

async function handleSend(message: string) {
  await chatStore.sendMessage(message)
}

function handleInsertPrompt(message: string) {
  chatInputRef.value?.setMessage(message)
  focusChatInput()
}

function focusChatInput() {
  nextTick(() => {
    chatInputRef.value?.focusInput()
  })
}

function createNewChat() {
  chatStore.createNewSession()
  focusChatInput()
}

async function handleClearChat() {
  await chatStore.clearChat()
  focusChatInput()
}

function handleHiddenFeature() {
  showToast('当前页面已切换为精简模式，先保留核心对话能力。')
}

function handleGlobalClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Node)) {
    return
  }
  if (!assistantPanelRef.value?.contains(target)) {
    showAgentMenu.value = false
  }
}

function handleNewChatShortcut() {
  createNewChat()
}

onMounted(async () => {
  window.addEventListener('click', handleGlobalClick)
  window.addEventListener('app:new-chat', handleNewChatShortcut as EventListener)
  await bootstrap()
  focusChatInput()
})

onUnmounted(() => {
  window.removeEventListener('click', handleGlobalClick)
  window.removeEventListener('app:new-chat', handleNewChatShortcut as EventListener)
})
</script>

<style scoped>
.chat-page {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 18px;
  height: calc(100vh - 124px);
  min-height: 680px;
}

.chat-sidebar,
.chat-main {
  min-height: 0;
}

.chat-sidebar {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 14px;
}

.sidebar-card,
.chat-main {
  border: 1px solid var(--border);
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(26, 30, 38, 0.96), rgba(19, 22, 27, 0.98));
  box-shadow: 0 18px 40px rgba(2, 6, 23, 0.24);
}

.assistant-panel {
  position: relative;
  padding: 18px;
  display: grid;
  gap: 14px;
}

.assistant-panel-copy,
.panel-head,
.assistant-title-row,
.assistant-copy,
.assistant-option,
.assistant-option-copy,
.chat-main-head,
.chat-main-copy {
  min-width: 0;
}

.panel-kicker {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text3);
}

.assistant-title-row {
  margin-top: 10px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.assistant-icon,
.assistant-option-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  flex: none;
  font-size: 18px;
  font-weight: 700;
}

.assistant-name,
.panel-title,
.chat-main-title {
  color: var(--text);
  font-size: 18px;
  font-weight: 700;
}

.assistant-desc,
.chat-main-subtitle {
  margin-top: 4px;
  color: var(--text2);
  font-size: 13px;
  line-height: 1.6;
}

.assistant-trigger,
.panel-action,
.head-btn {
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
  cursor: pointer;
  transition: border-color var(--transition), box-shadow var(--transition), transform var(--transition);
}

.assistant-trigger:hover,
.panel-action:hover,
.head-btn:hover {
  transform: translateY(-1px);
  border-color: rgba(59, 130, 246, 0.28);
  box-shadow: 0 10px 24px rgba(59, 130, 246, 0.08);
}

.assistant-trigger {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-height: 48px;
  padding: 0 14px;
  border-radius: 16px;
  text-align: left;
}

.assistant-trigger-value {
  min-width: 0;
  color: var(--text2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.assistant-trigger-arrow {
  color: var(--text3);
  font-size: 12px;
}

.assistant-menu {
  position: absolute;
  top: calc(100% - 8px);
  left: 18px;
  right: 18px;
  max-height: 320px;
  overflow-y: auto;
  padding: 8px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, rgba(26, 30, 38, 0.98), rgba(19, 22, 27, 0.99));
  box-shadow: 0 20px 40px rgba(2, 6, 23, 0.32);
  z-index: 20;
}

.assistant-option {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border: 0;
  border-radius: 14px;
  background: transparent;
  color: var(--text);
  text-align: left;
  cursor: pointer;
}

.assistant-option:hover,
.assistant-option.active {
  background: rgba(59, 130, 246, 0.08);
}

.assistant-option-copy {
  display: grid;
  gap: 4px;
}

.assistant-option-name {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
}

.assistant-option-desc {
  color: var(--text2);
  font-size: 12px;
  line-height: 1.5;
}

.sessions-panel {
  min-height: 0;
  padding: 16px;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  font-size: 16px;
}

.panel-action {
  min-width: 64px;
  height: 36px;
  padding: 0 14px;
  border-radius: 12px;
}

.sessions-scroll {
  min-height: 0;
  overflow: hidden;
}

.chat-main {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
}

.chat-main-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02));
}

.chat-main-subtitle {
  margin-top: 2px;
}

.chat-main-divider {
  margin: 0 8px;
  color: var(--text3);
}

.chat-main-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.head-btn {
  height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  white-space: nowrap;
}

.chat-main-body {
  min-height: 0;
  padding: 0 8px;
  overflow: hidden;
}

.chat-main-input {
  padding: 12px 16px 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  background: linear-gradient(180deg, rgba(19, 22, 27, 0.72), rgba(19, 22, 27, 0.96));
}

:deep(.session-list) {
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow-y: auto;
  padding-right: 4px;
}

:deep(.chat-messages) {
  height: 100%;
  padding: 20px 14px 16px;
}

:deep(.chat-input-area) {
  display: grid;
  gap: 10px;
}

:deep(.prompt-toolbar),
:deep(.template-panel),
:deep(.draft-switcher),
:deep(.input-checklist),
:deep(.input-meta-rail),
:deep(.input-helper-row) {
  display: none;
}

:deep(.chat-input-wrap) {
  padding: 14px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  box-shadow: 0 8px 20px rgba(2, 6, 23, 0.24);
}

:deep(.chat-input-wrap::before) {
  display: none;
}

:deep(.chat-input-main) {
  display: grid;
  gap: 12px;
}

:deep(.chat-input) {
  min-height: 44px;
  max-height: 160px;
}

:deep(.chat-input-actions) {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 1100px) {
  .chat-page {
    grid-template-columns: 300px minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .chat-page {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }

  .chat-sidebar {
    grid-template-rows: auto;
  }

  .sessions-panel {
    min-height: 320px;
  }

  .chat-main {
    min-height: calc(100vh - 240px);
  }
}

@media (max-width: 680px) {
  .chat-main-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .chat-main-actions {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .assistant-trigger {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }

  .assistant-trigger-arrow {
    justify-self: flex-start;
  }
}
</style>
