import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import RagQueryPanel from './RagQueryPanel.vue'
import { useRagStore } from '@/stores/rag'

const showToast = vi.fn()

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

describe('RagQueryPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    showToast.mockReset()
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true
    })
  })

  it('shows validation toast when query is empty', async () => {
    const store = useRagStore()
    store.currentKb = 'kb-001'
    store.currentKbName = '知识库'
    store.ragQuery = vi.fn() as never

    const wrapper = mount(RagQueryPanel)
    await wrapper.find('textarea').setValue('')
    await wrapper.find('button.btn.btn-primary').trigger('click')

    expect(store.ragQuery).not.toHaveBeenCalled()
    expect(showToast).toHaveBeenCalledWith('请输入问题')
  })

  it('stores recent question and uses suggestion actions', async () => {
    const store = useRagStore()
    store.currentKb = 'kb-001'
    store.currentKbName = '研发知识库'
    store.documents = [] as never
    store.queryResult = '回答结果'
    store.querySources = [] as never
    store.ragQuery = vi.fn().mockResolvedValue(undefined) as never

    const wrapper = mount(RagQueryPanel)
    await wrapper.find('textarea').setValue('接口规范是什么')
    await wrapper.find('button.btn.btn-primary').trigger('click')

    expect(sessionStorage.getItem('rag_recent_queries')).toContain('接口规范是什么')
    expect(store.ragQuery).toHaveBeenCalled()

    const suggestionButtons = wrapper.findAll('.query-no-evidence-actions .query-action-btn')
    await suggestionButtons[0]?.trigger('click')
    expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('10')
  })

  it('renders follow-up suggestions and copies query snapshot', async () => {
    const store = useRagStore()
    store.currentKb = 'kb-001'
    store.currentKbName = '研发知识库'
    store.documents = [] as never
    store.queryResult = '这是一个较完整的回答。'
    store.querySources = [
      { filename: '规范手册.pdf', score: 0.93, content: '证据内容', chunkIndex: 2 }
    ] as never
    store.ragQuery = vi.fn().mockResolvedValue(undefined) as never

    const wrapper = mount(RagQueryPanel)
    await wrapper.find('textarea').setValue('接口规范是什么？')

    expect(wrapper.text()).toContain('继续追问')

    const followUpButton = wrapper.findAll('.follow-up-card .query-helper-chip')[0]
    await followUpButton?.trigger('click')
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toContain('请按步骤展开说明')

    const snapshotButton = wrapper.findAll('.query-result-actions .query-action-btn').find((item) => item.text() === '复制问答快照')
    await snapshotButton?.trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalled()
    expect(String(vi.mocked(navigator.clipboard.writeText).mock.calls[0]?.[0])).toContain('问题：接口规范是什么？')
  })
})
