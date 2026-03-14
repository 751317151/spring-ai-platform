import client from './client'
import type { MonitorOverview, HourlyStat, AgentStat, TopUser, AuditLog, AlertEvent } from './types'

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
