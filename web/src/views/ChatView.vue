<template>
  <div class="chat-workspace space-chat">
    <aside class="chat-sidebar-panel">
      <div class="chat-sidebar-head">
        <div class="eyebrow">智能助手工作台</div>
        <div class="sidebar-title">助手与会话</div>
        <div class="sidebar-subtitle">左侧切换助手，并在同一会话里持续承接追问、上下文和阶段结果。</div>
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
        <div class="sidebar-footnote">当前先保留策略选择入口，后续可以接入真实路由和模型编排逻辑。</div>
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
          <button class="btn btn-ghost" @click="handleClearChat">清空会话</button>
          <button class="btn btn-ghost" @click="openCommandPalette">快捷操作</button>

          <div class="export-shell" @keydown.esc="closeExportMenu">
            <button class="btn btn-ghost" :disabled="chatStore.isThinking" @click="toggleExportMenu">导出</button>
            <div v-if="showExportMenu" class="export-panel">
              <button class="export-action" :disabled="!hasTranscript" @click="downloadTranscriptTxt">
                <strong>下载文本</strong>
                <span>将当前会话保存为 `.txt` 文本记录，适合归档或转发。</span>
              </button>
              <button class="export-action" :disabled="!hasTranscript" @click="downloadTranscriptMarkdown">
                <strong>导出 Markdown</strong>
                <span>保留结构化标题和消息分段，便于沉淀到笔记或知识库。</span>
              </button>
              <button class="export-action" :disabled="!hasTranscript" @click="downloadTranscriptJson">
                <strong>导出 JSON</strong>
                <span>输出结构化会话数据，便于后续程序处理或重新导入。</span>
              </button>
              <button class="export-action" :disabled="!hasTranscript" @click="copyTranscript">
                <strong>复制对话记录</strong>
                <span>直接复制完整会话内容。</span>
              </button>
              <button class="export-action" :disabled="!hasTranscript" @click="copySummary">
                <strong>复制摘要</strong>
                <span>复制标题、消息数和最新回复摘要，便于快速同步。</span>
              </button>
              <button class="export-action" :disabled="!chatStore.currentSessionId" @click="copySessionId">
                <strong>复制会话 ID</strong>
                <span>用于排查、记录或人工关联会话。</span>
              </button>
              <button class="export-action" :disabled="!chatStore.currentSessionId" @click="copyDeepLink">
                <strong>复制会话链接</strong>
                <span>生成可直接打开当前助手和会话的深链链接。</span>
              </button>
              <button class="export-action" :disabled="!hasTranscript" @click="copyContextPrompt">
                <strong>复制上下文提示词</strong>
                <span>基于当前会话生成可复用的上下文续写提示。</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <BackendStatusBanner
        service="chat"
        demo-message="当前聊天页面处于演示模式，回复来自本地模拟数据。"
        unavailable-message="聊天后端暂时不可用，页面不会静默回退到模拟结果。"
      />

      <div v-if="entryContextMessage" class="card section-card entry-context-card">
        <div class="entry-context-copy">
          <div class="entry-context-title">当前延续上一页的工作上下文</div>
          <div class="entry-context-desc">{{ entryContextMessage }}</div>
        </div>
        <div class="entry-context-actions">
          <button class="btn btn-ghost btn-sm" type="button" @click="focusChatInput">继续处理</button>
          <button class="btn btn-ghost btn-sm" type="button" @click="clearEntryContext">清除提示</button>
        </div>
      </div>

      <div v-if="highlightedContextCard" class="card section-card source-context-card">
        <div class="source-context-head">
          <div>
            <div class="source-context-kicker">来源定位</div>
            <div class="source-context-title">
              已定位到{{ highlightedContextCard.roleLabel }}消息 #{{ highlightedContextCard.messageNumber }}
            </div>
            <div class="source-context-desc">
              {{ highlightedContextCard.summary }}
            </div>
          </div>
          <div class="source-context-badges">
            <span class="tag">{{ highlightedContextCard.roleLabel }}</span>
            <span class="tag">{{ currentSessionLabel }}</span>
          </div>
        </div>

        <div class="source-context-grid">
          <div class="source-context-block">
            <div class="source-context-label">上一条</div>
            <div class="source-context-value">{{ highlightedContextCard.previousSummary }}</div>
          </div>
          <div class="source-context-block active">
            <div class="source-context-label">当前消息</div>
            <div class="source-context-value">{{ highlightedContextCard.currentSummary }}</div>
          </div>
          <div class="source-context-block">
            <div class="source-context-label">下一条</div>
            <div class="source-context-value">{{ highlightedContextCard.nextSummary }}</div>
          </div>
        </div>

        <div class="source-context-actions">
          <button class="btn btn-primary btn-sm" type="button" @click="continueFromHighlightedMessage">继续追问</button>
          <button class="btn btn-ghost btn-sm" type="button" @click="copyHighlightedSnippet">复制来源片段</button>
          <button class="btn btn-ghost btn-sm clear-highlight-btn" type="button" @click="clearHighlightedContext">清除定位</button>
        </div>
      </div>

      <div v-if="lastOpenedSession || recentSessions.length" class="card section-card continue-card">
        <div class="card-header">
          <div>
            <div class="card-title">继续最近会话</div>
            <div class="card-subtitle">从最近活跃会话里快速回到正在处理的话题。</div>
          </div>
        </div>
        <button v-if="lastOpenedSession" class="last-session-banner" @click="resumeSession(lastOpenedSession.sessionId)">
          <span class="last-session-label">恢复上次打开</span>
          <strong class="last-session-title">{{ lastOpenedSession.summary || '未命名会话' }}</strong>
          <span class="last-session-meta">{{ formatSessionTime(lastOpenedSession.updatedAt) }}</span>
        </button>
        <div class="continue-grid">
          <button
            v-for="session in recentSessions"
            :key="session.sessionId"
            class="continue-item action-tile"
            @click="resumeSession(session.sessionId)"
          >
            <span class="continue-title action-tile-title">{{ session.summary || '未命名会话' }}</span>
            <span class="continue-meta action-tile-desc">{{ formatSessionTime(session.updatedAt) }}</span>
          </button>
        </div>
      </div>

      <SessionConfigPanel />

      <div class="chat-overview-grid section-card">
        <div class="card chat-overview-card">
          <div class="chat-overview-label">当前会话</div>
          <div class="chat-overview-value">{{ currentSessionLabel }}</div>
          <div class="chat-overview-sub">{{ currentSessionIdLabel }}</div>
        </div>
        <div class="card chat-overview-card">
          <div class="chat-overview-label">消息统计</div>
          <div class="chat-overview-value">{{ chatStore.chatHistory.length }} 条</div>
          <div class="chat-overview-sub">用户 {{ userMessageCount }} / 助手 {{ assistantMessageCount }}</div>
        </div>
        <div class="card chat-overview-card">
          <div class="chat-overview-label">内容体量</div>
          <div class="chat-overview-value">{{ transcriptCharCount }} 字</div>
          <div class="chat-overview-sub">{{ currentDraftLabel }}</div>
        </div>
      </div>

      <div class="card section-card session-quick-actions">
        <div class="card-header">
          <div>
            <div class="card-title">会话快捷操作</div>
            <div class="card-subtitle">把当前会话常用动作集中到一处，减少来回展开菜单。</div>
          </div>
        </div>
        <div class="session-action-grid">
          <button class="session-action-tile" type="button" :disabled="!hasTranscript" @click="copySummary">
            <strong>复制摘要</strong>
            <span>快速同步标题、消息数和最新回答要点。</span>
          </button>
          <button class="session-action-tile" type="button" :disabled="!hasTranscript" @click="copyContextPrompt">
            <strong>复制上下文</strong>
            <span>生成可复用的续写提示词，便于继续追问。</span>
          </button>
          <button class="session-action-tile" type="button" :disabled="!chatStore.currentSessionId" @click="copyDeepLink">
            <strong>复制会话链接</strong>
            <span>分享当前助手和会话定位链接。</span>
          </button>
          <button class="session-action-tile" type="button" :disabled="!hasTranscript" @click="downloadTranscriptMarkdown">
            <strong>导出 Markdown</strong>
            <span>保留结构化对话记录，方便沉淀到文档。</span>
          </button>
        </div>
      </div>

      <div v-if="hasTranscript" class="card section-card session-review-card">
        <div class="card-header">
          <div>
            <div class="card-title">会话复盘</div>
            <div class="card-subtitle">把当前聊天自动整理成复盘模板，可直接复制、继续补充或沉淀到学习中心。</div>
          </div>
        </div>
        <div class="session-review-preview">{{ sessionReviewPreview }}</div>
        <div class="learning-card-tags session-review-tags">
          <span v-for="tag in sessionReviewTags" :key="tag" class="tag">{{ tag }}</span>
        </div>
        <div class="session-review-actions">
          <button class="btn btn-ghost btn-sm" type="button" @click="copySessionReview">复制复盘</button>
          <button class="btn btn-ghost btn-sm" type="button" @click="insertReviewPrompt">继续补完复盘</button>
          <button class="btn btn-primary btn-sm" type="button" @click="saveSessionReviewNote">保存到学习笔记</button>
        </div>
      </div>

      <div v-if="hasTranscript || hasDraft" class="card section-card continuation-card">
        <div class="card-header">
          <div>
            <div class="card-title">继续当前工作</div>
            <div class="card-subtitle">把最近回答、当前草稿和下一步建议放在一起，减少来回切换和上下文丢失。</div>
          </div>
        </div>

        <div class="continuation-grid">
          <div class="continuation-block">
            <div class="continuation-label">最近回答摘要</div>
            <div class="continuation-value">{{ latestAssistantExcerpt }}</div>
          </div>
          <div class="continuation-block">
            <div class="continuation-label">当前草稿</div>
            <div class="continuation-value">{{ currentDraftPreview }}</div>
          </div>
          <div class="continuation-block">
            <div class="continuation-label">建议下一步</div>
            <div class="continuation-actions">
              <button class="continuation-action-btn" type="button" @click="insertContinuePrompt">继续展开</button>
              <button class="continuation-action-btn" type="button" @click="insertSummaryPrompt">整理摘要</button>
              <button class="continuation-action-btn" type="button" @click="focusChatInput">回到输入框</button>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-stage-card">
        <div class="chat-stage-toolbar">
          <div>
            <div class="toolbar-title">对话窗口</div>
            <div class="toolbar-subtitle">在同一会话中持续保留上下文、反馈记录和未完成草稿。</div>
          </div>
          <div class="toolbar-meta">
            <span class="toolbar-pill">{{ chatStore.chatHistory.length }} 条消息</span>
            <span class="toolbar-pill">{{ chatStore.currentAgent }}</span>
            <span class="toolbar-pill">`/` 聚焦输入框</span>
          </div>
        </div>

        <div v-if="recentActionLabel" class="chat-recent-action">
          <span class="recent-action-label">最近操作</span>
          <span class="recent-action-text">{{ recentActionLabel }}</span>
        </div>

        <ChatMessages
          :highlighted-message-index="highlightedMessageIndex"
          @use-prompt="handleSend"
          @insert-prompt="handleInsertPrompt"
          @branch-session="handleBranchSession"
          @continue-response="handleContinueResponse"
          @regenerate-response="handleRegenerateResponse"
        />
        <ChatInput ref="chatInputRef" @send="handleSend" />
      </div>

      <div v-if="showCommandPalette" class="command-palette-mask" @click="closeCommandPalette">
        <div class="command-palette" @click.stop>
          <div class="command-palette-head">
            <div>
              <div class="command-palette-title">快捷操作</div>
              <div class="command-palette-subtitle">按名称搜索，回车执行，Esc 关闭。</div>
            </div>
            <button class="command-palette-close" @click="closeCommandPalette">关闭</button>
          </div>

          <input
            ref="commandInputRef"
            v-model.trim="commandKeyword"
            class="command-palette-input"
            type="text"
            placeholder="搜索操作，例如：新建会话、导出、聚焦输入框"
            @keydown.down.prevent="moveCommandSelection(1)"
            @keydown.up.prevent="moveCommandSelection(-1)"
            @keydown.enter.prevent="runSelectedCommand"
          />

          <div class="command-palette-list">
            <button
              v-for="(command, index) in filteredCommands"
              :key="command.id"
              class="command-palette-item"
              :class="{ active: index === selectedCommandIndex }"
              @mouseenter="selectedCommandIndex = index"
              @click="runCommand(command)"
            >
              <span class="command-palette-name">{{ command.label }}</span>
              <span class="command-palette-desc">{{ command.description }}</span>
            </button>

            <div v-if="!filteredCommands.length" class="command-palette-empty">没有匹配的快捷操作</div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AgentList from '@/components/chat/AgentList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import ChatMessages from '@/components/chat/ChatMessages.vue'
