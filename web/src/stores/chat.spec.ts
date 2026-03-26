import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from './chat'
import { useAuthStore } from './auth'
import { useRuntimeStore } from './runtime'
import * as agentApi from '@/api/agent'
import * as authApi from '@/api/auth'

vi.mock('@/api/agent', () => ({
  getSessions: vi.fn(),
  getHistory: vi.fn(),
  getSessionConfig: vi.fn(),
  saveSessionConfig: vi.fn(),
  renameSessionTitle: vi.fn(),
  pinSession: vi.fn(),
  archiveSession: vi.fn(),
  deleteSession: vi.fn(),
  clearMemory: vi.fn(),
  chatStream: vi.fn(),
  submitFeedback: vi.fn()
}))

vi.mock('@/api/auth', () => ({
  getMyBots: vi.fn()
}))

describe('chat store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('loads available bots and switches current agent to the first permitted one', async () => {
    vi.mocked(authApi.getMyBots).mockResolvedValue([
      { botType: 'search', enabled: true }
    ] as never)

    const store = useChatStore()
    await store.loadAvailableBots()

    expect(store.availableBots).toHaveLength(1)
    expect(store.currentAgent).toBe('search')
  })

  it('falls back to a local session when loading sessions fails', async () => {
    vi.mocked(agentApi.getSessions).mockRejectedValue(new Error('boom'))

    const store = useChatStore()
    const runtimeStore = useRuntimeStore()
    const authStore = useAuthStore()
    authStore.userId = 'EMP100'

    await store.loadSessions()

    expect(store.currentSessionId).toContain('EMP100')
    expect(store.sessionList.length).toBeGreaterThan(0)
    expect(runtimeStore.getServiceStatus('chat').available).toBe(false)
  })

  it('persists drafts by session and user id', () => {
    const store = useChatStore()
    const authStore = useAuthStore()
    authStore.userId = 'EMP200'

    store.createNewSession()
    const sessionId = store.currentSessionId
    store.setDraft(sessionId, 'draft text')

    const raw = sessionStorage.getItem('chat_session_drafts_EMP200')
    expect(raw).toContain('draft text')
    expect(store.getDraft(sessionId)).toBe('draft text')

    store.clearDraft(sessionId)
    expect(store.getDraft(sessionId)).toBe('')
  })

  it('passes current session config and stores a snapshot when sending a message', async () => {
    vi.mocked(agentApi.chatStream).mockReturnValue({
      response: Promise.resolve(new Response('data: {"chunk":"ok","done":false}\n\ndata: {"chunk":"","done":true,"responseId":"resp-1"}\n\n', {
        status: 200,
        headers: {
          'Content-Type': 'text/event-stream'
        }
      })),
      abort: vi.fn()
    })

    const store = useChatStore()
    const authStore = useAuthStore()
    authStore.userId = 'EMP300'
    store.createNewSession()
    store.sessionConfig = {
      model: 'gpt-4o-mini',
      temperature: 0.3,
      maxContextMessages: 6,
      knowledgeEnabled: false,
      systemPromptTemplate: 'summary first'
    } as never

    await store.sendMessage('plan the release')

    expect(agentApi.chatStream).toHaveBeenCalledWith(
      store.currentAgent,
      'plan the release',
      store.currentSessionId,
      expect.objectContaining({
        model: 'gpt-4o-mini',
        temperature: 0.3,
        maxContextMessages: 6,
        knowledgeEnabled: false,
        systemPromptTemplate: 'summary first'
      })
    )
    expect(store.chatHistory[0]?.sessionConfigSnapshot).toEqual(expect.objectContaining({
      model: 'gpt-4o-mini',
      temperature: 0.3,
      maxContextMessages: 6,
      knowledgeEnabled: false
    }))
    expect(store.chatHistory[1]?.sessionConfigSnapshot).toEqual(expect.objectContaining({
      model: 'gpt-4o-mini',
      temperature: 0.3,
      maxContextMessages: 6,
      knowledgeEnabled: false
    }))
  })

  it('uses one-off config override without mutating current session config', async () => {
    vi.mocked(agentApi.chatStream).mockReturnValue({
      response: Promise.resolve(new Response('data: {"chunk":"ok","done":false}\n\ndata: {"chunk":"","done":true,"responseId":"resp-override"}\n\n', {
        status: 200,
        headers: {
          'Content-Type': 'text/event-stream'
        }
      })),
      abort: vi.fn()
    })

    const store = useChatStore()
    const authStore = useAuthStore()
    authStore.userId = 'EMP301'
    store.createNewSession()
    store.sessionConfig = {
      model: 'auto',
      temperature: 0.7,
      maxContextMessages: 10,
      knowledgeEnabled: true,
      systemPromptTemplate: ''
    } as never

    await store.sendMessage('continue output', {
      sessionConfigOverride: {
        model: 'gpt-4.1',
        temperature: 0.2,
        maxContextMessages: 4,
        knowledgeEnabled: false
      },
      derivedFrom: {
        action: 'continue',
        messageIndex: 2
      }
    })

    expect(agentApi.chatStream).toHaveBeenCalledWith(
      store.currentAgent,
      'continue output',
      store.currentSessionId,
      expect.objectContaining({
        model: 'gpt-4.1',
        temperature: 0.2,
        maxContextMessages: 4,
        knowledgeEnabled: false
      })
    )
    expect(store.sessionConfig.model).toBe('auto')
    expect(store.chatHistory[0]?.derivedFrom).toEqual({
      action: 'continue',
      messageIndex: 2
    })
  })
})
