import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, reactive } from 'vue'
import ChatView from './ChatView.vue'
import { useChatStore } from '@/stores/chat'

const showToast = vi.fn()
const replace = vi.fn().mockResolvedValue(undefined)
const focusInputSpy = vi.fn()
const setMessageSpy = vi.fn()

const route = reactive({
  query: {} as Record<string, string>,
  name: 'chat'
})

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => route,
    useRouter: () => ({
      replace
    })
  }
})

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

vi.mock('@/components/chat/AgentList.vue', () => ({
  default: defineComponent({
    name: 'AgentListStub',
    setup() {
      return () => h('div', { class: 'agent-list-stub' })
    }
  })
}))

vi.mock('@/components/chat/SessionList.vue', () => ({
  default: defineComponent({
    name: 'SessionListStub',
    setup() {
      return () => h('div', { class: 'session-list-stub' })
    }
  })
}))

vi.mock('@/components/chat/SessionConfigPanel.vue', () => ({
  default: defineComponent({
    name: 'SessionConfigPanelStub',
    setup() {
      return () => h('div', { class: 'session-config-panel-stub' })
    }
  })
}))

vi.mock('@/components/common/BackendStatusBanner.vue', () => ({
  default: defineComponent({
    name: 'BackendStatusBannerStub',
    setup() {
      return () => h('div', { class: 'backend-status-banner-stub' })
    }
  })
}))

vi.mock('@/components/chat/ChatMessages.vue', () => ({
  default: defineComponent({
    name: 'ChatMessagesStub',
    emits: ['use-prompt', 'insert-prompt', 'branch-session', 'continue-response', 'regenerate-response'],
    setup(_props, { emit }) {
      return () => h('div', { class: 'chat-messages-stub' }, [
        h('button', {
          class: 'emit-continue',
          onClick: () => emit('continue-response', 1)
        }),
        h('button', {
          class: 'emit-regenerate',
          onClick: () => emit('regenerate-response', 1)
        }),
        h('button', {
          class: 'emit-branch',
          onClick: () => emit('branch-session', 1)
        })
      ])
    }
  })
}))

vi.mock('@/components/chat/ChatInput.vue', () => ({
  default: defineComponent({
    name: 'ChatInputStub',
    emits: ['send'],
    setup(_props, { emit, expose }) {
      expose({
        focusInput: focusInputSpy,
        setMessage: setMessageSpy
      })
      return () => h('div', { class: 'chat-input-stub' }, [
        h('button', {
          class: 'emit-send',
          onClick: () => emit('send', '继续补充方案')
        })
      ])
    }
  })
}))