import SessionConfigPanel from '@/components/chat/SessionConfigPanel.vue'
import SessionList from '@/components/chat/SessionList.vue'
import BackendStatusBanner from '@/components/common/BackendStatusBanner.vue'
import { useToast } from '@/composables/useToast'
import { useChatStore } from '@/stores/chat'
import type { LearningNoteRecord, SessionConfig } from '@/api/types'
import { normalizeTags, saveLearningNote } from '@/utils/learning'
import { MODEL_OPTIONS } from '@/utils/constants'

const chatStore = useChatStore()
const { showToast } = useToast()
const route = useRoute()
const router = useRouter()

const chatInputRef = ref<InstanceType<typeof ChatInput> | null>(null)
const commandInputRef = ref<HTMLInputElement | null>(null)
const showExportMenu = ref(false)
const showCommandPalette = ref(false)
const commandKeyword = ref('')
const selectedCommandIndex = ref(0)
const syncingRoute = ref(false)
const recentActionLabel = ref('')
const highlightedMessageIndex = ref<number | null>(null)
const LAST_OPENED_SESSION_KEY = 'chat_last_opened_session'
const RECENT_VISITED_KEY = 'chat_recent_visited_sessions'
const entrySource = computed(() => typeof route.query.source === 'string' ? route.query.source : '')
const entryPrompt = computed(() => typeof route.query.prompt === 'string' ? route.query.prompt.trim() : '')

