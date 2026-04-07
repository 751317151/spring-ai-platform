import type {
  BotPermission,
  DocumentChunkPreview,
  DocumentMeta,
  KnowledgeBase,
  LoginResponse,
  RagEvaluationOverview,
  RagEvaluationSample,
  SourceDocument
} from '@/api/types'
import { AGENT_CONFIG, DEMO_KNOWLEDGE_BASES, MOCK_RAG_RESPONSES, MOCK_RAG_SOURCES } from './constants'

export const GUEST_AUTH_MODE = 'guest'

export function createGuestLoginResponse(): LoginResponse {
  return {
    token: `mock-token-guest-${Date.now()}`,
    tokenType: 'Bearer',
    expiresIn: 7 * 24 * 60 * 60,
    refreshExpiresIn: 0,
    userId: 'guest',
    username: '游客体验',
    roles: 'ROLE_USER',
    department: '本地演示',
    province: '上海市',
    city: '上海市'
  }
}

export function createGuestBots(): BotPermission[] {
  return Object.keys(AGENT_CONFIG).map((botType, index) => ({
    id: `guest-bot-${index + 1}`,
    botType,
    allowedRoles: 'ROLE_USER',
    allowedDepartments: '本地演示',
    dataScope: 'ALL',
    allowedOperations: 'chat,query,search',
    dailyTokenLimit: 999999,
    enabled: true
  }))
}

export function createGuestKnowledgeBases(): KnowledgeBase[] {
  const departments = ['全公司', '研发中心', '销售部']
  return DEMO_KNOWLEDGE_BASES.map((item, index) => ({
    ...item,
    department: item.department || departments[index] || '本地演示',
    visibilityScope: index === 0 ? 'PUBLIC' : 'DEPARTMENT',
    chunkSize: 1000,
    chunkOverlap: 200,
    chunkStrategy: 'TOKEN',
    structuredBatchSize: 20,
    createdBy: 'guest',
    createdAt: new Date(Date.now() - (index + 1) * 86400000).toISOString()
  }))
}

export function createGuestDocumentsByKnowledgeBase(
  knowledgeBases: KnowledgeBase[]
): Record<string, DocumentMeta[]> {
  const now = Date.now()
  const filenames = [
    '入职指引与流程说明.pdf',
    '接口规范与开发手册.md',
    '典型案例与FAQ.xlsx'
  ]
  const contentTypes = [
    'application/pdf',
    'text/markdown',
    'application/vnd.ms-excel'
  ]

  return knowledgeBases.reduce<Record<string, DocumentMeta[]>>((acc, kb, index) => {
    const baseId = String(kb.id)
    acc[baseId] = Array.from({ length: 3 }, (_, docIndex) => ({
      id: `${baseId}-doc-${docIndex + 1}`,
      filename: filenames[docIndex] || `演示文档-${docIndex + 1}.txt`,
      knowledgeBaseId: baseId,
      fileSize: 180 * 1024 + docIndex * 42000,
      storagePath: `/mock/${baseId}/document-${docIndex + 1}`,
      contentType: contentTypes[docIndex] || 'text/plain',
      chunkCount: 18 + docIndex * 7 + index * 2,
      uploadedBy: 'guest',
      status: docIndex === 2 && index === 1 ? 'PROCESSING' : 'INDEXED',
      createdAt: new Date(now - (index * 5 + docIndex + 1) * 3600000).toISOString(),
      indexedAt: new Date(now - (index * 5 + docIndex) * 3500000).toISOString()
    }))
    return acc
  }, {})
}

export function createGuestDocumentChunks(document: DocumentMeta): DocumentChunkPreview[] {
  return Array.from({ length: Math.min(document.chunkCount || 6, 6) }, (_, index) => ({
    id: `${document.id}-chunk-${index + 1}`,
    chunkIndex: index,
    content: `这是 ${document.filename} 的第 ${index + 1} 个演示分段，包含可用于问答的示例内容。`,
    preview: `分段 ${index + 1}：${document.filename} 关键摘要片段。`,
    charCount: 96 + index * 14
  }))
}

export function createGuestRagSources(question: string, kbId: string): SourceDocument[] {
  const shortQuestion = question.slice(0, 12) || '当前问题'
  return MOCK_RAG_SOURCES.map((item, index) => ({
    ...item,
    documentId: `${kbId}-doc-${(index % 3) + 1}`,
    chunkId: `${kbId}-doc-${(index % 3) + 1}-chunk-${index + 1}`,
    chunkIndex: index,
    preview: `${item.filename} 命中了与“${shortQuestion}”相关的演示内容。`,
    content: item.content || `${item.filename} 的演示命中内容，用于展示知识库引用来源。`,
    feedback: null
  }))
}

export function createGuestRagAnswer(question: string, kbId: string): string {
  const preset = MOCK_RAG_RESPONSES[kbId]
  if (preset) {
    return `${preset}\n\n补充说明：这是游客模式下基于前端 mock 数据生成的演示回答。`
  }
  return `游客模式演示回答：\n\n你刚刚问的是“${question}”。当前页面未调用后端服务，答案和引用来源都来自前端本地 mock 数据，用于完整体验知识库问答流程。`
}

export function createGuestEvaluationOverview(documents: DocumentMeta[]): RagEvaluationOverview {
  const totalQueries = 48 + documents.length * 3
  const feedbackCount = 18 + documents.length
  const positiveFeedbackCount = Math.max(0, feedbackCount - 3)
  const negativeFeedbackCount = feedbackCount - positiveFeedbackCount
  const evidenceFeedbackCount = 26 + documents.length
  const positiveEvidenceCount = evidenceFeedbackCount - 4
  const negativeEvidenceCount = evidenceFeedbackCount - positiveEvidenceCount
  return {
    totalQueries,
    feedbackCount,
    positiveFeedbackCount,
    negativeFeedbackCount,
    positiveFeedbackRate: feedbackCount > 0 ? positiveFeedbackCount / feedbackCount : 0,
    evidenceFeedbackCount,
    positiveEvidenceCount,
    negativeEvidenceCount,
    positiveEvidenceRate: evidenceFeedbackCount > 0 ? positiveEvidenceCount / evidenceFeedbackCount : 0,
    lowRatedQueryCount: negativeFeedbackCount + negativeEvidenceCount
  }
}

export function createGuestEvaluationSamples(kbId: string): RagEvaluationSample[] {
  const questions = ['如何申请年假？', '接口版本如何命名？', '销售折扣审批怎么走？']
  const comments = ['回答过于概括', '缺少具体步骤', '希望增加来源说明']
  return Array.from({ length: 3 }, (_, index) => ({
    responseId: `${kbId}-feedback-${index + 1}`,
    userId: 'guest',
    knowledgeBaseId: kbId,
    question: questions[index] || '示例问题',
    answer: '这是游客模式下生成的示例回答，用于展示低评分样本列表。',
    feedback: 'down',
    comment: comments[index] || '需要更多细节',
    evidenceNegativeCount: index + 1,
    createdAt: new Date(Date.now() - (index + 1) * 7200000).toISOString()
  }))
}
