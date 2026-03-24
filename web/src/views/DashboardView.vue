<template>
  <div class="space-monitor">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">总览</div>
        <div class="page-title">平台总览</div>
        <div class="page-subtitle">先查看今日请求态势，再带着用户或助手上下文快速跳转到监控页继续排查。</div>
        <div class="hero-tags">
          <span class="tag">实时统计</span>
          <span class="tag">24 小时趋势</span>
          <span class="tag">{{ overview ? '监控已连接' : '等待数据' }}</span>
        </div>
      </div>
      <div class="page-hero-actions">
        <button class="btn btn-ghost" @click="monitorStore.loadDashboardData()">刷新看板</button>
      </div>
    </div>

    <div class="metrics-grid">
      <MetricCard label="今日请求数" :value="overview?.totalRequests ?? '--'" sub="实时统计" color="blue" />
      <MetricCard label="令牌用量" :value="formatTokens(overview?.totalTokens ?? 0)" sub="输入令牌 + 输出令牌" color="green" />
      <MetricCard label="平均延迟" color="amber">
        <template #default>{{ overview?.avgLatencyMs ?? '--' }}<span style="font-size: 14px">ms</span></template>
      </MetricCard>
      <MetricCard label="成功率" color="purple">
        <template #default>{{ overview?.successRate != null ? (overview.successRate * 100).toFixed(1) : '--' }}<span style="font-size: 14px">%</span></template>
      </MetricCard>
    </div>

    <div class="summary-grid">
      <div class="card summary-card">
        <div class="summary-label">最慢助手链路</div>
        <div class="summary-value">{{ slowestSummary.title }}</div>
        <div class="summary-sub">{{ slowestSummary.sub }}</div>
        <button v-if="slowestSummary.agent" class="summary-link" @click="openMonitorWithContext({ agent: slowestSummary.agent })">在监控页打开</button>
      </div>
      <div class="card summary-card">
        <div class="summary-label">最高风险助手</div>
        <div class="summary-value">{{ riskSummary.title }}</div>
        <div class="summary-sub">{{ riskSummary.sub }}</div>
        <button v-if="riskSummary.agent" class="summary-link" @click="openMonitorWithContext({ agent: riskSummary.agent })">在监控页打开</button>
      </div>
      <div class="card summary-card">
        <div class="summary-label">主力模型</div>
        <div class="summary-value">{{ modelSummary.title }}</div>
        <div class="summary-sub">{{ modelSummary.sub }}</div>
      </div>
    </div>

    <div class="grid-21" style="margin-bottom: 16px">
      <div class="card card-lg">
        <div class="card-header">
          <div>
            <div class="card-title">请求趋势（24h）</div>
            <div class="card-subtitle">先看全天请求量走势，再定位异常时段。</div>
          </div>
        </div>
        <div class="chart-container">
          <Line v-if="lineData" :data="lineData" :options="lineOptions" />
          <div v-else class="chart-empty">暂无请求趋势数据。</div>
        </div>
      </div>

      <div class="card card-lg">
        <div class="card-header">
          <div>
            <div class="card-title">助手流量占比</div>
            <div class="card-subtitle">查看当前由哪些助手承接了主要流量。</div>
          </div>
        </div>
        <div class="chart-container">
          <Doughnut v-if="doughnutData" :data="doughnutData" :options="doughnutOptions" />
          <div v-else class="chart-empty">暂无助手分布数据。</div>
        </div>
      </div>
    </div>

    <div class="grid-2">
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">模型排名</div>
            <div class="card-subtitle">按使用量、延迟和在线状态查看当前模型表现。</div>
          </div>
        </div>
        <table>
          <thead>
            <tr>
              <th>模型</th>
              <th>请求数</th>
              <th>延迟</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!monitorStore.models.length">
              <td colspan="4" class="table-empty">暂无模型数据。</td>
            </tr>
            <tr v-for="model in sortedModels" :key="model.id">
              <td>
                <div class="primary-cell">{{ model.name }}</div>
                <div class="subtle-text">{{ providerLabels[model.provider] || model.provider }}</div>
              </td>
              <td><span class="mono">{{ model.totalCalls }}</span></td>
              <td><span class="mono">{{ model.avgLatencyMs }}ms</span></td>
              <td><span class="pill" :class="model.enabled ? 'green' : 'red'">{{ model.enabled ? '在线' : '离线' }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">最近审计日志</div>
            <div class="card-subtitle">可从用户或助手记录直接跳转到带筛选条件的监控页。</div>
          </div>
        </div>
        <table>
          <thead>
            <tr>
              <th>时间</th>
              <th>用户</th>
              <th>助手</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!monitorStore.auditLogs.length">
              <td colspan="4" class="table-empty">暂无审计日志。</td>
            </tr>
            <tr v-for="log in monitorStore.auditLogs" :key="log.id">
              <td class="subtle-text">{{ formatTime(log.created_at) }}</td>
              <td>
                <div class="copy-cell">
                  <button class="context-link" @click="openMonitorWithContext({ userId: log.user_id })">{{ log.user_id }}</button>
                  <button class="mini-action" @click="copyText(log.user_id, '用户 ID 已复制')">复制</button>
                </div>
              </td>
              <td>
                <button class="context-link" @click="openMonitorWithContext({ agent: log.agent_type })">
                  {{ agentLabels[log.agent_type] || log.agent_type }}
                </button>
              </td>
              <td><span class="pill" :class="log.success ? 'green' : 'red'">{{ log.success ? '成功' : '失败' }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Doughnut, Line } from 'vue-chartjs'
import { ArcElement, CategoryScale, Chart as ChartJS, Filler, Legend, LinearScale, LineElement, PointElement, Tooltip } from 'chart.js'
import MetricCard from '@/components/common/MetricCard.vue'
import { useToast } from '@/composables/useToast'
import { useMonitorStore } from '@/stores/monitor'
import { AGENT_LABELS, PROVIDER_LABELS } from '@/utils/constants'
import { formatTime, formatTokens } from '@/utils/format'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, ArcElement, Tooltip, Legend, Filler)