const agentConfig = computed(() => chatStore.getAgentConfig())
const currentSession = computed(() => chatStore.sessionList.find((item) => item.sessionId === chatStore.currentSessionId) || null)
const sessionCount = computed(() => `${chatStore.activeSessions.length} 个进行中 / ${chatStore.archivedSessions.length} 个归档`)
const currentSessionLabel = computed(() => currentSession.value?.summary || '新会话')
const currentSessionIdLabel = computed(() => chatStore.currentSessionId || '未生成会话 ID')
const currentDraftLabel = computed(() => {
  const draft = chatStore.getDraft(chatStore.currentSessionId).trim()
  return draft ? `草稿 ${Math.min(draft.length, 999)} 字` : '无草稿'
})
const hasTranscript = computed(() => chatStore.chatHistory.length > 0)
const hasDraft = computed(() => Boolean(chatStore.getDraft(chatStore.currentSessionId).trim()))
const userMessageCount = computed(() => chatStore.chatHistory.filter((item) => item.role === 'user').length)
const assistantMessageCount = computed(() => chatStore.chatHistory.filter((item) => item.role === 'assistant').length)
const transcriptCharCount = computed(() =>
  chatStore.chatHistory.reduce((total, item) => total + (item.content?.length || 0), 0)
)
const latestAssistantExcerpt = computed(() => {
  const message = [...chatStore.chatHistory].reverse().find((item) => item.role === 'assistant')?.content?.replace(/\s+/g, ' ').trim()
  if (!message) return '当前还没有助手回答，可以直接开始新一轮提问。'
  return message.length > 120 ? `${message.slice(0, 120)}...` : message
})
const currentDraftPreview = computed(() => {
  const draft = chatStore.getDraft(chatStore.currentSessionId).trim()
  if (!draft) return '当前没有未发送草稿。'
  return draft.length > 120 ? `${draft.slice(0, 120)}...` : draft
})
const latestUserExcerpt = computed(() => {
  const message = [...chatStore.chatHistory].reverse().find((item) => item.role === 'user')?.content?.replace(/\s+/g, ' ').trim()
  if (!message) return '当前还没有明确的用户问题。'
  return message.length > 120 ? `${message.slice(0, 120)}...` : message
})
const entryContextMessage = computed(() => {
  if (entrySource.value === 'learning' && entryPrompt.value) {
    return '你从学习中心带入了一条继续追问提示，可以直接在输入框里基于来源内容继续推进。'
  }
  if (entrySource.value !== 'dashboard') return ''
  return hasTranscript.value
    ? '你从总览页进入了当前聊天工作台，可以直接基于现有会话继续处理未完成事项。'
    : '你从总览页进入了聊天工作台，可以直接开始新的问题处理或记录待办。'
})
const recentSessions = computed(() =>
  chatStore.activeSessions
    .filter((item) => item.sessionId !== chatStore.currentSessionId)
    .slice(0, 3)
)
const lastOpenedSession = computed(() => {
  try {
    const raw = localStorage.getItem(LAST_OPENED_SESSION_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as { agent?: string; sessionId?: string }
    if (!parsed.sessionId || parsed.agent !== chatStore.currentAgent || parsed.sessionId === chatStore.currentSessionId) {
      return null
    }
    return chatStore.sessionList.find((item) => item.sessionId === parsed.sessionId) || null
  } catch {
    return null
  }
})
const sessionReviewTags = computed(() => normalizeTags([
  '会话复盘',
  chatStore.currentAgent,
  currentSessionLabel.value,
  chatStore.sessionConfig.model ? `模型:${chatStore.sessionConfig.model}` : '',
  chatStore.sessionConfig.knowledgeEnabled ? '知识增强' : '纯聊天'
]))
const sessionReviewPreview = computed(() => buildSessionReview())
const highlightedMessage = computed(() => {
  if (highlightedMessageIndex.value === null) {
    return null
  }
  return chatStore.chatHistory[highlightedMessageIndex.value] || null
})
const highlightedContextCard = computed(() => {
  if (highlightedMessageIndex.value === null || !highlightedMessage.value) {
    return null
  }

  const previousMessage = highlightedMessageIndex.value > 0
    ? chatStore.chatHistory[highlightedMessageIndex.value - 1] || null
    : null
  const nextMessage = highlightedMessageIndex.value < chatStore.chatHistory.length - 1
    ? chatStore.chatHistory[highlightedMessageIndex.value + 1] || null
    : null
  const roleLabel = highlightedMessage.value.role === 'assistant' ? '助手' : '用户'

  return {
    roleLabel,
    messageNumber: highlightedMessageIndex.value + 1,
    summary: compactPreview(highlightedMessage.value.content, 140),
    currentSummary: compactPreview(highlightedMessage.value.content, 180),
    previousSummary: previousMessage ? compactPreview(previousMessage.content, 120) : '已经是当前会话的第一条消息。',
    nextSummary: nextMessage ? compactPreview(nextMessage.content, 120) : '当前定位已经是会话中的最后一条消息。'
  }
})

const commandActions = computed(() => [
  {
    id: 'new-chat',
    label: '新建会话',
    description: '创建新会话并聚焦输入框。',
    run: () => {
      createNewChat()
      setRecentAction('已通过快捷操作创建新会话。')
    }
  },
  {
    id: 'focus-input',
    label: '聚焦输入框',
    description: '将光标移动到聊天输入框。',
    run: () => {
      chatInputRef.value?.focusInput()
      setRecentAction('已聚焦聊天输入框。')
    }
  },
  {
    id: 'toggle-export',
    label: '打开导出菜单',
    description: '展开当前会话的导出操作面板。',
    run: () => {
      showExportMenu.value = true
      setRecentAction('已打开导出菜单。')
    }
  },
  {
    id: 'copy-link',
    label: '复制会话链接',
    description: '复制当前会话深链，便于分享和回访。',
    run: async () => {
      await copyDeepLink()
    }
  },
  {
    id: 'save-session-review',
    label: '保存会话复盘',
    description: '将当前会话整理为复盘笔记并写入学习中心。',
    run: async () => {
      await saveSessionReviewNote()
    }
  },
  {
    id: 'restore-last-session',
    label: '恢复上次打开会话',
    description: '回到上一次打开的会话上下文。',
    run: async () => {
      if (!lastOpenedSession.value) return
      await resumeSession(lastOpenedSession.value.sessionId)
      setRecentAction('已恢复上次打开的会话。')
    }
  },
  {
    id: 'toggle-archived',
    label: chatStore.showArchivedSessions ? '隐藏归档会话' : '显示归档会话',
    description: '切换左侧是否显示归档会话列表。',
    run: () => {
      chatStore.toggleArchivedVisibility()
      setRecentAction(chatStore.showArchivedSessions ? '已显示归档会话。' : '已隐藏归档会话。')
    }
  },
  {
    id: 'clear-chat',
    label: '清空当前会话',
    description: '清空当前会话消息并重新开始。',
    run: async () => {
      await handleClearChat()
    }
  }
])

const filteredCommands = computed(() => {
  const keyword = commandKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return commandActions.value
  }
  return commandActions.value.filter((item) => `${item.label} ${item.description}`.toLowerCase().includes(keyword))
})