describe('ChatView', () => {
  enableAutoUnmount(afterEach)

  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    localStorage.clear()

    route.query = {}
    replace.mockReset()
    replace.mockResolvedValue(undefined)
    showToast.mockReset()
    focusInputSpy.mockReset()
    setMessageSpy.mockReset()

    Object.defineProperty(window, 'location', {
      value: new URL('http://localhost/chat'),
      configurable: true
    })

    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: vi.fn().mockResolvedValue(undefined)
      },
      configurable: true
    })
  })

  function seedStore() {
    const chatStore = useChatStore()
    chatStore.currentAgent = 'rd'
    chatStore.currentSessionId = 'session-1'
    chatStore.sessionList = [
      {
        sessionId: 'session-1',
        summary: 'Current session',
        updatedAt: String(Date.now()),
        pinned: false,
        archived: false
      },
      {
        sessionId: 'session-2',
        summary: 'History session',
        updatedAt: String(Date.now() - 1000),
        pinned: false,
        archived: false
      }
    ] as never
    chatStore.chatHistory = [
      { role: 'user', content: 'plan the release' },
      {
        role: 'assistant',
        content: 'first define scope, then check rollback',
        responseId: 'resp-1',
        feedback: null,
        sessionConfigSnapshot: {
          model: 'gpt-4.1',
          temperature: 0.2,
          maxContextMessages: 4,
          knowledgeEnabled: false
        }
      }
    ] as never
    chatStore.selectAgent = vi.fn().mockResolvedValue(undefined) as never
    chatStore.switchSession = vi.fn().mockResolvedValue(undefined) as never
    chatStore.createNewSession = vi.fn() as never
    chatStore.clearChat = vi.fn().mockResolvedValue(undefined) as never
    chatStore.sendMessage = vi.fn().mockResolvedValue('done') as never
    chatStore.saveCurrentSessionConfig = vi.fn().mockResolvedValue(true) as never
    chatStore.normalizeSessionConfig = vi.fn((value) => value) as never
    return chatStore
  }

  it('opens command palette and runs focus-input action', async () => {
    seedStore()
    const wrapper = mount(ChatView)

    await wrapper.findAll('.page-hero-actions > .btn')[2]!.trigger('click')
    expect(wrapper.find('.command-palette').exists()).toBe(true)

    await wrapper.findAll('.command-palette-item')[1]!.trigger('click')

    expect(focusInputSpy).toHaveBeenCalledTimes(1)
  })

  it('copies current session deep link from export menu', async () => {
    seedStore()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText')
    const wrapper = mount(ChatView)

    await wrapper.get('.export-shell > .btn').trigger('click')
    await wrapper.findAll('.export-action')[6]!.trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledTimes(1)
    expect(String(writeText.mock.calls[0]?.[0])).toContain('agent=rd')
    expect(String(writeText.mock.calls[0]?.[0])).toContain('session=session-1')
  })

  it('copies session review content', async () => {
    seedStore()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText')
    const wrapper = mount(ChatView)

    await wrapper.findAll('button').find((button) => button.text() === '复制复盘')?.trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalled()
    expect(String(writeText.mock.calls.at(-1)?.[0])).toContain('一、问题背景')
    expect(String(writeText.mock.calls.at(-1)?.[0])).toContain('二、关键结论')
    expect(showToast).toHaveBeenCalledWith('会话复盘已复制')
  })

  it('saves session review into learning notes', async () => {
    seedStore()
    const wrapper = mount(ChatView)

    await wrapper.findAll('button').find((button) => button.text() === '保存到学习笔记')?.trigger('click')
    await flushPromises()

    const notes = JSON.parse(localStorage.getItem('learning_center_notes') || '[]')
    expect(notes).toHaveLength(1)
    expect(notes[0].title).toContain('会话复盘')
    expect(notes[0].content).toContain('三、待办事项')
    expect(notes[0].tags).toContain('会话复盘')
    expect(notes[0].relatedSessionId).toBe('session-1')
    expect(notes[0].relatedAgentType).toBe('rd')
    expect(showToast).toHaveBeenCalledWith('会话复盘已保存到学习笔记')
  })

  it('applies route query state on mount and syncs route when store state changes', async () => {
    const chatStore = seedStore()
    route.query = {
      agent: 'sales',
      session: 'session-2',
      message: '1'
    }

    mount(ChatView)
    await flushPromises()

    expect(chatStore.selectAgent).toHaveBeenCalledWith('sales')
    expect(chatStore.switchSession).toHaveBeenCalledWith('session-2')

    replace.mockClear()
    chatStore.currentAgent = 'code'
    chatStore.currentSessionId = 'session-1'
    await flushPromises()

    expect(replace).toHaveBeenCalledWith({
      name: 'chat',
      query: {
        agent: 'code',
        session: 'session-1',
        message: '1'
      }
    })
  })

  it('renders highlighted source context and copies source snippet', async () => {
    seedStore()
    route.query = {
      agent: 'rd',
      session: 'session-1',
      message: '1'
    }
    const writeText = vi.spyOn(navigator.clipboard, 'writeText')

    const wrapper = mount(ChatView)
    await flushPromises()

    expect(wrapper.text()).toContain('已定位到助手消息 #2')
    expect(wrapper.text()).toContain('当前消息')

    await wrapper.findAll('button').find((button) => button.text() === '复制来源片段')?.trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalled()
    expect(String(writeText.mock.calls.at(-1)?.[0])).toContain('消息序号：#2')
    expect(String(writeText.mock.calls.at(-1)?.[0])).toContain('角色：助手')
    expect(showToast).toHaveBeenCalledWith('来源片段已复制')
  })

  it('clears highlighted route state after sending a new message', async () => {
    seedStore()
    route.query = {
      agent: 'rd',
      session: 'session-1',
      message: '1'
    }

    const wrapper = mount(ChatView)
    await flushPromises()

    replace.mockClear()
    await wrapper.get('.emit-send').trigger('click')
    await flushPromises()

    expect(replace).toHaveBeenCalledWith({
      name: 'chat',
      query: {
        agent: 'rd',
        session: 'session-1'
      }
    })
  })

  it('clears highlighted state when clicking clear button', async () => {
    seedStore()
    route.query = {
      agent: 'rd',
      session: 'session-1',
      message: '1'
    }

    const wrapper = mount(ChatView)
    await flushPromises()

    expect(wrapper.find('.source-context-card').exists()).toBe(true)
    replace.mockClear()
    await wrapper.get('.clear-highlight-btn').trigger('click')
    await flushPromises()

    expect(replace).toHaveBeenCalledWith({
      name: 'chat',
      query: {
        agent: 'rd',
        session: 'session-1'
      }
    })
  })

  it('hydrates input prompt from learning center query and clears one-time prompt query', async () => {
    seedStore()
    route.query = {
      agent: 'rd',
      session: 'session-1',
      message: '1',
      source: 'learning',
      prompt: '请基于来源内容继续追问'
    }

    mount(ChatView)
    await flushPromises()

    expect(setMessageSpy).toHaveBeenCalledWith('请基于来源内容继续追问')
    expect(focusInputSpy).toHaveBeenCalled()
    expect(replace).toHaveBeenCalledWith({
      name: 'chat',
      query: {
        agent: 'rd',
        session: 'session-1',
        message: '1',
        source: 'learning'
      }
    })
  })

  it('inherits the original message config when continuing a response', async () => {
    const chatStore = seedStore()
    const wrapper = mount(ChatView)

    await wrapper.get('.emit-continue').trigger('click')

    expect(chatStore.sendMessage).toHaveBeenCalledWith(expect.any(String), {
      sessionConfigOverride: expect.objectContaining({
        model: 'gpt-4.1',
        temperature: 0.2,
        maxContextMessages: 4,
        knowledgeEnabled: false
      }),
      derivedFrom: {
        action: 'continue',
        messageIndex: 1
      }
    })
  })

  it('inherits the original message config when regenerating a response', async () => {
    const chatStore = seedStore()
    const wrapper = mount(ChatView)

    await wrapper.get('.emit-regenerate').trigger('click')

    expect(chatStore.sendMessage).toHaveBeenCalledWith(expect.any(String), {
      sessionConfigOverride: expect.objectContaining({
        model: 'gpt-4.1',
        temperature: 0.2,
        maxContextMessages: 4,
        knowledgeEnabled: false
      }),
      derivedFrom: {
        action: 'regenerate',
        messageIndex: 1
      }
    })
  })

  it('creates a branch and restores the original config into the new session', async () => {
    const chatStore = seedStore()
    const wrapper = mount(ChatView)

    await wrapper.get('.emit-branch').trigger('click')
    await flushPromises()

    expect(chatStore.createNewSession).toHaveBeenCalledTimes(1)
    expect(chatStore.saveCurrentSessionConfig).toHaveBeenCalledWith(expect.objectContaining({
      model: 'gpt-4.1',
      temperature: 0.2,
      maxContextMessages: 4,
      knowledgeEnabled: false
    }))
    expect(setMessageSpy).toHaveBeenCalledTimes(1)
  })
})
