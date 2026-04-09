import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRagStore } from './rag'
import { useRuntimeStore } from './runtime'
import * as ragApi from '@/api/rag'

vi.mock('@/api/rag', () => ({
  listKnowledgeBases: vi.fn(),
  listDocuments: vi.fn(),
  getEvaluationOverview: vi.fn(),
  getLowRatedSamples: vi.fn(),
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
    vi.mocked(ragApi.getEvaluationOverview).mockResolvedValue({
      totalQueries: 0,
      feedbackCount: 0,
      positiveFeedbackCount: 0,
      negativeFeedbackCount: 0,
      positiveFeedbackRate: 0,
      evidenceFeedbackCount: 0,
      positiveEvidenceCount: 0,
      negativeEvidenceCount: 0,
      positiveEvidenceRate: 0,
      lowRatedQueryCount: 0
    } as never)
    vi.mocked(ragApi.getLowRatedSamples).mockResolvedValue([] as never)

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

    expect(store.documentError).not.toBe('')
  })

  it('stores retrieval debug when rag query succeeds', async () => {
    vi.mocked(ragApi.ragQuery).mockResolvedValue({
      answer: '答案',
      latencyMs: 12,
      sources: [{ filename: 'a.md', content: '证据', score: 0.9 }],
      retrievalDebug: {
        retrievalQuery: '接口 规范',
        keywords: ['接口', '规范'],
        candidateCount: 5,
        selectedCount: 1
      }
    } as never)
    vi.mocked(ragApi.getEvaluationOverview).mockResolvedValue({
      totalQueries: 0,
      feedbackCount: 0,
      positiveFeedbackCount: 0,
      negativeFeedbackCount: 0,
      positiveFeedbackRate: 0,
      evidenceFeedbackCount: 0,
      positiveEvidenceCount: 0,
      negativeEvidenceCount: 0,
      positiveEvidenceRate: 0,
      lowRatedQueryCount: 0
    } as never)
    vi.mocked(ragApi.getLowRatedSamples).mockResolvedValue([] as never)

    const store = useRagStore()
    store.currentKb = 'kb-001'

    await store.ragQuery('什么是知识库')

    expect(store.queryResult).toBe('答案')
    expect(store.querySources.length).toBe(1)
    expect(store.queryRetrievalDebug?.retrievalQuery).toBe('接口 规范')
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

    expect(store.queryResult).not.toBe('')
    expect(store.querySources.length).toBeGreaterThan(0)
  })
})