function handleSend(message: string) {
  clearHighlightedMessage()
  chatStore.sendMessage(message)
}

function handleInsertPrompt(message: string) {
  chatInputRef.value?.setMessage(message)
}

function focusChatInput() {
  chatInputRef.value?.focusInput()
  setRecentAction('已回到聊天输入框。')
}

function clearEntryContext() {
  clearHighlightedContext()
}

function compactPreview(value: string, maxLength = 120) {
  const normalized = value.replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return '暂无内容摘要。'
  }
  return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}...` : normalized
}

function clearHighlightedMessage(skipRouteSync = false) {
  if (highlightedMessageIndex.value === null) {
    return
  }
  highlightedMessageIndex.value = null
  if (!skipRouteSync) {
    syncRouteFromState()
  }
}

async function clearHighlightedContext() {
  clearHighlightedMessage(true)
  await syncRouteFromState()
}

function continueFromHighlightedMessage() {
  if (!highlightedContextCard.value) {
    return
  }
  const prompt = `请基于当前定位的来源消息继续推进，并结合当前会话上下文补充下一步建议：${highlightedContextCard.value.currentSummary}`
  chatInputRef.value?.setMessage(prompt)
  chatInputRef.value?.focusInput()
  setRecentAction(`已基于来源消息 #${highlightedContextCard.value.messageNumber} 生成继续追问。`)
}

async function copyHighlightedSnippet() {
  if (!highlightedMessage.value || !highlightedContextCard.value) {
    return
  }

  const payload = [
    `会话：${currentSessionLabel.value}`,
    `消息序号：#${highlightedContextCard.value.messageNumber}`,
    `角色：${highlightedContextCard.value.roleLabel}`,
    '',
    highlightedMessage.value.content
  ].join('\n')

  if (await copyText(payload, '来源片段已复制')) {
    setRecentAction(`已复制来源消息 #${highlightedContextCard.value.messageNumber}。`)
  }
}

function insertContinuePrompt() {
  const prompt = hasTranscript.value
    ? '请基于当前会话继续展开，优先补完未说透的部分，并给出下一步建议。'
    : '请帮我从当前话题开始梳理思路，并给出一个可执行的起步方案。'
  chatInputRef.value?.setMessage(prompt)
  chatInputRef.value?.focusInput()
  setRecentAction('已插入“继续展开”提示。')
}

function insertSummaryPrompt() {
  const prompt = hasTranscript.value
    ? '请把当前会话整理成摘要，包含结论、待办和风险点。'
    : '请先帮我列出这个问题的核心目标、约束和建议步骤。'
  chatInputRef.value?.setMessage(prompt)
  chatInputRef.value?.focusInput()
  setRecentAction('已插入“整理摘要”提示。')
}

function insertReviewPrompt() {
  const prompt = hasTranscript.value
    ? '请基于当前整段会话，输出一份更完整的复盘，必须包含：问题背景、关键结论、待办事项、风险点、后续优化方向。'
    : '请先帮我生成一份可逐步补完的会话复盘模板，包含目标、约束、结论、待办和风险点。'
  chatInputRef.value?.setMessage(prompt)
  chatInputRef.value?.focusInput()
  setRecentAction('已插入“会话复盘”提示。')
}

