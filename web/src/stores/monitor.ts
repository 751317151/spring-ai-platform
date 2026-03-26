import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as gatewayApi from '@/api/gateway'
import * as monitorApi from '@/api/monitor'
import type {
  AlertEvent,
  AlertWorkflowHistory,
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
  ToolAudit,
  TopUser
} from '@/api/types'

type ExportType =
  | 'slow-requests'
  | 'failure-samples'
  | 'feedback'
  | 'evidence-feedback'
  | 'top-users'
  | 'gateway-models'

export const useMonitorStore = defineStore('monitor', () => {
  const overview = ref<MonitorOverview | null>(null)
  const hourlyStats = ref<HourlyStat[]>([])
  const agentStats = ref<AgentStat[]>([])
  const topUsers = ref<TopUser[]>([])
  const alerts = ref<AlertEvent[]>([])
  const alertHistories = ref<Record<string, AlertWorkflowHistory[]>>({})
  const auditLogs = ref<AuditLog[]>([])
  const modelStats = ref<ModelStat[]>([])
  const toolAudits = ref<ToolAudit[]>([])
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

  function getToolAuditsSafely(limit = 20, userId?: string, agentType?: string, toolName?: string) {
    try {
      const toolAuditFn = (monitorApi as unknown as Record<string, unknown>).getToolAudits
      return typeof toolAuditFn === 'function'
        ? (toolAuditFn as (limit?: number, userId?: string, agentType?: string, toolName?: string) => Promise<ToolAudit[]>)(limit, userId, agentType, toolName)
        : Promise.resolve([])
    } catch {
      return Promise.resolve([])
    }
  }

  async function loadDashboardData() {
    try {
      const [ov, hourly, agents, modelsData, logs, toolAuditRows] = await Promise.all([
        monitorApi.getOverview().catch(() => null),
        monitorApi.getHourlyStats().catch(() => []),
        monitorApi.getByAgent().catch(() => []),
        gatewayApi.getModels().catch(() => null),
        monitorApi.getAuditLogs(5).catch(() => []),
        getToolAuditsSafely(5).catch(() => [])
      ])
      if (ov) overview.value = ov
      hourlyStats.value = hourly
      agentStats.value = agents
      if (modelsData) models.value = modelsData.models || []
      auditLogs.value = logs
      toolAudits.value = toolAuditRows
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
        toolAuditRows,
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
        getToolAuditsSafely(),
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
      toolAudits.value = toolAuditRows
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

  async function updateAlertWorkflow(fingerprint: string, workflowStatus: string, workflowNote = '', silencedUntil = '') {
    await monitorApi.updateAlertWorkflow(fingerprint, workflowStatus, workflowNote, silencedUntil)
    alerts.value = alerts.value.map((item) =>
      item.fingerprint === fingerprint
        ? { ...item, workflowStatus, workflowNote, workflowUpdatedAt: new Date().toISOString(), silencedUntil: silencedUntil || undefined }
        : item
    )
    if (alertHistories.value[fingerprint]) {
      await loadAlertWorkflowHistory(fingerprint)
    }
  }

  async function loadAlertWorkflowHistory(fingerprint: string, limit = 10) {
    const rows = await monitorApi.getAlertWorkflowHistory(fingerprint, limit)
    alertHistories.value = {
      ...alertHistories.value,
      [fingerprint]: rows
    }
    return rows
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
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = fileName
      anchor.click()
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
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = fileName
      anchor.click()
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
    alertHistories,
    auditLogs,
    modelStats,
    toolAudits,
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
    updateAlertWorkflow,
    loadAlertWorkflowHistory,
    exportCsv,
    exportGatewayModelsCsv
  }
})
