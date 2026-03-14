import client from './client'
import { buildHeaders } from './client'
import type { AgentType, ChatMessage, SessionInfo } from './types'

const BASE = '/api/v1/agent'

export function chatStream(
  agentType: AgentType,
  message: string,
  userId: string,
  sessionId: string
): { response: Promise<Response>; abort: () => void } {
  const controller = new AbortController()
  const headers = buildHeaders({
    'X-User-Id': userId,
    'X-Session-Id': sessionId
  })

  const response = fetch(`${BASE}/${agentType}/chat/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ message }),
    signal: controller.signal
  })

  return { response, abort: () => controller.abort() }
}

export function getHistory(agentType: AgentType, userId: string, sessionId: string): Promise<ChatMessage[]> {
  return client.get(`${BASE}/${agentType}/memory`, {
    headers: { 'X-User-Id': userId, 'X-Session-Id': sessionId }
  })
}

export function clearMemory(agentType: AgentType, userId: string, sessionId: string): Promise<void> {
  return client.delete(`${BASE}/${agentType}/memory`, {
    headers: { 'X-User-Id': userId, 'X-Session-Id': sessionId }
  })
}

export function getSessions(agentType: AgentType, userId: string): Promise<SessionInfo[]> {
  return client.get(`${BASE}/${agentType}/sessions`, {
    headers: { 'X-User-Id': userId }
  })
}