function buildBranchPrompt(messageIndex: number) {
  const target = chatStore.chatHistory[messageIndex]
  if (!target || target.role !== 'assistant') {
    return ''
  }

  const relatedMessages = chatStore.chatHistory.slice(Math.max(0, messageIndex - 2), messageIndex + 1)
  const context = relatedMessages
    .map((message) => `${message.role === 'user' ? '用户' : '助手'}：${message.content.replace(/\s+/g, ' ').trim()}`)
    .join('\n')

  return [
    '请从这里开启一个新的分支会话，继续深入这个话题。',
    '下面是当前分支的参考上下文：',
    context,
    '',
    '请基于这些上下文继续分析，并给出下一步建议。'
  ].join('\n')
}

function buildContinuePrompt(messageIndex: number) {
  const target = chatStore.chatHistory[messageIndex]
  if (!target || target.role !== 'assistant') {
    return ''
  }
  return '请基于当前上下文继续刚才的回答，不要重复已经说过的内容，直接从未完成的部分继续。'
}

function buildRegeneratePrompt(messageIndex: number) {
  const target = chatStore.chatHistory[messageIndex]
  if (!target || target.role !== 'assistant') {
    return ''
  }

  let previousUserMessage = ''
  for (let index = messageIndex - 1; index >= 0; index -= 1) {
    if (chatStore.chatHistory[index]?.role === 'user') {
      previousUserMessage = chatStore.chatHistory[index]?.content || ''
      break
    }
  }

  const compactQuestion = previousUserMessage.replace(/\s+/g, ' ').trim()
  if (!compactQuestion) {
    return '请忽略上一条回答，基于当前会话上下文重新组织一版更完整、更清晰的答案。'
  }

  return `请忽略你刚才的上一条回答，重新回答这个问题，并给出更完整、更清晰的结果：${compactQuestion}`
}

function getMessageConfigSnapshot(messageIndex: number): SessionConfig | null {
  const snapshot = chatStore.chatHistory[messageIndex]?.sessionConfigSnapshot
  return snapshot ? chatStore.normalizeSessionConfig(snapshot) : null
}

async function handleContinueResponse(messageIndex: number) {
  const prompt = buildContinuePrompt(messageIndex)
  if (!prompt || chatStore.isThinking) {
    return
  }
  const snapshot = getMessageConfigSnapshot(messageIndex)
  await chatStore.sendMessage(prompt, {
    sessionConfigOverride: snapshot,
    derivedFrom: { action: 'continue', messageIndex }
  })
  setRecentAction('已发起继续生成，请等待新回复完成。')
  showToast('已发起继续生成')
}

async function handleRegenerateResponse(messageIndex: number) {
  const prompt = buildRegeneratePrompt(messageIndex)
  if (!prompt || chatStore.isThinking) {
    return
  }
  const snapshot = getMessageConfigSnapshot(messageIndex)
  await chatStore.sendMessage(prompt, {
    sessionConfigOverride: snapshot,
    derivedFrom: { action: 'regenerate', messageIndex }
  })
  setRecentAction('已基于上一轮问题重新发起回答。')
  showToast('已发起重新回答')
}

async function handleBranchSession(messageIndex: number) {
  const prompt = buildBranchPrompt(messageIndex)
  if (!prompt || chatStore.isThinking) {
    return
  }
  const snapshot = getMessageConfigSnapshot(messageIndex)
  createNewChat()
  if (snapshot) {
    await chatStore.saveCurrentSessionConfig(snapshot)
  }
  chatInputRef.value?.setMessage(prompt)
  setRecentAction('已创建分支会话，并自动填入上下文提示词。')
  showToast('已创建分支会话')
}

function createNewChat() {
  clearHighlightedMessage(true)
  chatStore.createNewSession()
  chatInputRef.value?.focusInput()
  persistSessionVisit()
}

async function resumeSession(sessionId: string) {
  clearHighlightedMessage(true)
  await chatStore.switchSession(sessionId)
  chatInputRef.value?.focusInput()
  persistSessionVisit()
  setRecentAction('已切换到最近会话。')
}

async function handleClearChat() {
  await chatStore.clearChat()
  setRecentAction('已清空当前会话并创建新会话。')
  showToast('已清空当前会话')
}

function handleNewChatShortcut() {
  createNewChat()
}

function openCommandPalette() {
  showCommandPalette.value = true
  commandKeyword.value = ''
  selectedCommandIndex.value = 0
  closeExportMenu()
  setTimeout(() => {
    commandInputRef.value?.focus()
  }, 0)
}

function closeCommandPalette() {
  showCommandPalette.value = false
  commandKeyword.value = ''
  selectedCommandIndex.value = 0
}

function moveCommandSelection(direction: number) {
  if (!filteredCommands.value.length) {
    return
  }
  const total = filteredCommands.value.length
  selectedCommandIndex.value = (selectedCommandIndex.value + direction + total) % total
}

async function runCommand(command: { id: string; label: string; description: string; run: () => void | Promise<void> }) {
  await command.run()
  closeCommandPalette()
}

