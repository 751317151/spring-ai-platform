import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import type { DocumentChunkPreview, DocumentMeta } from '@/api/types'
import DocumentTable from './DocumentTable.vue'
import { useRagStore } from '@/stores/rag'

const showToast = vi.fn()
const confirm = vi.fn()
const writeText = vi.fn()
const scrollIntoView = vi.fn()

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm
  })
}))

function createDocs(): DocumentMeta[] {
  return [
    { id: 'doc-1', filename: 'guide.pdf', contentType: 'application/pdf', fileSize: 1024, chunkCount: 2, uploadedBy: 'tester', status: 'INDEXED', createdAt: '2026-03-25T08:00:00Z', indexedAt: '2026-03-25T08:05:00Z' },
    { id: 'doc-2', filename: 'manual.md', contentType: 'text/markdown', fileSize: 512, chunkCount: 0, uploadedBy: 'tester', status: 'FAILED', createdAt: '2026-03-25T09:00:00Z', errorMessage: '解析失败' },
    { id: 'doc-3', filename: 'notes.txt', contentType: 'text/plain', fileSize: 256, chunkCount: 1, uploadedBy: 'tester', status: 'PROCESSING', createdAt: '2026-03-25T10:00:00Z' }
  ]
}

describe('DocumentTable', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    confirm.mockReset()
    confirm.mockResolvedValue(true)
    showToast.mockReset()
    writeText.mockReset()
    scrollIntoView.mockReset()

    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true
    })
    HTMLElement.prototype.scrollIntoView = scrollIntoView
  })

  it('filters documents and only batch reindexes eligible selections', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-1'
    ragStore.documents = createDocs() as never
    ragStore.reindexDocument = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(DocumentTable)

    expect(wrapper.findAll('tbody tr[data-doc-id]')).toHaveLength(3)

    await wrapper.findAll('.doc-filter-chip')[3]!.trigger('click')
    expect(wrapper.findAll('tbody tr[data-doc-id]')).toHaveLength(1)
    expect(wrapper.find('tbody tr[data-doc-id]').attributes('data-doc-id')).toBe('doc-3')

    await wrapper.findAll('.doc-filter-chip')[0]!.trigger('click')
    await wrapper.get('.doc-search-input').setValue('guide')
    expect(wrapper.findAll('tbody tr[data-doc-id]')).toHaveLength(1)
    expect(wrapper.find('tbody tr[data-doc-id]').attributes('data-doc-id')).toBe('doc-1')

    await wrapper.get('.doc-search-clear').trigger('click')

    const checkboxes = wrapper.findAll('tbody tr[data-doc-id] .check-col input')
    await checkboxes[0]!.setValue(true)
    await checkboxes[1]!.setValue(true)
    await wrapper.findAll('.bulk-actions .btn')[1]!.trigger('click')

    expect(ragStore.reindexDocument).toHaveBeenCalledTimes(2)
    expect(ragStore.reindexDocument).toHaveBeenNthCalledWith(1, 'doc-1')
    expect(ragStore.reindexDocument).toHaveBeenNthCalledWith(2, 'doc-2')
  })

  it('clears highlighted document state after the locate banner is dismissed', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-1'
    ragStore.documents = createDocs() as never

    const wrapper = mount(DocumentTable, {
      props: {
        highlightDocumentId: 'doc-2',
        highlightDocumentName: 'manual.md'
      }
    })

    await flushPromises()

    expect(wrapper.find('.doc-highlight-banner').exists()).toBe(true)
    expect(wrapper.get('[data-doc-id="doc-2"]').classes()).toContain('doc-row-highlight')
    expect((wrapper.get('.doc-search-input').element as HTMLInputElement).value).toBe('manual.md')

    await wrapper.get('.doc-highlight-btn').trigger('click')
    await nextTick()

    expect(wrapper.find('.doc-highlight-banner').exists()).toBe(false)
    expect(wrapper.get('[data-doc-id="doc-2"]').classes()).not.toContain('doc-row-highlight')
    expect((wrapper.get('.doc-search-input').element as HTMLInputElement).value).toBe('')
  })

  it('opens chunk preview modal and copies the current chunk content', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-1'
    ragStore.documents = createDocs() as never
    ragStore.activeDocumentChunks = [] as never
    ragStore.clearDocumentChunks = vi.fn(() => {
      ragStore.activeDocumentChunks = [] as never
      ragStore.activeChunkDocument = null as never
    }) as never
    ragStore.loadDocumentChunks = vi.fn(async (doc: DocumentMeta) => {
      const chunks: DocumentChunkPreview[] = [
        { id: 'chunk-1', chunkIndex: 1, preview: 'Alpha Beta preview', content: 'Alpha Beta content', charCount: 18 },
        { id: 'chunk-2', chunkIndex: 2, preview: 'Gamma preview', content: 'Gamma content', charCount: 13 }
      ]
      ragStore.activeChunkDocument = doc as never
      ragStore.activeDocumentChunks = chunks as never
      return true
    }) as never

    const wrapper = mount(DocumentTable, {
      props: {
        highlightTerms: ['Beta']
      }
    })

    const rowButtons = wrapper.get('[data-doc-id="doc-1"]').findAll('.doc-action-btn')
    await rowButtons[2]!.trigger('click')
    await flushPromises()

    expect(ragStore.loadDocumentChunks).toHaveBeenCalledWith(expect.objectContaining({ id: 'doc-1' }))
    expect(wrapper.find('.chunk-modal-mask').exists()).toBe(true)
    expect(wrapper.find('.chunk-highlight').exists()).toBe(true)

    await wrapper.findAll('.chunk-modal-actions .btn')[0]!.trigger('click')

    expect(writeText).toHaveBeenCalledTimes(1)
    expect(String(writeText.mock.calls[0]?.[0])).toContain('Alpha Beta content')
  })

  it('offers quick failed-document actions under failed status filter', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-1'
    ragStore.documents = createDocs() as never
    ragStore.reindexDocument = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(DocumentTable)

    await wrapper.findAll('.doc-filter-chip')[2]!.trigger('click')

    expect(wrapper.text()).toContain('当前聚焦 1 个失败文档')

    const quickButtons = wrapper.findAll('.status-action-buttons .btn')
    await quickButtons[0]!.trigger('click')
    expect(wrapper.text()).toContain('1 个已选')

    await quickButtons[1]!.trigger('click')

    expect(ragStore.reindexDocument).toHaveBeenCalledTimes(1)
    expect(ragStore.reindexDocument).toHaveBeenCalledWith('doc-2')
  })
})
