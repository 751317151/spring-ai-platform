<template>
  <div class="chat-workspace space-chat">
    <aside class="chat-sidebar-panel">
      <div class="chat-sidebar-head">
        <div class="eyebrow">智能助手工作台</div>
        <div class="sidebar-title">助手与会话</div>
        <div class="sidebar-subtitle">左侧切换助手，并在同一个会话中持续承接相关追问与上下文。</div>
      </div>

      <AgentList />
      <SessionList />

      <div class="chat-sidebar-footer">
        <div class="form-group" style="margin: 0">
          <label class="form-label">模型策略</label>
          <select class="form-select chat-model-select" disabled>
            <option v-for="opt in MODEL_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div class="sidebar-footnote">当前仅提供策略选择界面，后续可接入真实路由与模型选择逻辑。</div>
      </div>
    </aside>

    <section class="chat-stage">
      <div class="page-hero chat-hero">
        <div class="page-hero-main">
          <div class="page-hero-title-row">
            <div class="chat-agent-badge" :style="{ background: `${agentConfig.color}22`, color: agentConfig.color }">
              {{ agentConfig.icon }}
            </div>
            <div>
              <div class="page-title">{{ agentConfig.name }}</div>
              <div class="page-subtitle">{{ agentConfig.desc }}</div>
            </div>
          </div>
          <div class="hero-tags">
            <span class="tag">{{ sessionCount }}</span>
            <span class="tag">{{ chatStore.isThinking ? '生成中' : '就绪' }}</span>
            <span class="tag">{{ currentSessionLabel }}</span>
            <span class="tag">{{ currentDraftLabel }}</span>
          </div>
        </div>

        <div class="page-hero-actions">
          <button class="btn btn-primary" @click="createNewChat">新建会话</button>
          <button class="btn btn-ghost" @click="chatStore.clearChat()">清空会话</button>

          <div class="export-shell" @keydown.esc="closeExportMenu">
            <button class="btn btn-ghost" :disabled="chatStore.isThinking" @click="toggleExportMenu">
              导出
            </button>
            <div v-if="showExportMenu" class="export-panel">
              <button class="export-action" :disabled="!hasTranscript" @click="downloadTranscript">
                <strong>下载对话记录</strong>
                <span>将当前会话保存为 `.txt` 文件。</span>
              </button>
              <button class="export-action" :disabled="!hasTranscript" @click="copyTranscript">
                <strong>复制对话记录</strong>
                <span>将完整会话内容复制到剪贴板。</span>
              </button>
              <button class="export-action" :disabled="!hasTranscript" @click="copySummary">
                <strong>复制摘要</strong>
                <span>复制包含标题、消息数量和最新回复的简要摘要。</span>
              </button>
              <button class="export-action" :disabled="!chatStore.currentSessionId" @click="copySessionId">
                <strong>复制会话 ID</strong>
                <span>复制当前会话标识，便于手动追踪或排查。</span>
              </button>
              <button class="export-action" :disabled="!chatStore.currentSessionId" @click="copyDeepLink">
                <strong>复制会话链接</strong>
                <span>复制可直接打开当前助手与会话的深链地址。</span>
              </button>
              <button class="export-action" :disabled="!hasTranscript" @click="copyContextPrompt">
                <strong>复制上下文提示词</strong>
                <span>复制基于当前会话状态生成的可复用提示词框架。</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <BackendStatusBanner
        service="chat"
        demo-message="当前聊天页处于演示模式，回复来自本地模拟数据。"
        unavailable-message="聊天后端暂时不可用，页面不会静默回退到模拟响应。"
      />

      <div class="chat-stage-card">
        <div class="chat-stage-toolbar">
          <div>
            <div class="toolbar-title">对话窗口</div>
            <div class="toolbar-subtitle">保持在同一个会话中，可持续保留上下文、反馈记录和未完成草稿。</div>
          </div>
          <div class="toolbar-meta">
            <span class="toolbar-pill">{{ chatStore.chatHistory.length }} 条消息</span>
            <span class="toolbar-pill">{{ chatStore.currentAgent }}</span>
            <span class="toolbar-pill">`/` 聚焦输入框</span>
          </div>
        </div>

        <ChatMessages @use-prompt="handleSend" @insert-prompt="handleInsertPrompt" />
        <ChatInput ref="chatInputRef" @send="handleSend" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AgentList from '@/components/chat/AgentList.vue'