async function runSelectedCommand() {
  const command = filteredCommands.value[selectedCommandIndex.value]
  if (!command) {
    return
  }
  await runCommand(command)
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

function buildMarkdownTranscript() {
  const lines = [
    `# ${currentSessionLabel.value}`,
    '',
    `- 助手：${agentConfig.value.name}`,
    `- 会话 ID：${chatStore.currentSessionId || '-'}`,
    `- 导出时间：${new Date().toLocaleString('zh-CN')}`,
    ''
  ]

  chatStore.chatHistory.forEach((message, index) => {
    const role = message.role === 'user' ? '用户' : '助手'
    lines.push(`## ${role} ${index + 1}`)
    lines.push('')
    lines.push(message.content || '')
    lines.push('')
  })

  return lines.join('\n')
}

function buildJsonTranscript() {
  return JSON.stringify(
    {
      title: currentSessionLabel.value,
      agent: {
        type: chatStore.currentAgent,
        name: agentConfig.value.name
      },
      sessionId: chatStore.currentSessionId,
      exportedAt: new Date().toISOString(),
      messageCount: chatStore.chatHistory.length,
      draft: chatStore.getDraft(chatStore.currentSessionId).trim(),
      messages: chatStore.chatHistory.map((message, index) => ({
        index: index + 1,
        role: message.role,
        content: message.content,
        responseId: message.responseId ?? null,
        feedback: message.feedback ?? null
      }))
    },
    null,
    2
  )
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

function buildSessionReview() {
  const latestAssistant = [...chatStore.chatHistory]
    .reverse()
    .find((item) => item.role === 'assistant')?.content?.replace(/\s+/g, ' ').trim() || '暂无助手回复。'
  const draft = chatStore.getDraft(chatStore.currentSessionId).trim()

  return [
    `会话标题：${currentSessionLabel.value}`,
    `助手角色：${agentConfig.value.name}`,
    `会话 ID：${chatStore.currentSessionId || '-'}`,
    '',
    '一、问题背景',
    latestUserExcerpt.value,
    '',
    '二、关键结论',
    latestAssistant.slice(0, 320) + (latestAssistant.length > 320 ? '...' : ''),
    '',
    '三、待办事项',
    draft ? `1. 继续处理当前草稿：${draft}` : '1. 补充下一步执行动作。',
    '2. 把关键回答转成可执行清单。',
    '',
    '四、风险与注意点',
    '- 需要核对当前回答是否覆盖边界条件。',
    '- 如涉及配置、接口或检索结果，建议进一步验证。',
    '',
    '五、后续优化方向',
    '- 将本次结论补充到学习中心或知识库。',
    '- 如果问题仍未闭环，可继续发起追问或分支讨论。'
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
    '请在保持相同上下文的前提下继续回答，避免重复之前内容，并尽量给出可执行结果。'
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
  return safeTitle || '对话记录'
}

function formatSessionTime(value?: string) {
  const time = Number(value || 0)
  if (!time) {
    return '刚刚更新'
  }
  return new Date(time).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function setRecentAction(message: string) {
  recentActionLabel.value = `${new Date().toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })} · ${message}`
}

function persistSessionVisit() {
  if (!chatStore.currentSessionId) return
  try {
    localStorage.setItem(
      LAST_OPENED_SESSION_KEY,
      JSON.stringify({
        agent: chatStore.currentAgent,
        sessionId: chatStore.currentSessionId
      })
    )

    const raw = localStorage.getItem(RECENT_VISITED_KEY)
    const ids = raw ? (JSON.parse(raw) as string[]) : []
    const next = [chatStore.currentSessionId, ...ids.filter((id) => id !== chatStore.currentSessionId)].slice(0, 8)
    localStorage.setItem(RECENT_VISITED_KEY, JSON.stringify(next))
  } catch {
    // ignore persistence failures
  }
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
    return true
  } catch {
    showToast('复制失败，请重试')
    return false
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

function downloadTranscriptTxt() {
  downloadTextFile(buildTranscript(), `${getExportFileName()}-${Date.now()}.txt`)
  setRecentAction('已导出 TXT 会话记录。')
  closeExportMenu()
  showToast('TXT 会话记录已开始下载')
}

function downloadTranscriptMarkdown() {
  downloadTextFile(buildMarkdownTranscript(), `${getExportFileName()}-${Date.now()}.md`)
  setRecentAction('已导出 Markdown 会话记录。')
  closeExportMenu()
  showToast('Markdown 会话记录已开始下载')
}

function downloadTranscriptJson() {
  downloadTextFile(buildJsonTranscript(), `${getExportFileName()}-${Date.now()}.json`)
  setRecentAction('已导出 JSON 会话记录。')
  closeExportMenu()
  showToast('JSON 会话记录已开始下载')
}

async function copyTranscript() {
  if (await copyText(buildTranscript(), '对话记录已复制')) {
    setRecentAction('已复制完整对话记录。')
  }
  closeExportMenu()
}

async function copySummary() {
  if (await copyText(buildSummary(), '会话摘要已复制')) {
    setRecentAction('已复制会话摘要。')
  }
  closeExportMenu()
}

async function copySessionReview() {
  if (await copyText(buildSessionReview(), '会话复盘已复制')) {
    setRecentAction('已复制会话复盘。')
  }
}

async function saveSessionReviewNote() {
  if (!hasTranscript.value) {
    showToast('当前没有可复盘的会话内容')
    return
  }

  const now = Date.now()
  const note: LearningNoteRecord = {
    id: `chat-review-${chatStore.currentAgent}-${chatStore.currentSessionId || now}-${now}`,
    title: `${currentSessionLabel.value} - 会话复盘`,
    content: buildSessionReview(),
    sourceType: 'manual',
    relatedSessionId: chatStore.currentSessionId,
    relatedAgentType: chatStore.currentAgent,
    relatedSessionSummary: currentSessionLabel.value,
    tags: sessionReviewTags.value,
    createdAt: now,
    updatedAt: now
  }
  saveLearningNote(note)
  setRecentAction('已将会话复盘保存到学习笔记。')
  showToast('会话复盘已保存到学习笔记')
}

async function copySessionId() {
  if (await copyText(chatStore.currentSessionId || '', '会话 ID 已复制')) {
    setRecentAction('已复制当前会话 ID。')
  }
  closeExportMenu()
}

async function copyDeepLink() {
  if (await copyText(buildDeepLink(), '会话链接已复制')) {
    setRecentAction('已复制当前会话深链。')
  }
  closeExportMenu()
}

async function copyContextPrompt() {
  if (await copyText(buildContextPrompt(), '上下文提示词已复制')) {
    setRecentAction('已复制上下文提示词。')
  }
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
  if (highlightedMessageIndex.value !== null) {
    nextQuery.message = String(highlightedMessageIndex.value)
  }

  const currentAgentQuery = typeof route.query.agent === 'string' ? route.query.agent : ''
  const currentSessionQuery = typeof route.query.session === 'string' ? route.query.session : ''
  const currentMessageQuery = typeof route.query.message === 'string' ? route.query.message : ''
  if (
    currentAgentQuery === nextQuery.agent
    && currentSessionQuery === (nextQuery.session || '')
    && currentMessageQuery === (nextQuery.message || '')
  ) {
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
  const routeMessage = typeof route.query.message === 'string' ? Number(route.query.message) : NaN
  const routePrompt = typeof route.query.prompt === 'string' ? route.query.prompt.trim() : ''
  highlightedMessageIndex.value = Number.isInteger(routeMessage) && routeMessage >= 0 ? routeMessage : null

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

  if (routePrompt) {
    chatInputRef.value?.setMessage(routePrompt)
    chatInputRef.value?.focusInput()
    setRecentAction('已从学习中心带入继续追问提示。')

    const nextQuery: Record<string, string> = {}
    if (routeAgent) {
      nextQuery.agent = routeAgent
    }
    if (routeSession) {
      nextQuery.session = routeSession
    }
    if (Number.isInteger(routeMessage) && routeMessage >= 0) {
      nextQuery.message = String(routeMessage)
    }
    if (entrySource.value) {
      nextQuery.source = entrySource.value
    }

    syncingRoute.value = true
    await router.replace({ name: 'chat', query: nextQuery })
    syncingRoute.value = false
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

function handleChatHotkeys(event: KeyboardEvent) {
  const target = event.target as HTMLElement | null
  const isTypingTarget = target instanceof HTMLInputElement
    || target instanceof HTMLTextAreaElement
    || target instanceof HTMLSelectElement
    || target?.isContentEditable

  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'j') {
    event.preventDefault()
    if (showCommandPalette.value) {
      closeCommandPalette()
    } else {
      openCommandPalette()
    }
    return
  }

  if (event.key === '/' && !event.ctrlKey && !event.metaKey && !event.altKey && !isTypingTarget && !showCommandPalette.value) {
    event.preventDefault()
    chatInputRef.value?.focusInput()
    setRecentAction('已通过快捷键聚焦输入框。')
    return
  }

  if (event.key === 'Escape' && showCommandPalette.value) {
    closeCommandPalette()
  }
}

onMounted(() => {
  window.addEventListener('app:new-chat', handleNewChatShortcut as EventListener)
  window.addEventListener('click', handleGlobalClick)
  window.addEventListener('keydown', handleChatHotkeys)
  applyRouteState()
})

onUnmounted(() => {
  window.removeEventListener('app:new-chat', handleNewChatShortcut as EventListener)
  window.removeEventListener('click', handleGlobalClick)
  window.removeEventListener('keydown', handleChatHotkeys)
})

watch(() => [chatStore.currentAgent, chatStore.currentSessionId], () => {
  persistSessionVisit()
  syncRouteFromState()
})

watch(() => route.query, () => {
  applyRouteState()
})

watch(filteredCommands, (commands) => {
  if (!commands.length) {
    selectedCommandIndex.value = 0
    return
  }
  if (selectedCommandIndex.value > commands.length - 1) {
    selectedCommandIndex.value = 0
  }
})
</script>

<style scoped>
.section-card {
  margin-bottom: 16px;
}

.continue-card {
  overflow: hidden;
}

.entry-context-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(79, 142, 247, 0.16);
  background: linear-gradient(135deg, rgba(79, 142, 247, 0.1), rgba(255, 255, 255, 0.03));
}

.entry-context-title {
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
}

.entry-context-desc {
  margin-top: 6px;
  color: var(--text2);
  font-size: 12px;
  line-height: 1.6;
}

.entry-context-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.source-context-card {
  overflow: hidden;
  border: 1px solid rgba(15, 118, 110, 0.14);
  background:
    radial-gradient(circle at top right, rgba(13, 148, 136, 0.14), transparent 40%),
    linear-gradient(135deg, rgba(240, 253, 250, 0.96), rgba(255, 255, 255, 0.9));
}

.source-context-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.source-context-kicker {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #0f766e;
}

.source-context-title {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}

.source-context-desc {
  margin-top: 8px;
  color: var(--text2);
  font-size: 13px;
  line-height: 1.7;
}

.source-context-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.source-context-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.source-context-block {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.66);
}

.source-context-block.active {
  border-color: rgba(13, 148, 136, 0.28);
  background: rgba(240, 253, 250, 0.92);
}

.source-context-label {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text3);
}

.source-context-value {
  margin-top: 8px;
  color: var(--text);
  font-size: 13px;
  line-height: 1.7;
  min-height: 66px;
}

.source-context-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.chat-overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.chat-overview-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background:
    radial-gradient(circle at top right, rgba(79, 142, 247, 0.08), transparent 40%),
    rgba(255, 255, 255, 0.03);
}

.chat-overview-label {
  margin-bottom: 8px;
  font-size: 11px;
  color: var(--text3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.chat-overview-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}

.chat-overview-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text2);
  word-break: break-word;
}

.session-quick-actions {
  overflow: hidden;
}

.session-review-card {
  overflow: hidden;
}

.session-review-preview {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background:
    radial-gradient(circle at top right, rgba(79, 142, 247, 0.08), transparent 40%),
    rgba(255, 255, 255, 0.03);
  color: var(--text2);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.session-review-tags {
  margin-top: 12px;
}

.session-review-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.continuation-card {
  overflow: hidden;
}

.continuation-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.continuation-block {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background:
    radial-gradient(circle at top right, rgba(79, 142, 247, 0.08), transparent 40%),
    rgba(255, 255, 255, 0.03);
}

.continuation-label {
  font-size: 11px;
  color: var(--text3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.continuation-value {
  margin-top: 8px;
  color: var(--text);
  font-size: 13px;
  line-height: 1.7;
  min-height: 68px;
}

.continuation-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.continuation-action-btn {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text2);
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition);
}

.continuation-action-btn:hover {
  border-color: rgba(79, 142, 247, 0.24);
  background: rgba(79, 142, 247, 0.08);
  color: var(--accent2);
}

.session-action-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.session-action-tile {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  text-align: left;
  cursor: pointer;
  transition: transform var(--transition), border-color var(--transition), background var(--transition);
}

.session-action-tile:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: rgba(79, 142, 247, 0.24);
  background: rgba(79, 142, 247, 0.06);
}

.session-action-tile strong {
  color: var(--text);
  font-size: 14px;
}

.session-action-tile span {
  color: var(--text3);
  font-size: 12px;
  line-height: 1.5;
}

.last-session-banner {
  width: 100%;
  display: grid;
  gap: 4px;
  margin-bottom: 12px;
  padding: 14px 16px;
  border: 1px solid rgba(79, 142, 247, 0.22);
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(79, 142, 247, 0.12), rgba(59, 130, 246, 0.04));
  text-align: left;
  cursor: pointer;
}

.last-session-label {
  font-size: 11px;
  color: var(--text3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.last-session-title {
  color: var(--text);
  font-size: 15px;
}

.last-session-meta {
  font-size: 12px;
  color: var(--text2);
}

.continue-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.continue-item {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
  text-align: left;
  cursor: pointer;
  transition: transform var(--transition), border-color var(--transition), box-shadow var(--transition);
}

.continue-item:hover {
  transform: translateY(-2px);
  border-color: rgba(79, 142, 247, 0.24);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}

.continue-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.continue-meta {
  font-size: 12px;
  color: var(--text3);
}

.export-shell {
  position: relative;
}

.export-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: min(360px, calc(100vw - 32px));
  max-height: min(68vh, 560px);
  overflow-y: auto;
  padding: 12px;
  border-radius: 20px;
  border: 1px solid rgba(79, 142, 247, 0.16);
  background:
    linear-gradient(180deg, rgba(79, 142, 247, 0.08), rgba(255, 255, 255, 0.02) 14%, rgba(255, 255, 255, 0.02)),
    var(--surface);
  box-shadow: 0 28px 60px rgba(15, 23, 42, 0.28);
  display: grid;
  gap: 10px;
  z-index: 12;
  backdrop-filter: blur(18px);
}

.export-action {
  border: 1px solid rgba(255, 255, 255, 0.04);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.015));
  color: var(--text);
  border-radius: 14px;
  padding: 11px 12px;
  text-align: left;
  cursor: pointer;
  display: grid;
  gap: 5px;
  transition: transform var(--transition), border-color var(--transition), background var(--transition), box-shadow var(--transition);
}

