// Backend unified response envelope
export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
  traceId?: string
}

// Auth types
export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  refreshToken?: string
  tokenType: string
  expiresIn: number
  refreshExpiresIn?: number
  userId: string
  username: string
  roles: string
  department: string
}

export interface AiUser {
  id: string
  username: string
  employeeId?: string
  department?: string
  roles?: string
  enabled?: boolean
  createdAt?: string
  lastLoginAt?: string
}

export interface BotPermission {
  id: string
  botType: string
  allowedRoles: string
  allowedDepartments?: string
  dataScope: string
  allowedOperations: string
  dailyTokenLimit: number
  enabled: boolean
}

// Agent types
export type AgentType = string

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface SessionInfo {
  sessionId: string
  summary: string
}

export interface SSEChunk {
  chunk: string
  done: boolean
}

// RAG types
export interface KnowledgeBase {
  id: string
  name: string
  description?: string
  department?: string
  status?: string
  documentCount?: number
  totalChunks?: number
  chunkSize?: number
  chunkOverlap?: number
  createdBy?: string
  createdAt?: string
}

export interface DocumentMeta {
  id: string
  filename: string
  knowledgeBaseId?: string
  fileSize?: number
  storagePath?: string
  contentType?: string
  chunkCount?: number
  uploadedBy?: string
  status?: string
  createdAt?: string
  indexedAt?: string
  errorMessage?: string
}

export interface RagQueryRequest {
  question: string
  knowledgeBaseId: string
  topK?: number
  returnSources?: boolean
  history?: ChatMessage[]
}

export interface RagQueryResponse {
  answer: string
  sources: SourceDocument[]
  latencyMs: number
}

export interface SourceDocument {
  filename: string
  content: string
  score: number
}

// Monitor types
export interface MonitorOverview {
  totalRequests: number
  errorRequests: number
  successRate: number
  avgLatencyMs: number
  p95LatencyMs: number
  p99LatencyMs: number
  totalPromptTokens: number
  totalCompletionTokens: number
  totalTokens: number
  activeRequests: number
}

export interface HourlyStat {
  hour: number
  total: number
  errors: number
  avg_latency: number
  p50: number
  p95: number
}

export interface AgentStat {
  agent_type: string
  count: number
  avg_latency?: number
  errors?: number
}

export interface AlertEvent {
  level: string
  type: string
  message: string
  time: string
  source?: string
  status?: string
  fingerprint?: string
  silenceUrl?: string
  labels?: Record<string, string>
}

export interface TopUser {
  user_id: string
  agent_type: string
  calls: number
  avg_latency: number
}

export interface AuditLog {
  id: string
  user_id: string
  agent_type: string
  latency_ms: number
  success: boolean
  created_at: string
}

// Gateway types
export interface ModelInfo {
  id: string
  name: string
  provider: string
  enabled: boolean
  weight: number
  capabilities: string[]
  rpmLimit?: number
  totalCalls: number
  successCalls: number
  avgLatencyMs: number
  successRate: number
}

export interface GatewayModelsResponse {
  models: ModelInfo[]
  count: number
  sceneRoutes: Record<string, string[]>
  loadBalanceStrategy: string
}
