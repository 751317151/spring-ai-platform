import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as agentApi from '@/api/agent'
import type { AgentType, ChatMessage, SessionInfo } from '@/api/types'
import { AGENT_CONFIG, MOCK_RESPONSES } from '@/utils/constants'
import { useAuthStore } from './auth'
import { buildHeaders } from '@/api/client'

export const useChatStore = defineStore('chat', () => {
  const currentAgent = ref<AgentType>('rd')
  const currentSessionId = ref<string | null>(null)
  const sessionList = ref<SessionInfo[]>([])
  const chatHistory = ref<ChatMessage[]>([])
  const isThinking = ref(false)

  const authStore = useAuthStore()

  function getAgentConfig() {
    return AGENT_CONFIG[currentAgent.value] || AGENT_CONFIG.rd
  }

  function generateSessionId(): string {
    return 'sess-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6)
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
    getAgentConfig, selectAgent, loadSessions, switchSession, createNewSession,
    sendMessage, clearChat
  }
})