import SessionList from '@/components/chat/SessionList.vue'
import ChatMessages from '@/components/chat/ChatMessages.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import BackendStatusBanner from '@/components/common/BackendStatusBanner.vue'
import { useChatStore } from '@/stores/chat'
import { useToast } from '@/composables/useToast'
import { MODEL_OPTIONS } from '@/utils/constants'

const chatStore = useChatStore()
const { showToast } = useToast()
const route = useRoute()
const router = useRouter()
const chatInputRef = ref<InstanceType<typeof ChatInput> | null>(null)
const showExportMenu = ref(false)
const syncingRoute = ref(false)

const agentConfig = computed(() => chatStore.getAgentConfig())
const currentSession = computed(() => chatStore.sessionList.find((item) => item.sessionId === chatStore.currentSessionId) || null)
const sessionCount = computed(() => `${chatStore.activeSessions.length} 个进行中 / ${chatStore.archivedSessions.length} 个归档`)
const currentSessionLabel = computed(() => currentSession.value?.summary || '新会话')
const currentDraftLabel = computed(() => {
  const draft = chatStore.getDraft(chatStore.currentSessionId).trim()
  return draft ? `草稿 ${Math.min(draft.length, 999)} 字` : '无草稿'
})
const hasTranscript = computed(() => chatStore.chatHistory.length > 0)

function handleSend(message: string) {
  chatStore.sendMessage(message)
}

function handleInsertPrompt(message: string) {
  chatInputRef.value?.setMessage(message)
}

function createNewChat() {
  chatStore.createNewSession()
  chatInputRef.value?.focusInput()
}

function handleNewChatShortcut() {
  createNewChat()
}

function buildTranscript() {
  const lines = [
    `会话：${currentSessionLabel.value}`,
    `助手：${agentConfig.value.name}`,
    `会话 ID：${chatStore.currentSessionId || '-'}`,
    `导出时间：${new Date().toLocaleString('zh-CN')}`,
    ''
  ]

  chatStore.chatHistory.forEach((message, index) => {
    const role = message.role === 'user' ? '用户' : '助手'
    lines.push(`${role} #${index + 1}`)
    lines.push(message.content || '')
    lines.push('')
  })

  return lines.join('\n')
}

function buildSummary() {
  const assistantMessages = chatStore.chatHistory.filter((item) => item.role === 'assistant')
  const latestAnswer = assistantMessages[assistantMessages.length - 1]?.content?.replace(/\s+/g, ' ').trim() || '暂无助手回复。'

  return [
    `会话：${currentSessionLabel.value}`,
    `助手：${agentConfig.value.name}`,
    `消息数：${chatStore.chatHistory.length}`,
    `当前草稿：${chatStore.getDraft(chatStore.currentSessionId).trim() ? '有内容' : '为空'}`,
    `最新回复：${latestAnswer.slice(0, 240)}${latestAnswer.length > 240 ? '...' : ''}`
  ].join('\n')
}

function buildContextPrompt() {
  const recentMessages = chatStore.chatHistory.slice(-6)
  const conversationContext = recentMessages
    .map((message, index) => {
      const role = message.role === 'user' ? '用户' : '助手'
      return `${index + 1}. ${role}: ${(message.content || '').replace(/\s+/g, ' ').trim()}`
    })
    .join('\n')

  return [
    '请继续下面这段 AI 助手会话。',
    `助手：${agentConfig.value.name}`,
    `会话标题：${currentSessionLabel.value}`,
    `会话 ID：${chatStore.currentSessionId || '-'}`,
    `当前草稿：${chatStore.getDraft(chatStore.currentSessionId).trim() || '无'}`,
    '',
    '最近对话：',
    conversationContext || '暂无历史对话。',
    '',
    '请在保持相同上下文的前提下继续回答，避免重复之前内容，并尽量给出可执行的结果。'
  ].join('\n')
}

function buildDeepLink() {
  const url = new URL(window.location.href)
  url.pathname = '/chat'
  url.search = ''
  url.searchParams.set('agent', chatStore.currentAgent)
  if (chatStore.currentSessionId) {
    url.searchParams.set('session', chatStore.currentSessionId)
  }
  return url.toString()
}

