<template>
  <div class="space-monitor">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">可观测性</div>
        <div class="page-title">运行监控</div>
        <div class="page-subtitle">围绕用户与智能体调用链路，持续跟踪延迟、失败、告警和反馈信号。</div>
        <div class="hero-tags">
          <span class="tag">{{ monitorStore.models.length }} 个模型</span>
          <span class="tag">{{ monitorStore.alerts.length }} 条告警</span>
          <span class="tag">{{ filteredRecentFeedback.length }} 条反馈</span>
        </div>
      </div>
      <div class="page-hero-actions">
        <button class="btn btn-ghost" :disabled="monitorStore.loading" @click="monitorStore.loadMonitorData()">
          {{ monitorStore.loading ? '刷新中...' : '刷新监控' }}
        </button>
      </div>
    </div>

    <EmptyState
      v-if="monitorStore.error"
      icon="!"
      title="监控数据加载失败"
      :description="monitorStore.error"
      action-text="重试"
      @action="monitorStore.loadMonitorData()"
    />

    <template v-else>
      <div class="metrics-grid">
        <MetricCard label="P95 延迟" color="blue">
          <template #default>{{ overview?.p95LatencyMs ?? '--' }}<span class="metric-unit">ms</span></template>
        </MetricCard>
        <MetricCard label="错误率" color="green">
          <template #default>{{ errorRate }}<span class="metric-unit">%</span></template>
        </MetricCard>
        <MetricCard label="活跃请求" :value="overview?.activeRequests ?? '--'" :sub="`总请求 ${overview?.totalRequests ?? '--'}`" color="amber" />
        <MetricCard label="令牌用量" :value="formatTokens(overview?.totalTokens ?? 0)" sub="提示词 + 补全" color="purple" />
      </div>

      <div class="summary-grid">
        <div class="card summary-card">
          <div class="summary-label">风险最高的智能体</div>
          <div class="summary-value">{{ summaryRisk.title }}</div>
          <div class="summary-sub">{{ summaryRisk.sub }}</div>
        </div>
        <div class="card summary-card">
          <div class="summary-label">最慢链路</div>
          <div class="summary-value">{{ summarySlow.title }}</div>
          <div class="summary-sub">{{ summarySlow.sub }}</div>
        </div>
        <div class="card summary-card">
          <div class="summary-label">负反馈热点</div>
          <div class="summary-value">{{ summaryFeedback.title }}</div>
          <div class="summary-sub">{{ summaryFeedback.sub }}</div>
        </div>
      </div>

      <div class="card section-gap context-card">
        <div class="card-header">
          <div>
            <div class="card-title">上下文筛选</div>
            <div class="card-subtitle">下方表格中的用户或智能体选择会自动复用到反馈、慢请求和失败样本中。</div>
          </div>
          <button class="btn btn-ghost btn-sm" :disabled="!hasContextFilter" @click="clearContextFilter">清空筛选</button>
        </div>
        <div class="context-grid">
          <div class="context-item">
            <span class="context-label">用户</span>
            <span class="context-value">{{ selectedUserId || '全部用户' }}</span>
          </div>
          <div class="context-item">
            <span class="context-label">智能体</span>
            <span class="context-value">{{ selectedAgent ? agentLabel(selectedAgent) : '全部智能体' }}</span>
          </div>
          <div class="context-item">
            <span class="context-label">当前范围</span>
            <span class="context-value">{{ filteredSlowRequests.length }} 条慢请求 / {{ filteredFailureSamples.length }} 条失败 / {{ filteredRecentFeedback.length }} 条反馈</span>
          </div>
        </div>
      </div>

      <div class="card section-gap">
        <div class="card-header">
          <div>
            <div class="card-title">告警事件</div>
            <div class="card-subtitle">集中查看当前系统告警及其来源标签。</div>
          </div>
        </div>
        <AlertEvents :alerts="monitorStore.alerts" />
      </div>

      <div class="grid-2 section-gap">
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">延迟分布</div>
              <div class="card-subtitle">对比最近 24 小时的 P50 和 P95 延迟。</div>
            </div>
          </div>
          <div class="chart-container">
            <Bar v-if="barData" :data="barData" :options="barOptions" />
            <div v-else class="chart-empty">暂无延迟数据。</div>
          </div>
        </div>
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">错误率趋势</div>
              <div class="card-subtitle">先看趋势，再决定是否深入分析失败样本。</div>
            </div>
          </div>
          <div class="chart-container">
            <Line v-if="errorLineData" :data="errorLineData" :options="lineOptions" />
            <div v-else class="chart-empty">暂无错误率趋势数据。</div>
          </div>
        </div>
      </div>

      <div class="grid-2 section-gap">
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">智能体使用情况</div>
              <div class="card-subtitle">点击某一行即可复用为当前监控上下文。</div>
            </div>
          </div>
          <table class="compact-table">
            <thead>
              <tr>
                <th>智能体</th>
                <th>调用次数</th>
                <th>平均延迟</th>
                <th>错误数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!monitorStore.agentStats.length">
                <td colspan="4" class="empty-cell">暂无智能体统计数据。</td>
              </tr>
              <tr v-for="item in monitorStore.agentStats" :key="item.agent_type">
                <td><button class="context-link" @click="applyAgentFilter(item.agent_type)">{{ agentLabel(item.agent_type) }}</button></td>
                <td>{{ item.count }}</td>
                <td>{{ Math.round(item.avg_latency || 0) }}ms</td>
                <td>{{ item.errors || 0 }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">高令牌消耗用户</div>
              <div class="card-subtitle">通过行操作将用户或智能体上下文带入整个监控页面。</div>
            </div>
            <button class="btn btn-ghost btn-sm" :disabled="monitorStore.isExporting('top-users')" @click="downloadCsv('top-users', 'top-users.csv')">
              {{ monitorStore.isExporting('top-users') ? '导出中...' : '导出数据文件' }}
            </button>
          </div>
          <TopUsersTable :users="filteredTopUsers" @pick-user="applyUserFilter" @pick-agent="applyAgentFilter" />
        </div>
      </div>

      <div class="grid-2 section-gap">
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">近期反馈</div>
              <div class="card-subtitle">当前列表会自动按上下文筛选。</div>
            </div>
            <button class="btn btn-ghost btn-sm" :disabled="monitorStore.isExporting('feedback')" @click="downloadCsv('feedback', 'feedback.csv', 200)">
              {{ monitorStore.isExporting('feedback') ? '导出中...' : '导出数据文件' }}
            </button>
          </div>
          <table class="compact-table">
            <thead>
              <tr>
                <th>来源</th>
                <th>用户</th>
                <th>反馈</th>
                <th>评论</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!filteredRecentFeedback.length">
                <td colspan="5" class="empty-cell">当前筛选下没有反馈数据。</td>
              </tr>
              <tr v-for="item in filteredRecentFeedback" :key="`${item.responseId}-${item.createdAt}`">
                <td>{{ feedbackSource(item) }}</td>
                <td>
                  <button v-if="item.userId" class="context-link" @click="applyUserFilter(item.userId)">{{ item.userId }}</button>
                  <span v-else>-</span>
                </td>
                <td :class="item.feedback === 'up' ? 'feedback-up' : 'feedback-down'">{{ item.feedback === 'up' ? '正向' : '负向' }}</td>
                <td>{{ item.comment || '-' }}</td>
                <td>{{ formatDateTime(item.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">证据反馈</div>
              <div class="card-subtitle">在相同上下文下检查检索证据的质量。</div>
            </div>
            <button class="btn btn-ghost btn-sm" :disabled="monitorStore.isExporting('evidence-feedback')" @click="downloadCsv('evidence-feedback', 'evidence-feedback.csv', 200)">
              {{ monitorStore.isExporting('evidence-feedback') ? '导出中...' : '导出数据文件' }}
            </button>
          </div>
          <table class="compact-table">
            <thead>
              <tr>
                <th>知识库</th>
                <th>分段</th>
                <th>用户</th>
                <th>反馈</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!filteredEvidenceFeedback.length">
                <td colspan="5" class="empty-cell">当前筛选下没有证据反馈数据。</td>
              </tr>
              <tr v-for="item in filteredEvidenceFeedback" :key="`${item.responseId}-${item.chunkId}`">
                <td>{{ item.knowledgeBaseId || '-' }}</td>
                <td class="subtle-text">{{ item.chunkId }}</td>
                <td>
                  <button v-if="item.userId" class="context-link" @click="applyUserFilter(item.userId)">{{ item.userId }}</button>
                  <span v-else>-</span>
                </td>
                <td :class="item.feedback === 'up' ? 'feedback-up' : 'feedback-down'">{{ item.feedback === 'up' ? '匹配' : '偏弱' }}</td>
                <td>{{ formatDateTime(item.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="grid-2 section-gap">
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">慢请求样本</div>
              <div class="card-subtitle">一键复制用户 ID，或直接将某行提升为共享上下文。</div>
            </div>
            <button class="btn btn-ghost btn-sm" :disabled="monitorStore.isExporting('slow-requests')" @click="downloadCsv('slow-requests', 'slow-requests.csv', 200)">
              {{ monitorStore.isExporting('slow-requests') ? '导出中...' : '导出数据文件' }}
            </button>
          </div>
          <table class="compact-table">
            <thead>
              <tr>
                <th>用户</th>
                <th>智能体</th>
                <th>模型</th>
                <th>延迟</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!filteredSlowRequests.length">
                <td colspan="5" class="empty-cell">当前筛选下没有慢请求数据。</td>
              </tr>
              <tr v-for="item in filteredSlowRequests" :key="item.id">
                <td>
                  <div class="copy-cell">
                    <button v-if="item.user_id" class="context-link" @click="applyUserFilter(item.user_id)">{{ item.user_id }}</button>
                    <span v-else>-</span>
                    <button v-if="item.user_id" class="mini-action" @click="copyText(item.user_id, '用户 ID 已复制')">复制</button>
                  </div>
                </td>
                <td><button class="context-link" @click="applyAgentFilter(item.agent_type)">{{ agentLabel(item.agent_type) }}</button></td>
                <td>{{ item.model_id || '-' }}</td>
                <td>{{ item.latency_ms }}ms</td>
                <td>{{ formatDateTime(item.created_at) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">失败样本</div>
              <div class="card-subtitle">让失败排查始终保持在当前选中的用户或智能体上下文中。</div>
            </div>
            <button class="btn btn-ghost btn-sm" :disabled="monitorStore.isExporting('failure-samples')" @click="downloadCsv('failure-samples', 'failure-samples.csv', 200)">
              {{ monitorStore.isExporting('failure-samples') ? '导出中...' : '导出数据文件' }}
            </button>
          </div>
          <table class="compact-table">
            <thead>
              <tr>
                <th>用户</th>
                <th>智能体</th>
                <th>错误信息</th>
                <th>会话</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!filteredFailureSamples.length">
                <td colspan="5" class="empty-cell">当前筛选下没有失败样本。</td>
              </tr>
              <tr v-for="item in filteredFailureSamples" :key="item.id">
                <td>
                  <div class="copy-cell">
                    <button v-if="item.user_id" class="context-link" @click="applyUserFilter(item.user_id)">{{ item.user_id }}</button>
                    <span v-else>-</span>
                    <button v-if="item.user_id" class="mini-action" @click="copyText(item.user_id, '用户 ID 已复制')">复制</button>
                  </div>
                </td>
                <td><button class="context-link" @click="applyAgentFilter(item.agent_type)">{{ agentLabel(item.agent_type) }}</button></td>
                <td class="error-text">{{ item.error_message || '未记录错误信息' }}</td>
                <td>{{ item.session_id || '-' }}</td>
                <td>{{ formatDateTime(item.created_at) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Bar, Line } from 'vue-chartjs'
import { BarElement, CategoryScale, Chart as ChartJS, Filler, Legend, LinearScale, LineElement, PointElement, Tooltip } from 'chart.js'
import type { FeedbackSample } from '@/api/types'
import { useRoute, useRouter } from 'vue-router'
import EmptyState from '@/components/common/EmptyState.vue'
import MetricCard from '@/components/common/MetricCard.vue'
import TopUsersTable from '@/components/monitor/TopUsersTable.vue'
import AlertEvents from '@/components/monitor/AlertEvents.vue'
import { useToast } from '@/composables/useToast'
import { useMonitorStore } from '@/stores/monitor'
import { AGENT_LABELS } from '@/utils/constants'
import { formatTokens } from '@/utils/format'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Tooltip, Legend, Filler)

const monitorStore = useMonitorStore()
const { showToast } = useToast()
const route = useRoute()
const router = useRouter()
const selectedUserId = ref('')
const selectedAgent = ref('')

const overview = computed(() => monitorStore.overview)
const hasContextFilter = computed(() => Boolean(selectedUserId.value || selectedAgent.value))

const filteredTopUsers = computed(() => monitorStore.topUsers.filter((item) => matchesContext(item.user_id, item.agent_type)))
const filteredSlowRequests = computed(() => monitorStore.slowRequests.filter((item) => matchesContext(item.user_id, item.agent_type)))
const filteredFailureSamples = computed(() => monitorStore.failureSamples.filter((item) => matchesContext(item.user_id, item.agent_type)))
const filteredRecentFeedback = computed(() => monitorStore.recentFeedback.filter((item) => matchesContext(item.userId, item.agentType)))
const filteredEvidenceFeedback = computed(() => monitorStore.recentEvidenceFeedback.filter((item) => matchesContext(item.userId)))

const errorRate = computed(() => (!overview.value || overview.value.successRate == null ? '--' : ((1 - overview.value.successRate) * 100).toFixed(1)))

const summaryRisk = computed(() => {
  const item = [...monitorStore.agentStats].sort((a, b) => (b.errors || 0) - (a.errors || 0))[0]
  return item
    ? { title: agentLabel(item.agent_type), sub: `${item.errors || 0} 次错误 / ${item.count} 次调用` }
    : { title: '暂无数据', sub: '等待监控指标加载。' }
})

const summarySlow = computed(() => {
  const item = [...filteredSlowRequests.value].sort((a, b) => b.latency_ms - a.latency_ms)[0]
  return item
    ? { title: `${agentLabel(item.agent_type)} / ${item.model_id || '未知模型'}`, sub: `${item.user_id || '-'} 的请求耗时 ${item.latency_ms}ms` }
    : { title: '暂无数据', sub: '当前筛选范围内没有慢请求。' }
})

const summaryFeedback = computed(() => {
  const item = filteredRecentFeedback.value.find((entry) => entry.feedback === 'down')
  return item
    ? { title: feedbackSource(item), sub: `${item.userId || '-'} 于 ${formatDateTime(item.createdAt)}` }
    : { title: '暂无热点', sub: '当前筛选范围内没有近期负反馈。' }
})

const barData = computed(() => {
  if (!monitorStore.hourlyStats.length) return null
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const p50 = new Array(24).fill(0)
  const p95 = new Array(24).fill(0)
  monitorStore.hourlyStats.forEach((item) => {
    if (item.hour >= 0 && item.hour < 24) {
      p50[item.hour] = Math.round(item.p50 || 0)
      p95[item.hour] = Math.round(item.p95 || 0)
    }
  })
  return {
    labels: hours,
    datasets: [
      { label: 'P50', data: p50, backgroundColor: 'rgba(79,142,247,0.6)' },
      { label: 'P95', data: p95, backgroundColor: 'rgba(245,166,35,0.6)' }
    ]
  }
})

const errorLineData = computed(() => {
  if (!monitorStore.hourlyStats.length) return null
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const values = new Array(24).fill(0)
  monitorStore.hourlyStats.forEach((item) => {
    if (item.hour >= 0 && item.hour < 24) {
      values[item.hour] = item.total ? +(((item.errors || 0) * 100) / item.total).toFixed(2) : 0
    }
  })
  return {
    labels: hours,
    datasets: [
      {
        label: '错误率',
        data: values,
        borderColor: '#f06060',
        backgroundColor: 'rgba(240,96,96,0.1)',
        fill: true,
        tension: 0.4,
        pointRadius: 0
      }
    ]
  }
})

const barOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { labels: { color: '#8b90a0', font: { size: 10 } } } },
  scales: {
    x: { grid: { color: 'rgba(255,255,255,0.04)' }, ticks: { color: '#5c6070', font: { size: 10 } } },
    y: { grid: { color: 'rgba(255,255,255,0.04)' }, ticks: { color: '#5c6070', font: { size: 10 } } }
  }
}

const lineOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: false } },
  scales: {
    x: { grid: { color: 'rgba(255,255,255,0.04)' }, ticks: { color: '#5c6070', font: { size: 10 } } },
    y: { grid: { color: 'rgba(255,255,255,0.04)' }, ticks: { color: '#5c6070', font: { size: 10 } }, min: 0 }
  }
}

function matchesContext(userId?: string, agent?: string) {
  const userMatch = !selectedUserId.value || (userId || '') === selectedUserId.value
  const agentMatch = !selectedAgent.value || (agent || '') === selectedAgent.value
  return userMatch && agentMatch
}

function agentLabel(agent: string) {
  return AGENT_LABELS[agent] || agent
}

function applyUserFilter(userId: string) {
  selectedUserId.value = userId
  syncRouteQuery()
}

function applyAgentFilter(agent: string) {
  selectedAgent.value = agent
  syncRouteQuery()
}

function clearContextFilter() {
  selectedUserId.value = ''
  selectedAgent.value = ''
  syncRouteQuery()
}

function syncRouteQuery() {
  const nextQuery: Record<string, string> = {}
  if (selectedUserId.value) nextQuery.userId = selectedUserId.value
  if (selectedAgent.value) nextQuery.agent = selectedAgent.value

  const currentUserId = typeof route.query.userId === 'string' ? route.query.userId : ''
  const currentAgent = typeof route.query.agent === 'string' ? route.query.agent : ''
  if (currentUserId === (nextQuery.userId || '') && currentAgent === (nextQuery.agent || '')) {
    return
  }
  router.replace({ name: 'monitor', query: nextQuery })
}

function applyRouteQuery() {
  selectedUserId.value = typeof route.query.userId === 'string' ? route.query.userId : ''
  selectedAgent.value = typeof route.query.agent === 'string' ? route.query.agent : ''
}

function feedbackSource(item: FeedbackSample) {
  if (item.sourceType === 'rag') {
    return item.knowledgeBaseId ? `知识库 / ${item.knowledgeBaseId}` : '知识库'
  }
  return item.agentType ? `智能体 / ${agentLabel(item.agentType)}` : item.sourceType
}

function formatDateTime(value: string) {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return value
  }
}

async function downloadCsv(type: 'slow-requests' | 'failure-samples' | 'feedback' | 'evidence-feedback' | 'top-users', fileName: string, limit?: number) {
  try {
    await monitorStore.exportCsv(type, fileName, limit)
    showToast('导出已开始')
  } catch {
    showToast('导出失败，请稍后重试')
  }
}

async function copyText(value: string, message: string) {
  try {
    await navigator.clipboard.writeText(value)
    showToast(message)
  } catch {
    showToast('复制失败，请稍后重试')
  }
}

onMounted(() => {
  applyRouteQuery()
  monitorStore.loadMonitorData()
})

watch(() => route.query, applyRouteQuery)
</script>

<style scoped>
.section-gap {
  margin-bottom: 16px;
}

.metric-unit {
  font-size: 14px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 16px;
}

.summary-label {
  font-size: 12px;
  color: var(--text3);
  margin-bottom: 10px;
}

.summary-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
}

.summary-sub {
  margin-top: 8px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}

.context-card {
  border-color: rgba(245, 158, 11, 0.18);
}

.context-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.context-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: var(--r2);
  border: 1px solid var(--border);
  background: rgba(255,255,255,0.03);
}

.context-label {
  font-size: 11px;
  color: var(--text3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.context-value {
  color: var(--text);
  font-weight: 500;
  word-break: break-word;
}

.compact-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.compact-table th,
.compact-table td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  vertical-align: top;
}

.compact-table th {
  color: var(--text3);
  font-weight: 500;
}

.compact-table td {
  color: var(--text2);
}

.empty-cell,
.chart-empty {
  text-align: center;
  color: var(--text3);
  padding: 18px 10px;
}

.error-text {
  max-width: 280px;
  color: #f06060;
  white-space: pre-wrap;
  word-break: break-word;
}

.feedback-up {
  color: #0f9d58 !important;
}

.feedback-down {
  color: #d93025 !important;
}

.copy-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mini-action,
.context-link {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text3);
  border-radius: 999px;
  font-size: 11px;
  padding: 2px 8px;
  cursor: pointer;
  text-decoration: none;
}

.context-link {
  color: var(--text);
}

.mini-action:hover,
.context-link:hover {
  color: var(--text);
  border-color: var(--accent);
}

@media (max-width: 960px) {
  .summary-grid,
  .context-grid {
    grid-template-columns: 1fr;
  }
}
</style>
