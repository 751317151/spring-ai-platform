import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ChatInput from './ChatInput.vue'
import { useChatStore } from '@/stores/chat'

const showToast = vi.fn()

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

describe('ChatInput', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
    showToast.mockReset()
  })

  it('emits send and clears draft on enter', async () => {
    const chatStore = useChatStore()
    chatStore.currentSessionId = 'session-1'

    const wrapper = mount(ChatInput)
    const textarea = wrapper.get('textarea')

    await textarea.setValue('整理这段需求并给出方案')
    await textarea.trigger('keydown', { key: 'Enter', shiftKey: false, preventDefault: () => {} })

    expect(wrapper.emitted('send')?.[0]).toEqual(['整理这段需求并给出方案'])
    expect(chatStore.getDraft('session-1::main')).toBe('')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('')
  })

  it('fills textarea when clicking a quick prompt chip', async () => {
    const chatStore = useChatStore()
    chatStore.currentSessionId = 'session-2'

    const wrapper = mount(ChatInput)
    const chips = wrapper.findAll('.prompt-chip')
    await chips[0].trigger('click')

    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value.length).toBeGreaterThan(0)
    expect(chatStore.getDraft('session-2::main').length).toBeGreaterThan(0)
  })

  it('switches between draft slots', async () => {
    const chatStore = useChatStore()
    chatStore.currentSessionId = 'session-3'

    const wrapper = mount(ChatInput)
    const textarea = wrapper.get('textarea')
    await textarea.setValue('主草稿内容')

    const draftChips = wrapper.findAll('.draft-chip')
    await draftChips[1]!.trigger('click')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')

    await wrapper.get('textarea').setValue('计划草稿内容')
    await draftChips[0]!.trigger('click')

    expect(chatStore.getDraft('session-3::main')).toBe('主草稿内容')
    expect(chatStore.getDraft('session-3::plan')).toBe('计划草稿内容')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('主草稿内容')
  })

  it('opens template panel and inserts template text', async () => {
    const chatStore = useChatStore()
    chatStore.currentSessionId = 'session-4'

    const wrapper = mount(ChatInput)
    await wrapper.findAll('.prompt-chip').at(-1)!.trigger('click')
    expect(wrapper.find('.template-panel').exists()).toBe(true)

    await wrapper.find('.template-card').trigger('click')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toContain('请先帮我澄清这个需求')
  })

  it('shows checklist and blocks too-short vague messages', async () => {
    const chatStore = useChatStore()
    chatStore.currentSessionId = 'session-5'

    const wrapper = mount(ChatInput)
    await wrapper.get('textarea').setValue('帮我看')

    expect(wrapper.find('.input-checklist').exists()).toBe(true)
    await wrapper.find('.send-btn').trigger('click')

    expect(wrapper.emitted('send')).toBeUndefined()
    // expect(showToast).toHaveBeenCalledWith('建议先补充目标或上下文，再发送会更有效。')
  })
})
