import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as monitorApi from '@/api/monitor'
import * as gatewayApi from '@/api/gateway'
import type { MonitorOverview, HourlyStat, AgentStat, TopUser, AlertEvent, AuditLog, ModelInfo } from '@/api/types'

export const useMonitorStore = defineStore('monitor', () => {
  const overview = ref<MonitorOverview | null>(null)
  const hourlyStats = ref<HourlyStat[]>([])
  const agentStats = ref<AgentStat[]>([])
  const topUsers = ref<TopUser[]>([])
  const alerts = ref<AlertEvent[]>([])
  const auditLogs = ref<AuditLog[]>([])
  const models = ref<ModelInfo[]>([])
  let intervalId: ReturnType<typeof setInterval> | null = null

  async function loadDashboardData() {
    try {
      const [ov, hourly, agents, modelsData, logs] = await Promise.all([
        monitorApi.getOverview().catch(() => null),
        monitorApi.getHourlyStats().catch(() => []),
        monitorApi.getByAgent().catch(() => []),
        gatewayApi.getModels().catch(() => null),
        monitorApi.getAuditLogs(5).catch(() => [])
      ])
      if (ov) overview.value = ov
      hourlyStats.value = hourly
      agentStats.value = agents
      if (modelsData) models.value = modelsData.models || []
      auditLogs.value = logs
    } catch {
      // ignore
    }
  }

  async function loadMonitorData() {
    try {
      const [ov, users, alertsData, hourly] = await Promise.all([
        monitorApi.getOverview().catch(() => null),
        monitorApi.getTokenTopUsers().catch(() => []),
        monitorApi.getAlerts().catch(() => ({ alerts: [] })),
        monitorApi.getHourlyStats().catch(() => [])
      ])
      if (ov) overview.value = ov
      topUsers.value = users
      alerts.value = alertsData?.alerts || []
      hourlyStats.value = hourly
    } catch {
      // ignore
    }
  }

  function startRealtimeUpdates() {
    stopRealtimeUpdates()
    intervalId = setInterval(async () => {
      try {
        const ov = await monitorApi.getOverview()
        overview.value = ov
      } catch {
        // ignore
      }
    }, 5000)
  }

  function stopRealtimeUpdates() {
    if (intervalId) {
      clearInterval(intervalId)
      intervalId = null
    }
  }

  return {
    overview, hourlyStats, agentStats, topUsers, alerts, auditLogs, models,
    loadDashboardData, loadMonitorData, startRealtimeUpdates, stopRealtimeUpdates
  }
})
