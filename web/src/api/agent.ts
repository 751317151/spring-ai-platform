import client, { buildHeaders } from './client'
import type {
  AgentAccessOverviewResponse,
  AgentLogArchiveDetailResponse,
  AgentLogArchivePreviewResponse,
  AgentDiagnosticsResponse,
  AgentArchivedTraceLookupResponse,
  AgentLogCleanupRequest,
  AgentLogCleanupResponse,
  AgentLogLifecycleSummaryResponse,
  AgentMetadataResponse,
  AgentToolAuditLog,
  AgentType,
  AgentWorkbenchSummaryResponse,
  ChatMessage,
  McpServerListResponse,
  MultiAgentTraceRecoverRequest,
  MultiAgentTraceResponse,
  SessionConfig,
  SessionInfo,
  ToolSecurityOverviewResponse
} from './types'

const BASE = '/api/v1/agent'

export function chatStream(
  agentType: AgentType,
  message: string,
  sessionId: string,
  sessionConfig?: SessionConfig | null
): { response: Promise<Response>; abort: () => void } {
  const controller = new AbortController()
  const headers = buildHeaders({
    'X-Session-Id': sessionId
  })

  const response = fetch(`${BASE}/${agentType}/chat/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ message, sessionConfig }),
    signal: controller.signal
  })

  return { response, abort: () => controller.abort() }
}

export function getHistory(agentType: AgentType, sessionId: string): Promise<ChatMessage[]> {
  return client.get(`${BASE}/${agentType}/memory`, {
    headers: { 'X-Session-Id': sessionId }
  })
}

export function clearMemory(agentType: AgentType, sessionId: string): Promise<void> {
  return client.delete(`${BASE}/${agentType}/memory`, {
    headers: { 'X-Session-Id': sessionId }
  })
}

export function getSessions(agentType: AgentType): Promise<SessionInfo[]> {
  return client.get(`${BASE}/${agentType}/sessions`)
}

export function renameSessionTitle(agentType: AgentType, sessionId: string, title: string): Promise<void> {
  return client.post(`${BASE}/${agentType}/sessions/title`, { title }, {
    headers: { 'X-Session-Id': sessionId }
  })
}

export function pinSession(agentType: AgentType, sessionId: string, pinned: boolean): Promise<void> {
  return client.post(`${BASE}/${agentType}/sessions/pin`, { pinned }, {
    headers: { 'X-Session-Id': sessionId }
  })
}

export function archiveSession(agentType: AgentType, sessionId: string, archived: boolean): Promise<void> {
  return client.post(`${BASE}/${agentType}/sessions/archive`, { archived }, {
    headers: { 'X-Session-Id': sessionId }
  })
}

export function deleteSession(agentType: AgentType, sessionId: string): Promise<void> {
  return client.delete(`${BASE}/${agentType}/sessions`, {
    headers: { 'X-Session-Id': sessionId }
  })
}

export function getSessionConfig(agentType: AgentType, sessionId: string): Promise<SessionConfig> {
  return client.get(`${BASE}/${agentType}/sessions/config`, {
    headers: { 'X-Session-Id': sessionId }
  })
}

export function saveSessionConfig(agentType: AgentType, sessionId: string, config: SessionConfig): Promise<void> {
  return client.post(`${BASE}/${agentType}/sessions/config`, config, {
    headers: { 'X-Session-Id': sessionId }
  })
}

export function submitFeedback(responseId: string, feedback: 'up' | 'down', comment?: string): Promise<void> {
  return client.post(`${BASE}/feedback`, { responseId, feedback, comment })
}

export function getMcpServers(): Promise<McpServerListResponse> {
  return client.get(`${BASE}/mcp/servers`)
}

export function getAgentMetadata(): Promise<AgentMetadataResponse> {
  return client.get(`${BASE}/metadata`)
}

export function getMcpServersByAgent(agentType: AgentType): Promise<McpServerListResponse> {
  return client.get(`${BASE}/mcp/servers/${agentType}`)
}

export function getToolSecurityOverview(agentType: AgentType): Promise<ToolSecurityOverviewResponse> {
  return client.get(`${BASE}/tools/security/${agentType}`)
}

export function getAgentAccessOverview(agentType: AgentType): Promise<AgentAccessOverviewResponse> {
  return client.get(`${BASE}/access/${agentType}`)
}

export function getAgentWorkbenchSummary(agentType: AgentType): Promise<AgentWorkbenchSummaryResponse> {
  return client.get(`${BASE}/workbench/${agentType}`)
}

export function compareAgentWorkbench(leftAgent: AgentType, rightAgent: AgentType): Promise<import('./types').AgentWorkbenchCompareResponse> {
  return client.get(`${BASE}/workbench/compare`, {
    params: {
      leftAgent,
      rightAgent
    }
  })
}

export function getAgentLogLifecycleSummary(agentType: AgentType): Promise<AgentLogLifecycleSummaryResponse> {
  return client.get(`${BASE}/logs/lifecycle/${agentType}`)
}

export function getLatestAgentLogArchive(agentType: AgentType): Promise<AgentLogArchiveDetailResponse> {
  return client.get(`${BASE}/logs/lifecycle/${agentType}/archive/latest`)
}

export function previewLatestAgentLogArchive(
  agentType: AgentType,
  artifactType: string,
  limit = 5
): Promise<AgentLogArchivePreviewResponse> {
  return client.get(`${BASE}/logs/lifecycle/${agentType}/archive/latest/preview`, {
    params: {
      artifactType,
      limit
    }
  })
}

export function findLatestArchivedTrace(
  agentType: AgentType,
  traceId: string
): Promise<AgentArchivedTraceLookupResponse> {
  return client.get(`${BASE}/logs/lifecycle/${agentType}/archive/latest/trace`, {
    params: { traceId }
  })
}

export function replayLatestArchivedTrace(
  agentType: AgentType,
  traceId: string
): Promise<MultiAgentTraceResponse> {
  return client.post(`${BASE}/logs/lifecycle/${agentType}/archive/latest/trace/replay`, undefined, {
    params: { traceId }
  })
}

export function cleanupAgentLogs(agentType: AgentType, body?: AgentLogCleanupRequest): Promise<AgentLogCleanupResponse> {
  return client.post(`${BASE}/logs/lifecycle/${agentType}/cleanup`, body || { dryRun: true })
}

export function getToolAuditLogs(
  limit = 20,
  agentType?: AgentType,
  toolName?: string,
  traceId?: string
): Promise<AgentToolAuditLog[]> {
  const params: Record<string, unknown> = { limit }
  if (agentType) params.agentType = agentType
  if (toolName) params.toolName = toolName
  if (traceId) params.traceId = traceId
  return client.get(`${BASE}/tools/audit`, { params })
}

export function getAgentDiagnostics(agentType: AgentType): Promise<AgentDiagnosticsResponse> {
  return client.get(`${BASE}/diagnostics/${agentType}`)
}

export function getMultiAgentTraces(sessionId?: string | null, limit = 20): Promise<MultiAgentTraceResponse[]> {
  return client.get(`${BASE}/multi/traces`, {
    params: {
      sessionId: sessionId || undefined,
      limit
    }
  })
}

export function getMultiAgentTrace(traceId: string): Promise<MultiAgentTraceResponse> {
  return client.get(`${BASE}/multi/traces/${traceId}`)
}

export function recoverMultiAgentTrace(traceId: string, body: MultiAgentTraceRecoverRequest): Promise<MultiAgentTraceResponse> {
  return client.post(`${BASE}/multi/traces/${traceId}/recover`, body)
}