.export-action:hover:not(:disabled) {
  background: linear-gradient(180deg, rgba(79, 142, 247, 0.09), rgba(255, 255, 255, 0.03));
  border-color: rgba(79, 142, 247, 0.18);
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.16);
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

.chat-recent-action {
  margin: 0 0 12px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.08), rgba(14, 165, 233, 0.04));
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.recent-action-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--accent2);
}

.recent-action-text {
  font-size: 13px;
  color: var(--text2);
}

.command-palette-mask {
  position: fixed;
  inset: 0;
  background:
    radial-gradient(circle at top, rgba(79, 142, 247, 0.12), transparent 30%),
    rgba(15, 23, 42, 0.46);
  backdrop-filter: blur(10px);
  display: grid;
  place-items: start center;
  padding: 12vh 20px 20px;
  z-index: 40;
}

.command-palette {
  width: min(640px, 100%);
  border-radius: 24px;
  border: 1px solid rgba(79, 142, 247, 0.14);
  background:
    linear-gradient(180deg, rgba(79, 142, 247, 0.09), rgba(255, 255, 255, 0.03) 16%, rgba(255, 255, 255, 0.03)),
    rgba(255, 255, 255, 0.96);
  box-shadow: 0 30px 90px rgba(15, 23, 42, 0.28);
  overflow: hidden;
}

