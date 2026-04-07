import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as gatewayApi from '@/api/gateway'
import * as monitorApi from '@/api/monitor'
import { ENABLE_SCREEN_MOCK } from '@/config/app-config'
import { useRuntimeStore } from './runtime'
import type {
  AlertEvent,
  AlertWorkflowHistory,
  AgentStat,
  AuditLog,
  EvidenceFeedbackSample,
  FailureSample,
  FeedbackOverview,
  FeedbackSample,
  GatewayModelsResponse,
  HourlyStat,
  ModelInfo,
  ModelStat,
  MonitorOverview,
  MonitorScreenSnapshot,
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

function buildMockScreenSnapshot(): MonitorScreenSnapshot {
  const now = new Date()
  const currentHour = now.getHours()
  const hourlyStats: HourlyStat[] = Array.from({ length: 24 }, (_, index) => {
    const hour = (currentHour - 23 + index + 24) % 24
    const total = 120 + ((index * 37) % 180)
    const errors = Math.max(3, Math.round(total * (0.04 + (index % 5) * 0.008)))
    return {
      hour,
      total,
      errors,
      avg_latency: 680 + (index % 6) * 55,
      p50: 420 + (index % 5) * 32,
      p95: 1080 + (index % 4) * 95
    }
  })

  const agentStats: AgentStat[] = [
    { agent_type: 'multi', count: 328, avg_latency: 1320, errors: 16 },
    { agent_type: 'rd', count: 286, avg_latency: 910, errors: 9 },
    { agent_type: 'data-analysis', count: 223, avg_latency: 1180, errors: 7 },
    { agent_type: 'search', count: 198, avg_latency: 760, errors: 6 },
    { agent_type: 'finance', count: 154, avg_latency: 840, errors: 4 },
    { agent_type: 'hr', count: 127, avg_latency: 690, errors: 3 }
  ]

  const topUsers: TopUser[] = [
    { user_id: 'admin', agent_type: 'multi', calls: 96, avg_latency: 1220 },
    { user_id: 'rd001', agent_type: 'rd', calls: 82, avg_latency: 910 },
    { user_id: 'finance01', agent_type: 'finance', calls: 65, avg_latency: 840 },
    { user_id: 'sales01', agent_type: 'search', calls: 61, avg_latency: 770 },
    { user_id: 'hr01', agent_type: 'hr', calls: 43, avg_latency: 680 },
    { user_id: 'ops01', agent_type: 'multi', calls: 38, avg_latency: 1350 }
  ]

  const regionHeat = [
    { province: '北京市', city: '北京市', regionName: '北京 / 北京', calls: 162, errors: 8, avgLatencyMs: 820, successRate: 0.951 },
    { province: '上海市', city: '上海市', regionName: '上海 / 上海', calls: 145, errors: 6, avgLatencyMs: 790, successRate: 0.959 },
    { province: '广东省', city: '深圳市', regionName: '广东 / 深圳', calls: 188, errors: 11, avgLatencyMs: 870, successRate: 0.941 },
    { province: '广东省', city: '广州市', regionName: '广东 / 广州', calls: 133, errors: 5, avgLatencyMs: 760, successRate: 0.962 },
    { province: '浙江省', city: '杭州市', regionName: '浙江 / 杭州', calls: 126, errors: 4, avgLatencyMs: 730, successRate: 0.968 },
    { province: '江苏省', city: '南京市', regionName: '江苏 / 南京', calls: 118, errors: 5, avgLatencyMs: 750, successRate: 0.958 },
    { province: '四川省', city: '成都市', regionName: '四川 / 成都', calls: 109, errors: 6, avgLatencyMs: 890, successRate: 0.945 },
    { province: '湖北省', city: '武汉市', regionName: '湖北 / 武汉', calls: 94, errors: 4, avgLatencyMs: 810, successRate: 0.957 },
    { province: '陕西省', city: '西安市', regionName: '陕西 / 西安', calls: 88, errors: 3, avgLatencyMs: 845, successRate: 0.966 },
    { province: '重庆市', city: '重庆市', regionName: '重庆 / 重庆', calls: 79, errors: 5, avgLatencyMs: 930, successRate: 0.937 }
  ]

  const failureSamples: FailureSample[] = [
    {
      id: 'mock-fail-1',
      user_id: 'admin',
      agent_type: 'multi',
      model_id: 'qwen-max',
      error_message: '下游工具响应超时，已触发自动重试',
      latency_ms: 3821,
      session_id: 'screen-mock-session-1',
      trace_id: 'screen-mock-trace-1',
      created_at: now.toISOString()
    },
    {
      id: 'mock-fail-2',
      user_id: 'rd001',
      agent_type: 'rd',
      model_id: 'deepseek-chat',
      error_message: '代码检索结果为空，建议补充仓库索引',
      latency_ms: 2410,
      session_id: 'screen-mock-session-2',
      trace_id: 'screen-mock-trace-2',
      created_at: new Date(now.getTime() - 8 * 60 * 1000).toISOString()
    },
    {
      id: 'mock-fail-3',
      user_id: 'finance01',
      agent_type: 'data-analysis',
      model_id: 'qwen-plus',
      error_message: 'SQL 执行计划扫描行数过大，已阻断执行',
      latency_ms: 1978,
      session_id: 'screen-mock-session-3',
      trace_id: 'screen-mock-trace-3',
      created_at: new Date(now.getTime() - 19 * 60 * 1000).toISOString()
    }
  ]

  const alerts: AlertEvent[] = [
    {
      level: 'WARN',
      type: 'TOOL_TIMEOUT',
      message: '多智能体链路近 10 分钟工具超时率上升',
      time: now.toISOString(),
      source: 'agent-service',
      status: 'firing',
      fingerprint: 'mock-alert-1'
    },
    {
      level: 'INFO',
      type: 'MODEL_DEGRADED',
      message: 'qwen-max 延迟波动，已切换到降级观察状态',
      time: new Date(now.getTime() - 15 * 60 * 1000).toISOString(),
      source: 'gateway-service',
      status: 'watching',
      fingerprint: 'mock-alert-2'
    }
  ]

  return {
    overview: {
      totalRequests: 2856,
      errorRequests: 96,
      successRate: 0.9664,
      avgLatencyMs: 812,
      p95LatencyMs: 1680,
      p99LatencyMs: 2410,
      totalPromptTokens: 864200,
      totalCompletionTokens: 498600,
      totalTokens: 1362800,
      activeRequests: 29
    },
    hourlyStats,
    agentStats,
    topUsers,
    regionHeat,
    failureSamples,
    feedbackOverview: {
      totalCount: 426,
      positiveCount: 379,
      negativeCount: 47,
      positiveRate: 0.8897
    },
    alerts: {
      activeAlerts: alerts.filter((item) => ['WARN', 'ERROR', 'CRITICAL'].includes(item.level)).length,
      alerts
    }
  }
}

function buildMockGatewayModels(): GatewayModelsResponse {
  return {
    count: 4,
    loadBalanceStrategy: 'round-robin',
    sceneRoutes: {
      default: ['qwen-max', 'deepseek-chat'],
      multi: ['qwen-max'],
      search: ['qwen-plus'],
      'data-analysis': ['deepseek-chat']
    },
    models: [
      {
        id: 'qwen-max',
        name: 'Qwen Max',
        provider: 'alibaba',
        enabled: true,
        weight: 10,
        capabilities: ['chat', 'reasoning'],
        healthStatus: 'healthy',
        totalCalls: 1280,
        successCalls: 1248,
        avgLatencyMs: 860,
        successRate: 97.5
      },
      {
        id: 'deepseek-chat',
        name: 'DeepSeek Chat',
        provider: 'deepseek',
        enabled: true,
        weight: 8,
        capabilities: ['chat', 'code'],
        healthStatus: 'healthy',
        totalCalls: 942,
        successCalls: 919,
        avgLatencyMs: 920,
        successRate: 97.6
      },
      {
        id: 'qwen-plus',
        name: 'Qwen Plus',
        provider: 'alibaba',
        enabled: true,
        weight: 6,
        capabilities: ['chat', 'search'],
        healthStatus: 'degraded',
        totalCalls: 615,
        successCalls: 580,
        avgLatencyMs: 1180,
        successRate: 94.3
      },
      {
        id: 'embedding-v1',
        name: 'Embedding V1',
        provider: 'alibaba',
        enabled: true,
        weight: 4,
        capabilities: ['embedding'],
        healthStatus: 'healthy',
        totalCalls: 386,
        successCalls: 384,
        avgLatencyMs: 210,
        successRate: 99.5
      }
    ]
  }
}

export const useMonitorStore = defineStore('monitor', () => {
  const runtimeStore = useRuntimeStore()
  const overview = ref<MonitorOverview | null>(null)
  const screenSnapshot = ref<MonitorScreenSnapshot | null>(null)
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

  function applyScreenSnapshot(snapshot: MonitorScreenSnapshot | null) {
    screenSnapshot.value = snapshot
    overview.value = snapshot?.overview ?? null
    hourlyStats.value = snapshot?.hourlyStats || []
    agentStats.value = snapshot?.agentStats || []
    topUsers.value = snapshot?.topUsers || []
    failureSamples.value = snapshot?.failureSamples || []
    feedbackOverview.value = snapshot?.feedbackOverview || null
    alerts.value = snapshot?.alerts?.alerts || []
  }

  function applyGatewayModels(response: GatewayModelsResponse | null) {
    models.value = response?.models || []
    gatewayLoadBalanceStrategy.value = response?.loadBalanceStrategy || ''
    gatewaySceneRoutes.value = response?.sceneRoutes || {}
  }

  function getToolAuditsSafely(limit = 20, userId?: string, agentType?: string, toolName?: string) {
    try {
      const toolAuditFn = (monitorApi as unknown as Record<string, unknown>).getToolAudits
      return typeof toolAuditFn === 'function'
        ? (toolAuditFn as (
            limit?: number,
            userId?: string,
            agentType?: string,
            toolName?: string
          ) => Promise<ToolAudit[]>)(limit, userId, agentType, toolName)
        : Promise.resolve([])
    } catch {
      return Promise.resolve([])
    }
  }

  async function loadDashboardData() {
    if (runtimeStore.demoMode) {
      const snapshot = buildMockScreenSnapshot()
      applyScreenSnapshot(snapshot)
      applyGatewayModels(buildMockGatewayModels())
      modelStats.value = snapshot.agentStats.map((item) => ({
        model_id: `${item.agent_type}-model`,
        count: item.count,
        avg_latency: item.avg_latency,
        errors: item.errors
      }))
      auditLogs.value = snapshot.failureSamples.map((item) => ({
        id: item.id,
        user_id: item.user_id,
        agent_type: item.agent_type,
        model_id: item.model_id,
        error_message: item.error_message,
        session_id: item.session_id,
        trace_id: item.trace_id,
        latency_ms: item.latency_ms,
        success: false,
        created_at: item.created_at
      }))
      toolAudits.value = snapshot.failureSamples.map((item, index) => ({
        id: `tool-${index + 1}`,
        user_id: item.user_id,
        session_id: item.session_id,
        agent_type: item.agent_type,
        tool_name: ['webSearch', 'queryKnowledgeBase', 'routeDecision'][index] || 'mockTool',
        tool_class: 'GuestMockTool',
        input_summary: '游客模式演示输入',
        output_summary: '游客模式演示输出',
        success: index % 2 === 0,
        error_message: index % 2 === 0 ? '' : '演示超时',
        latency_ms: 260 + index * 90,
        trace_id: item.trace_id,
        created_at: item.created_at
      }))
      return
    }

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

  async function loadScreenData() {
    loading.value = true
    error.value = ''
    try {
      if (ENABLE_SCREEN_MOCK || runtimeStore.demoMode) {
        applyScreenSnapshot(buildMockScreenSnapshot())
        applyGatewayModels(buildMockGatewayModels())
        return
      }

      const [screen, gatewayModelsResponse] = await Promise.all([
        monitorApi.getScreenSnapshot(),
        gatewayApi.getModels().catch(() => null)
      ])
      applyScreenSnapshot(screen)
      applyGatewayModels(gatewayModelsResponse)
    } catch (err) {
      error.value = err instanceof Error ? err.message : '大屏数据加载失败'
    } finally {
      loading.value = false
    }
  }

  async function loadMonitorData() {
    loading.value = true
    error.value = ''
    try {
      if (runtimeStore.demoMode) {
        const snapshot = buildMockScreenSnapshot()
        applyScreenSnapshot(snapshot)
        applyGatewayModels(buildMockGatewayModels())
        modelStats.value = snapshot.agentStats.map((item) => ({
          model_id: `${item.agent_type}-model`,
          count: item.count,
          avg_latency: item.avg_latency,
          errors: item.errors
        }))
        toolAudits.value = snapshot.failureSamples.map((item, index) => ({
          id: `tool-${index + 1}`,
          user_id: item.user_id,
          session_id: item.session_id,
          agent_type: item.agent_type,
          tool_name: ['webSearch', 'queryKnowledgeBase', 'routeDecision'][index] || 'mockTool',
          tool_class: 'GuestMockTool',
          input_summary: '游客模式演示输入',
          output_summary: '游客模式演示输出',
          success: index % 2 === 0,
          error_message: index % 2 === 0 ? '' : '演示超时',
          latency_ms: 260 + index * 90,
          trace_id: item.trace_id,
          created_at: item.created_at
        }))
        slowRequests.value = snapshot.failureSamples.map((item) => ({
          id: item.id,
          trace_id: item.trace_id || '',
          user_id: item.user_id,
          agent_type: item.agent_type,
          model_id: item.model_id,
          latency_ms: item.latency_ms,
          success: false,
          created_at: item.created_at
        }))
        recentFeedback.value = []
        recentEvidenceFeedback.value = []
        return
      }

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
        gatewayModelsResponse
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
      models.value = gatewayModelsResponse?.models || []
      gatewayLoadBalanceStrategy.value = gatewayModelsResponse?.loadBalanceStrategy || ''
      gatewaySceneRoutes.value = gatewayModelsResponse?.sceneRoutes || {}
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

  async function updateAlertWorkflow(
    fingerprint: string,
    workflowStatus: string,
    workflowNote = '',
    silencedUntil = ''
  ) {
    await monitorApi.updateAlertWorkflow(fingerprint, workflowStatus, workflowNote, silencedUntil)
    alerts.value = alerts.value.map((item) =>
      item.fingerprint === fingerprint
        ? {
            ...item,
            workflowStatus,
            workflowNote,
            workflowUpdatedAt: new Date().toISOString(),
            silencedUntil: silencedUntil || undefined
          }
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
    screenSnapshot,
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
    loadScreenData,
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
