import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as ragApi from '@/api/rag'
import type { DocumentChunkPreview, DocumentMeta, KnowledgeBase, SourceDocument, SSEChunk } from '@/api/types'
import { DEMO_KNOWLEDGE_BASES, MOCK_RAG_RESPONSES, MOCK_RAG_SOURCES } from '@/utils/constants'
import { useAuthStore } from './auth'
import { useRuntimeStore } from './runtime'

export const useRagStore = defineStore('rag', () => {
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
  const knowledgeBaseError = ref('')
  const documentError = ref('')

  const authStore = useAuthStore()
  const runtimeStore = useRuntimeStore()

  async function loadKnowledgeBases() {
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
    loadDocuments()
  }

  async function loadDocuments() {
    if (!currentKb.value) {
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
      documentError.value = runtimeStore.demoMode
        ? '文档列表接口不可用，当前仅展示知识库概览。'
        : '文档列表加载失败，请稍后重试。'
      runtimeStore.markServiceUnavailable('rag', documentError.value)
    } finally {
      loadingDocuments.value = false
    }
  }

  async function uploadFile(file: File, replaceExisting = false): Promise<DocumentMeta | null> {
    try {
      const meta = await ragApi.uploadDocument(file, currentKb.value, authStore.userId, replaceExisting)
      runtimeStore.markServiceAvailable('rag')
      await loadDocuments()
      await loadKnowledgeBases()
      return meta
    } catch {
      runtimeStore.markServiceUnavailable('rag', '文件上传失败，请检查知识库服务或对象存储配置。')
      return null
    }
  }

  async function deleteDocument(docId: string) {
    try {
      await ragApi.deleteDocument(docId)
      runtimeStore.markServiceAvailable('rag')
      await loadDocuments()
      await loadKnowledgeBases()
      return true
    } catch {
      runtimeStore.markServiceUnavailable('rag', '删除文档失败，请稍后重试。')
      return false
    }
  }

  async function retryDocument(docId: string) {
    try {
      await ragApi.retryDocument(docId)
      runtimeStore.markServiceAvailable('rag')
      await loadDocuments()
      await loadKnowledgeBases()
      return true
    } catch {
      runtimeStore.markServiceUnavailable('rag', '文档重试失败，请稍后重试。')
      return false
    }
  }

  async function reindexDocument(docId: string) {
    try {
      await ragApi.reindexDocument(docId)
      runtimeStore.markServiceAvailable('rag')
      await loadDocuments()
      await loadKnowledgeBases()
      return true
    } catch {
      runtimeStore.markServiceUnavailable('rag', '重建索引失败，请稍后重试。')
      return false
    }
  }

  async function downloadDocument(docId: string, filename: string) {
    try {
      const blob = await ragApi.downloadDocument(docId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
      runtimeStore.markServiceAvailable('rag')
      return true
    } catch {
      runtimeStore.markServiceUnavailable('rag', '下载文档失败，请稍后重试。')
      return false
    }
  }

  async function previewDocument(docId: string) {
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
    try {
      const data = await ragApi.listDocumentChunks(doc.id)
      activeDocumentChunks.value = data || []
      activeChunkDocument.value = doc
      runtimeStore.markServiceAvailable('rag')
      return true
    } catch {
      activeDocumentChunks.value = []
      activeChunkDocument.value = null
      runtimeStore.markServiceUnavailable('rag', '文档分块加载失败，请稍后重试。')
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
    } catch {
      queryError.value = runtimeStore.demoMode
        ? '问答后端不可用，已切换为演示答案。'
        : '知识库问答服务暂不可用，请稍后重试。'
      runtimeStore.markServiceUnavailable(
        'rag',
        runtimeStore.demoMode
          ? '知识库问答后端不可用，当前返回的是演示答案。'
          : '知识库问答服务不可用，请检查 rag-service 状态。'
      )

      if (runtimeStore.demoMode) {
        queryResult.value = MOCK_RAG_RESPONSES[currentKb.value] || '当前为演示模式，暂未找到对应的模拟答案。'
        querySources.value = MOCK_RAG_SOURCES.map((item) => ({ ...item, feedback: null }))
      } else {
        queryResult.value = ''
        querySources.value = []
      }
      queryResponseId.value = ''
      queryFeedback.value = null
    } finally {
      isQuerying.value = false
      queryStage.value = 'idle'
    }
  }

  async function submitQueryFeedback(feedback: 'up' | 'down') {
    if (!queryResponseId.value) {
      return false
    }
    await ragApi.submitFeedback(queryResponseId.value, feedback)
    queryFeedback.value = feedback
    return true
  }

  async function submitEvidenceFeedback(chunkId: string, feedback: 'up' | 'down') {
    if (!queryResponseId.value || !chunkId) {
      return false
    }
    await ragApi.submitEvidenceFeedback(queryResponseId.value, chunkId, currentKb.value, feedback)
    querySources.value = querySources.value.map((item) =>
      item.chunkId === chunkId ? { ...item, feedback } : item
    )
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
    knowledgeBaseError,
    documentError,
    loadKnowledgeBases,
    selectKb,
    loadDocuments,
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
