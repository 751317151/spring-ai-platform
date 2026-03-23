import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as monitorApi from '@/api/monitor'
import * as gatewayApi from '@/api/gateway'
import type { MonitorOverview, HourlyStat, AgentStat, TopUser, AlertEvent, AuditLog, ModelInfo, FailureSample, ModelStat, SlowRequestSample, FeedbackOverview, FeedbackSample, EvidenceFeedbackSample } from '@/api/types'

export const useMonitorStore = defineStore('monitor', () => {
  const overview = ref<MonitorOverview | null>(null)
  const hourlyStats = ref<HourlyStat[]>([])
  const agentStats = ref<AgentStat[]>([])
  const topUsers = ref<TopUser[]>([])
  const alerts = ref<AlertEvent[]>([])
  const auditLogs = ref<AuditLog[]>([])
  const modelStats = ref<ModelStat[]>([])
  const slowRequests = ref<SlowRequestSample[]>([])
  const failureSamples = ref<FailureSample[]>([])
  const feedbackOverview = ref<FeedbackOverview | null>(null)
  const recentFeedback = ref<FeedbackSample[]>([])
  const recentEvidenceFeedback = ref<EvidenceFeedbackSample[]>([])
  const models = ref<ModelInfo[]>([])
  const gatewayLoadBalanceStrategy = ref('')
  const gatewaySceneRoutes = ref<Record<string, string[]>>({})
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
      const [ov, users, alertsData, hourly, agents, modelsByUsage, slow, failures, feedbackStats, feedbackList, evidenceFeedbackList, gatewayModels] = await Promise.all([
        monitorApi.getOverview().catch(() => null),
        monitorApi.getTokenTopUsers().catch(() => []),
        monitorApi.getAlerts().catch(() => ({ alerts: [] })),
        monitorApi.getHourlyStats().catch(() => []),
        monitorApi.getByAgent().catch(() => []),
        monitorApi.getByModel().catch(() => []),
        monitorApi.getSlowRequests().catch(() => []),
        monitorApi.getFailureSamples().catch(() => []),
        monitorApi.getFeedbackOverview().catch(() => null),
        monitorApi.getRecentFeedback().catch(() => []),
        monitorApi.getRecentEvidenceFeedback().catch(() => []),
        gatewayApi.getModels().catch(() => null)
      ])
      if (ov) overview.value = ov
      topUsers.value = users
      alerts.value = alertsData?.alerts || []
      hourlyStats.value = hourly
      agentStats.value = agents
      modelStats.value = modelsByUsage
      slowRequests.value = slow
      failureSamples.value = failures
      feedbackOverview.value = feedbackStats
      recentFeedback.value = feedbackList
      recentEvidenceFeedback.value = evidenceFeedbackList
      if (gatewayModels) {
        models.value = gatewayModels.models || []
        gatewayLoadBalanceStrategy.value = gatewayModels.loadBalanceStrategy || ''
        gatewaySceneRoutes.value = gatewayModels.sceneRoutes || {}
      }
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

  async function exportCsv(
    type: 'slow-requests' | 'failure-samples' | 'feedback' | 'top-users',
    fileName: string,
    limit?: number
  ) {
    const blob = await monitorApi.downloadExport(type, limit)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    a.click()
    URL.revokeObjectURL(url)
  }

  async function exportGatewayModelsCsv(fileName: string) {
    const rows = [
      ['modelId', 'name', 'provider', 'enabled', 'weight', 'totalCalls', 'successCalls', 'successRate', 'avgLatencyMs'],
      ...models.value.map((item) => [
        item.id,
        item.name,
        item.provider,
        item.enabled,
        item.weight,
        item.totalCalls,
        item.successCalls,
        item.successRate,
        item.avgLatencyMs
      ])
    ]
    const blob = monitorApi.toCsvBlob(rows)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    a.click()
    URL.revokeObjectURL(url)
  }

  return {
    overview, hourlyStats, agentStats, topUsers, alerts, auditLogs, modelStats, slowRequests, failureSamples, feedbackOverview, recentFeedback, recentEvidenceFeedback, models, gatewayLoadBalanceStrategy, gatewaySceneRoutes,
    loadDashboardData, loadMonitorData, startRealtimeUpdates, stopRealtimeUpdates, exportCsv, exportGatewayModelsCsv
  }
})
