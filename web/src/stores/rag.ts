import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as ragApi from '@/api/rag'
import type { KnowledgeBase, DocumentMeta, SourceDocument } from '@/api/types'
import { DEMO_KNOWLEDGE_BASES, MOCK_RAG_RESPONSES, MOCK_RAG_SOURCES } from '@/utils/constants'
import { useAuthStore } from './auth'
import { useRuntimeStore } from './runtime'

export const useRagStore = defineStore('rag', () => {
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const currentKb = ref('kb-001')
  const currentKbName = ref('')
  const documents = ref<DocumentMeta[]>([])
  const queryResult = ref('')
  const querySources = ref<SourceDocument[]>([])
  const isQuerying = ref(false)

  const authStore = useAuthStore()
  const runtimeStore = useRuntimeStore()

  async function loadKnowledgeBases() {
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
        runtimeStore.markServiceUnavailable('rag', '知识库列表加载失败，请检查 rag-service 状态。')
      }
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
    try {
      const data = await ragApi.listDocuments(currentKb.value)
      documents.value = data || []
      runtimeStore.markServiceAvailable('rag')
    } catch {
      documents.value = []
      runtimeStore.markServiceUnavailable('rag', runtimeStore.demoMode
        ? '文档列表接口不可用，当前仅展示演示知识库摘要。'
        : '文档列表加载失败，请稍后重试。')
    }
  }

  async function uploadFile(file: File): Promise<DocumentMeta | null> {
    try {
      const meta = await ragApi.uploadDocument(file, currentKb.value, authStore.userId)
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
    } catch {
      runtimeStore.markServiceUnavailable('rag', '删除文档失败，请稍后重试。')
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
    } catch {
      runtimeStore.markServiceUnavailable('rag', '下载文档失败，请稍后重试。')
    }
  }

  async function previewDocument(docId: string) {
    try {
      const data = await ragApi.previewDocument(docId)
      if (data?.previewUrl) {
        window.open(data.previewUrl, '_blank')
      }
      runtimeStore.markServiceAvailable('rag')
    } catch {
      runtimeStore.markServiceUnavailable('rag', '文档预览失败，请稍后重试。')
    }
  }

  async function ragQuery(question: string, stream = false, topK = 5) {
    if (!question.trim()) {
      return
    }

    queryResult.value = ''
    querySources.value = []
    isQuerying.value = true

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
              const data = JSON.parse(jsonStr)
              if (data.chunk) {
                queryResult.value += data.chunk
              }
            } catch {
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
        queryResult.value = data.answer || ''
        querySources.value = data.sources || []
      }

      runtimeStore.markServiceAvailable('rag')
    } catch {
      runtimeStore.markServiceUnavailable('rag', runtimeStore.demoMode
        ? '知识库问答后端不可用，当前返回的是演示答案。'
        : '知识库问答服务不可用，请检查 rag-service 状态。')

      if (runtimeStore.demoMode) {
        queryResult.value = MOCK_RAG_RESPONSES[currentKb.value] || '当前为演示模式，暂未找到对应的模拟答案。'
        querySources.value = MOCK_RAG_SOURCES
      } else {
        queryResult.value = '知识库服务暂不可用，请稍后重试。当前页面不会自动切换到模拟答案。'
        querySources.value = []
      }
    } finally {
      isQuerying.value = false
    }
  }

  return {
    knowledgeBases,
    currentKb,
    currentKbName,
    documents,
    queryResult,
    querySources,
    isQuerying,
    loadKnowledgeBases,
    selectKb,
    loadDocuments,
    uploadFile,
    deleteDocument,
    downloadDocument,
    previewDocument,
    ragQuery
  }
})
