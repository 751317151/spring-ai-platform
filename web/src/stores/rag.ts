import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as ragApi from '@/api/rag'
import type { KnowledgeBase, DocumentMeta, SourceDocument } from '@/api/types'
import { DEMO_KNOWLEDGE_BASES, MOCK_RAG_RESPONSES, MOCK_RAG_SOURCES } from '@/utils/constants'
import { useAuthStore } from './auth'

export const useRagStore = defineStore('rag', () => {
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const currentKb = ref('kb-001')
  const currentKbName = ref('')
  const documents = ref<DocumentMeta[]>([])
  const queryResult = ref('')
  const querySources = ref<SourceDocument[]>([])
  const isQuerying = ref(false)

  const authStore = useAuthStore()

  async function loadKnowledgeBases() {
    try {
      const data = await ragApi.listKnowledgeBases()
      knowledgeBases.value = data || []
    } catch {
      knowledgeBases.value = DEMO_KNOWLEDGE_BASES
    }
    if (knowledgeBases.value.length > 0 && !knowledgeBases.value.find(kb => kb.id === currentKb.value)) {
      selectKb(knowledgeBases.value[0].id)
    }
  }

  function selectKb(kbId: string) {
    currentKb.value = kbId
    const kb = knowledgeBases.value.find(k => k.id === kbId)
    currentKbName.value = kb?.name || ''
    loadDocuments()
  }

  async function loadDocuments() {
    if (!currentKb.value) return
    try {
      const data = await ragApi.listDocuments(currentKb.value)
      documents.value = data || []
    } catch {
      documents.value = []
    }
  }

  async function uploadFile(file: File): Promise<DocumentMeta | null> {
    try {
      const meta = await ragApi.uploadDocument(file, currentKb.value, authStore.userId)
      await loadDocuments()
      await loadKnowledgeBases()
      return meta
    } catch {
      return null
    }
  }

  async function deleteDocument(docId: string) {
    try {
      await ragApi.deleteDocument(docId)
      await loadDocuments()
      await loadKnowledgeBases()
    } catch {
      // ignore
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
    } catch {
      // ignore - axios call already includes auth headers, no fallback to window.open
    }
  }

  async function previewDocument(docId: string) {
    try {
      const data = await ragApi.previewDocument(docId)
      if (data?.previewUrl) {
        window.open(data.previewUrl, '_blank')
      }
    } catch {
      // ignore
    }
  }

  async function ragQuery(question: string, stream = false, topK = 5) {
    if (!question.trim()) return
    queryResult.value = ''
    querySources.value = []
    isQuerying.value = true

    if (stream) {
      try {
        const { response } = ragApi.ragQueryStream({
          question,
          knowledgeBaseId: currentKb.value,
          topK
        })
        const res = await response
        if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          for (const line of lines) {
            const trimmed = line.trim()
            if (!trimmed || !trimmed.startsWith('data:')) continue
            const jsonStr = trimmed.slice(5).trim()
            if (!jsonStr || jsonStr === '[DONE]') continue
            try {
              const data = JSON.parse(jsonStr)
              if (data.chunk) queryResult.value += data.chunk
            } catch {
              queryResult.value += jsonStr
            }
          }
        }
      } catch {
        queryResult.value = MOCK_RAG_RESPONSES[currentKb.value] || '暂未找到相关信息。'
        querySources.value = MOCK_RAG_SOURCES
      }
    } else {
      try {
        const data = await ragApi.ragQuery({
          question,
          knowledgeBaseId: currentKb.value,
          topK
        })
        queryResult.value = data.answer || ''
        querySources.value = data.sources || []
      } catch {
        queryResult.value = MOCK_RAG_RESPONSES[currentKb.value] || '暂未找到相关信息。'
        querySources.value = MOCK_RAG_SOURCES
      }
    }

    isQuerying.value = false
  }

  return {
    knowledgeBases, currentKb, currentKbName, documents,
    queryResult, querySources, isQuerying,
    loadKnowledgeBases, selectKb, loadDocuments, uploadFile,
    deleteDocument, downloadDocument, previewDocument, ragQuery
  }
})