.command-palette-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 12px;
}

.command-palette-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}

.command-palette-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text3);
}

.command-palette-close {
  border: none;
  background: transparent;
  color: var(--text3);
  cursor: pointer;
}

.command-palette-input {
  width: calc(100% - 36px);
  margin: 0 18px 14px;
  border-radius: 16px;
  border: 1px solid rgba(79, 142, 247, 0.12);
  padding: 12px 14px;
  font-size: 14px;
  outline: none;
  background: rgba(255, 255, 255, 0.76);
}

.command-palette-input:focus {
  border-color: rgba(59, 130, 246, 0.4);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.08);
}

.command-palette-list {
  display: grid;
  gap: 8px;
  padding: 0 14px 14px;
  max-height: 420px;
  overflow: auto;
}

.command-palette-item {
  border: 1px solid rgba(255, 255, 255, 0.02);
  background: rgba(255, 255, 255, 0.02);
  border-radius: 16px;
  padding: 12px 14px;
  text-align: left;
  display: grid;
  gap: 4px;
  cursor: pointer;
  transition: transform var(--transition), border-color var(--transition), background var(--transition), box-shadow var(--transition);
}

.command-palette-item:hover,
.command-palette-item.active {
  background: linear-gradient(180deg, rgba(79, 142, 247, 0.09), rgba(255, 255, 255, 0.03));
  border-color: rgba(79, 142, 247, 0.14);
  transform: translateY(-1px);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.08);
}

.command-palette-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.command-palette-desc {
  font-size: 12px;
  color: var(--text3);
}

.command-palette-empty {
  padding: 18px 14px;
  text-align: center;
  color: var(--text3);
  font-size: 13px;
}

@media (max-width: 960px) {
  .chat-overview-grid,
  .source-context-grid,
  .session-action-grid,
  .continue-grid,
  .continuation-grid {
    grid-template-columns: 1fr;
  }

  .entry-context-card,
  .source-context-head {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 820px) {
  .page-hero-actions,
  .toolbar-meta,
  .continuation-actions {
    width: 100%;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .page-hero-actions > .btn,
  .page-hero-actions > .export-shell,
  .continuation-actions > .continuation-action-btn {
    flex: 1 1 calc(50% - 8px);
    min-width: 0;
  }

  .session-action-tile,
  .continuation-block {
    padding: 12px 14px;
  }

  .toolbar-meta {
    justify-content: flex-start;
  }

  .toolbar-pill {
    white-space: nowrap;
  }

  .export-panel {
    position: fixed;
    left: 16px;
    right: 16px;
    top: auto;
    bottom: 18px;
    width: auto;
    max-height: min(62vh, 520px);
  }

  .command-palette-mask {
    padding: 10vh 12px 12px;
  }
}

@media (max-width: 560px) {
  .page-hero-actions > .btn,
  .page-hero-actions > .export-shell,
  .continuation-actions > .continuation-action-btn {
    flex-basis: 100%;
  }

  .chat-recent-action,
  .entry-context-card,
  .source-context-block {
    padding: 12px;
  }
}
</style>
