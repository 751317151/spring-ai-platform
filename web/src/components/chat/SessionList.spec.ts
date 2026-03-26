import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import SessionList from './SessionList.vue'
import { useChatStore } from '@/stores/chat'

const showToast = vi.fn()

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

function createSessions(now: number) {
  return [
    {
      sessionId: 'session-pinned',
      summary: 'Pinned Alpha',
      updatedAt: String(now),
      pinned: true,
      archived: false
    },
    {
      sessionId: 'session-today',
      summary: 'Today Beta',
      updatedAt: String(now - 60_000),
      pinned: false,
      archived: false
    },
    {
      sessionId: 'session-yesterday',
      summary: 'Yesterday Gamma',
      updatedAt: String(now - 86_400_000),
      pinned: false,
      archived: false
    },
    {
      sessionId: 'session-archived',
      summary: 'Archived Delta',
      updatedAt: String(now - 2 * 86_400_000),
      pinned: false,
      archived: true
    }
  ]
}

describe('SessionList', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    localStorage.clear()
    showToast.mockReset()
  })

  function seedStore() {
    const now = new Date('2026-03-25T10:00:00+08:00').getTime()
    vi.setSystemTime(now)

    const chatStore = useChatStore()
    chatStore.currentAgent = 'rd'
    chatStore.currentSessionId = 'session-today'
    chatStore.sessionList = createSessions(now) as never
    chatStore.showArchivedSessions = false
    chatStore.toggleArchiveSession = vi.fn().mockResolvedValue(undefined) as never
    chatStore.deleteSession = vi.fn().mockResolvedValue(undefined) as never
    chatStore.switchSession = vi.fn().mockResolvedValue(undefined) as never
    chatStore.renameSession = vi.fn().mockResolvedValue(undefined) as never
    chatStore.togglePinSession = vi.fn().mockResolvedValue(undefined) as never
    chatStore.getDraft = vi.fn((sessionId: string) => (sessionId === 'session-yesterday' ? 'draft text' : '')) as never
    localStorage.setItem('chat_recent_visited_sessions', JSON.stringify(['session-yesterday', 'session-pinned']))
    return chatStore
  }

  it('filters sessions by keyword and highlights matching results', async () => {
    seedStore()
    const wrapper = mount(SessionList)

    expect(wrapper.text()).toContain('Pinned Alpha')
    expect(wrapper.text()).toContain('Today Beta')
    expect(wrapper.text()).toContain('Yesterday Gamma')

    await wrapper.get('.session-search').setValue('beta')

    expect(wrapper.text()).toContain('Today Beta')
    expect(wrapper.find('.session-highlight').text().toLowerCase()).toBe('beta')
    expect(wrapper.findAll('.session-item')).toHaveLength(1)

    await wrapper.findAll('.session-clear-search')[1]!.trigger('click')
    expect((wrapper.get('.session-search').element as HTMLInputElement).value).toBe('')
    expect(wrapper.text()).toContain('Pinned Alpha')
  })

  it('supports draft filter and recent visited shortcuts', async () => {
    const chatStore = seedStore()
    const wrapper = mount(SessionList)

    expect(wrapper.text()).toContain('最近访问')
    await wrapper.findAll('.session-recent-chip')[0]!.trigger('click')
    expect(chatStore.switchSession).toHaveBeenCalledWith('session-yesterday')

    const draftFilter = wrapper.findAll('.session-filter-chip').find((item) => item.text().includes('仅看草稿'))
    await draftFilter!.trigger('click')

    expect(wrapper.text()).toContain('Yesterday Gamma')
    expect(wrapper.findAll('.session-item')).toHaveLength(1)
  })

  it('archives all selected visible sessions in batch', async () => {
    const chatStore = seedStore()
    const wrapper = mount(SessionList)

    const checkboxes = wrapper.findAll('.session-select-input')
    await checkboxes[0]!.setValue(true)
    await checkboxes[1]!.setValue(true)

    expect(wrapper.find('.session-batch-bar').exists()).toBe(true)

    await wrapper.findAll('.session-batch-actions .session-toolbar-btn')[1]!.trigger('click')

    expect(chatStore.toggleArchiveSession).toHaveBeenCalledTimes(2)
    expect(chatStore.toggleArchiveSession).toHaveBeenNthCalledWith(1, 'session-pinned')
    expect(chatStore.toggleArchiveSession).toHaveBeenNthCalledWith(2, 'session-today')
    expect(showToast).toHaveBeenCalledTimes(1)
  })

  it('selects all visible filtered sessions before batch actions', async () => {
    seedStore()
    const wrapper = mount(SessionList)

    await wrapper.get('.session-search').setValue('session')
    await wrapper.findAll('.session-search-tools .session-clear-search')[0]!.trigger('click')

    const checked = wrapper.findAll('.session-select-input').filter((node) => (node.element as HTMLInputElement).checked)
    expect(checked).toHaveLength(3)
  })

  it('deletes selected sessions in batch', async () => {
    const chatStore = seedStore()
    const wrapper = mount(SessionList)

    const checkboxes = wrapper.findAll('.session-select-input')
    await checkboxes[1]!.setValue(true)
    await checkboxes[2]!.setValue(true)

    await wrapper.find('.session-batch-actions .danger').trigger('click')

    expect(chatStore.deleteSession).toHaveBeenCalledTimes(2)
    expect(chatStore.deleteSession).toHaveBeenNthCalledWith(1, 'session-today')
    expect(chatStore.deleteSession).toHaveBeenNthCalledWith(2, 'session-yesterday')
    expect(showToast).toHaveBeenCalledTimes(1)
  })

  afterEach(() => {
    vi.useRealTimers()
  })
})
