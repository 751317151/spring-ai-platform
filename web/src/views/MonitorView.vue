<template>
  <div class="space-monitor">
    <div class="page-hero">
      <div>
        <div class="eyebrow">运行观测</div>
        <div class="page-title">运行监控</div>
        <div class="page-subtitle">聚合请求、异常、Trace、工具审计与反馈数据，方便快速定位问题与判断优化优先级。</div>
      </div>
      <div class="page-hero-actions">
        <div class="range-switch segmented-switch">
          <button
            v-for="item in rangeOptions"
            :key="item.value"
            class="range-chip segmented-chip"
            :class="{ active: selectedRange === item.value }"
            @click="applyRange(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
        <button class="btn btn-ghost" :disabled="monitorStore.loading" @click="refreshPage">
          {{ monitorStore.loading ? '刷新中...' : '刷新监控' }}
        </button>
      </div>
    </div>

    <EmptyState
      v-if="monitorStore.error"
      icon="!"
      badge="监控状态"
      title="监控数据加载失败"
      :description="monitorStore.error"
      action-text="重试"
      @action="refreshPage"
    />

    <template v-else>
      <div class="metrics-grid section-gap">
        <MetricCard label="成功率" color="green">
          <template #default>
            {{ successRateText }}<span class="metric-unit">%</span>
          </template>
        </MetricCard>
        <MetricCard label="P95 延迟" color="blue">
          <template #default>
            {{ overview?.p95LatencyMs ?? '--' }}<span class="metric-unit">ms</span>
          </template>
        </MetricCard>
        <MetricCard label="总调用量" :value="overview?.totalRequests ?? '--'" :sub="`活跃请求 ${overview?.activeRequests ?? 0}`" color="amber" />
        <MetricCard label="总 Token" :value="formatTokens(overview?.totalTokens ?? 0)" sub="Prompt + Completion" color="purple" />
      </div>

      <div class="summary-grid section-gap">
        <div class="card summary-card">
          <div class="summary-label">风险最高的助手</div>
          <div class="summary-value">{{ summaryRisk.title }}</div>
          <div class="summary-sub">{{ summaryRisk.sub }}</div>
        </div>
        <div class="card summary-card">
          <div class="summary-label">当前最慢请求</div>
          <div class="summary-value">{{ summarySlow.title }}</div>
          <div class="summary-sub">{{ summarySlow.sub }}</div>
        </div>
        <div class="card summary-card">
          <div class="summary-label">工具调用概况</div>
          <div class="summary-value">{{ filteredToolAudits.length }} 条</div>
          <div class="summary-sub">{{ toolAuditSummary }}</div>
        </div>
      </div>

      <div class="card section-gap context-card">
        <div class="card-header">
          <div>
            <div class="card-title">筛选上下文</div>
            <div class="card-subtitle">按时间、用户、助手和 Trace 过滤数据，慢请求、失败样本和工具审计会同步联动。</div>
          </div>
          <button class="btn btn-ghost btn-sm" :disabled="!hasContextFilter" @click="clearContextFilter">清空筛选</button>
        </div>
        <div class="context-grid">
          <div class="context-item">
            <span class="context-label">时间范围</span>
            <span class="context-value">{{ selectedRangeLabel }}</span>
          </div>
          <div class="context-item">
            <span class="context-label">用户</span>
            <span class="context-value">{{ selectedUserId || '全部用户' }}</span>
          </div>
          <div class="context-item">
            <span class="context-label">助手</span>
            <span class="context-value">{{ selectedAgent ? agentLabel(selectedAgent) : '全部助手' }}</span>
          </div>
          <div class="context-item">
            <span class="context-label">Trace</span>
            <span class="context-value">{{ selectedTraceId || '未选择' }}</span>
          </div>
        </div>
      </div>

      <div v-if="selectedTraceId" class="card section-gap trace-detail-panel">
        <div class="card-header">
          <div>
            <div class="card-title">Trace 详情</div>
            <div class="card-subtitle">查看输入、输出、异常与工具调用明细，适合排查权限、工具执行和上下游链路问题。</div>
          </div>
          <button class="btn btn-ghost btn-sm" @click="clearTraceFilter">关闭详情</button>
        </div>

        <div v-if="traceLoading" class="trace-detail-loading">正在加载 Trace 详情...</div>
        <EmptyState
          v-else-if="traceError"
          icon="T"
          title="Trace 详情加载失败"
          :description="traceError"
          action-text="重试"
          variant="compact"
          align="left"
          @action="loadTraceDetail(selectedTraceId)"
        />
        <template v-else-if="traceDetail">
          <div class="trace-meta-grid">
            <div class="trace-meta-item"><span class="trace-meta-label">请求 ID</span><span class="trace-meta-value">{{ traceDetail.id }}</span></div>
            <div class="trace-meta-item"><span class="trace-meta-label">用户</span><span class="trace-meta-value">{{ traceDetail.user_id || '-' }}</span></div>
            <div class="trace-meta-item"><span class="trace-meta-label">助手</span><span class="trace-meta-value">{{ traceDetail.agent_type ? agentLabel(traceDetail.agent_type) : '-' }}</span></div>
            <div class="trace-meta-item"><span class="trace-meta-label">模型</span><span class="trace-meta-value">{{ traceDetail.model_id || '-' }}</span></div>
            <div class="trace-meta-item">
              <span class="trace-meta-label">状态</span>
              <span class="trace-meta-value" :class="traceDetail.success ? 'trace-ok' : 'trace-error'">{{ traceDetail.success ? '成功' : '失败' }}</span>
            </div>
            <div class="trace-meta-item"><span class="trace-meta-label">耗时</span><span class="trace-meta-value">{{ traceDetail.latency_ms }}ms</span></div>
          </div>

          <div v-if="tracePhases.length" class="trace-block section-top">
            <div class="trace-block-title">阶段耗时归因</div>
            <div class="trace-phase-list">
              <div v-for="phase in tracePhases" :key="phase.key" class="trace-phase-item">
                <div class="trace-phase-head">
                  <div>
                    <div class="trace-phase-name">
                      {{ phase.label }}
                      <span v-if="phase.estimated" class="trace-phase-badge">估算</span>
                    </div>
                    <div class="trace-phase-desc">{{ phase.description || '用于快速定位链路瓶颈。' }}</div>
                  </div>
                  <div class="trace-phase-metrics">
                    <strong>{{ phase.latency_ms }}ms</strong>
                    <span>{{ phase.share.toFixed(1) }}%</span>
                  </div>
                </div>
                <div class="trace-phase-bar">
                  <div class="trace-phase-bar-inner" :style="{ width: `${Math.max(phase.share, 4)}%` }"></div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="traceDetail.error_message" class="trace-block trace-block-error">
            <div class="trace-block-title">错误信息</div>
            <pre class="trace-block-content">{{ traceDetail.error_message }}</pre>
          </div>

          <div class="trace-content-grid">
            <div class="trace-block">
              <div class="trace-block-title">用户输入</div>
              <pre class="trace-block-content">{{ traceDetail.user_message || '无' }}</pre>
            </div>
            <div class="trace-block">
              <div class="trace-block-title">模型输出</div>
              <pre class="trace-block-content">{{ traceDetail.ai_response || '无' }}</pre>
            </div>
          </div>

          <div class="trace-block section-top">
            <div class="trace-block-title">工具执行明细</div>
            <table class="compact-table">
              <thead><tr><th>工具</th><th>状态</th><th>耗时</th><th>输入摘要</th><th>输出摘要</th></tr></thead>
              <tbody>
                <tr v-if="!traceToolExecutions.length"><td colspan="5" class="empty-cell">当前 Trace 没有工具执行记录</td></tr>
                <tr v-for="item in traceToolExecutions" :key="item.id">
                  <td>{{ item.tool_name || item.tool_class || '-' }}</td>
                  <td :class="item.success ? 'trace-ok' : 'trace-error'">{{ item.success ? '成功' : '失败' }}</td>
                  <td>{{ item.latency_ms }}ms</td>
                  <td class="text-wrap">{{ item.input_summary || '-' }}</td>
                  <td class="text-wrap">{{ item.output_summary || item.error_message || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>

      <div class="grid-2 section-gap">
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">告警事件</div>
              <div class="card-subtitle">统一展示当前告警与处理流转状态。</div>
            </div>
          </div>
          <AlertEvents
            :alerts="filteredAlerts"
            :histories="monitorStore.alertHistories"
            @workflow="handleAlertWorkflow"
            @silence="handleAlertSilence"
            @note="handleAlertNote"
            @history="handleAlertHistory"
          />
        </div>
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">高频用户</div>
              <div class="card-subtitle">支持一键按用户或助手过滤，便于聚焦高调用场景。</div>
            </div>
          </div>
          <TopUsersTable :users="filteredTopUsers" @pick-user="applyUserFilter" @pick-agent="applyAgentFilter" />
        </div>
      </div>

      <div class="monitor-detail-grid section-gap">
        <div class="monitor-main-column">
          <div class="card">
            <div class="card-header"><div><div class="card-title">慢请求样本</div><div class="card-subtitle">点击行可直接带入右侧详情和 Trace 过滤。</div></div></div>
            <table class="compact-table">
              <thead><tr><th>用户</th><th>助手</th><th>模型</th><th>延迟</th><th>时间</th></tr></thead>
              <tbody>
                <tr v-if="!filteredSlowRequests.length"><td colspan="5" class="empty-cell">当前筛选下没有慢请求</td></tr>
                <tr
                  v-for="item in filteredSlowRequests"
                  :key="item.id"
                  class="sample-row"
                  :class="{ active: selectedSample?.kind === 'slow' && selectedSample.id === item.id }"
                  @click="selectSlowSample(item)"
                >
                  <td>
                    <div class="copy-cell">
                      <button v-if="item.user_id" class="context-link" @click.stop="applyUserFilter(item.user_id)">{{ item.user_id }}</button>
                      <span v-else>-</span>
                      <button v-if="item.trace_id" class="mini-action" @click.stop="applyTraceFilter(item.trace_id)">Trace</button>
                    </div>
                  </td>
                  <td><button class="context-link" @click.stop="applyAgentFilter(item.agent_type)">{{ agentLabel(item.agent_type) }}</button></td>
                  <td>{{ item.model_id || '-' }}</td>
                  <td>{{ item.latency_ms }}ms</td>
                  <td>{{ formatDateTime(item.created_at) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="card">
            <div class="card-header"><div><div class="card-title">失败样本</div><div class="card-subtitle">优先排查异常文案、会话范围和 Trace 详情。</div></div></div>
            <table class="compact-table">
              <thead><tr><th>用户</th><th>助手</th><th>错误信息</th><th>会话</th><th>时间</th></tr></thead>
              <tbody>
                <tr v-if="!filteredFailureSamples.length"><td colspan="5" class="empty-cell">当前筛选下没有失败样本</td></tr>
                <tr
                  v-for="item in filteredFailureSamples"
                  :key="item.id"
                  class="sample-row"
                  :class="{ active: selectedSample?.kind === 'failure' && selectedSample.id === item.id }"
                  @click="selectFailureSample(item)"
                >
                  <td>
                    <div class="copy-cell">
                      <button v-if="item.user_id" class="context-link" @click.stop="applyUserFilter(item.user_id)">{{ item.user_id }}</button>
                      <span v-else>-</span>
                      <button v-if="item.trace_id" class="mini-action" @click.stop="applyTraceFilter(item.trace_id)">Trace</button>
                    </div>
                  </td>
                  <td><button class="context-link" @click.stop="applyAgentFilter(item.agent_type)">{{ agentLabel(item.agent_type) }}</button></td>
                  <td class="error-text">{{ item.error_message || '未记录' }}</td>
                  <td>{{ item.session_id || '-' }}</td>
                  <td>{{ formatDateTime(item.created_at) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="card">
            <div class="card-header"><div><div class="card-title">工具审计</div><div class="card-subtitle">来自 Agent 工具调用审计链路，可快速看出慢工具、失败工具和高频工具。</div></div></div>
            <table class="compact-table">
              <thead><tr><th>用户</th><th>助手</th><th>工具</th><th>状态</th><th>耗时</th><th>时间</th></tr></thead>
              <tbody>
                <tr v-if="!filteredToolAudits.length"><td colspan="6" class="empty-cell">当前筛选下没有工具审计数据</td></tr>
                <tr v-for="item in filteredToolAudits" :key="item.id" class="sample-row">
                  <td><button v-if="item.user_id" class="context-link" @click.stop="applyUserFilter(item.user_id)">{{ item.user_id }}</button><span v-else>-</span></td>
                  <td><button v-if="item.agent_type" class="context-link" @click.stop="applyAgentFilter(item.agent_type)">{{ agentLabel(item.agent_type) }}</button><span v-else>-</span></td>
                  <td><div class="tool-name">{{ item.tool_name || item.tool_class || '-' }}</div><button v-if="item.trace_id" class="mini-action" @click.stop="applyTraceFilter(item.trace_id)">查看 Trace</button></td>
                  <td :class="item.success ? 'trace-ok' : 'trace-error'">{{ item.success ? '成功' : '失败' }}</td>
                  <td>{{ item.latency_ms }}ms</td>
                  <td>{{ formatDateTime(item.created_at) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="card sample-detail-panel">
          <div class="card-header">
            <div><div class="card-title">样本详情</div><div class="card-subtitle">用来快速生成排查结论，适合复制到问题单或后续优化文档。</div></div>
            <button class="btn btn-ghost btn-sm" :disabled="!selectedSample" @click="clearSelectedSample">清空</button>
          </div>
          <EmptyState
            v-if="!selectedSample"
            icon="D"
            title="尚未选择样本"
            description="点击左侧慢请求或失败样本后，这里会显示详细上下文与排查建议。"
            variant="compact"
            align="left"
          />
          <template v-else>
            <div class="sample-kind-banner" :class="selectedSample.kind">{{ selectedSample.kind === 'slow' ? '慢请求样本' : '失败样本' }}</div>
            <div class="sample-meta-grid">
              <div class="sample-meta-item"><span class="sample-meta-label">用户</span><span class="sample-meta-value">{{ selectedSample.user_id || '-' }}</span></div>
              <div class="sample-meta-item"><span class="sample-meta-label">助手</span><span class="sample-meta-value">{{ agentLabel(selectedSample.agent_type) }}</span></div>
              <div class="sample-meta-item"><span class="sample-meta-label">模型</span><span class="sample-meta-value">{{ selectedSample.model_id || '-' }}</span></div>
              <div class="sample-meta-item"><span class="sample-meta-label">耗时</span><span class="sample-meta-value">{{ selectedSample.latency_ms }}ms</span></div>
              <div class="sample-meta-item"><span class="sample-meta-label">Trace</span><span class="sample-meta-value">{{ selectedSample.trace_id || '无' }}</span></div>
              <div class="sample-meta-item"><span class="sample-meta-label">时间</span><span class="sample-meta-value">{{ formatDateTime(selectedSample.created_at) }}</span></div>
              <div class="sample-meta-item"><span class="sample-meta-label">会话</span><span class="sample-meta-value">{{ selectedSample.session_id || '无' }}</span></div>
            </div>
            <div class="sample-action-row">
              <button v-if="selectedSample.user_id" class="sample-row-action" @click="applyUserFilter(selectedSample.user_id)">按用户筛选</button>
              <button class="sample-row-action" @click="applyAgentFilter(selectedSample.agent_type)">按助手筛选</button>
              <button v-if="selectedSample.trace_id" class="sample-row-action" @click="applyTraceFilter(selectedSample.trace_id)">查看 Trace</button>
              <button class="sample-row-action" @click="copySelectedSampleSummary">复制排查摘要</button>
            </div>
            <div class="sample-insight-block">
              <div class="sample-insight-title">排查建议</div>
              <div class="sample-insight-content">{{ selectedSampleAdvice }}</div>
            </div>
            <div v-if="selectedSample.kind === 'failure'" class="sample-insight-block danger">
              <div class="sample-insight-title">错误信息</div>
              <pre class="sample-insight-content">{{ selectedSample.error_message || '未记录错误信息' }}</pre>
            </div>
          </template>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTraceDetail as fetchTraceDetail } from '@/api/monitor'
import type { FailureSample, SlowRequestSample, TraceDetail } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import MetricCard from '@/components/common/MetricCard.vue'
import AlertEvents from '@/components/monitor/AlertEvents.vue'
import TopUsersTable from '@/components/monitor/TopUsersTable.vue'
import { useToast } from '@/composables/useToast'
import { useMonitorStore } from '@/stores/monitor'
import { formatTokens } from '@/utils/format'

type RangeValue = '24h' | '3d' | '7d' | 'all'
type SelectedSample = ({ kind: 'slow' } & SlowRequestSample) | ({ kind: 'failure' } & FailureSample)

const agentNames: Record<string, string> = {
  RdAssistantAgent: '研发助手',
  SalesAssistantAgent: '销售助手',
  HrAssistantAgent: 'HR 助手',
  FinanceAssistantAgent: '财务助手',
  SupplyChainAssistantAgent: '供应链助手',
  QcAssistantAgent: '质控助手',
  WeatherAssistantAgent: '天气助手',
  SearchAssistantAgent: '搜索助手',
  DataAnalysisAssistantAgent: '数据分析助手',
  CodeAssistantAgent: '代码助手',
  McpAssistantAgent: 'MCP 助手',
  MultiAssistantAgent: '多智能体',
  assistant: '通用助手',
  writer: '写作助手'
}

const rangeOptions = [
  { value: '24h', label: '近 24 小时' },
  { value: '3d', label: '近 3 天' },
  { value: '7d', label: '近 7 天' },
  { value: 'all', label: '全部' }
] as Array<{ value: RangeValue; label: string }>

const monitorStore = useMonitorStore()
const { showToast } = useToast()
const route = useRoute()
const router = useRouter()

const selectedUserId = ref('')
const selectedAgent = ref('')
const selectedTraceId = ref('')
const selectedRange = ref<RangeValue>('24h')
const selectedSample = ref<SelectedSample | null>(null)
const traceDetail = ref<TraceDetail | null>(null)
const traceLoading = ref(false)
const traceError = ref('')

const overview = computed(() => monitorStore.overview)
const hasContextFilter = computed(() => Boolean(selectedUserId.value || selectedAgent.value || selectedTraceId.value))
const selectedRangeLabel = computed(() => rangeOptions.find((item) => item.value === selectedRange.value)?.label || '近 24 小时')
const successRateText = computed(() => overview.value?.successRate == null ? '--' : (overview.value.successRate * 100).toFixed(1))
const filteredTopUsers = computed(() => monitorStore.topUsers.filter((item) => matchesContext(item.user_id, item.agent_type)))
const filteredAlerts = computed(() => monitorStore.alerts)
const filteredSlowRequests = computed(() => monitorStore.slowRequests.filter((item) => matchesContext(item.user_id, item.agent_type, item.trace_id) && matchesDateRange(item.created_at)))
const filteredFailureSamples = computed(() => monitorStore.failureSamples.filter((item) => matchesContext(item.user_id, item.agent_type, item.trace_id) && matchesDateRange(item.created_at)))
const filteredToolAudits = computed(() => monitorStore.toolAudits.filter((item) => matchesContext(item.user_id, item.agent_type, item.trace_id) && matchesDateRange(item.created_at)))
const traceToolExecutions = computed(() => traceDetail.value?.tool_executions || [])
const tracePhases = computed(() => traceDetail.value?.phase_breakdown || [])

const summaryRisk = computed(() => {
  const item = [...monitorStore.agentStats].sort((a, b) => (b.errors || 0) - (a.errors || 0))[0]
  return item ? { title: agentLabel(item.agent_type), sub: `${item.errors || 0} 次错误 / ${item.count} 次调用` } : { title: '暂无数据', sub: '等待监控指标加载。' }
})
const summarySlow = computed(() => {
  const item = [...filteredSlowRequests.value].sort((a, b) => b.latency_ms - a.latency_ms)[0]
  return item ? { title: `${agentLabel(item.agent_type)} / ${item.model_id || '未知模型'}`, sub: `${item.user_id || '-'} 的请求耗时 ${item.latency_ms}ms` } : { title: '暂无数据', sub: '当前筛选范围内没有慢请求。' }
})
const toolAuditSummary = computed(() => {
  if (!filteredToolAudits.value.length) return '当前没有工具执行记录'
  const failed = filteredToolAudits.value.filter((item) => !item.success).length
  const slowest = [...filteredToolAudits.value].sort((a, b) => b.latency_ms - a.latency_ms)[0]
  return `失败 ${failed} 条，最慢工具 ${(slowest?.tool_name || slowest?.tool_class || '-')} ${slowest?.latency_ms || 0}ms`
})
const selectedSampleAdvice = computed(() => {
  if (!selectedSample.value) return ''
  if (selectedSample.value.kind === 'slow') {
    return selectedSample.value.trace_id ? '优先查看 Trace 详情，确认延迟来自模型生成、工具调用还是下游服务响应。' : '当前样本没有 Trace，建议先按用户和助手继续缩小范围，再对照同时间段日志排查。'
  }
  return selectedSample.value.trace_id ? '先看 Trace 输入输出，再结合错误信息判断是鉴权、模型失败还是业务参数问题。' : '当前失败样本没有 Trace，建议先按用户和助手缩小范围，再核对后端日志。'
})

function parseTime(value?: string) {
  const time = value ? new Date(value).getTime() : Number.NaN
  return Number.isNaN(time) ? null : time
}
function matchesDateRange(value?: string) {
  if (selectedRange.value === 'all') return true
  const t = parseTime(value)
  if (t == null) return false
  const map = { '24h': 86_400_000, '3d': 259_200_000, '7d': 604_800_000 }
  return Date.now() - t <= map[selectedRange.value]
}
function matchesContext(userId?: string, agent?: string, traceId?: string) {
  return (!selectedUserId.value || (userId || '') === selectedUserId.value) && (!selectedAgent.value || (agent || '') === selectedAgent.value) && (!selectedTraceId.value || (traceId || '') === selectedTraceId.value)
}
function agentLabel(agent: string) { return agentNames[agent] || agent }
function formatDateTime(value: string) {
  try { return value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-' } catch { return value }
}
function syncRouteQuery() {
  const query: Record<string, string> = {}
  if (selectedUserId.value) query.userId = selectedUserId.value
  if (selectedAgent.value) query.agent = selectedAgent.value
  if (selectedTraceId.value) query.traceId = selectedTraceId.value
  if (selectedRange.value !== '24h') query.range = selectedRange.value
  router.replace({ name: 'monitor', query })
}
function applyUserFilter(userId: string) { selectedUserId.value = userId; syncRouteQuery() }
function applyAgentFilter(agent: string) { selectedAgent.value = agent; syncRouteQuery() }
function applyTraceFilter(traceId: string) { selectedTraceId.value = traceId; syncRouteQuery() }
function clearTraceFilter() { selectedTraceId.value = ''; syncRouteQuery() }
function applyRange(range: RangeValue) { selectedRange.value = range; syncRouteQuery() }
function clearContextFilter() { selectedUserId.value = ''; selectedAgent.value = ''; selectedTraceId.value = ''; syncRouteQuery() }
function clearSelectedSample() { selectedSample.value = null }
function selectSlowSample(item: SlowRequestSample) { selectedSample.value = { kind: 'slow', ...item } }
function selectFailureSample(item: FailureSample) { selectedSample.value = { kind: 'failure', ...item } }
function applyRouteQuery() {
  selectedUserId.value = typeof route.query.userId === 'string' ? route.query.userId : ''
  selectedAgent.value = typeof route.query.agent === 'string' ? route.query.agent : ''
  selectedTraceId.value = typeof route.query.traceId === 'string' ? route.query.traceId : ''
  const range = typeof route.query.range === 'string' ? route.query.range : ''
  selectedRange.value = rangeOptions.some((item) => item.value === range) ? (range as RangeValue) : '24h'
}
async function loadTraceDetail(traceId: string) {
  if (!traceId) { traceDetail.value = null; traceError.value = ''; return }
  traceLoading.value = true
  traceError.value = ''
  try { traceDetail.value = await fetchTraceDetail(traceId) } catch (error) { traceDetail.value = null; traceError.value = error instanceof Error ? error.message : 'Trace 详情加载失败' } finally { traceLoading.value = false }
}
async function copySelectedSampleSummary() {
  if (!selectedSample.value) return
  const payload = ['监控排查摘要', `样本类型：${selectedSample.value.kind === 'slow' ? '慢请求' : '失败样本'}`, `用户：${selectedSample.value.user_id || '-'}`, `助手：${agentLabel(selectedSample.value.agent_type)}`, `模型：${selectedSample.value.model_id || '-'}`, `Trace：${selectedSample.value.trace_id || '无'}`, `耗时：${selectedSample.value.latency_ms}ms`, `排查建议：${selectedSampleAdvice.value}`, selectedSample.value.kind === 'failure' ? `错误：${selectedSample.value.error_message || '未记录'}` : ''].filter(Boolean).join('\n')
  try { await navigator.clipboard.writeText(payload); showToast('已复制排查摘要') } catch { showToast('复制失败，请稍后重试') }
}
async function handleAlertWorkflow(payload: { fingerprint: string; workflowStatus: string }) { try { await monitorStore.updateAlertWorkflow(payload.fingerprint, payload.workflowStatus); showToast('告警状态已更新') } catch { showToast('告警状态更新失败') } }
async function handleAlertSilence(payload: { fingerprint: string; hours: number }) {
  try {
    const until = new Date(Date.now() + payload.hours * 60 * 60 * 1000).toISOString()
    await monitorStore.updateAlertWorkflow(payload.fingerprint, 'silenced', '', until)
    showToast(`告警已静默 ${payload.hours} 小时`)
  } catch { showToast('告警静默设置失败') }
}
async function handleAlertNote(alert: { fingerprint?: string; workflowStatus?: string; workflowNote?: string; silencedUntil?: string }) {
  if (!alert.fingerprint) return
  const note = window.prompt('请输入处理备注', alert.workflowNote || '')
  if (note == null) return
  try { await monitorStore.updateAlertWorkflow(alert.fingerprint, alert.workflowStatus || 'acknowledged', note, alert.silencedUntil || ''); showToast('告警备注已保存') } catch { showToast('告警备注保存失败') }
}
async function handleAlertHistory(fingerprint: string) { try { await monitorStore.loadAlertWorkflowHistory(fingerprint); showToast('已加载告警处理历史') } catch { showToast('告警历史加载失败') } }
async function refreshPage() { await monitorStore.loadMonitorData(); if (selectedTraceId.value) await loadTraceDetail(selectedTraceId.value) }

onMounted(() => { applyRouteQuery(); void refreshPage() })
watch(() => route.query, applyRouteQuery)
watch(selectedTraceId, (traceId) => { void loadTraceDetail(traceId) }, { immediate: true })
watch([filteredSlowRequests, filteredFailureSamples], () => {
  if (!selectedSample.value) return
  const exists = (selectedSample.value.kind === 'slow' && filteredSlowRequests.value.some((item) => item.id === selectedSample.value?.id)) || (selectedSample.value.kind === 'failure' && filteredFailureSamples.value.some((item) => item.id === selectedSample.value?.id))
  if (!exists) selectedSample.value = null
})
</script>

<style scoped>
.section-gap { margin-bottom: 16px; }
.section-top { margin-top: 16px; }
.metric-unit { font-size: 14px; }
.summary-grid, .context-grid, .trace-meta-grid, .sample-meta-grid { display: grid; gap: 12px; }
.summary-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.context-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.trace-meta-grid, .sample-meta-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); margin-bottom: 12px; }
.summary-card, .context-item, .trace-meta-item, .trace-block, .sample-meta-item, .sample-insight-block { padding: 14px; border: 1px solid var(--border); border-radius: 16px; background: rgba(255, 255, 255, 0.03); }
.summary-label, .context-label, .trace-meta-label, .sample-meta-label { font-size: 11px; color: var(--text3); text-transform: uppercase; letter-spacing: 0.08em; }
.summary-value { font-size: 18px; font-weight: 600; color: var(--text); }
.summary-sub, .trace-detail-loading, .sample-insight-content { font-size: 12px; color: var(--text3); }
.context-value, .trace-meta-value, .sample-meta-value { color: var(--text); font-weight: 500; word-break: break-word; }
.trace-content-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.trace-block-title, .sample-insight-title { margin-bottom: 8px; font-size: 12px; font-weight: 600; }
.trace-block-content, .sample-insight-content { white-space: pre-wrap; word-break: break-word; font-family: var(--mono); }
.trace-phase-list { display: grid; gap: 10px; }
.trace-phase-item { padding: 12px; border-radius: 14px; border: 1px solid var(--border); background: rgba(255, 255, 255, 0.02); }
.trace-phase-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.trace-phase-name { display: flex; align-items: center; gap: 8px; color: var(--text); font-size: 13px; font-weight: 700; }
.trace-phase-badge { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 999px; background: rgba(245, 158, 11, 0.12); color: #b45309; font-size: 11px; font-weight: 600; }
.trace-phase-desc { margin-top: 4px; color: var(--text3); font-size: 12px; line-height: 1.6; }
.trace-phase-metrics { text-align: right; display: grid; gap: 2px; color: var(--text2); font-size: 12px; }
.trace-phase-bar { margin-top: 10px; height: 8px; border-radius: 999px; background: rgba(79, 142, 247, 0.08); overflow: hidden; }
.trace-phase-bar-inner { height: 100%; border-radius: 999px; background: linear-gradient(90deg, rgba(79, 142, 247, 0.8), rgba(59, 130, 246, 0.45)); }
.trace-ok { color: #0f9d58; }
.trace-error, .error-text { color: #d93025; }
.copy-cell { display: flex; gap: 8px; flex-wrap: wrap; }
.compact-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.compact-table th, .compact-table td { padding: 8px 10px; border-bottom: 1px solid var(--border); text-align: left; vertical-align: top; }
.mini-action, .context-link, .sample-row-action { border: 1px solid var(--border); background: transparent; color: var(--text3); border-radius: 999px; font-size: 11px; padding: 2px 8px; cursor: pointer; }
.context-link, .sample-row-action { color: var(--text); }
.range-switch { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
.range-chip { min-width: 84px; }
.context-card { position: sticky; top: 12px; z-index: 5; }
.monitor-detail-grid { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.9fr); gap: 16px; align-items: start; }
.monitor-main-column { display: grid; gap: 16px; }
.sample-row { cursor: pointer; transition: background var(--transition); }
.sample-row:hover { background: rgba(79, 142, 247, 0.05); }
.sample-row.active { background: rgba(79, 142, 247, 0.1); }
.sample-detail-panel { position: sticky; top: 104px; }
.sample-kind-banner { display: inline-flex; align-items: center; margin-bottom: 12px; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.sample-kind-banner.slow { background: rgba(245, 158, 11, 0.12); color: #b45309; }
.sample-kind-banner.failure { background: rgba(220, 38, 38, 0.1); color: #b91c1c; }
.sample-action-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.sample-insight-block { margin-top: 12px; }
.sample-insight-block.danger { border-color: rgba(220, 38, 38, 0.24); background: rgba(220, 38, 38, 0.05); }
.empty-cell { color: var(--text3); text-align: center; }
.text-wrap { white-space: pre-wrap; word-break: break-word; max-width: 320px; }
.tool-name { margin-bottom: 6px; color: var(--text); }
@media (max-width: 1100px) { .monitor-detail-grid { grid-template-columns: 1fr; } .sample-detail-panel, .context-card { position: static; } }
@media (max-width: 960px) {
  .summary-grid, .context-grid, .trace-meta-grid, .trace-content-grid, .sample-meta-grid, .grid-2 { grid-template-columns: 1fr; }
  .page-hero-actions, .range-switch, .copy-cell, .sample-action-row { width: 100%; }
  .page-hero-actions .btn, .range-chip, .sample-row-action { width: 100%; justify-content: center; }
  .copy-cell .mini-action, .copy-cell .context-link { width: 100%; justify-content: center; text-align: center; }
}
@media (max-width: 640px) {
  .summary-card, .context-item, .trace-meta-item, .trace-block, .sample-meta-item, .sample-insight-block { padding: 12px; }
  .compact-table th, .compact-table td { padding: 8px 6px; font-size: 11px; }
}
</style>
