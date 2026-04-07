import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as ragApi from '@/api/rag'
import type {
  DocumentChunkPreview,
  DocumentMeta,
  KnowledgeBase,
  RagEvaluationOverview,
  RagEvaluationSample,
  SourceDocument,
  SSEChunk
} from '@/api/types'
import { DEMO_KNOWLEDGE_BASES } from '@/utils/constants'
import { useAuthStore } from './auth'
import { useRuntimeStore } from './runtime'
import {
  createGuestDocumentChunks,
  createGuestDocumentsByKnowledgeBase,
  createGuestEvaluationOverview,
  createGuestEvaluationSamples,
  createGuestKnowledgeBases,
  createGuestRagAnswer,
  createGuestRagSources
} from '@/utils/guest-mock'

const DEFAULT_EVALUATION: RagEvaluationOverview = {
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
}

export const useRagStore = defineStore('rag', () => {
  const guestKnowledgeBaseState = ref<KnowledgeBase[]>(createGuestKnowledgeBases())
  const guestDocumentsByKb = ref<Record<string, DocumentMeta[]>>(
    createGuestDocumentsByKnowledgeBase(guestKnowledgeBaseState.value)
  )
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const currentKb = ref('kb-001')
  const currentKbName = ref('')
  const documents = ref<DocumentMeta[]>([])
  const activeDocumentChunks = ref<DocumentChunkPreview[]>([])
  const activeChunkDocument = ref<DocumentMeta | null>(null)
  const queryResult = ref('')
  const querySources = ref<SourceDocument[]>([])
  const queryResponseId = ref('')
  const queryFeedback = ref<'up' | 'down' | null>(null)
  const isQuerying = ref(false)
  const queryStage = ref<'idle' | 'retrieving' | 'answering'>('idle')
  const queryError = ref('')
  const loadingKnowledgeBases = ref(false)
  const loadingDocuments = ref(false)
  const loadingEvaluation = ref(false)
  const knowledgeBaseError = ref('')
  const documentError = ref('')
  const evaluationOverview = ref<RagEvaluationOverview>({ ...DEFAULT_EVALUATION })
  const lowRatedSamples = ref<RagEvaluationSample[]>([])

  const authStore = useAuthStore()
  const runtimeStore = useRuntimeStore()

  async function loadKnowledgeBases() {
    if (runtimeStore.demoMode) {
      knowledgeBases.value = guestKnowledgeBaseState.value
      loadingKnowledgeBases.value = false
      knowledgeBaseError.value = ''
      if (
        knowledgeBases.value.length > 0 &&
        !knowledgeBases.value.find((kb) => kb.id === currentKb.value)
      ) {
        selectKb(knowledgeBases.value[0].id)
      }
      return
    }

    loadingKnowledgeBases.value = true
    knowledgeBaseError.value = ''
    try {
      const data = await ragApi.listKnowledgeBases()
      knowledgeBases.value = data || []
      runtimeStore.markServiceAvailable('rag')
    } catch {
      if (runtimeStore.demoMode) {
        knowledgeBases.value = DEMO_KNOWLEDGE_BASES
        runtimeStore.markServiceUnavailable('rag', '知识库后端不可用，当前展示的是演示知识库数据。')
      } else {
        knowledgeBases.value = []
        currentKb.value = ''
        currentKbName.value = ''
        documents.value = []
        evaluationOverview.value = { ...DEFAULT_EVALUATION }
        lowRatedSamples.value = []
        knowledgeBaseError.value = '知识库列表加载失败，请检查 rag-service 状态。'
        runtimeStore.markServiceUnavailable('rag', knowledgeBaseError.value)
      }
    } finally {
      loadingKnowledgeBases.value = false
    }

    if (knowledgeBases.value.length > 0 && !knowledgeBases.value.find((kb) => kb.id === currentKb.value)) {
      selectKb(knowledgeBases.value[0].id)
    }
  }

  function selectKb(kbId: string) {
    currentKb.value = kbId
    const kb = knowledgeBases.value.find((item) => item.id === kbId)
    currentKbName.value = kb?.name || ''
    void Promise.all([loadDocuments(), loadEvaluation()])
  }

  async function createKnowledgeBase(payload: Partial<KnowledgeBase>) {
    if (runtimeStore.demoMode) {
      const created: KnowledgeBase = {
        id: `kb-${Date.now()}`,
        name: payload.name || '新建演示知识库',
        description: payload.description || '游客模式下创建的本地知识库',
        department: payload.department || '本地演示',
        visibilityScope: payload.visibilityScope || 'PRIVATE',
        status: payload.status || 'ACTIVE',
        documentCount: 0,
        totalChunks: 0,
        chunkSize: payload.chunkSize || 1000,
        chunkOverlap: payload.chunkOverlap || 200,
        chunkStrategy: payload.chunkStrategy || 'TOKEN',
        structuredBatchSize: payload.structuredBatchSize || 20,
        createdBy: authStore.userId,
        createdAt: new Date().toISOString()
      }
      guestKnowledgeBaseState.value = [created, ...guestKnowledgeBaseState.value]
      guestDocumentsByKb.value[created.id] = []
      knowledgeBases.value = guestKnowledgeBaseState.value
      selectKb(created.id)
      return created
    }

    try {
      const created = await ragApi.createKnowledgeBase(payload)
      runtimeStore.markServiceAvailable('rag')
      await loadKnowledgeBases()
      if (created?.id) {
        selectKb(created.id)
      }
      return created
    } catch {
      runtimeStore.markServiceUnavailable('rag', '知识库创建失败，请稍后重试。')
      return null
    }
  }

  async function updateKnowledgeBase(id: string, payload: Partial<KnowledgeBase>) {
    if (runtimeStore.demoMode) {
      guestKnowledgeBaseState.value = guestKnowledgeBaseState.value.map((item) =>
        item.id === id ? { ...item, ...payload } : item
      )
      knowledgeBases.value = guestKnowledgeBaseState.value
      if (currentKb.value === id) {
        currentKbName.value =
          guestKnowledgeBaseState.value.find((item) => item.id === id)?.name || currentKbName.value
      }
      return guestKnowledgeBaseState.value.find((item) => item.id === id) || null
    }

    try {
      const updated = await ragApi.updateKnowledgeBase(id, payload)
      runtimeStore.markServiceAvailable('rag')
      await loadKnowledgeBases()
      if (currentKb.value === id) {
        currentKbName.value = updated?.name || currentKbName.value
        await Promise.all([loadDocuments(), loadEvaluation()])
      }
      return updated
    } catch {
      runtimeStore.markServiceUnavailable('rag', '知识库更新失败，请稍后重试。')
      return null
    }
  }

  async function deleteKnowledgeBase(id: string) {
    if (runtimeStore.demoMode) {
      guestKnowledgeBaseState.value = guestKnowledgeBaseState.value.filter((item) => item.id !== id)
      delete guestDocumentsByKb.value[id]
      knowledgeBases.value = guestKnowledgeBaseState.value
      if (currentKb.value === id) {
        if (knowledgeBases.value.length > 0) {
          selectKb(knowledgeBases.value[0].id)
        } else {
          currentKb.value = ''
          currentKbName.value = ''
          documents.value = []
        }
      }
      return true
    }

    try {
      await ragApi.deleteKnowledgeBase(id)
      runtimeStore.markServiceAvailable('rag')
      const deletingCurrent = currentKb.value === id
      await loadKnowledgeBases()
      if (deletingCurrent) {
        if (knowledgeBases.value.length > 0) {
          selectKb(knowledgeBases.value[0].id)
        } else {
          currentKb.value = ''
          currentKbName.value = ''
          documents.value = []
          evaluationOverview.value = { ...DEFAULT_EVALUATION }
          lowRatedSamples.value = []
        }
      }
      return true
    } catch {
      runtimeStore.markServiceUnavailable('rag', '知识库删除失败，请先清空文档或稍后重试。')
      return false
    }
  }

  async function loadDocuments() {
    if (!currentKb.value) {
      documents.value = []
      return
    }

    if (runtimeStore.demoMode) {
      documents.value = guestDocumentsByKb.value[currentKb.value] || []
      documentError.value = ''
      return
    }

    loadingDocuments.value = true
    documentError.value = ''
    try {
      const data = await ragApi.listDocuments(currentKb.value)
      documents.value = data || []
      runtimeStore.markServiceAvailable('rag')
    } catch {
      documents.value = []
      documentError.value = '文档列表加载失败，请稍后重试。'
      runtimeStore.markServiceUnavailable('rag', documentError.value)
    } finally {
      loadingDocuments.value = false
    }
  }

  async function loadEvaluation() {
    if (!currentKb.value) {
      evaluationOverview.value = { ...DEFAULT_EVALUATION }
      lowRatedSamples.value = []
      return
    }

    if (runtimeStore.demoMode) {
      const currentDocuments = guestDocumentsByKb.value[currentKb.value] || []
      evaluationOverview.value = createGuestEvaluationOverview(currentDocuments)
      lowRatedSamples.value = createGuestEvaluationSamples(currentKb.value)
      return
    }

    loadingEvaluation.value = true
    try {
      const [overview, samples] = await Promise.all([
        ragApi.getEvaluationOverview(currentKb.value),
        ragApi.getLowRatedSamples(currentKb.value, 10)
      ])
      evaluationOverview.value = overview || { ...DEFAULT_EVALUATION }
      lowRatedSamples.value = samples || []
      runtimeStore.markServiceAvailable('rag')
    } catch {
      evaluationOverview.value = { ...DEFAULT_EVALUATION }
      lowRatedSamples.value = []
    } finally {
      loadingEvaluation.value = false
    }
  }

  async function refreshCurrentKnowledgeBase() {
    await Promise.all([loadDocuments(), loadEvaluation()])
  }

  async function uploadFile(file: File, replaceExisting = false): Promise<DocumentMeta | null> {
    if (runtimeStore.demoMode) {
      const record: DocumentMeta = {
        id: `${currentKb.value}-doc-${Date.now()}`,
        filename: file.name,
        knowledgeBaseId: currentKb.value,
        fileSize: file.size,
        contentType: file.type || 'application/octet-stream',
        chunkCount: Math.max(6, Math.ceil(file.size / 20480)),
        uploadedBy: authStore.userId,
        status: 'INDEXED',
        createdAt: new Date().toISOString(),
        indexedAt: new Date().toISOString()
      }
      const existing = guestDocumentsByKb.value[currentKb.value] || []
      guestDocumentsByKb.value[currentKb.value] = replaceExisting
        ? [record, ...existing.filter((item) => item.filename !== file.name)]
        : [record, ...existing]
      guestKnowledgeBaseState.value = guestKnowledgeBaseState.value.map((item) =>
        item.id === currentKb.value
          ? {
              ...item,
              documentCount: (guestDocumentsByKb.value[currentKb.value] || []).length,
              totalChunks: (guestDocumentsByKb.value[currentKb.value] || []).reduce(
                (sum, doc) => sum + (doc.chunkCount || 0),
                0
              )
            }
          : item
      )
      knowledgeBases.value = guestKnowledgeBaseState.value
      documents.value = guestDocumentsByKb.value[currentKb.value] || []
      return record
    }

    try {
      const meta = await ragApi.uploadDocument(file, currentKb.value, authStore.userId, replaceExisting)
      runtimeStore.markServiceAvailable('rag')
      await Promise.all([loadDocuments(), loadKnowledgeBases(), loadEvaluation()])
      return meta
    } catch {
      runtimeStore.markServiceUnavailable('rag', '文件上传失败，请检查知识库服务或对象存储配置。')
      return null
    }
  }

  async function deleteDocument(docId: string) {
    if (runtimeStore.demoMode) {
      guestDocumentsByKb.value[currentKb.value] = (guestDocumentsByKb.value[currentKb.value] || []).filter(
        (item) => item.id !== docId
      )
      documents.value = guestDocumentsByKb.value[currentKb.value] || []
      return true
    }

    try {
      await ragApi.deleteDocument(docId)
      runtimeStore.markServiceAvailable('rag')
      await Promise.all([loadDocuments(), loadKnowledgeBases(), loadEvaluation()])
      return true
    } catch {
      runtimeStore.markServiceUnavailable('rag', '删除文档失败，请稍后重试。')
      return false
    }
  }

  async function retryDocument(docId: string) {
    if (runtimeStore.demoMode) {
      guestDocumentsByKb.value[currentKb.value] = (guestDocumentsByKb.value[currentKb.value] || []).map((item) =>
        item.id === docId ? { ...item, status: 'INDEXED', indexedAt: new Date().toISOString() } : item
      )
      documents.value = guestDocumentsByKb.value[currentKb.value] || []
      return true
    }

    try {
      await ragApi.retryDocument(docId)
      runtimeStore.markServiceAvailable('rag')
      await Promise.all([loadDocuments(), loadKnowledgeBases(), loadEvaluation()])
      return true
    } catch {
      runtimeStore.markServiceUnavailable('rag', '文档重试失败，请稍后重试。')
      return false
    }
  }

  async function reindexDocument(docId: string) {
    if (runtimeStore.demoMode) {
      return retryDocument(docId)
    }

    try {
      await ragApi.reindexDocument(docId)
      runtimeStore.markServiceAvailable('rag')
      await Promise.all([loadDocuments(), loadKnowledgeBases(), loadEvaluation()])
      return true
    } catch {
      runtimeStore.markServiceUnavailable('rag', '重建索引失败，请稍后重试。')
      return false
    }
  }

  async function downloadDocument(docId: string, filename: string) {
    if (runtimeStore.demoMode) {
      const blob = new Blob(
        [`这是游客模式下导出的演示文档：${filename}\n文档ID：${docId}\n知识库：${currentKbName.value}`],
        { type: 'text/plain;charset=utf-8' }
      )
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      anchor.click()
      URL.revokeObjectURL(url)
      return true
    }

    try {
      const blob = await ragApi.downloadDocument(docId)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      anchor.click()
      URL.revokeObjectURL(url)
      runtimeStore.markServiceAvailable('rag')
      return true
    } catch {
      runtimeStore.markServiceUnavailable('rag', '下载文档失败，请稍后重试。')
      return false
    }
  }

  async function previewDocument(docId: string) {
    if (runtimeStore.demoMode) {
      window.open(
        `data:text/plain;charset=utf-8,${encodeURIComponent(`游客模式预览文档：${docId}`)}`,
        '_blank'
      )
      return true
    }

    try {
      const data = await ragApi.previewDocument(docId)
      if (data?.previewUrl) {
        window.open(data.previewUrl, '_blank')
      }
      runtimeStore.markServiceAvailable('rag')
      return true
    } catch {
      runtimeStore.markServiceUnavailable('rag', '文档预览失败，请稍后重试。')
      return false
    }
  }

  async function loadDocumentChunks(doc: DocumentMeta) {
    if (runtimeStore.demoMode) {
      activeDocumentChunks.value = createGuestDocumentChunks(doc)
      activeChunkDocument.value = doc
      return true
    }

    try {
      const data = await ragApi.listDocumentChunks(doc.id)
      activeDocumentChunks.value = data || []
      activeChunkDocument.value = doc
      runtimeStore.markServiceAvailable('rag')
      return true
    } catch {
      activeDocumentChunks.value = []
      activeChunkDocument.value = null
      runtimeStore.markServiceUnavailable('rag', '文档分段加载失败，请稍后重试。')
      return false
    }
  }

  function clearDocumentChunks() {
    activeDocumentChunks.value = []
    activeChunkDocument.value = null
  }

  async function ragQuery(question: string, stream = false, topK = 5) {
    if (!question.trim()) {
      return
    }

    queryResult.value = ''
    querySources.value = []
    queryResponseId.value = ''
    queryFeedback.value = null
    queryError.value = ''
    isQuerying.value = true
    queryStage.value = 'retrieving'

    if (runtimeStore.demoMode) {
      await new Promise((resolve) => setTimeout(resolve, stream ? 450 : 180))
      queryStage.value = 'answering'
      queryResult.value = createGuestRagAnswer(question, currentKb.value)
      querySources.value = createGuestRagSources(question, currentKb.value)
      queryResponseId.value = `guest-rag-${Date.now()}`
      queryFeedback.value = null
      isQuerying.value = false
      queryStage.value = 'idle'
      return
    }

    try {
      if (stream) {
        const { response } = ragApi.ragQueryStream({
          question,
          knowledgeBaseId: currentKb.value,
          topK
        })
        const res = await response
        if (!res.ok || !res.body) {
          throw new Error(`HTTP ${res.status}`)
        }

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) {
            break
          }
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          for (const line of lines) {
            const trimmed = line.trim()
            if (!trimmed || !trimmed.startsWith('data:')) {
              continue
            }
            const jsonStr = trimmed.slice(5).trim()
            if (!jsonStr || jsonStr === '[DONE]') {
              continue
            }
            try {
              const data = JSON.parse(jsonStr) as SSEChunk
              if (data.chunk) {
                queryStage.value = 'answering'
                queryResult.value += data.chunk
              }
              if (Array.isArray(data.sources)) {
                querySources.value = data.sources.map((item) => ({ ...item, feedback: null }))
              }
              if (data.done && data.responseId) {
                queryResponseId.value = data.responseId
              }
            } catch {
              queryStage.value = 'answering'
              queryResult.value += jsonStr
            }
          }
        }
      } else {
        const data = await ragApi.ragQuery({
          question,
          knowledgeBaseId: currentKb.value,
          topK
        })
        queryStage.value = 'answering'
        queryResult.value = data.answer || ''
        querySources.value = (data.sources || []).map((item) => ({ ...item, feedback: null }))
        queryResponseId.value = data.responseId || ''
      }

      runtimeStore.markServiceAvailable('rag')
      await loadEvaluation()
    } catch {
      queryError.value = '知识库问答服务暂不可用，请稍后重试。'
      runtimeStore.markServiceUnavailable('rag', '知识库问答服务不可用，请检查 rag-service 状态。')
      queryResult.value = ''
      querySources.value = []
      queryResponseId.value = ''
      queryFeedback.value = null
    } finally {
      isQuerying.value = false
      queryStage.value = 'idle'
    }
  }

  async function submitQueryFeedback(feedback: 'up' | 'down') {
    if (runtimeStore.demoMode) {
      queryFeedback.value = feedback
      return true
    }
    if (!queryResponseId.value) {
      return false
    }
    await ragApi.submitFeedback(queryResponseId.value, feedback)
    queryFeedback.value = feedback
    await loadEvaluation()
    return true
  }

  async function submitEvidenceFeedback(chunkId: string, feedback: 'up' | 'down') {
    if (runtimeStore.demoMode) {
      querySources.value = querySources.value.map((item) =>
        item.chunkId === chunkId ? { ...item, feedback } : item
      )
      return true
    }
    if (!queryResponseId.value || !chunkId) {
      return false
    }
    await ragApi.submitEvidenceFeedback(queryResponseId.value, chunkId, currentKb.value, feedback)
    querySources.value = querySources.value.map((item) =>
      item.chunkId === chunkId ? { ...item, feedback } : item
    )
    await loadEvaluation()
    return true
  }

  return {
    knowledgeBases,
    currentKb,
    currentKbName,
    documents,
    activeDocumentChunks,
    activeChunkDocument,
    queryResult,
    querySources,
    queryResponseId,
    queryFeedback,
    isQuerying,
    queryStage,
    queryError,
    loadingKnowledgeBases,
    loadingDocuments,
    loadingEvaluation,
    knowledgeBaseError,
    documentError,
    evaluationOverview,
    lowRatedSamples,
    loadKnowledgeBases,
    selectKb,
    createKnowledgeBase,
    updateKnowledgeBase,
    deleteKnowledgeBase,
    loadDocuments,
    loadEvaluation,
    refreshCurrentKnowledgeBase,
    uploadFile,
    deleteDocument,
    retryDocument,
    reindexDocument,
    downloadDocument,
    previewDocument,
    loadDocumentChunks,
    clearDocumentChunks,
    ragQuery,
    submitQueryFeedback,
    submitEvidenceFeedback
  }
})
