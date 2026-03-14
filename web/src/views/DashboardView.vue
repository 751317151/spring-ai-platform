<template>
  <div>
    <div class="metrics-grid">
      <MetricCard label="今日请求" :value="overview?.totalRequests ?? '—'" sub="实时统计" color="blue" />
      <MetricCard label="Token 消耗" :value="formatTokens(overview?.totalTokens ?? 0)" sub="Prompt + Completion" color="green" />
      <MetricCard label="平均延迟" color="amber">
        <template #default>{{ overview?.avgLatencyMs ?? '—' }}<span style="font-size: 14px">ms</span></template>
      </MetricCard>
      <MetricCard label="成功率" color="purple">
        <template #default>{{ overview?.successRate != null ? (overview.successRate * 100).toFixed(1) : '—' }}<span style="font-size: 14px">%</span></template>
      </MetricCard>
    </div>

    <div class="grid-21" style="margin-bottom: 16px">
      <div class="card card-lg">
        <div class="card-title">请求量趋势（24h）</div>
        <div class="chart-container">
          <Line v-if="lineData" :data="lineData" :options="lineOptions" />
        </div>
      </div>
      <div class="card card-lg">
        <div class="card-title">Agent 使用分布</div>
        <div class="chart-container">
          <Doughnut v-if="doughnutData" :data="doughnutData" :options="doughnutOptions" />
        </div>
      </div>
    </div>

    <div class="grid-2">
      <div class="card">
        <div class="card-title">模型使用排行</div>
        <table>
          <thead><tr><th>模型</th><th>请求数</th><th>延迟</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-if="!monitorStore.models.length">
              <td colspan="4" style="text-align: center; color: var(--text3)">暂无数据</td>
            </tr>
            <tr v-for="m in monitorStore.models" :key="m.id">
              <td>
                <div style="font-weight: 500; color: var(--text)">{{ m.name }}</div>
                <div style="font-size: 10px; color: var(--text3)">{{ providerLabels[m.provider] || m.provider }}</div>
              </td>
              <td><span class="mono">{{ m.totalCalls }}</span></td>
              <td><span class="mono">{{ m.avgLatencyMs }}ms</span></td>
              <td><span class="pill" :class="m.enabled ? 'green' : 'red'">{{ m.enabled ? '在线' : '离线' }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="card">
        <div class="card-title">最近审计日志</div>
        <table>
          <thead><tr><th>时间</th><th>用户</th><th>Agent</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-if="!monitorStore.auditLogs.length">
              <td colspan="4" style="text-align: center; color: var(--text3)">暂无数据</td>
            </tr>
            <tr v-for="log in monitorStore.auditLogs" :key="log.id">
              <td style="font-size: 11px; color: var(--text3)">{{ formatTime(log.created_at) }}</td>
              <td>{{ log.user_id }}</td>
              <td>{{ agentLabels[log.agent_type] || log.agent_type }}</td>
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
import { Line, Doughnut } from 'vue-chartjs'
import {
  Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement,
  ArcElement, Tooltip, Legend, Filler
} from 'chart.js'
import MetricCard from '@/components/common/MetricCard.vue'
import { useMonitorStore } from '@/stores/monitor'
import { formatTokens, formatTime } from '@/utils/format'
import { AGENT_LABELS, PROVIDER_LABELS } from '@/utils/constants'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, ArcElement, Tooltip, Legend, Filler)

const monitorStore = useMonitorStore()
const agentLabels = AGENT_LABELS
const providerLabels = PROVIDER_LABELS

const overview = computed(() => monitorStore.overview)

const lineData = computed(() => {
  const stats = monitorStore.hourlyStats
  if (!stats.length) return null
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const values = new Array(24).fill(0)
  stats.forEach(s => {
    if (s.hour >= 0 && s.hour < 24) values[s.hour] = s.total || 0
  })
  return {
    labels: hours,
    datasets: [{
      label: '请求量',
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
    labels: stats.map(s => AGENT_LABELS[s.agent_type] || s.agent_type),
    datasets: [{
      data: stats.map(s => s.count),
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

onMounted(() => {
  monitorStore.loadDashboardData()
  monitorStore.startRealtimeUpdates()
})

onUnmounted(() => {
  monitorStore.stopRealtimeUpdates()
})
</script>
