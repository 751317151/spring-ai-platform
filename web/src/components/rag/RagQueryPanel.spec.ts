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
    await wrapper.find('textarea').setValue('接口规范是什么？')
    await wrapper.find('button.btn.btn-primary').trigger('click')

    expect(sessionStorage.getItem('rag_recent_queries')).toContain('接口规范是什么？')
    expect(store.ragQuery).toHaveBeenCalled()

    const suggestionButtons = wrapper.findAll('.chip-btn')
    const expandButton = suggestionButtons.find((item) => item.text().includes('提高 TopK 到 10'))
    await expandButton?.trigger('click')

    expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('10')
  })

  it('renders retrieval debug info and copies query snapshot', async () => {
    const store = useRagStore()
    store.currentKb = 'kb-001'
    store.currentKbName = '研发知识库'
    store.documents = [] as never
    store.queryResult = '这是一条完整回答'
    store.querySources = [
      { filename: '规范手册.pdf', score: 0.93, content: '证据内容', chunkIndex: 2 }
    ] as never
    store.queryRetrievalDebug = {
      retrievalQuery: '接口 规范',
      keywords: ['接口', '规范'],
      candidateCount: 6,
      selectedCount: 1,
      recallSteps: [{ source: 'vector-original', query: '接口规范是什么？', returnedCount: 4 }]
    } as never
    store.ragQuery = vi.fn().mockResolvedValue(undefined) as never

    const wrapper = mount(RagQueryPanel)
    await wrapper.find('textarea').setValue('接口规范是什么？')

    expect(wrapper.text()).toContain('检索解释')
    expect(wrapper.text()).toContain('vector-original')

    const snapshotButton = wrapper.findAll('.chip-btn').find((item) => item.text().includes('复制问答快照'))
    await snapshotButton?.trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalled()
  })
})
