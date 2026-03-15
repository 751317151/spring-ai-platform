import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as agentApi from '@/api/agent'
import * as authApi from '@/api/auth'
import type { AgentType, ChatMessage, SessionInfo, BotPermission } from '@/api/types'
import { AGENT_CONFIG, MOCK_RESPONSES } from '@/utils/constants'
import type { AgentConfig } from '@/utils/constants'
import { useAuthStore } from './auth'
import { buildHeaders } from '@/api/client'

export const useChatStore = defineStore('chat', () => {
  const currentAgent = ref<AgentType>('rd')
  const currentSessionId = ref<string | null>(null)
  const sessionList = ref<SessionInfo[]>([])
  const chatHistory = ref<ChatMessage[]>([])
  const isThinking = ref(false)
  const availableBots = ref<BotPermission[]>([])

  const authStore = useAuthStore()

  // 动态 Agent 列表：合并后端权限 + 前端静态配置
  const agentList = computed(() => {
    if (availableBots.value.length === 0) {
      return Object.entries(AGENT_CONFIG).map(([type, config]) => ({
        type,
        ...config
      }))
    }
    return availableBots.value.map(bot => {
      const staticConfig = AGENT_CONFIG[bot.botType]
      return {
        type: bot.botType,
        name: staticConfig?.name || bot.botType,
        icon: staticConfig?.icon || '🤖',
        color: staticConfig?.color || '#6b7280',
        desc: staticConfig?.desc || ''
      }
    })
  })

  function getAgentConfig(): AgentConfig {
    const found = agentList.value.find(a => a.type === currentAgent.value)
    if (found) return { name: found.name, icon: found.icon, color: found.color, desc: found.desc }
    return AGENT_CONFIG[currentAgent.value] || AGENT_CONFIG.rd
  }

  async function loadAvailableBots() {
    try {
      const bots = await authApi.getMyBots()
      availableBots.value = bots || []
      if (bots.length > 0 && !bots.find(b => b.botType === currentAgent.value)) {
        currentAgent.value = bots[0].botType
      }
    } catch {
      availableBots.value = []
    }
  }

  function generateSessionId(): string {
    return authStore.userId + '-' + currentAgent.value + '-' + Date.now()
  }

  async function selectAgent(type: AgentType) {
    currentAgent.value = type
    chatHistory.value = []
    currentSessionId.value = null
    sessionList.value = []
    await loadSessions()
  }

  async function loadSessions() {
    try {
      const data = await agentApi.getSessions(currentAgent.value, authStore.userId)
      sessionList.value = data || []
      if (sessionList.value.length > 0) {
        await switchSession(sessionList.value[0].sessionId)
      } else {
        createNewSession()
      }
    } catch {
      createNewSession()
    }
  }

  async function switchSession(sessionId: string) {
    currentSessionId.value = sessionId
    chatHistory.value = []
    try {
      const data = await agentApi.getHistory(currentAgent.value, authStore.userId, sessionId)
      if (data && Array.isArray(data)) {
        chatHistory.value = data
      }
    } catch {
      // ignore
    }
  }

  function createNewSession() {
    const sid = generateSessionId()
    currentSessionId.value = sid
    chatHistory.value = []
    sessionList.value.unshift({ sessionId: sid, summary: '新对话' })
  }

  async function sendMessage(message: string): Promise<string> {
    if (!message.trim() || isThinking.value) return ''

    chatHistory.value.push({ role: 'user', content: message })
    isThinking.value = true

    const sessionId = currentSessionId.value || generateSessionId()
    if (!currentSessionId.value) currentSessionId.value = sessionId

    // Update session summary with first message
    const session = sessionList.value.find(s => s.sessionId === sessionId)
    if (session && session.summary === '新对话') {
      session.summary = message.slice(0, 20) + (message.length > 20 ? '...' : '')
    }

    let fullResponse = ''

    try {
      const headers = buildHeaders({
        'X-User-Id': authStore.userId,
        'X-Session-Id': sessionId
      })

      const res = await fetch(`/api/v1/agent/${currentAgent.value}/chat/stream`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ message })
      })

      if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)

      // Add empty assistant message for streaming
      chatHistory.value.push({ role: 'assistant', content: '' })
      const msgIndex = chatHistory.value.length - 1

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed || !trimmed.startsWith('data:')) continue
          const jsonStr = trimmed.slice(5).trim()
          if (!jsonStr || jsonStr === '[DONE]') continue
          try {
            const data = JSON.parse(jsonStr)
            if (data.chunk) {
              fullResponse += data.chunk
              chatHistory.value[msgIndex] = { role: 'assistant', content: fullResponse }
            }
          } catch {
            fullResponse += jsonStr
            chatHistory.value[msgIndex] = { role: 'assistant', content: fullResponse }
          }
        }
      }
    } catch {
      // Mock fallback
      const mockFn = MOCK_RESPONSES[currentAgent.value] || MOCK_RESPONSES.rd
      fullResponse = mockFn(message)
      await new Promise(r => setTimeout(r, 800 + Math.random() * 1000))
      chatHistory.value.push({ role: 'assistant', content: fullResponse })
    } finally {
      isThinking.value = false
    }

    return fullResponse
  }

  async function clearChat() {
    try {
      if (currentSessionId.value) {
        await agentApi.clearMemory(currentAgent.value, authStore.userId, currentSessionId.value)
      }
    } catch {
      // ignore
    }
    chatHistory.value = []
    createNewSession()
  }

  return {
    currentAgent, currentSessionId, sessionList, chatHistory, isThinking,
    availableBots, agentList,
    getAgentConfig, loadAvailableBots, selectAgent, loadSessions, switchSession, createNewSession,
    sendMessage, clearChat
  }
})
