<template>
  <div>
    <div class="metrics-grid">
      <MetricCard label="P95 延迟" color="blue">
        <template #default>{{ overview?.p95LatencyMs ?? '—' }}<span style="font-size: 14px">ms</span></template>
      </MetricCard>
      <MetricCard label="错误率" color="green">
        <template #default>{{ errorRate }}<span style="font-size: 14px">%</span></template>
      </MetricCard>
      <MetricCard label="活跃会话" :value="overview?.activeRequests ?? '—'" :sub="`总请求: ${overview?.totalRequests ?? '—'}`" color="amber" />
      <MetricCard label="Token 消耗" :value="formatTokens(overview?.totalTokens ?? 0)" sub="Prompt + Completion" color="purple" />
    </div>

    <div class="grid-2" style="margin-bottom: 16px">
      <div class="card">
        <div class="card-title">延迟分布（ms）</div>
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

    <div class="grid-2">
      <div class="card">
        <div class="card-title">Token 消耗（按用户 Top 10）</div>
        <TopUsersTable :users="monitorStore.topUsers" />
      </div>
      <div class="card">
        <div class="card-title">告警事件</div>
        <AlertEvents :alerts="monitorStore.alerts" />
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

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Tooltip, Legend, Filler)

const monitorStore = useMonitorStore()

const overview = computed(() => monitorStore.overview)

const errorRate = computed(() => {
  if (!overview.value || overview.value.successRate == null) return '—'
  return ((1 - overview.value.successRate) * 100).toFixed(1)
})

const barData = computed(() => {
  const stats = monitorStore.hourlyStats
  if (!stats.length) return null
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const p50Arr = new Array(24).fill(0)
  const p95Arr = new Array(24).fill(0)
  stats.forEach(s => {
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
  stats.forEach(s => {
    if (s.hour >= 0 && s.hour < 24) {
      const total = s.total || 0
      const errors = s.errors || 0
      errRateArr[s.hour] = total > 0 ? +((errors * 100 / total).toFixed(2)) : 0
    }
  })
  return {
    labels: hours,
    datasets: [{
      label: '错误率%',
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

onMounted(() => {
  monitorStore.loadMonitorData()
})
</script>
