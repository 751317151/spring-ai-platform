<template>
  <div>
    <div class="metrics-grid">
      <MetricCard label="P95 延迟" color="blue">
        <template #default>{{ overview?.p95LatencyMs ?? '--' }}<span style="font-size: 14px">ms</span></template>
      </MetricCard>
      <MetricCard label="错误率" color="green">
        <template #default>{{ errorRate }}<span style="font-size: 14px">%</span></template>
      </MetricCard>
      <MetricCard label="活跃请求" :value="overview?.activeRequests ?? '--'" :sub="`总请求 ${overview?.totalRequests ?? '--'}`" color="amber" />
      <MetricCard label="Token 消耗" :value="formatTokens(overview?.totalTokens ?? 0)" sub="Prompt + Completion" color="purple" />
    </div>

    <div class="grid-2 section-gap">
      <div class="card">
        <div class="card-title">延迟分布 (ms)</div>
        <div class="chart-container">
          <Bar v-if="barData" :data="barData" :options="barOptions" />
        </div>
      </div>
      <div class="card">
        <div class="card-title">错误率趋势</div>
        <div class="chart-container">
          <Line v-if="errorLineData" :data="errorLineData" :options="lineOptions" />
        </div>
      </div>
    </div>

    <div class="grid-2 section-gap">
      <div class="card">
        <div class="card-title">Agent 调用分布</div>
        <table class="compact-table">
          <thead>
            <tr><th>Agent</th><th>调用数</th><th>平均延迟</th><th>错误数</th></tr>
          </thead>
          <tbody>
            <tr v-if="!monitorStore.agentStats.length">
              <td colspan="4" class="empty-cell">暂无数据</td>
            </tr>
            <tr v-for="item in monitorStore.agentStats" :key="item.agent_type">
              <td>{{ agentLabel(item.agent_type) }}</td>
              <td>{{ item.count }}</td>
              <td>{{ Math.round(item.avg_latency || 0) }}ms</td>
              <td>{{ item.errors || 0 }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card">
        <div class="card-title">模型调用分布</div>
        <table class="compact-table">
          <thead>
            <tr><th>模型</th><th>调用数</th><th>平均延迟</th><th>错误数</th></tr>
          </thead>
          <tbody>
            <tr v-if="!monitorStore.modelStats.length">
              <td colspan="4" class="empty-cell">暂无数据</td>
            </tr>
            <tr v-for="item in monitorStore.modelStats" :key="item.model_id">
              <td>{{ item.model_id }}</td>
              <td>{{ item.count }}</td>
              <td>{{ Math.round(item.avg_latency || 0) }}ms</td>
              <td>{{ item.errors || 0 }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="grid-2 section-gap">
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">网关模型路由统计</div>
            <div class="card-subtitle">展示 gateway-service 当前模型调用表现和成功率</div>
          </div>
          <button class="btn btn-ghost btn-sm" @click="downloadGatewayModels">导出 CSV</button>
        </div>
        <table class="compact-table">
          <thead>
            <tr><th>模型</th><th>提供方</th><th>调用数</th><th>成功率</th><th>平均延迟</th><th>权重</th></tr>
          </thead>
          <tbody>
            <tr v-if="!monitorStore.models.length">
              <td colspan="6" class="empty-cell">暂无网关模型数据</td>
            </tr>
            <tr v-for="item in monitorStore.models" :key="item.id">
              <td>
                <div>{{ item.name }}</div>
                <div class="subtle-text">{{ item.id }}</div>
              </td>
              <td>{{ item.provider }}</td>
              <td>{{ item.totalCalls }}</td>
              <td>{{ item.successRate }}%</td>
              <td>{{ item.avgLatencyMs }}ms</td>
              <td>{{ item.weight }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card">
        <div class="card-header">
          <div class="card-title">Token 消耗用户 Top 10</div>
          <button class="btn btn-ghost btn-sm" @click="downloadCsv('top-users', 'top-users.csv')">导出 CSV</button>
        </div>
        <TopUsersTable :users="monitorStore.topUsers" />
      </div>
    </div>

    <div class="grid-2 section-gap">
      <div class="card">
        <div class="card-title">告警事件</div>
        <AlertEvents :alerts="monitorStore.alerts" />
      </div>
      <div class="card">
        <div class="card-title">路由策略概览</div>
        <div class="route-summary">
          <div class="route-metric">
            <span class="feedback-value">{{ monitorStore.gatewayLoadBalanceStrategy || '-' }}</span>
            <span class="feedback-caption">负载策略</span>
          </div>
          <div class="route-metric">
            <span class="feedback-value">{{ Object.keys(monitorStore.gatewaySceneRoutes || {}).length }}</span>
            <span class="feedback-caption">场景路由数</span>
          </div>
        </div>
        <table class="compact-table" style="margin-top: 12px">
          <thead>
            <tr><th>场景</th><th>路由模型</th></tr>
          </thead>
          <tbody>
            <tr v-if="!Object.keys(monitorStore.gatewaySceneRoutes || {}).length">
              <td colspan="2" class="empty-cell">暂无路由配置</td>
            </tr>
            <tr v-for="(models, scene) in monitorStore.gatewaySceneRoutes" :key="scene">
              <td>{{ scene }}</td>
              <td>{{ models.join(', ') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="grid-2 section-gap">
      <div class="card">
        <div class="card-title">反馈概览</div>
        <div class="feedback-summary">
          <div class="feedback-metric">
            <span class="feedback-value">{{ monitorStore.feedbackOverview?.totalCount ?? 0 }}</span>
            <span class="feedback-caption">总反馈</span>
          </div>
          <div class="feedback-metric">
            <span class="feedback-value positive">{{ monitorStore.feedbackOverview?.positiveCount ?? 0 }}</span>
            <span class="feedback-caption">点赞</span>
          </div>
          <div class="feedback-metric">
            <span class="feedback-value negative">{{ monitorStore.feedbackOverview?.negativeCount ?? 0 }}</span>
            <span class="feedback-caption">点踩</span>
          </div>
          <div class="feedback-metric">
            <span class="feedback-value">{{ positiveRate }}</span>
            <span class="feedback-caption">正向率</span>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <div class="card-title">最近反馈</div>
          <button class="btn btn-ghost btn-sm" @click="downloadCsv('feedback', 'feedback.csv', 200)">导出 CSV</button>
        </div>
        <table class="compact-table">
          <thead>
            <tr><th>来源</th><th>用户</th><th>反馈</th><th>备注</th><th>时间</th></tr>
          </thead>
          <tbody>
            <tr v-if="!monitorStore.recentFeedback.length">
              <td colspan="5" class="empty-cell">暂无反馈</td>
            </tr>
            <tr v-for="item in monitorStore.recentFeedback" :key="item.responseId">
              <td>{{ feedbackSource(item) }}</td>
              <td>{{ item.userId || '-' }}</td>
              <td :class="item.feedback === 'up' ? 'feedback-up' : 'feedback-down'">
                {{ item.feedback === 'up' ? '点赞' : '点踩' }}
              </td>
              <td>{{ item.comment || '-' }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="grid-2 section-gap">
      <div class="card">
        <div class="card-header">
          <div class="card-title">最近证据反馈</div>
          <button class="btn btn-ghost btn-sm" @click="downloadCsv('evidence-feedback', 'evidence-feedback.csv', 200)">导出 CSV</button>
        </div>
        <table class="compact-table">
          <thead>
            <tr><th>知识库</th><th>Chunk</th><th>用户</th><th>反馈</th><th>时间</th></tr>
          </thead>
          <tbody>
            <tr v-if="!monitorStore.recentEvidenceFeedback.length">
              <td colspan="5" class="empty-cell">暂无证据反馈</td>
            </tr>
            <tr v-for="item in monitorStore.recentEvidenceFeedback" :key="`${item.responseId}-${item.chunkId}`">
              <td>{{ item.knowledgeBaseId || '-' }}</td>
              <td class="subtle-text">{{ item.chunkId }}</td>
              <td>{{ item.userId || '-' }}</td>
              <td :class="item.feedback === 'up' ? 'feedback-up' : 'feedback-down'">
                {{ item.feedback === 'up' ? '命中准确' : '命中偏差' }}
              </td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card">
        <div class="card-title">按模型审计统计</div>
        <table class="compact-table">
          <thead>
            <tr><th>模型</th><th>调用数</th><th>平均延迟</th><th>错误数</th></tr>
          </thead>
          <tbody>
            <tr v-if="!monitorStore.modelStats.length">
              <td colspan="4" class="empty-cell">暂无审计模型数据</td>
            </tr>
            <tr v-for="item in monitorStore.modelStats" :key="item.model_id">
              <td>{{ item.model_id }}</td>
              <td>{{ item.count }}</td>
              <td>{{ Math.round(item.avg_latency || 0) }}ms</td>
              <td>{{ item.errors || 0 }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="grid-2 section-gap">
      <div class="card">
        <div class="card-header">
          <div class="card-title">慢请求 Top 10</div>
          <button class="btn btn-ghost btn-sm" @click="downloadCsv('slow-requests', 'slow-requests.csv', 200)">导出 CSV</button>
        </div>
        <table class="compact-table">
          <thead>
            <tr><th>用户</th><th>Agent</th><th>模型</th><th>延迟</th><th>时间</th></tr>
          </thead>
          <tbody>
            <tr v-if="!monitorStore.slowRequests.length">
              <td colspan="5" class="empty-cell">暂无数据</td>
            </tr>
            <tr v-for="item in monitorStore.slowRequests" :key="item.id">
              <td>{{ item.user_id || '-' }}</td>
              <td>{{ agentLabel(item.agent_type) }}</td>
              <td>{{ item.model_id || '-' }}</td>
              <td>{{ item.latency_ms }}ms</td>
              <td>{{ formatDateTime(item.created_at) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card">
        <div class="card-header">
          <div class="card-title">最近失败样本</div>
          <button class="btn btn-ghost btn-sm" @click="downloadCsv('failure-samples', 'failure-samples.csv', 200)">导出 CSV</button>
        </div>
        <table class="compact-table">
          <thead>
            <tr><th>用户</th><th>Agent</th><th>错误</th><th>会话</th><th>时间</th></tr>
          </thead>
          <tbody>
            <tr v-if="!monitorStore.failureSamples.length">
              <td colspan="5" class="empty-cell">暂无失败记录</td>
            </tr>
            <tr v-for="item in monitorStore.failureSamples" :key="item.id">
              <td>{{ item.user_id || '-' }}</td>
              <td>{{ agentLabel(item.agent_type) }}</td>
              <td class="error-text">{{ item.error_message || '未记录错误信息' }}</td>
              <td>{{ item.session_id || '-' }}</td>
              <td>{{ formatDateTime(item.created_at) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Bar, Line } from 'vue-chartjs'
import {
  Chart as ChartJS, CategoryScale, LinearScale, BarElement, PointElement,
  LineElement, Tooltip, Legend, Filler
} from 'chart.js'
import MetricCard from '@/components/common/MetricCard.vue'
import TopUsersTable from '@/components/monitor/TopUsersTable.vue'
import AlertEvents from '@/components/monitor/AlertEvents.vue'
import { useMonitorStore } from '@/stores/monitor'
import { formatTokens } from '@/utils/format'
import { AGENT_LABELS } from '@/utils/constants'
import type { FeedbackSample } from '@/api/types'
import { useToast } from '@/composables/useToast'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Tooltip, Legend, Filler)

const monitorStore = useMonitorStore()
const { showToast } = useToast()
const overview = computed(() => monitorStore.overview)

const errorRate = computed(() => {
  if (!overview.value || overview.value.successRate == null) return '--'
  return ((1 - overview.value.successRate) * 100).toFixed(1)
})

const positiveRate = computed(() => {
  const value = monitorStore.feedbackOverview?.positiveRate
  if (value == null) {
    return '--'
  }
  return `${(value * 100).toFixed(1)}%`
})

const barData = computed(() => {
  const stats = monitorStore.hourlyStats
  if (!stats.length) return null
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const p50Arr = new Array(24).fill(0)
  const p95Arr = new Array(24).fill(0)
  stats.forEach((s) => {
    if (s.hour >= 0 && s.hour < 24) {
      p50Arr[s.hour] = Math.round(s.p50 || 0)
      p95Arr[s.hour] = Math.round(s.p95 || 0)
    }
  })
  return {
    labels: hours,
    datasets: [
      { label: 'P50', data: p50Arr, backgroundColor: 'rgba(79,142,247,0.6)' },
      { label: 'P95', data: p95Arr, backgroundColor: 'rgba(245,166,35,0.6)' }
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

const errorLineData = computed(() => {
  const stats = monitorStore.hourlyStats
  if (!stats.length) return null
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const errRateArr = new Array(24).fill(0)
  stats.forEach((s) => {
    if (s.hour >= 0 && s.hour < 24) {
      const total = s.total || 0
      const errors = s.errors || 0
      errRateArr[s.hour] = total > 0 ? +((errors * 100 / total).toFixed(2)) : 0
    }
  })
  return {
    labels: hours,
    datasets: [{
      label: '错误率',
      data: errRateArr,
      borderColor: '#f06060',
      backgroundColor: 'rgba(240,96,96,0.1)',
      fill: true,
      tension: 0.4,
      pointRadius: 0
    }]
  }
})

const lineOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: false } },
  scales: {
    x: { grid: { color: 'rgba(255,255,255,0.04)' }, ticks: { color: '#5c6070', font: { size: 10 } } },
    y: { grid: { color: 'rgba(255,255,255,0.04)' }, ticks: { color: '#5c6070', font: { size: 10 } }, min: 0 }
  }
}

function agentLabel(agent: string) {
  return AGENT_LABELS[agent] || agent
}

function feedbackSource(item: FeedbackSample) {
  if (item.sourceType === 'rag') {
    return item.knowledgeBaseId ? `RAG / ${item.knowledgeBaseId}` : 'RAG'
  }
  return item.agentType ? `Agent / ${agentLabel(item.agentType)}` : item.sourceType
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

async function downloadCsv(
  type: 'slow-requests' | 'failure-samples' | 'feedback' | 'top-users',
  fileName: string,
  limit?: number
) {
  try {
    await monitorStore.exportCsv(type, fileName, limit)
    showToast('导出已开始')
  } catch {
    showToast('导出失败，请稍后重试')
  }
}

async function downloadGatewayModels() {
  try {
    await monitorStore.exportGatewayModelsCsv('gateway-models.csv')
    showToast('导出已开始')
  } catch {
    showToast('导出失败，请稍后重试')
  }
}

onMounted(() => {
  monitorStore.loadMonitorData()
})
</script>

<style scoped>
.section-gap {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
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

.empty-cell {
  text-align: center;
  color: var(--text3);
}

.error-text {
  max-width: 280px;
  color: #f06060;
  white-space: pre-wrap;
  word-break: break-word;
}

.card-subtitle,
.subtle-text {
  font-size: 12px;
  color: var(--text3);
}

.feedback-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.route-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.feedback-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--r2);
}

.route-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--r2);
}

.feedback-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--text);
}

.feedback-value.positive {
  color: #0f9d58;
}

.feedback-value.negative {
  color: #d93025;
}

.feedback-caption {
  font-size: 12px;
  color: var(--text3);
}

.feedback-up {
  color: #0f9d58 !important;
}

.feedback-down {
  color: #d93025 !important;
}
</style>
