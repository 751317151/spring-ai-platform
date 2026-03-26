export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
  traceId?: string
}

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

export interface UserUpsertRequest {
  username?: string
  password?: string
  employeeId?: string
  department?: string
  roles?: string
  enabled?: string
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

export interface BotPermissionUpsertRequest {
  botType?: string
  allowedRoles?: string
  allowedDepartments?: string
  dataScope?: string
  allowedOperations?: string
  dailyTokenLimit?: number
  enabled?: boolean
}

export type AgentType = string

export interface MessageDerivedFrom {
  action: 'continue' | 'regenerate' | 'branch'
  messageIndex: number
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  responseId?: string
  feedback?: 'up' | 'down' | null
  sessionConfigSnapshot?: SessionConfig | null
  derivedFrom?: MessageDerivedFrom | null
}

export interface FavoriteMessageRecord {
  id: string
  responseId?: string
  role: 'user' | 'assistant'
  content: string
  agentType?: string
  sessionId?: string
  sessionSummary?: string
  sourceMessageIndex?: number | null
  createdAt: number
  lastCollectedAt?: number
  duplicateCount?: number
  tags?: string[]
  sessionConfigSnapshot?: SessionConfig | null
}

export interface LearningNoteRecord {
  id: string
  title: string
  content: string
  sourceType?: 'favorite' | 'manual' | 'session-search'
  relatedFavoriteId?: string | null
  relatedSessionId?: string | null
  relatedAgentType?: string | null
  relatedSessionSummary?: string | null
  relatedMessageIndex?: number | null
  tags?: string[]
  createdAt: number
  updatedAt: number
}

export interface SessionSearchResult {
  agentType: string
  sessionId: string
  sessionSummary: string
  matchedRole: 'user' | 'assistant'
  matchedContent: string
  matchedMessageIndex?: number
  updatedAt?: string
  matchedField?: 'title' | 'message'
  excerpt?: string
  contextBefore?: string
  contextAfter?: string
  additionalMatchCount?: number
}

export interface SessionInfo {
  sessionId: string
  summary: string
  updatedAt?: string
  pinned?: string | boolean
  archived?: string | boolean
  model?: string
  knowledgeEnabled?: string | boolean
}

export interface SessionConfig {
  model?: string | null
  temperature?: number | null
  maxContextMessages?: number | null
  knowledgeEnabled?: boolean | null
  systemPromptTemplate?: string | null
  updatedAt?: number | null
}

export interface SendMessageOptions {
  sessionConfigOverride?: SessionConfig | null
  derivedFrom?: MessageDerivedFrom | null
}

export interface SSEChunk {
  chunk: string
  done: boolean
  responseId?: string
  sources?: SourceDocument[]
}

export interface KnowledgeBase {
  id: string
  name: string
  description?: string
  department?: string
  visibilityScope?: 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE' | string
  status?: string
  documentCount?: number
  totalChunks?: number
  chunkSize?: number
  chunkOverlap?: number
  chunkStrategy?: string
  structuredBatchSize?: number
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

export interface DocumentChunkPreview {
  id: string
  chunkIndex: number
  content: string
  preview?: string
  charCount?: number
}

export interface RagQueryRequest {
  question: string
  knowledgeBaseId: string
  topK?: number
  returnSources?: boolean
  history?: ChatMessage[]
}

export interface RagQueryResponse {
  responseId?: string
  answer: string
  sources: SourceDocument[]
  latencyMs: number
}

export interface SourceDocument {
  documentId?: string
  chunkId?: string
  chunkIndex?: number
  filename: string
  preview?: string
  content: string
  score: number
  feedback?: 'up' | 'down' | null
}

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

export interface ModelStat {
  model_id: string
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
  workflowStatus?: string
  workflowNote?: string
  workflowUpdatedAt?: string
  silencedUntil?: string
  labels?: Record<string, string>
}

export interface AlertWorkflowHistory {
  fingerprint: string
  workflowStatus: string
  workflowNote?: string
  silencedUntil?: string
  createdAt: string
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
  model_id?: string
  error_message?: string
  session_id?: string
  trace_id?: string
  latency_ms: number
  success: boolean
  created_at: string
}

export interface TokenUsage {
  userId: string
  date: string
  tokensUsed: number
}

export interface SlowRequestSample {
  id: string
  user_id: string
  agent_type: string
  model_id?: string
  trace_id?: string
  latency_ms: number
  success: boolean
  created_at: string
}

export interface FailureSample {
  id: string
  user_id: string
  agent_type: string
  model_id?: string
  error_message?: string
  latency_ms: number
  session_id?: string
  trace_id?: string
  created_at: string
}

export interface TraceDetail {
  id: string
  trace_id: string
  user_id?: string
  agent_type?: string
  model_id?: string
  session_id?: string
  success: boolean
  error_message?: string
  latency_ms: number
  prompt_tokens?: number | null
  completion_tokens?: number | null
  created_at: string
  user_message?: string
  ai_response?: string
  tool_executions?: ToolAudit[]
  phase_breakdown?: TracePhase[]
}

export interface TracePhase {
  key: string
  label: string
  latency_ms: number
  share: number
  estimated?: boolean
  description?: string
}

export interface ToolAudit {
  id: string
  user_id?: string
  session_id?: string
  agent_type?: string
  tool_name?: string
  tool_class?: string
  input_summary?: string
  output_summary?: string
  success: boolean
  error_message?: string
  latency_ms: number
  trace_id?: string
  created_at: string
}

export interface FeedbackOverview {
  totalCount: number
  positiveCount: number
  negativeCount: number
  positiveRate: number
}

export interface FeedbackSample {
  responseId: string
  userId?: string
  sourceType: string
  agentType?: string
  knowledgeBaseId?: string
  feedback: 'up' | 'down'
  comment?: string
  createdAt: string
}

export interface EvidenceFeedbackSample {
  responseId: string
  chunkId: string
  userId?: string
  knowledgeBaseId?: string
  feedback: 'up' | 'down'
  comment?: string
  createdAt: string
}

export interface RagEvaluationOverview {
  totalQueries: number
  feedbackCount: number
  positiveFeedbackCount: number
  negativeFeedbackCount: number
  positiveFeedbackRate: number
  evidenceFeedbackCount: number
  positiveEvidenceCount: number
  negativeEvidenceCount: number
  positiveEvidenceRate: number
  lowRatedQueryCount: number
}

export interface RagEvaluationSample {
  responseId: string
  userId?: string
  knowledgeBaseId?: string
  question: string
  answer?: string
  feedback?: 'up' | 'down' | string
  comment?: string
  evidenceNegativeCount: number
  createdAt: string
}

export interface ModelInfo {
  id: string
  name: string
  provider: string
  enabled: boolean
  weight: number
  capabilities: string[]
  rpmLimit?: number
  healthStatus?: 'healthy' | 'degraded'
  degradedUntil?: number | null
  healthReason?: string
  consecutiveFailures?: number
  totalCalls: number
  successCalls: number
  avgLatencyMs: number
  successRate: number
  promptCostPer1kTokens?: number
  completionCostPer1kTokens?: number
  totalPromptTokens?: number
  totalCompletionTokens?: number
  totalEstimatedCost?: number
  lastCheckedAt?: number | null
  lastProbeLatencyMs?: number | null
}

export interface GatewayRouteDecisionPreview {
  scene: string
  requestedModelId?: string
  selectedModelId: string
  strategy: string
  reason: string
  fallbackTriggered: boolean
  estimatedCostNote?: string
  candidateModels: GatewayRouteCandidate[]
}

export interface GatewayRouteCandidate {
  id: string
  name: string
  provider: string
  enabled: boolean
  healthy: boolean
  selected: boolean
  degraded: boolean
  weight: number
  avgLatencyMs?: number
  successRate?: number
  promptCostPer1kTokens?: number
  completionCostPer1kTokens?: number
  reason?: string
}

export interface GatewayModelsResponse {
  models: ModelInfo[]
  count: number
  sceneRoutes: Record<string, string[]>
  loadBalanceStrategy: string
}

export interface McpServerInfo {
  code: string
  command: string
  args: string[]
  enabled: boolean
  clientEnabled: boolean
  source: string
}

export interface McpServerListResponse {
  clientEnabled: boolean
  source: string
  count: number
  servers: McpServerInfo[]
}
