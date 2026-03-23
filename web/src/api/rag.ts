import client from './client'
import { buildHeaders } from './client'
import type { KnowledgeBase, DocumentChunkPreview, DocumentMeta, RagQueryRequest, RagQueryResponse } from './types'

const BASE = '/api/v1/rag'

// Knowledge Base CRUD
export function listKnowledgeBases(): Promise<KnowledgeBase[]> {
  return client.get(`${BASE}/knowledge-bases`)
}

export function getKnowledgeBase(id: string): Promise<KnowledgeBase> {
  return client.get(`${BASE}/knowledge-bases/${id}`)
}

export function createKnowledgeBase(data: Partial<KnowledgeBase>): Promise<KnowledgeBase> {
  return client.post(`${BASE}/knowledge-bases`, data)
}

export function updateKnowledgeBase(id: string, data: Partial<KnowledgeBase>): Promise<KnowledgeBase> {
  return client.put(`${BASE}/knowledge-bases/${id}`, data)
}

export function deleteKnowledgeBase(id: string): Promise<void> {
  return client.delete(`${BASE}/knowledge-bases/${id}`)
}

// Document management
export function listDocuments(knowledgeBaseId: string): Promise<DocumentMeta[]> {
  return client.get(`${BASE}/documents`, { params: { knowledgeBaseId } })
}

export function getDocument(id: string): Promise<DocumentMeta> {
  return client.get(`${BASE}/documents/${id}`)
}

export function listDocumentChunks(id: string): Promise<DocumentChunkPreview[]> {
  return client.get(`${BASE}/documents/${id}/chunks`)
}

export function uploadDocument(
  file: File,
  knowledgeBaseId: string,
  userId: string,
  replaceExisting = false
): Promise<DocumentMeta> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('knowledgeBaseId', knowledgeBaseId)
  formData.append('replaceExisting', String(replaceExisting))
  return client.post(`${BASE}/documents/upload`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      'X-User-Id': userId
    },
    timeout: 60000
  })
}

export function deleteDocument(id: string): Promise<void> {
  return client.delete(`${BASE}/documents/${id}`)
}

export async function downloadDocument(id: string): Promise<Blob> {
  const response = await client.get(`${BASE}/documents/${id}/download`, {
    responseType: 'blob'
  })
  return response as unknown as Blob
}

export function previewDocument(id: string): Promise<{ previewUrl: string; filename: string; contentType: string }> {
  return client.get(`${BASE}/documents/${id}/preview`)
}

export function retryDocument(id: string): Promise<DocumentMeta> {
  return client.post(`${BASE}/documents/${id}/retry`)
}

export function reindexDocument(id: string): Promise<DocumentMeta> {
  return client.post(`${BASE}/documents/${id}/reindex`)
}

// RAG query
export function ragQuery(data: RagQueryRequest): Promise<RagQueryResponse> {
  return client.post(`${BASE}/query`, data, { timeout: 120000 })
}

export function submitFeedback(responseId: string, feedback: 'up' | 'down', comment?: string): Promise<void> {
  return client.post(`${BASE}/feedback`, { responseId, feedback, comment })
}

export function submitEvidenceFeedback(
  responseId: string,
  chunkId: string,
  knowledgeBaseId: string,
  feedback: 'up' | 'down',
  comment?: string
): Promise<void> {
  return client.post(`${BASE}/feedback/evidence`, { responseId, chunkId, knowledgeBaseId, feedback, comment })
}

export function ragQueryStream(
  data: RagQueryRequest
): { response: Promise<Response>; abort: () => void } {
  const controller = new AbortController()
  const headers = buildHeaders()

  const response = fetch(`${BASE}/query/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(data),
    signal: controller.signal
  })

  return { response, abort: () => controller.abort() }
}
