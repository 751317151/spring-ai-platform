import client, { buildHeaders } from './client'
import type { MonitorOverview, HourlyStat, AgentStat, TopUser, AuditLog, AlertEvent, FailureSample, ModelStat, SlowRequestSample, FeedbackOverview, FeedbackSample, EvidenceFeedbackSample } from './types'

const BASE = '/api/v1/monitor'

export function getOverview(): Promise<MonitorOverview> {
  return client.get(`${BASE}/overview`)
}

export function getByAgent(): Promise<AgentStat[]> {
  return client.get(`${BASE}/by-agent`)
}

export function getAuditLogs(limit = 20, userId?: string): Promise<AuditLog[]> {
  const params: Record<string, unknown> = { limit }
  if (userId) params.userId = userId
  return client.get(`${BASE}/audit-logs`, { params })
}

export function getTokenTopUsers(): Promise<TopUser[]> {
  return client.get(`${BASE}/token-top-users`)
}

export function getHourlyStats(): Promise<HourlyStat[]> {
  return client.get(`${BASE}/hourly-stats`)
}

export function getAlerts(): Promise<{ activeAlerts: number; alerts: AlertEvent[] }> {
  return client.get(`${BASE}/alerts`)
}

export function getByModel(): Promise<ModelStat[]> {
  return client.get(`${BASE}/by-model`)
}

export function getSlowRequests(limit = 10): Promise<SlowRequestSample[]> {
  return client.get(`${BASE}/slow-requests`, { params: { limit } })
}

export function getFailureSamples(limit = 10): Promise<FailureSample[]> {
  return client.get(`${BASE}/failure-samples`, { params: { limit } })
}

export function getFeedbackOverview(): Promise<FeedbackOverview> {
  return client.get(`${BASE}/feedback/overview`)
}

export function getRecentFeedback(limit = 10): Promise<FeedbackSample[]> {
  return client.get(`${BASE}/feedback/recent`, { params: { limit } })
}

export function getRecentEvidenceFeedback(limit = 10): Promise<EvidenceFeedbackSample[]> {
  return client.get(`${BASE}/feedback/evidence`, { params: { limit } })
}

export async function downloadExport(
  type: 'slow-requests' | 'failure-samples' | 'feedback' | 'evidence-feedback' | 'top-users',
  limit?: number
): Promise<Blob> {
  const params = new URLSearchParams()
  if (limit != null) {
    params.set('limit', String(limit))
  }
  const query = params.toString()
  const response = await fetch(`${BASE}/export/${type}${query ? `?${query}` : ''}`, {
    headers: buildHeaders({ Accept: 'text/csv' })
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.blob()
}

export function toCsvBlob(lines: string[][]): Blob {
  const csv = lines
    .map((line) => line.map((item) => `"${String(item ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\n')
  return new Blob([csv], { type: 'text/csv;charset=utf-8' })
}
