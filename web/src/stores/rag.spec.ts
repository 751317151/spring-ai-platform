import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRagStore } from './rag'
import { useRuntimeStore } from './runtime'
import * as ragApi from '@/api/rag'

vi.mock('@/api/rag', () => ({
  listKnowledgeBases: vi.fn(),
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  deleteDocument: vi.fn(),
  retryDocument: vi.fn(),
  reindexDocument: vi.fn(),
  downloadDocument: vi.fn(),
  previewDocument: vi.fn(),
  listDocumentChunks: vi.fn(),
  ragQuery: vi.fn(),
  ragQueryStream: vi.fn(),
  submitFeedback: vi.fn(),
  submitEvidenceFeedback: vi.fn()
}))

describe('rag store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads knowledge bases and selects current one', async () => {
    vi.mocked(ragApi.listKnowledgeBases).mockResolvedValue([
      { id: 'kb-2', name: '产品知识库' }
    ] as never)
    vi.mocked(ragApi.listDocuments).mockResolvedValue([] as never)

    const store = useRagStore()
    await store.loadKnowledgeBases()

    expect(store.knowledgeBases).toHaveLength(1)
    expect(store.currentKb).toBe('kb-2')
    expect(store.currentKbName).toBe('产品知识库')
  })

  it('sets readable error when loading documents fails', async () => {
    vi.mocked(ragApi.listDocuments).mockRejectedValue(new Error('boom'))

    const store = useRagStore()
    store.currentKb = 'kb-1'

    await store.loadDocuments()

    expect(store.documentError).toBe('文档列表加载失败，请稍后重试。')
  })

  it('falls back to demo answer when query fails in demo mode', async () => {
    vi.mocked(ragApi.ragQuery).mockRejectedValue(new Error('boom'))

    const store = useRagStore()
    const runtimeStore = useRuntimeStore()
    Object.defineProperty(runtimeStore, 'demoMode', {
      get: () => true
    })

    store.currentKb = 'kb-001'
    await store.ragQuery('什么是知识库')

    expect(store.queryError).toBe('问答后端不可用，已切换为演示答案。')
    expect(store.queryResult).not.toBe('')
    expect(store.querySources.length).toBeGreaterThan(0)
  })
})
