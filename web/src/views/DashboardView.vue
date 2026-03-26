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

    <div class="card section-gap quick-actions-card">
      <div class="card-header">
        <div>
          <div class="card-title">快捷操作</div>
          <div class="card-subtitle">从总览直接进入高频页面，减少来回跳转。</div>
        </div>
      </div>
      <div class="quick-actions-grid">
        <button class="quick-action-item action-tile" @click="router.push({ path: '/chat', query: { source: 'dashboard' } })">
          <span class="quick-action-title action-tile-title">进入 AI 助手</span>
          <span class="quick-action-desc action-tile-desc">继续最近会话，处理对话和草稿。</span>
        </button>
        <button class="quick-action-item action-tile" @click="router.push('/rag')">
          <span class="quick-action-title action-tile-title">进入知识库</span>
          <span class="quick-action-desc action-tile-desc">上传文档、检查状态并验证证据。</span>
        </button>
        <button class="quick-action-item action-tile warning" @click="openRagWithContext({ status: 'FAILED' }, { source: 'dashboard' })">
          <span class="quick-action-title action-tile-title">处理失败文档</span>
          <span class="quick-action-desc action-tile-desc">直接带失败筛选进入知识库，优先处理异常文档。</span>
        </button>
        <button class="quick-action-item action-tile" @click="router.push('/monitor')">
          <span class="quick-action-title action-tile-title">进入运行监控</span>
          <span class="quick-action-desc action-tile-desc">查看慢请求、失败样本和近期反馈。</span>
        </button>
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
      <div class="card summary-card elevated-summary-card interactive-metric-card">
        <div class="summary-label">当前最慢助手链路</div>
        <div class="summary-value">{{ slowestSummary.title }}</div>
        <div class="summary-sub">{{ slowestSummary.sub }}</div>
        <button v-if="slowestSummary.agent" class="summary-link" @click="openMonitorWithContext({ agent: slowestSummary.agent })">在监控页打开</button>
      </div>
      <div class="card summary-card elevated-summary-card interactive-metric-card">
        <div class="summary-label">当前最高风险助手</div>
        <div class="summary-value">{{ riskSummary.title }}</div>
        <div class="summary-sub">{{ riskSummary.sub }}</div>
        <button v-if="riskSummary.agent" class="summary-link" @click="openMonitorWithContext({ agent: riskSummary.agent })">在监控页打开</button>
      </div>
      <div class="card summary-card elevated-summary-card interactive-metric-card">
        <div class="summary-label">知识库待处理项</div>
        <div class="summary-value">{{ ragFocusSummary.title }}</div>
        <div class="summary-sub">{{ ragFocusSummary.sub }}</div>
        <button class="summary-link" @click="openRagWithContext(ragFocusSummary.context, { source: 'dashboard' })">在知识库页打开</button>
      </div>
    </div>

    <div class="card section-gap focus-card">
      <div class="card-header">
        <div>
          <div class="card-title">今日处理建议</div>
          <div class="card-subtitle">按当前数据优先级给出下一步入口，减少在总览页停留后还要自己判断去哪。</div>
        </div>
      </div>
      <div class="focus-grid">
        <button
          v-for="item in focusActions"
          :key="item.title"
          class="focus-item action-tile"
          type="button"
          @click="item.run"
        >
          <span class="focus-kicker">{{ item.kicker }}</span>
          <span class="focus-title action-tile-title">{{ item.title }}</span>
          <span class="focus-desc action-tile-desc">{{ item.desc }}</span>
        </button>
      </div>
    </div>

    <div class="grid-21 section-gap">
      <div class="card card-lg">
        <div class="card-header">
          <div>
            <div class="card-title">请求趋势（24h）</div>
            <div class="card-subtitle">先看全天请求量走势，再定位异常时段。</div>
          </div>
        </div>
        <div class="chart-container">
          <Line v-if="lineData" :data="lineData" :options="lineOptions" />
          <EmptyState v-else icon="24h" title="暂无请求趋势数据" description="等待监控指标上报后，这里会展示最近 24 小时的请求变化。" variant="compact" />
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
          <EmptyState v-else icon="A" title="暂无助手分布数据" description="当有助手调用记录后，这里会自动汇总各助手流量占比。" variant="compact" />
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
              <td colspan="4" class="table-empty-cell">
                <EmptyState icon="M" title="暂无模型数据" description="模型被调用后，这里会展示请求量、延迟和在线状态。" variant="compact" />
              </td>
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
              <td colspan="4" class="table-empty-cell">
                <EmptyState icon="L" title="暂无审计日志" description="后续的用户调用和操作行为会自动沉淀到这里。" variant="compact" />
              </td>
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
import EmptyState from '@/components/common/EmptyState.vue'
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

