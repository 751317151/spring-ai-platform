import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as agentApi from '@/api/agent'
import * as authApi from '@/api/auth'
import type { AgentType, BotPermission, ChatMessage, SendMessageOptions, SessionConfig, SessionInfo, SSEChunk } from '@/api/types'
import { AGENT_CONFIG, MOCK_RESPONSES } from '@/utils/constants'
import type { AgentConfig } from '@/utils/constants'
import { useAuthStore } from './auth'
import { useRuntimeStore } from './runtime'

const DEFAULT_SESSION_TITLE = '新对话'
const DEFAULT_SESSION_CONFIG: SessionConfig = {
  model: 'auto',
  temperature: 0.7,
  maxContextMessages: 10,
  knowledgeEnabled: true,
  systemPromptTemplate: ''
}

export const useChatStore = defineStore('chat', () => {
  const currentAgent = ref<AgentType>('rd')
  const currentSessionId = ref<string | null>(null)
  const sessionList = ref<SessionInfo[]>([])
  const chatHistory = ref<ChatMessage[]>([])
  const sessionDrafts = ref<Record<string, string>>({})
  const sessionConfig = ref<SessionConfig>({ ...DEFAULT_SESSION_CONFIG })
  const isThinking = ref(false)
  const availableBots = ref<BotPermission[]>([])
  const showArchivedSessions = ref(false)
  const activeStreamAbort = ref<(() => void) | null>(null)

  const authStore = useAuthStore()
  const runtimeStore = useRuntimeStore()

  function normalizeSessionConfig(config?: SessionConfig | null): SessionConfig {
    const temperature = typeof config?.temperature === 'number'
      ? Math.min(1, Math.max(0, Number(config.temperature)))
      : DEFAULT_SESSION_CONFIG.temperature
    const maxContextMessages = typeof config?.maxContextMessages === 'number'
      ? Math.min(20, Math.max(1, Math.round(config.maxContextMessages)))
      : DEFAULT_SESSION_CONFIG.maxContextMessages

    return {
      model: config?.model?.trim() ? config.model.trim() : DEFAULT_SESSION_CONFIG.model,
      temperature,
      maxContextMessages,
      knowledgeEnabled: typeof config?.knowledgeEnabled === 'boolean'
        ? config.knowledgeEnabled
        : DEFAULT_SESSION_CONFIG.knowledgeEnabled,
      systemPromptTemplate: config?.systemPromptTemplate?.trim() || '',
      updatedAt: config?.updatedAt ?? null
    }
  }

  function normalizeChatMessage(message: ChatMessage): ChatMessage {
    return {
      ...message,
      feedback: message.feedback ?? null,
      sessionConfigSnapshot: message.sessionConfigSnapshot
        ? normalizeSessionConfig(message.sessionConfigSnapshot)
        : null,
      derivedFrom: message.derivedFrom ?? null
    }
  }

  function getDraftStorageKey() {
    return `chat_session_drafts_${encodeURIComponent(authStore.userId || 'anonymous')}`
  }

  function loadDrafts() {
    try {
      const raw = sessionStorage.getItem(getDraftStorageKey())
      sessionDrafts.value = raw ? JSON.parse(raw) : {}
    } catch {
      sessionDrafts.value = {}
    }
  }

  function persistDrafts() {
    try {
      sessionStorage.setItem(getDraftStorageKey(), JSON.stringify(sessionDrafts.value))
    } catch {
      // ignore persistence failures
    }
  }

  const agentList = computed(() => {
    if (runtimeStore.demoMode) {
      return Object.entries(AGENT_CONFIG).map(([type, config]) => ({
        type,
        ...config
      }))
    }

    return availableBots.value.map((bot) => {
      const staticConfig = AGENT_CONFIG[bot.botType]
      return {
        type: bot.botType,
        name: staticConfig?.name || bot.botType,
        icon: staticConfig?.icon || 'AI',
        color: staticConfig?.color || '#6b7280',
        desc: staticConfig?.desc || ''
      }
    })
  })

  const activeSessions = computed(() => sessionList.value.filter((session) => !isArchived(session)))
  const archivedSessions = computed(() => sessionList.value.filter((session) => isArchived(session)))

  function getAgentConfig(): AgentConfig {
    const found = agentList.value.find((agent) => agent.type === currentAgent.value)
    if (found) {
      return { name: found.name, icon: found.icon, color: found.color, desc: found.desc }
    }
    return AGENT_CONFIG[currentAgent.value] || AGENT_CONFIG.rd
  }

  function isPinned(session: SessionInfo): boolean {
    return session.pinned === true || session.pinned === 'true'
  }

  function isArchived(session: SessionInfo): boolean {
    return session.archived === true || session.archived === 'true'
  }

  function sortSessions(sessions: SessionInfo[]): SessionInfo[] {
    return [...sessions].sort((left, right) => {
      const leftArchived = isArchived(left)
      const rightArchived = isArchived(right)
      if (leftArchived !== rightArchived) {
        return leftArchived ? 1 : -1
      }

      const leftPinned = isPinned(left)
      const rightPinned = isPinned(right)
      if (leftPinned !== rightPinned) {
        return leftPinned ? -1 : 1
      }

      return Number(right.updatedAt || 0) - Number(left.updatedAt || 0)
    })
  }

  function resetSessionConfig() {
    sessionConfig.value = normalizeSessionConfig(DEFAULT_SESSION_CONFIG)
  }

  async function loadSessionConfig(sessionId: string | null = currentSessionId.value) {
    if (!sessionId) {
      resetSessionConfig()
      return
    }

    try {
      const data = await agentApi.getSessionConfig(currentAgent.value, sessionId)
      sessionConfig.value = normalizeSessionConfig(data)
      runtimeStore.markServiceAvailable('chat')
    } catch {
      resetSessionConfig()
    }
  }

  async function saveCurrentSessionConfig(partial?: Partial<SessionConfig>) {
    const sessionId = currentSessionId.value
    if (!sessionId) {
      return false
    }

    sessionConfig.value = normalizeSessionConfig({
      ...sessionConfig.value,
      ...partial
    })

    try {
      await agentApi.saveSessionConfig(currentAgent.value, sessionId, sessionConfig.value)
      runtimeStore.markServiceAvailable('chat')
      return true
    } catch {
      runtimeStore.markServiceUnavailable('chat', '会话配置保存失败，请稍后重试。')
      return false
    }
  }

  async function loadAvailableBots() {
    try {
      const bots = await authApi.getMyBots()
      availableBots.value = bots || []
      runtimeStore.markServiceAvailable('chat')
      if (bots.length > 0 && !bots.find((bot) => bot.botType === currentAgent.value)) {
        currentAgent.value = bots[0].botType
      }
    } catch {
      availableBots.value = []
      runtimeStore.markServiceUnavailable(
        'chat',
        runtimeStore.demoMode
          ? '助手权限接口不可用，当前使用演示配置。'
          : '助手权限接口不可用，当前无法展示真实可用助手列表。'
      )
    }
  }

  function generateSessionId(): string {
    return `${encodeURIComponent(authStore.userId)}-${currentAgent.value}-${Date.now()}`
  }

  async function selectAgent(type: AgentType) {
    if (isThinking.value) {
      stopStreaming()
    }
    currentAgent.value = type
    currentSessionId.value = null
    sessionList.value = []
    chatHistory.value = []
    resetSessionConfig()
    await loadSessions()
  }

  async function loadSessions() {
    try {
      const data = await agentApi.getSessions(currentAgent.value)
      sessionList.value = sortSessions(data || [])
      runtimeStore.markServiceAvailable('chat')
      if (activeSessions.value.length > 0) {
        await switchSession(activeSessions.value[0].sessionId)
      } else if (sessionList.value.length > 0) {
        await switchSession(sessionList.value[0].sessionId)
      } else {
        createNewSession()
      }
    } catch {
      runtimeStore.markServiceUnavailable(
        'chat',
        runtimeStore.demoMode
          ? '会话服务不可用，当前使用本地演示会话。'
          : '会话服务不可用，当前仅保留本地临时会话。'
      )
      createNewSession()
    }
  }

  async function switchSession(sessionId: string) {
    if (isThinking.value) {
      stopStreaming()
    }
    currentSessionId.value = sessionId
    chatHistory.value = []
    try {
      const data = await agentApi.getHistory(currentAgent.value, sessionId)
      if (data && Array.isArray(data)) {
        chatHistory.value = data.map((item) => normalizeChatMessage(item))
      }
      await loadSessionConfig(sessionId)
      runtimeStore.markServiceAvailable('chat')
    } catch {
      resetSessionConfig()
      runtimeStore.markServiceUnavailable('chat', '历史会话读取失败，请稍后重试。')
    }
  }

  function createNewSession() {
    const sid = generateSessionId()
    currentSessionId.value = sid
    chatHistory.value = []
    resetSessionConfig()

    if (!sessionDrafts.value[sid]) {
      sessionDrafts.value[sid] = ''
      persistDrafts()
    }

    sessionList.value = sortSessions([
      {
        sessionId: sid,
        summary: DEFAULT_SESSION_TITLE,
        updatedAt: String(Date.now()),
        pinned: false,
        archived: false
      },
      ...sessionList.value
    ])
  }

  async function renameSession(sessionId: string, title: string) {
    const normalizedTitle = title.trim()
    if (!normalizedTitle) {
      return
    }

    const session = sessionList.value.find((item) => item.sessionId === sessionId)
    if (session) {
      session.summary = normalizedTitle
      session.updatedAt = String(Date.now())
      sessionList.value = sortSessions(sessionList.value)
    }

    try {
      await agentApi.renameSessionTitle(currentAgent.value, sessionId, normalizedTitle)
      runtimeStore.markServiceAvailable('chat')
    } catch {
      runtimeStore.markServiceUnavailable('chat', '会话标题更新失败，请稍后重试。')
      await loadSessions()
    }
  }

  async function togglePinSession(sessionId: string) {
    const session = sessionList.value.find((item) => item.sessionId === sessionId)
    if (!session) {
      return
    }

    const currentPinned = isPinned(session)
    session.pinned = !currentPinned
    session.updatedAt = String(Date.now())
    sessionList.value = sortSessions(sessionList.value)

    try {
      await agentApi.pinSession(currentAgent.value, sessionId, !currentPinned)
      runtimeStore.markServiceAvailable('chat')
    } catch {
      runtimeStore.markServiceUnavailable('chat', '会话置顶更新失败，请稍后重试。')
      await loadSessions()
    }
  }

  async function toggleArchiveSession(sessionId: string) {
    const session = sessionList.value.find((item) => item.sessionId === sessionId)
    if (!session) {
      return
    }

    const currentArchived = isArchived(session)
    session.archived = !currentArchived
    session.updatedAt = String(Date.now())
    sessionList.value = sortSessions(sessionList.value)

    if (currentSessionId.value === sessionId && !currentArchived) {
      const nextSession = activeSessions.value.find((item) => item.sessionId !== sessionId)
      if (nextSession) {
        await switchSession(nextSession.sessionId)
      } else {
        createNewSession()
      }
    }

    try {
      await agentApi.archiveSession(currentAgent.value, sessionId, !currentArchived)
      runtimeStore.markServiceAvailable('chat')
    } catch {
      runtimeStore.markServiceUnavailable('chat', '会话归档更新失败，请稍后重试。')
      await loadSessions()
    }
  }

  async function deleteSession(sessionId: string) {
    try {
      await agentApi.deleteSession(currentAgent.value, sessionId)
      sessionList.value = sessionList.value.filter((item) => item.sessionId !== sessionId)
      if (sessionId in sessionDrafts.value) {
        delete sessionDrafts.value[sessionId]
        persistDrafts()
      }

      if (currentSessionId.value === sessionId) {
        chatHistory.value = []
        currentSessionId.value = null
        resetSessionConfig()
        if (activeSessions.value.length > 0) {
          await switchSession(activeSessions.value[0].sessionId)
        } else if (sessionList.value.length > 0) {
          await switchSession(sessionList.value[0].sessionId)
        } else {
          createNewSession()
        }
      }

      runtimeStore.markServiceAvailable('chat')
    } catch {
      runtimeStore.markServiceUnavailable('chat', '删除会话失败，请稍后重试。')
      await loadSessions()
    }
  }

  function toggleArchivedVisibility() {
    showArchivedSessions.value = !showArchivedSessions.value
  }

  async function sendMessage(message: string, options?: SendMessageOptions): Promise<string> {
    if (!message.trim() || isThinking.value) {
      return ''
    }

    const effectiveConfig = normalizeSessionConfig(options?.sessionConfigOverride ?? sessionConfig.value)
    const derivedFrom = options?.derivedFrom ?? null

    chatHistory.value.push({
      role: 'user',
      content: message,
      sessionConfigSnapshot: effectiveConfig,
      derivedFrom
    })
    isThinking.value = true

    const sessionId = currentSessionId.value || generateSessionId()
    if (!currentSessionId.value) {
      currentSessionId.value = sessionId
    }
    clearDraft(sessionId)

    const session = sessionList.value.find((item) => item.sessionId === sessionId)
    if (session) {
      if (session.summary === DEFAULT_SESSION_TITLE) {
        session.summary = message.slice(0, 20) + (message.length > 20 ? '...' : '')
      }
      session.updatedAt = String(Date.now())
      session.archived = false
      session.model = String(effectiveConfig.model || '')
      session.knowledgeEnabled = Boolean(effectiveConfig.knowledgeEnabled)
      sessionList.value = sortSessions(sessionList.value)
    }

    let fullResponse = ''
    let messageIndex = -1

    try {
      const { response, abort } = agentApi.chatStream(
        currentAgent.value,
        message,
        sessionId,
        effectiveConfig
      )
      activeStreamAbort.value = abort
      const res = await response

      if (!res.ok || !res.body) {
        throw new Error(`HTTP ${res.status}`)
      }

      chatHistory.value.push({
        role: 'assistant',
        content: '',
        feedback: null,
        sessionConfigSnapshot: effectiveConfig,
        derivedFrom
      })
      messageIndex = chatHistory.value.length - 1

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed || !trimmed.startsWith('data:')) {
            continue
          }

          const jsonStr = trimmed.slice(5).trim()
          if (!jsonStr || jsonStr === '[DONE]') {
            continue
          }

          try {
            const data = JSON.parse(jsonStr) as SSEChunk
            if (data.chunk) {
              fullResponse += data.chunk
              chatHistory.value[messageIndex] = {
                ...chatHistory.value[messageIndex],
                role: 'assistant',
                content: fullResponse
              }
            }
            if (data.done && data.responseId) {
              chatHistory.value[messageIndex] = {
                ...chatHistory.value[messageIndex],
                role: 'assistant',
                content: fullResponse,
                responseId: data.responseId,
                feedback: chatHistory.value[messageIndex]?.feedback ?? null
              }
            }
          } catch {
            fullResponse += jsonStr
            chatHistory.value[messageIndex] = {
              ...chatHistory.value[messageIndex],
              role: 'assistant',
              content: fullResponse
            }
          }
        }
      }

      runtimeStore.markServiceAvailable('chat')
    } catch (error) {
      const aborted = error instanceof DOMException && error.name === 'AbortError'
      if (aborted) {
        if (messageIndex >= 0) {
          const current = chatHistory.value[messageIndex]
          chatHistory.value[messageIndex] = {
            ...current,
            content: (current?.content || '已停止生成').trim()
          }
        }
        return fullResponse
      }

      runtimeStore.markServiceUnavailable(
        'chat',
        runtimeStore.demoMode
          ? '聊天后端不可用，当前返回的是本地演示回答。'
          : '聊天后端不可用，请检查 agent-service 或网关状态。'
      )

      if (runtimeStore.demoMode) {
        const mockFn = MOCK_RESPONSES[currentAgent.value] || MOCK_RESPONSES.rd
        fullResponse = mockFn(message)
        await new Promise((resolve) => setTimeout(resolve, 500))
        chatHistory.value.push({
          role: 'assistant',
          content: fullResponse,
          feedback: null,
          sessionConfigSnapshot: effectiveConfig,
          derivedFrom
        })
      } else {
        chatHistory.value.push({
          role: 'assistant',
          content: '聊天服务暂不可用，请稍后重试。当前页面不会自动切换到模拟回答。',
          feedback: null,
          sessionConfigSnapshot: effectiveConfig,
          derivedFrom
        })
      }
    } finally {
      activeStreamAbort.value = null
      isThinking.value = false
    }

    return fullResponse
  }

  async function submitFeedback(messageIndex: number, feedback: 'up' | 'down') {
    const message = chatHistory.value[messageIndex]
    if (!message?.responseId) {
      return false
    }

    await agentApi.submitFeedback(message.responseId, feedback)
    chatHistory.value[messageIndex] = {
      ...message,
      feedback
    }
    return true
  }

  async function clearChat() {
    try {
      if (currentSessionId.value) {
        await agentApi.clearMemory(currentAgent.value, currentSessionId.value)
      }
    } catch {
      runtimeStore.markServiceUnavailable('chat', '清空会话失败，请稍后重试。')
    }
    chatHistory.value = []
    createNewSession()
  }

  function stopStreaming() {
    activeStreamAbort.value?.()
  }

  function setDraft(sessionId: string | null, value: string) {
    if (!sessionId) {
      return
    }
    sessionDrafts.value[sessionId] = value
    persistDrafts()
  }

  function getDraft(sessionId: string | null) {
    if (!sessionId) {
      return ''
    }
    return sessionDrafts.value[sessionId] || ''
  }

  function clearDraft(sessionId: string | null) {
    if (!sessionId) {
      return
    }
    sessionDrafts.value[sessionId] = ''
    persistDrafts()
  }

  loadDrafts()

  return {
    currentAgent,
    currentSessionId,
    sessionList,
    activeSessions,
    archivedSessions,
    sessionDrafts,
    sessionConfig,
    showArchivedSessions,
    chatHistory,
    isThinking,
    availableBots,
    activeStreamAbort,
    agentList,
    getAgentConfig,
    loadAvailableBots,
    selectAgent,
    loadSessions,
    switchSession,
    createNewSession,
    loadSessionConfig,
    saveCurrentSessionConfig,
    resetSessionConfig,
    normalizeSessionConfig,
    renameSession,
    togglePinSession,
    toggleArchiveSession,
    toggleArchivedVisibility,
    deleteSession,
    setDraft,
    getDraft,
    clearDraft,
    sendMessage,
    submitFeedback,
    clearChat,
    stopStreaming
  }
})
