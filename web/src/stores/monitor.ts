import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as monitorApi from '@/api/monitor'
import * as gatewayApi from '@/api/gateway'
import type {
  AlertEvent,
  AgentStat,
  AuditLog,
  EvidenceFeedbackSample,
  FailureSample,
  FeedbackOverview,
  FeedbackSample,
  HourlyStat,
  ModelInfo,
  ModelStat,
  MonitorOverview,
  SlowRequestSample,
  TopUser
} from '@/api/types'

type ExportType = 'slow-requests' | 'failure-samples' | 'feedback' | 'evidence-feedback' | 'top-users' | 'gateway-models'

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
  const loading = ref(false)
  const error = ref('')
  const exportingType = ref<ExportType | ''>('')
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
    loading.value = true
    error.value = ''
    try {
      const [
        ov,
        users,
        alertsData,
        hourly,
        agents,
        modelsByUsage,
        slow,
        failures,
        feedbackStats,
        feedbackList,
        evidenceFeedbackList,
        gatewayModels
      ] = await Promise.all([
        monitorApi.getOverview(),
        monitorApi.getTokenTopUsers(),
        monitorApi.getAlerts(),
        monitorApi.getHourlyStats(),
        monitorApi.getByAgent(),
        monitorApi.getByModel(),
        monitorApi.getSlowRequests(),
        monitorApi.getFailureSamples(),
        monitorApi.getFeedbackOverview(),
        monitorApi.getRecentFeedback(),
        monitorApi.getRecentEvidenceFeedback(),
        gatewayApi.getModels()
      ])
      overview.value = ov
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
      models.value = gatewayModels?.models || []
      gatewayLoadBalanceStrategy.value = gatewayModels?.loadBalanceStrategy || ''
      gatewaySceneRoutes.value = gatewayModels?.sceneRoutes || {}
    } catch (err) {
      error.value = err instanceof Error ? err.message : '监控数据加载失败'
    } finally {
      loading.value = false
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

  function isExporting(type: ExportType) {
    return exportingType.value === type
  }

  async function exportCsv(
    type: Exclude<ExportType, 'gateway-models'>,
    fileName: string,
    limit?: number
  ) {
    exportingType.value = type
    try {
      const blob = await monitorApi.downloadExport(type, limit)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName
      a.click()
      URL.revokeObjectURL(url)
    } finally {
      exportingType.value = ''
    }
  }

  async function exportGatewayModelsCsv(fileName: string) {
    exportingType.value = 'gateway-models'
    try {
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
    } finally {
      exportingType.value = ''
    }
  }

  return {
    overview,
    hourlyStats,
    agentStats,
    topUsers,
    alerts,
    auditLogs,
    modelStats,
    slowRequests,
    failureSamples,
    feedbackOverview,
    recentFeedback,
    recentEvidenceFeedback,
    models,
    gatewayLoadBalanceStrategy,
    gatewaySceneRoutes,
    loading,
    error,
    exportingType,
    loadDashboardData,
    loadMonitorData,
    startRealtimeUpdates,
    stopRealtimeUpdates,
    isExporting,
    exportCsv,
    exportGatewayModelsCsv
  }
})
