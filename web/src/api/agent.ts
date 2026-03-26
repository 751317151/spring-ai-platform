import client, { buildHeaders } from './client'
import type { AgentType, ChatMessage, McpServerListResponse, SessionConfig, SessionInfo } from './types'

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
