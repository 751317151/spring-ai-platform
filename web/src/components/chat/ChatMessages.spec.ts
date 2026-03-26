import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ChatMessages from './ChatMessages.vue'
import { useChatStore } from '@/stores/chat'

const showToast = vi.fn()

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

describe('ChatMessages', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    localStorage.clear()
    showToast.mockReset()
  })

  it('shows empty state quick prompts and emits use-prompt when clicked', async () => {
    const chatStore = useChatStore()
    chatStore.currentAgent = 'rd'
    chatStore.chatHistory = [] as never

    const wrapper = mount(ChatMessages)

    expect(wrapper.find('.chat-empty-state').exists()).toBe(true)
    expect(wrapper.findAll('.chat-empty-prompt').length).toBeGreaterThan(0)

    await wrapper.findAll('.chat-empty-prompt')[0]!.trigger('click')

    expect(wrapper.emitted('use-prompt')?.[0]?.[0]).toBeTruthy()
  })

  it('renders follow-up suggestions and recent prompts, and emits insert-prompt on click', async () => {
    sessionStorage.setItem('chat_recent_followups', JSON.stringify(['继续细化上线方案', '整理验收清单']))

    const chatStore = useChatStore()
    chatStore.currentAgent = 'rd'
    chatStore.currentSessionId = 'session-1'
    chatStore.chatHistory = [
      { role: 'user', content: '给我一个发布计划' },
      {
        role: 'assistant',
        content: '先梳理范围，再拆分风险、资源、发布时间窗口，最后给出回滚和验收步骤。',
        responseId: 'resp-1',
        feedback: null
      }
    ] as never

    const wrapper = mount(ChatMessages)
    await flushPromises()

    const suggestionButtons = wrapper.findAll('.followup-block .followup-chip')
    expect(suggestionButtons.length).toBeGreaterThan(1)
    expect(wrapper.text()).toContain('继续细化上线方案')

    const recentButtons = wrapper.findAll('.followup-chip.recent')
    await recentButtons[0]!.trigger('click')
    expect(wrapper.emitted('insert-prompt')?.[0]?.[0]).toBe('继续细化上线方案')

    await wrapper.findAll('.followup-block .followup-chip')[0]!.trigger('click')
    const insertedPrompts = wrapper.emitted('insert-prompt') || []
    expect(insertedPrompts.at(-1)?.[0]).toBeTruthy()
  })

  it('submits feedback for assistant messages and shows toast after success', async () => {
    const chatStore = useChatStore()
    chatStore.currentAgent = 'rd'
    chatStore.chatHistory = [
      {
        role: 'assistant',
        content: '这是一个可执行的方案。',
        responseId: 'resp-2',
        feedback: null
      }
    ] as never
    chatStore.submitFeedback = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(ChatMessages)

    const feedbackButtons = wrapper.findAll('.feedback-btn')
    expect(feedbackButtons).toHaveLength(2)

    await feedbackButtons[0]!.trigger('click')
    await flushPromises()

    expect(chatStore.submitFeedback).toHaveBeenCalledWith(0, 'up')
    expect(showToast).toHaveBeenCalledTimes(1)
  })

  it('highlights the requested source message', async () => {
    const chatStore = useChatStore()
    chatStore.currentAgent = 'rd'
    chatStore.currentSessionId = 'session-1'
    chatStore.chatHistory = [
      { role: 'user', content: '第一条消息' },
      { role: 'assistant', content: '第二条消息', responseId: 'resp-2', feedback: null }
    ] as never

    const wrapper = mount(ChatMessages, {
      props: {
        highlightedMessageIndex: 1
      }
    })
    await flushPromises()

    expect(wrapper.find('.msg.is-highlighted').exists()).toBe(true)
    expect(wrapper.find('.msg.is-highlighted').attributes('data-message-index')).toBe('1')
  })
})