const ragFocusSummary = computed(() => {
  const failedLogs = monitorStore.auditLogs.filter((item) => item.success === false).length
  if (failedLogs > 0) {
    return {
      title: '优先处理失败项',
      sub: `最近审计中有 ${failedLogs} 条失败记录，建议先查看失败文档或异常索引。`,
      context: { status: 'FAILED' }
    }
  }
  return {
    title: '检查处理中项',
    sub: '可直接打开知识库处理中筛选，确认是否有长时间未完成的文档。',
    context: { status: 'PROCESSING' }
  }
})

const focusActions = computed(() => {
  const actions: Array<{ kicker: string; title: string; desc: string; run: () => void }> = []
  const latestFailed = monitorStore.auditLogs.find((item) => item.success === false)

  if (latestFailed) {
    actions.push({
      kicker: '优先级 P1',
      title: '先处理失败文档或异常索引',
      desc: `最近出现失败记录，可直接带着失败筛选进入知识库处理。`,
      run: () => openRagWithContext({ status: 'FAILED' }, { source: 'dashboard' })
    })
  }

  if (slowestSummary.value.agent) {
    actions.push({
      kicker: '优先级 P2',
      title: `检查 ${slowestSummary.value.title} 的慢链路`,
      desc: slowestSummary.value.sub,
      run: () => openMonitorWithContext({ agent: slowestSummary.value.agent })
    })
  }

    actions.push({
      kicker: '优先级 P3',
      title: '继续最近 AI 会话',
      desc: '回到聊天工作台，继续处理当前草稿、对话和未完成事项。',
      run: () => router.push({ path: '/chat', query: { source: 'dashboard' } })
    })

  return actions.slice(0, 3)
})

const lineData = computed(() => {
  const stats = monitorStore.hourlyStats
  if (!stats.length) return null
  const hours = Array.from({ length: 24 }, (_, index) => `${index}:00`)
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

function openRagWithContext(context: { status?: string; kb?: string }, options?: { source?: string }) {
  const query: Record<string, string> = {}
  if (context.status) query.status = context.status
  if (context.kb) query.kb = context.kb
  if (options?.source) query.source = options.source
  router.push({ path: '/rag', query })
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
.section-gap {
  margin-bottom: 16px;
}

.quick-actions-card {
  margin-bottom: 16px;
}

.focus-card {
  overflow: hidden;
}

.quick-actions-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.focus-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.focus-item {
  display: grid;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background:
    radial-gradient(circle at top right, rgba(79, 142, 247, 0.08), transparent 42%),
    rgba(255, 255, 255, 0.03);
  text-align: left;
  cursor: pointer;
  transition: transform var(--transition), border-color var(--transition), box-shadow var(--transition);
}

.focus-item:hover {
  transform: translateY(-2px);
  border-color: rgba(79, 142, 247, 0.24);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}

.focus-kicker {
  font-size: 11px;
  color: var(--text3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.focus-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.focus-desc {
  font-size: 12px;
  color: var(--text3);
  line-height: 1.6;
}

.quick-action-item {
  display: grid;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
  text-align: left;
  cursor: pointer;
  transition: transform var(--transition), border-color var(--transition), box-shadow var(--transition);
}

.quick-action-item:hover {
  transform: translateY(-2px);
  border-color: rgba(79, 142, 247, 0.24);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}

.quick-action-item.warning {
  border-color: rgba(245, 158, 11, 0.22);
  background: rgba(245, 158, 11, 0.06);
}

.quick-action-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.quick-action-desc {
  font-size: 12px;
  color: var(--text3);
  line-height: 1.6;
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

.table-empty-cell {
  padding: 12px;
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
  .quick-actions-grid,
  .summary-grid,
  .focus-grid {
    grid-template-columns: 1fr;
  }
}
</style>
