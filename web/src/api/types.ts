export interface Result<T> {
  code: number
  message: string
  data: T
  error?: unknown
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
  traceId?: string
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

export interface FollowUpTemplateRecord {
  id: string
  name: string
  content: string
  sourceCount: number
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
  traceId?: string
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
  entryFile?: string
  entryFileExists?: boolean
  commandAvailable?: boolean
  diagnosticStatus?: 'ready' | 'issue' | 'disabled' | string
  issueReason?: string
  commandLinePreview?: string
  runtimeHint?: string
  authorized?: boolean
  authorizedAgentType?: string
  authorizedTools?: string[]
  accessReasonCode?: string
  accessReasonMessage?: string
  accessDetail?: string
}

export interface McpServerListResponse {
  clientEnabled: boolean
  source: string
  agentType?: string
  count: number
  authorizedCount?: number
  issueCount?: number
  servers: McpServerInfo[]
}

export interface ToolSecurityOverviewResponse {
  securityEnabled: boolean
  agentType: string
  allowedTools: string[]
  allowedConnectors: string[]
  enabledConnectors: string[]
}

export interface AgentAccessRuleItem {
  code: string
  name: string
  category: string
  enabled: boolean
  authorized: boolean
  status: string
  reason?: string
  reasonCode?: string
  reasonMessage?: string
  resource?: string
  detail?: string
}

export interface AgentAccessOverviewResponse {
  agentType: string
  securityEnabled: boolean
  runtimePolicySummary?: AgentRuntimePolicySummary
  tools: AgentAccessRuleItem[]
  connectors: AgentAccessRuleItem[]
  mcpServers: AgentAccessRuleItem[]
  summary: string
}

export interface AgentWorkbenchFailureItem {
  traceId?: string
  sessionId?: string
  userId?: string
  summary?: string
  errorMessage?: string
  latencyMs?: number
  createdAt?: string
}

export interface AgentWorkbenchTrendPoint {
  label: string
  totalCalls: number
  failureCalls: number
  toolCalls: number
  avgLatencyMs: number
}

export interface AgentWorkbenchToolRankItem {
  toolName: string
  callCount: number
  failureCount: number
  avgLatencyMs: number
  latestTraceId?: string
}

export interface AgentWorkbenchErrorTypeItem {
  type: string
  label: string
  count: number
}

export interface AgentWorkbenchHealthSummary {
  accessible: boolean
  failureSpike: boolean
  toolFailureSpike: boolean
  warning: boolean
  summary: string
}

export interface AgentRuntimePolicySummary {
  securityEnabled: boolean
  connectorResourceIsolationEnabled: boolean
  mcpToolIsolationEnabled: boolean
  dataScopeIsolationEnabled: boolean
  dataSourceIsolationEnabled?: boolean
  crossSchemaAccessControlled?: boolean
  concurrencyIsolationEnabled?: boolean
  wildcardToolAccess: boolean
  wildcardConnectorAccess: boolean
  wildcardMcpAccess: boolean
  wildcardDataAccess: boolean
  wildcardDataSourceAccess?: boolean
  wildcardCrossSchemaAccess?: boolean
  currentActiveRequests?: number
  maxConcurrency?: number
  dailyTokenLimit?: number
  requestTimeoutMs?: number
  streamTimeoutMs?: number
  restrictedResourceCount: number
  riskCount: number
  riskLevel: 'low' | 'medium' | 'high' | string
  summary: string
  highlights?: string[]
}

export interface AgentWorkbenchChangeItem {
  type: string
  label: string
  direction: 'up' | 'down' | 'flat' | string
  severity: 'low' | 'medium' | 'high' | string
  summary: string
}

export interface AgentWorkbenchCompareInsight {
  type: string
  severity: 'low' | 'medium' | 'high' | string
  winnerAgentType: string
  loserAgentType: string
  metricKey: string
  title: string
  summary: string
  leftEvidence: string
  rightEvidence: string
  whyItMatters: string
  suggestedAction: string
}

export interface AgentWorkbenchCompareMetric {
  key: string
  label: string
  leftValue: string
  rightValue: string
  delta: string
  trend: string
  winnerAgentType: string
  summary: string
}

export interface AgentLogLifecycleBucket {
  type: string
  archiveAfterDays: number
  deleteAfterDays: number
  activeCount: number
  archiveCandidateCount: number
  deleteCandidateCount: number
}

export interface AgentLogArchiveArtifact {
  type: string
  path: string
  recordCount: number
}

export interface AgentLogArchiveSample {
  type: string
  id?: string
  traceId?: string
  sessionId?: string
  summary?: string
  createdAt?: string
}

export interface AgentLogArchiveDetailResponse {
  agentType: string
  enabled: boolean
  manifestDir?: string
  bundleDir?: string
  manifestPath?: string
  generatedAt?: string
  dryRun?: boolean
  exportedRecordCount?: number
  coldDataCount?: number
  sampleLimit?: number
  exportBatchSize?: number
  operationHints?: string[]
  artifacts: AgentLogArchiveArtifact[]
  samples: AgentLogArchiveSample[]
}

export interface AgentLogArchivePreviewItem {
  lineNumber: number
  content: string
}

export interface AgentLogArchivePreviewResponse {
  agentType: string
  artifactType: string
  bundleDir?: string
  artifactPath?: string
  previewLimit: number
  items: AgentLogArchivePreviewItem[]
}

export interface AgentLogLifecycleSummaryResponse {
  agentType: string
  totalActiveCount: number
  totalArchiveCandidateCount: number
  totalDeleteCandidateCount: number
  totalColdDataCount?: number
  automationEnabled: boolean
  automationDryRun: boolean
  automationIntervalMs: number
  archiveManifestDir?: string
  lastArchiveBundleDir?: string
  lastArchiveManifestPath?: string
  lastArchiveManifestAt?: string
  lastArchiveExportedRecordCount?: number
  buckets: AgentLogLifecycleBucket[]
  summary: string
}

export interface AgentLogCleanupRequest {
  dryRun?: boolean
}

export interface AgentLogCleanupResponse {
  agentType: string
  dryRun: boolean
  deletedAuditLogs: number
  deletedToolAuditLogs: number
  deletedTraceSteps: number
  deletedTraces: number
  summary: string
}

export interface AgentWorkbenchSummaryResponse {
  agentType: string
  windowLabel: string
  totalCalls: number
  failureCalls: number
  successRate: number
  avgLatencyMs: number
  toolCallCount: number
  toolFailureCount: number
  avgToolLatencyMs: number
  slowestToolName?: string
  slowestToolLatencyMs: number
  recentTraceCount: number
  latestTraceId?: string
  latestErrorMessage?: string
  healthSummary?: AgentWorkbenchHealthSummary
  runtimePolicySummary?: AgentRuntimePolicySummary
  last24hTrend?: AgentWorkbenchTrendPoint[]
  last7dTrend?: AgentWorkbenchTrendPoint[]
  last4wTrend?: AgentWorkbenchTrendPoint[]
  toolRanking?: AgentWorkbenchToolRankItem[]
  errorTypes?: AgentWorkbenchErrorTypeItem[]
  recentChanges?: AgentWorkbenchChangeItem[]
  weeklyDigest?: string
  recentFailures: AgentWorkbenchFailureItem[]
}

export interface AgentWorkbenchCompareResponse {
  left: AgentWorkbenchSummaryResponse
  right: AgentWorkbenchSummaryResponse
  summary: string
  metrics: AgentWorkbenchCompareMetric[]
  insights: AgentWorkbenchCompareInsight[]
  leftDetail?: AgentWorkbenchCompareAgentDetail
  rightDetail?: AgentWorkbenchCompareAgentDetail
  changeComparison?: AgentWorkbenchCompareChangeItem[]
}

export interface AgentWorkbenchCompareAgentDetail {
  agentType: string
  summary?: string
  healthSummary?: string
  policySummary?: string
  totalCalls?: number
  failureRateLabel?: string
  riskLevel?: string
  highlights?: string[]
  topErrorTypes?: string[]
  recentChanges?: AgentWorkbenchChangeItem[]
  recentFailures?: AgentWorkbenchFailureItem[]
}

export interface AgentWorkbenchCompareChangeItem {
  type: string
  label: string
  leftSummary: string
  rightSummary: string
  direction: string
  severity: 'low' | 'medium' | 'high' | string
  suggestedAction: string
}

export interface AgentArchivedTraceLookupResponse {
  agentType: string
  found: boolean
  artifactType?: string
  artifactPath?: string
  traceId?: string
  archivedAt?: string
  summary?: string
  replayable?: boolean
  trace?: MultiAgentTraceResponse
}

export interface AgentMetadataItem {
  agentType: string
  name: string
  icon: string
  color: string
  description: string
  defaultModel?: string
  defaultTemperature?: number
  defaultMaxContextMessages?: number
  supportsKnowledge: boolean
  supportsTools: boolean
  supportsMultiAgentMode: boolean
  supportsMultiStepRecovery: boolean
  registered: boolean
}

export interface AgentMetadataResponse {
  count: number
  agents: AgentMetadataItem[]
}

export interface AgentToolAuditLog {
  id: string
  userId?: string
  sessionId?: string
  agentType?: string
  toolName?: string
  toolClass?: string
  inputSummary?: string
  outputSummary?: string
  success?: boolean
  errorMessage?: string
  reasonCode?: string
  deniedResource?: string
  latencyMs?: number
  traceId?: string
  createdAt?: string
}

export interface MultiAgentTraceStepResponse {
  stepOrder: number
  stage: string
  agentName: string
  inputSummary?: string
  outputSummary?: string
  promptTokens?: number
  completionTokens?: number
  latencyMs?: number
  success: boolean
  errorMessage?: string
  recoverable?: boolean
  skipped?: boolean
  recoveryAction?: string
  sourceTraceId?: string
  sourceStepOrder?: number
  createdAt?: string
}

export interface MultiAgentTraceResponse {
  traceId: string
  sessionId: string
  userId: string
  agentType: string
  requestSummary?: string
  finalSummary?: string
  status: string
  totalPromptTokens?: number
  totalCompletionTokens?: number
  totalLatencyMs?: number
  stepCount?: number
  errorMessage?: string
  parentTraceId?: string
  recoverySourceTraceId?: string
  recoverySourceStepOrder?: number
  recoveryAction?: string
  createdAt?: string
  updatedAt?: string
  steps?: MultiAgentTraceStepResponse[] | null
}

export interface MultiAgentTraceRecoverRequest {
  stepOrder?: number | null
  action?: 'retry' | 'replay' | 'skip' | string
}

export interface AgentDiagnosticsResponse {
  agentType: string
  accessible: boolean
  toolSecurityEnabled: boolean
  allowedTools: string[]
  allowedConnectors: string[]
  allowedMcpServers: string[]
  enabledConnectors: string[]
  recentMultiTraceCount: number
  availableMcpServerCount: number
  mcpIssueCount: number
  summary: string
}