const monitorStore = useMonitorStore()
const { showToast } = useToast()
const router = useRouter()
const agentLabels = AGENT_LABELS
const providerLabels = PROVIDER_LABELS

const overview = computed(() => monitorStore.overview)
const sortedModels = computed(() => [...monitorStore.models].sort((a, b) => b.totalCalls - a.totalCalls).slice(0, 6))

const slowestSummary = computed(() => {
  const slowest = [...monitorStore.agentStats].sort((a, b) => (b.avg_latency || 0) - (a.avg_latency || 0))[0]
  return slowest
    ? {
        title: agentLabels[slowest.agent_type] || slowest.agent_type,
        sub: `平均延迟 ${Math.round(slowest.avg_latency || 0)}ms，错误 ${slowest.errors || 0}`,
        agent: slowest.agent_type
      }
    : { title: '暂无数据', sub: '等待监控指标到达。', agent: '' }
})

const riskSummary = computed(() => {
  const riskiest = [...monitorStore.agentStats].sort((a, b) => (b.errors || 0) - (a.errors || 0))[0]
  return riskiest
    ? {
        title: agentLabels[riskiest.agent_type] || riskiest.agent_type,
        sub: `${riskiest.count} 次调用中出现 ${riskiest.errors || 0} 次错误`,
        agent: riskiest.agent_type
      }
    : { title: '暂无数据', sub: '当前尚未发现集中风险。', agent: '' }
})

const modelSummary = computed(() => {
  const model = sortedModels.value[0]
  return model
    ? { title: model.name, sub: `${model.totalCalls} 次调用，平均延迟 ${model.avgLatencyMs}ms` }
    : { title: '暂无数据', sub: '模型统计尚未就绪。' }
})

const lineData = computed(() => {
  const stats = monitorStore.hourlyStats
  if (!stats.length) return null
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const values = new Array(24).fill(0)
  stats.forEach((item) => {
    if (item.hour >= 0 && item.hour < 24) values[item.hour] = item.total || 0
  })
  return {
    labels: hours,
    datasets: [{
      label: '请求数',
      data: values,
      borderColor: '#4f8ef7',
      backgroundColor: 'rgba(79,142,247,0.1)',
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
    y: { grid: { color: 'rgba(255,255,255,0.04)' }, ticks: { color: '#5c6070', font: { size: 10 } } }
  }
}

const doughnutData = computed(() => {
  const stats = monitorStore.agentStats
  if (!stats.length) return null
  const colors = ['#4f8ef7', '#3dd68c', '#9d7cf4', '#f5a623', '#2dd4bf', '#f06060', '#6fa3ff']
  return {
    labels: stats.map((item) => AGENT_LABELS[item.agent_type] || item.agent_type),
    datasets: [{
      data: stats.map((item) => item.count),
      backgroundColor: colors.slice(0, stats.length),
      borderWidth: 0
    }]
  }
})

const doughnutOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { position: 'right' as const, labels: { color: '#8b90a0', font: { size: 11 }, padding: 8 } }
  }
}

function openMonitorWithContext(context: { userId?: string; agent?: string }) {
  const query: Record<string, string> = {}
  if (context.userId) query.userId = context.userId
  if (context.agent) query.agent = context.agent
  router.push({ name: 'monitor', query })
}

async function copyText(value: string, message: string) {
  try {
    await navigator.clipboard.writeText(value)
    showToast(message)
  } catch {
    showToast('复制失败，请重试')
  }
}

onMounted(() => {
  monitorStore.loadDashboardData()
  monitorStore.startRealtimeUpdates()
})

onUnmounted(() => {
  monitorStore.stopRealtimeUpdates()
})
</script>

<style scoped>
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

.summary-link,
.context-link {
  border: none;
  background: transparent;
  color: var(--accent2);
  cursor: pointer;
  padding: 0;
  font: inherit;
  text-align: left;
}

.summary-link {
  margin-top: 10px;
  font-size: 12px;
  font-weight: 500;
}

.summary-link:hover,
.context-link:hover {
  color: var(--accent);
}

.chart-empty,
.table-empty {
  text-align: center;
  color: var(--text3);
  padding: 18px 10px;
}

.primary-cell {
  font-weight: 500;
  color: var(--text);
}

.copy-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mini-action {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text3);
  border-radius: 999px;
  font-size: 11px;
  padding: 2px 8px;
  cursor: pointer;
}

.mini-action:hover {
  color: var(--text);
  border-color: var(--accent);
}

@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