function getExportFileName() {
  const safeTitle = currentSessionLabel.value.replace(/[\\/:*?"<>|]/g, '-').slice(0, 40) || '对话记录'
  return `${safeTitle}-${Date.now()}.txt`
}

function toggleExportMenu() {
  showExportMenu.value = !showExportMenu.value
}

function closeExportMenu() {
  showExportMenu.value = false
}

async function copyText(value: string, successMessage: string) {
  try {
    await navigator.clipboard.writeText(value)
    showToast(successMessage)
  } catch {
    showToast('复制失败，请重试')
  }
}

function downloadTextFile(content: string, fileName: string) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

function downloadTranscript() {
  downloadTextFile(buildTranscript(), getExportFileName())
  closeExportMenu()
  showToast('对话记录开始下载')
}

async function copyTranscript() {
  await copyText(buildTranscript(), '对话记录已复制')
  closeExportMenu()
}

async function copySummary() {
  await copyText(buildSummary(), '会话摘要已复制')
  closeExportMenu()
}

async function copySessionId() {
  await copyText(chatStore.currentSessionId || '', '会话 ID 已复制')
  closeExportMenu()
}

async function copyDeepLink() {
  await copyText(buildDeepLink(), '会话链接已复制')
  closeExportMenu()
}

async function copyContextPrompt() {
  await copyText(buildContextPrompt(), '上下文提示词已复制')
  closeExportMenu()
}

async function syncRouteFromState() {
  if (syncingRoute.value) {
    return
  }

  const nextQuery: Record<string, string> = { agent: chatStore.currentAgent }
  if (chatStore.currentSessionId) {
    nextQuery.session = chatStore.currentSessionId
  }

  const currentAgentQuery = typeof route.query.agent === 'string' ? route.query.agent : ''
  const currentSessionQuery = typeof route.query.session === 'string' ? route.query.session : ''
  if (currentAgentQuery === nextQuery.agent && currentSessionQuery === (nextQuery.session || '')) {
    return
  }

  syncingRoute.value = true
  await router.replace({ name: 'chat', query: nextQuery })
  syncingRoute.value = false
}

async function applyRouteState() {
  if (syncingRoute.value) {
    return
  }

  const routeAgent = typeof route.query.agent === 'string' ? route.query.agent : ''
  const routeSession = typeof route.query.session === 'string' ? route.query.session : ''

  if (routeAgent && routeAgent !== chatStore.currentAgent) {
    syncingRoute.value = true
    await chatStore.selectAgent(routeAgent)
    syncingRoute.value = false
  }

  if (routeSession && routeSession !== chatStore.currentSessionId) {
    const exists = chatStore.sessionList.some((item) => item.sessionId === routeSession)
    if (exists) {
      syncingRoute.value = true
      await chatStore.switchSession(routeSession)
      syncingRoute.value = false
    }
  }
}

function handleGlobalClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Node)) {
    return
  }
  const panel = document.querySelector('.export-shell')
  if (panel && !panel.contains(target)) {
    closeExportMenu()
  }
}

onMounted(() => {
  window.addEventListener('app:new-chat', handleNewChatShortcut as EventListener)
  window.addEventListener('click', handleGlobalClick)
  applyRouteState()
})

onUnmounted(() => {
  window.removeEventListener('app:new-chat', handleNewChatShortcut as EventListener)
  window.removeEventListener('click', handleGlobalClick)
})

watch(() => [chatStore.currentAgent, chatStore.currentSessionId], () => {
  syncRouteFromState()
})

watch(() => route.query, () => {
  applyRouteState()
})
</script>

<style scoped>
.export-shell {
  position: relative;
}

.export-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 280px;
  padding: 10px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: var(--surface);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.14);
  display: grid;
  gap: 8px;
  z-index: 12;
}

.export-action {
  border: 1px solid transparent;
  background: transparent;
  color: var(--text);
  border-radius: 12px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  display: grid;
  gap: 4px;
}

.export-action:hover:not(:disabled) {
  background: var(--surface2);
  border-color: var(--border);
}

.export-action:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.export-action strong {
  font-size: 13px;
  font-weight: 600;
}

.export-action span {
  font-size: 11px;
  color: var(--text3);
  line-height: 1.5;
}
</style>
