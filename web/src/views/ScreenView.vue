<template>
  <div ref="shellRef" class="screen-page">
    <div
      class="screen-stage"
      :style="{
        transform: `translate(-50%, -50%) scale(${stageScale})`
      }"
    >
      <div class="screen-backdrop"></div>

      <header class="screen-topbar">
        <div class="screen-title-block">
          <div class="screen-kicker">AI PLATFORM COMMAND CENTER</div>
          <h1 class="screen-title">AI 平台运营指挥中心</h1>
        </div>

        <div class="screen-clock">
          <strong>{{ currentTime }}</strong>
          <span>{{ currentDate }}</span>
        </div>

        <div class="screen-actions">
          <button class="action-btn action-btn-primary" type="button" @click="refreshScreen">
            {{ monitorStore.loading ? '刷新中...' : '刷新数据' }}
          </button>
          <button class="action-btn" type="button" @click="toggleFullscreen">
            {{ isFullscreen ? '退出全屏' : '进入全屏' }}
          </button>
        </div>
      </header>

      <section class="hero-strip">
        <article v-for="card in heroCards" :key="card.label" class="hero-card">
          <span class="hero-label">{{ card.label }}</span>
          <strong class="hero-value">{{ card.value }}</strong>
          <small class="hero-sub">{{ card.sub }}</small>
        </article>
      </section>

      <main class="screen-grid">
        <section class="panel-column">
          <article class="panel-card stage-card">
            <div class="panel-header">
              <div>
                <span class="panel-kicker">SYSTEM STATUS</span>
                <h2>{{ stageStatus }}</h2>
              </div>
              <span class="panel-chip">{{ successRateText }}</span>
            </div>
            <p class="stage-summary">{{ stageSummary }}</p>
            <div class="stage-metrics">
              <div>
                <span>高优先级告警</span>
                <strong>{{ criticalAlerts.length }}</strong>
              </div>
              <div>
                <span>健康模型</span>
                <strong>{{ healthyModels.length }}/{{ models.length }}</strong>
              </div>
              <div>
                <span>正向反馈率</span>
                <strong>{{ positiveRateText }}</strong>
              </div>
            </div>
          </article>

          <article class="panel-card">
            <div class="panel-header">
              <div>
                <span class="panel-kicker">24H TRAFFIC</span>
                <h2>请求趋势</h2>
              </div>
              <span class="panel-chip">峰值 {{ formatCompactNumber(maxHourlyTotal) }}</span>
            </div>
            <div class="traffic-chart">
              <div v-for="item in trendBars" :key="item.hour" class="traffic-column">
                <div class="traffic-track">
                  <span class="traffic-bar" :style="{ height: `${item.height}%` }"></span>
                  <span class="traffic-error" :style="{ height: `${item.errorHeight}%` }"></span>
                </div>
                <small>{{ item.hour }}</small>
              </div>
            </div>
          </article>

          <article class="panel-card">
            <div class="panel-header">
              <div>
                <span class="panel-kicker">AGENT LOAD</span>
                <h2>助手调用排行</h2>
              </div>
            </div>
            <div ref="agentShellRef" class="scroll-shell">
              <div class="scroll-track">
                <div v-for="item in topAgentStatsMarquee" :key="item.key" class="rank-row">
                  <strong>{{ item.agent_type }}</strong>
                  <div class="rank-meta">
                    <span>{{ item.count }} 次</span>
                    <span>{{ item.avg_latency ?? 0 }} ms</span>
                  </div>
                  <div class="rank-track">
                    <span :style="{ width: `${buildPercent(item.count, topAgentStats[0]?.count || 1)}%` }"></span>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </section>

        <section class="panel-column panel-center">
          <article class="panel-card map-card">
            <div class="panel-header">
              <div>
                <span class="panel-kicker">CHINA MAP</span>
                <h2>省份热力 + 城市散点</h2>
              </div>
              <span class="panel-chip">近 24 小时</span>
            </div>
            <div v-if="chartError" class="chart-error">{{ chartError }}</div>
            <div ref="chartRef" class="china-chart"></div>
          </article>

          <article class="panel-card">
            <div class="panel-header">
              <div>
                <span class="panel-kicker">REGION RANK</span>
                <h2>热点区域</h2>
              </div>
            </div>
            <div ref="regionShellRef" class="scroll-shell">
              <div class="scroll-track">
                <div v-for="item in regionHeatMarquee" :key="item.key" class="region-row">
                  <div>
                    <strong>{{ item.regionName }}</strong>
                    <small>成功率 {{ formatPercent(item.successRate) }}</small>
                  </div>
                  <div class="region-metrics">
                    <span>{{ item.calls }} 次</span>
                    <span>{{ item.avgLatencyMs }} ms</span>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </section>

        <section class="panel-column">
          <article class="panel-card">
            <div class="panel-header">
              <div>
                <span class="panel-kicker">TOP USERS</span>
                <h2>高频用户</h2>
              </div>
            </div>
            <div ref="topUsersShellRef" class="scroll-shell">
              <div class="scroll-track">
                <div v-for="item in topUsersMarquee" :key="item.key" class="user-row">
                  <span class="user-index">{{ item.rank }}</span>
                  <div>
                    <strong>{{ item.user_id }}</strong>
                    <small>{{ item.agent_type }} / {{ item.avg_latency }} ms</small>
                  </div>
                  <strong>{{ item.calls }}</strong>
                </div>
              </div>
            </div>
          </article>

          <article class="panel-card">
            <div class="panel-header">
              <div>
                <span class="panel-kicker">MODEL HEALTH</span>
                <h2>模型健康</h2>
              </div>
            </div>
            <div ref="topModelsShellRef" class="scroll-shell">
              <div class="scroll-track">
                <div
                  v-for="item in topModelsMarquee"
                  :key="item.key"
                  class="model-row"
                  :class="item.healthStatus === 'degraded' ? 'is-degraded' : 'is-healthy'"
                >
                  <div>
                    <strong>{{ item.name || item.id }}</strong>
                    <small>{{ item.provider }}</small>
                  </div>
                  <div class="model-meta">
                    <span>{{ item.avgLatencyMs }} ms</span>
                    <span>{{ Math.round(item.successRate || 0) }}%</span>
                  </div>
                </div>
              </div>
            </div>
          </article>

          <article class="panel-card">
            <div class="panel-header">
              <div>
                <span class="panel-kicker">FAILURES</span>
                <h2>最近失败样本</h2>
              </div>
            </div>
            <div ref="failureShellRef" class="scroll-shell">
              <div class="scroll-track">
                <div v-for="item in failureSamplesMarquee" :key="item.key" class="failure-row">
                  <strong>{{ item.agent_type || 'unknown' }}</strong>
                  <small>{{ item.user_id || 'anonymous' }} / {{ formatTime(item.created_at) }}</small>
                  <p>{{ item.error_message || '未记录异常详情' }}</p>
                </div>
              </div>
            </div>
          </article>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch, type Ref } from 'vue'
import type { EChartsCoreOption } from 'echarts/core'
import { useMonitorStore } from '@/stores/monitor'

type LonLat = [number, number]
type EChartsModule = typeof import('echarts/core')
type EChartsInstance = import('echarts/core').ECharts

const STAGE_WIDTH = 1920
const STAGE_HEIGHT = 1080

const monitorStore = useMonitorStore()
const shellRef = ref<HTMLDivElement | null>(null)
const chartRef = ref<HTMLDivElement | null>(null)
const agentShellRef = ref<HTMLDivElement | null>(null)
const regionShellRef = ref<HTMLDivElement | null>(null)
const topUsersShellRef = ref<HTMLDivElement | null>(null)
const topModelsShellRef = ref<HTMLDivElement | null>(null)
const failureShellRef = ref<HTMLDivElement | null>(null)
const now = ref(new Date())
const isFullscreen = ref(Boolean(document.fullscreenElement))
const chartError = ref('')
const stageScale = ref(1)

let echartsModule: EChartsModule | null = null
let chart: EChartsInstance | null = null
let timer: ReturnType<typeof setInterval> | null = null
let resizeObserver: ResizeObserver | null = null
let chinaMapReady = false
let cleanupAutoScrolls: Array<() => void> = []

const overview = computed(() => monitorStore.screenSnapshot?.overview ?? monitorStore.overview)
const feedbackOverview = computed(
  () => monitorStore.screenSnapshot?.feedbackOverview ?? monitorStore.feedbackOverview
)
const screenAlerts = computed(
  () => monitorStore.screenSnapshot?.alerts ?? { activeAlerts: 0, alerts: [] }
)
const topUsers = computed(() => (monitorStore.screenSnapshot?.topUsers ?? []).slice(0, 12))
const failureSamples = computed(
  () => (monitorStore.screenSnapshot?.failureSamples ?? []).slice(0, 10)
)
const regionHeat = computed(() => (monitorStore.screenSnapshot?.regionHeat ?? []).slice(0, 20))
const models = computed(() => monitorStore.models ?? [])
const topAgentStats = computed(() =>
  [...(monitorStore.screenSnapshot?.agentStats ?? [])]
    .sort((a, b) => (b.count ?? 0) - (a.count ?? 0))
    .slice(0, 10)
)
const topModels = computed(() =>
  [...models.value].sort((a, b) => (b.totalCalls ?? 0) - (a.totalCalls ?? 0)).slice(0, 12)
)
const healthyModels = computed(() =>
  models.value.filter((item) => !item.healthStatus || item.healthStatus === 'healthy')
)
const criticalAlerts = computed(() =>
  (screenAlerts.value.alerts || []).filter((item) =>
    ['CRITICAL', 'ERROR', 'WARN', 'WARNING'].includes((item.level || '').toUpperCase())
  )
)

const positiveRateText = computed(() => formatPercent(feedbackOverview.value?.positiveRate ?? 0))
const successRateText = computed(() => formatPercent(overview.value?.successRate ?? 0))
const maxHourlyTotal = computed(() =>
  Math.max(1, ...(monitorStore.screenSnapshot?.hourlyStats ?? []).map((item) => item.total || 0))
)
const trendBars = computed(() =>
  (monitorStore.screenSnapshot?.hourlyStats ?? []).map((item) => ({
    hour: `${String(item.hour).padStart(2, '0')}`,
    height: Math.max(8, ((item.total || 0) / maxHourlyTotal.value) * 100),
    errorHeight: item.total ? Math.max(4, ((item.errors || 0) / item.total) * 100) : 0
  }))
)
const currentTime = computed(() =>
  now.value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
)
const currentDate = computed(() =>
  now.value.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })
)

function buildAutoScrollList<T extends Record<string, unknown>>(
  items: T[],
  duplicateThreshold: number,
  rankFactory?: (index: number, total: number) => string
) {
  const shouldDuplicate = items.length > duplicateThreshold
  const base = shouldDuplicate ? [...items, ...items] : items
  return base.map((item, index) => ({
    ...item,
    key: `${String(item.id ?? item.regionName ?? item.user_id ?? item.agent_type ?? index)}-${index}`,
    rank: rankFactory ? rankFactory(index, items.length) : ''
  }))
}

const topAgentStatsMarquee = computed(() =>
  buildAutoScrollList(topAgentStats.value, 4)
)
const regionHeatMarquee = computed(() =>
  buildAutoScrollList(regionHeat.value, 4)
)
const topUsersMarquee = computed(() =>
  buildAutoScrollList(topUsers.value, 4, (index, total) =>
    String((index % total) + 1).padStart(2, '0')
  )
)
const topModelsMarquee = computed(() =>
  buildAutoScrollList(topModels.value, 4)
)
const failureSamplesMarquee = computed(() =>
  buildAutoScrollList(failureSamples.value, 3)
)

const stageStatus = computed(() => {
  if ((screenAlerts.value.activeAlerts || 0) > 0) return '告警观察中'
  if ((overview.value?.successRate ?? 0) < 0.95) return '波动上升'
  return '稳定运行'
})

const stageSummary = computed(() => {
  if (criticalAlerts.value.length) {
    return `当前存在 ${criticalAlerts.value.length} 条高优先级告警，建议优先处理告警流转。`
  }
  if (failureSamples.value.length) {
    return `最近捕获 ${failureSamples.value.length} 条失败样本，建议优先复盘错误与模型状态。`
  }
  return `当前整体成功率 ${successRateText.value}，在线链路运行稳定。`
})

const heroCards = computed(() => [
  {
    label: '今日总请求',
    value: formatCompactNumber(overview.value?.totalRequests ?? 0),
    sub: `活跃请求 ${formatCompactNumber(Math.round(overview.value?.activeRequests ?? 0))}`
  },
  {
    label: '成功率',
    value: successRateText.value,
    sub: `P95 ${overview.value?.p95LatencyMs ?? 0} ms`
  },
  {
    label: 'Token 总量',
    value: formatCompactNumber(overview.value?.totalTokens ?? 0),
    sub: `Prompt ${formatCompactNumber(
      overview.value?.totalPromptTokens ?? 0
    )} / Completion ${formatCompactNumber(overview.value?.totalCompletionTokens ?? 0)}`
  },
  {
    label: '活动告警',
    value: String(screenAlerts.value.activeAlerts ?? 0),
    sub: `高优先级 ${criticalAlerts.value.length} 条`
  },
  {
    label: '模型健康',
    value: `${healthyModels.value.length}/${models.value.length}`,
    sub: '已接入模型运行状态'
  },
  {
    label: '正向反馈率',
    value: positiveRateText.value,
    sub: `总反馈 ${formatCompactNumber(feedbackOverview.value?.totalCount ?? 0)}`
  }
])

const provinceCoordMap: Record<string, LonLat> = {
  北京市: [116.4, 39.9],
  上海市: [121.47, 31.23],
  广东省: [113.27, 23.13],
  浙江省: [120.15, 30.28],
  江苏省: [118.78, 32.04],
  四川省: [104.06, 30.67],
  湖北省: [114.31, 30.52],
  陕西省: [108.95, 34.27],
  重庆市: [106.55, 29.57],
  未设置省份: [104.19, 35.86]
}

const cityCoordMap: Record<string, LonLat> = {
  北京市: [116.4, 39.9],
  上海市: [121.47, 31.23],
  广州市: [113.27, 23.13],
  深圳市: [114.05, 22.55],
  杭州市: [120.15, 30.28],
  南京市: [118.78, 32.04],
  成都市: [104.06, 30.67],
  武汉市: [114.31, 30.52],
  西安市: [108.95, 34.27],
  重庆市: [106.55, 29.57],
  未设置城市: [104.19, 35.86]
}

const provinceSeriesData = computed(() => {
  const grouped = new Map<string, number>()
  regionHeat.value.forEach((item) => {
    const key = item.province || '未设置省份'
    grouped.set(key, (grouped.get(key) || 0) + item.calls)
  })
  return Array.from(grouped.entries()).map(([name, value]) => ({
    name,
    value
  }))
})

const cityScatterData = computed(() =>
  regionHeat.value.map((item) => {
    const coord =
      cityCoordMap[item.city] ||
      provinceCoordMap[item.province] ||
      provinceCoordMap['未设置省份']
    return {
      name: item.regionName,
      value: [...coord, item.calls],
      raw: item
    }
  })
)

function updateStageScale() {
  const shell = shellRef.value
  if (!shell) {
    stageScale.value = 1
    return
  }
  const widthScale = shell.clientWidth / STAGE_WIDTH
  const heightScale = shell.clientHeight / STAGE_HEIGHT
  stageScale.value = Math.max(0.35, Math.min(widthScale, heightScale))
}

function createAutoScroller(
  shellRefValue: Ref<HTMLDivElement | null>,
  enabled: () => boolean,
  speed = 0.35
) {
  let intervalId: ReturnType<typeof setInterval> | null = null
  let pausedUntil = 0
  let remainder = 0
  let hovered = false

  const tick = () => {
    const shell = shellRefValue.value
    if (!shell) {
      return
    }

    if (!enabled()) {
      return
    }

    const halfHeight = shell.scrollHeight / 2
    if (halfHeight <= shell.clientHeight) {
      shell.scrollTop = 0
      return
    }

    if (!hovered && Date.now() >= pausedUntil) {
      remainder += speed
      const step = Math.floor(remainder)
      if (step <= 0) {
        return
      }
      remainder -= step
      shell.scrollTop += step
      if (shell.scrollTop >= halfHeight) {
        shell.scrollTop -= halfHeight
      }
    }
  }

  const pause = (duration = 1800) => {
    pausedUntil = Date.now() + duration
  }

  const bind = () => {
    const shell = shellRefValue.value
    if (!shell) return () => {}
    shell.scrollTop = 0

    const onMouseEnter = () => {
      hovered = true
    }
    const onMouseLeave = () => {
      hovered = false
      pause(500)
    }
    const onWheel = () => pause(2200)
    const onTouch = () => pause(2200)

    shell.addEventListener('mouseenter', onMouseEnter)
    shell.addEventListener('mouseleave', onMouseLeave)
    shell.addEventListener('wheel', onWheel, { passive: true })
    shell.addEventListener('touchmove', onTouch, { passive: true })

    return () => {
      shell.removeEventListener('mouseenter', onMouseEnter)
      shell.removeEventListener('mouseleave', onMouseLeave)
      shell.removeEventListener('wheel', onWheel)
      shell.removeEventListener('touchmove', onTouch)
    }
  }

  const unbind = bind()
  intervalId = setInterval(tick, 16)

  return () => {
    if (intervalId) {
      clearInterval(intervalId)
    }
    unbind()
  }
}

async function ensureEcharts() {
  if (echartsModule) return echartsModule
  const [core, charts, components, renderers] = await Promise.all([
    import('echarts/core'),
    import('echarts/charts'),
    import('echarts/components'),
    import('echarts/renderers')
  ])
  core.use([
    charts.MapChart,
    charts.EffectScatterChart,
    components.GeoComponent,
    components.TooltipComponent,
    components.VisualMapComponent,
    components.LegendComponent,
    renderers.CanvasRenderer
  ])
  echartsModule = core
  return core
}

function buildChartOption(): EChartsCoreOption {
  const maxProvinceValue = Math.max(
    1,
    ...provinceSeriesData.value.map((item) => Number(item.value) || 0)
  )
  const maxCityValue = Math.max(
    1,
    ...cityScatterData.value.map((item) => Number(item.value[2]) || 0)
  )

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(2, 6, 23, 0.92)',
      borderColor: 'rgba(56, 189, 248, 0.28)',
      textStyle: { color: '#e2e8f0' },
      formatter: (params: any) => {
        if (Array.isArray(params.value)) {
          const raw = params.data.raw
          return `<div><strong>${raw.regionName}</strong></div><div>请求 ${raw.calls} 次</div><div>成功率 ${formatPercent(raw.successRate)}</div><div>平均延迟 ${raw.avgLatencyMs} ms</div>`
        }
        return `<div><strong>${params.name}</strong></div><div>请求 ${params.value || 0} 次</div>`
      }
    },
    visualMap: {
      min: 0,
      max: maxProvinceValue,
      calculable: true,
      orient: 'horizontal',
      left: 18,
      bottom: 8,
      text: ['高热度', '低热度'],
      textStyle: { color: '#cbd5e1' },
      inRange: {
        color: ['#0f172a', '#0ea5e9', '#38bdf8', '#22d3ee']
      }
    },
    geo: {
      map: 'china',
      roam: true,
      zoom: 1.08,
      label: { show: false },
      itemStyle: {
        areaColor: '#13243c',
        borderColor: '#5eead4',
        borderWidth: 1
      },
      emphasis: {
        label: { show: false },
        itemStyle: { areaColor: '#1d4ed8' }
      }
    },
    series: [
      {
        name: '省份热力',
        type: 'map',
        geoIndex: 0,
        data: provinceSeriesData.value
      },
      {
        name: '城市散点',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        data: cityScatterData.value,
        rippleEffect: { scale: 4, brushType: 'stroke' },
        symbolSize: (value: number[]) => 8 + (Number(value[2]) / maxCityValue) * 16,
        itemStyle: {
          color: '#f97316',
          shadowBlur: 16,
          shadowColor: 'rgba(249, 115, 22, 0.65)'
        },
        emphasis: { scale: true }
      }
    ]
  }
}

async function ensureChinaMap() {
  if (chinaMapReady) return
  const core = await ensureEcharts()
  const response = await fetch('/china.geo.json')
  if (!response.ok) {
    throw new Error(`地图数据加载失败: HTTP ${response.status}`)
  }
  const geoJson = await response.json()
  core.registerMap('china', geoJson)
  chinaMapReady = true
}

async function ensureChart() {
  if (!chartRef.value) return
  try {
    chartError.value = ''
    const core = await ensureEcharts()
    await ensureChinaMap()
    if (!chart) {
      chart = core.init(chartRef.value)
    }
    chart.setOption(buildChartOption())
    chart.resize()
  } catch (error) {
    chartError.value = error instanceof Error ? error.message : '地图加载失败'
  }
}

function resizeChart() {
  updateStageScale()
  chart?.resize()
}

function initAutoScrolls() {
  cleanupAutoScrolls.forEach((fn) => fn())
  cleanupAutoScrolls = [
    createAutoScroller(agentShellRef, () => topAgentStats.value.length > 4, 0.26),
    createAutoScroller(regionShellRef, () => regionHeat.value.length > 4, 0.3),
    createAutoScroller(topUsersShellRef, () => topUsers.value.length > 4, 0.3),
    createAutoScroller(topModelsShellRef, () => topModels.value.length > 4, 0.28),
    createAutoScroller(failureShellRef, () => failureSamples.value.length > 3, 0.22)
  ]
}

function buildPercent(value: number, max: number) {
  return Math.max(8, (value / Math.max(1, max)) * 100)
}

function formatCompactNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    notation: 'compact',
    maximumFractionDigits: 1
  }).format(value || 0)
}

function formatPercent(value: number) {
  const percent = value <= 1 ? value * 100 : value
  return `${percent.toFixed(1)}%`
}

function formatTime(value?: string) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

async function refreshScreen() {
  await monitorStore.loadScreenData()
  await nextTick()
  updateStageScale()
  await ensureChart()
  initAutoScrolls()
}

async function toggleFullscreen() {
  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen()
    isFullscreen.value = true
    return
  }
  await document.exitFullscreen()
  isFullscreen.value = false
}

function syncFullscreenState() {
  isFullscreen.value = Boolean(document.fullscreenElement)
  resizeChart()
}

watch(
  [regionHeat, topUsers, topModels, failureSamples, topAgentStats, () => monitorStore.loading],
  async () => {
    await nextTick()
    updateStageScale()
    await ensureChart()
    initAutoScrolls()
  },
  { deep: true }
)

onMounted(async () => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 1000)
  document.addEventListener('fullscreenchange', syncFullscreenState)
  window.addEventListener('resize', resizeChart)
  if (shellRef.value) {
    resizeObserver = new ResizeObserver(() => resizeChart())
    resizeObserver.observe(shellRef.value)
  }
  await refreshScreen()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  document.removeEventListener('fullscreenchange', syncFullscreenState)
  window.removeEventListener('resize', resizeChart)
  resizeObserver?.disconnect()
  cleanupAutoScrolls.forEach((fn) => fn())
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.screen-page {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #030712;
}

.screen-stage {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 1920px;
  height: 1080px;
  transform-origin: center center;
  color: #edf6ff;
  padding: 16px;
  display: grid;
  grid-template-rows: 92px 144px minmax(0, 1fr);
  gap: 14px;
  overflow: hidden;
}

.screen-backdrop {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 15% 15%, rgba(56, 189, 248, 0.18), transparent 25%),
    radial-gradient(circle at 85% 10%, rgba(45, 212, 191, 0.16), transparent 22%),
    radial-gradient(circle at 50% 100%, rgba(59, 130, 246, 0.2), transparent 35%),
    linear-gradient(135deg, #020617, #08111f 55%, #0b1220);
}

.screen-topbar,
.hero-strip,
.screen-grid {
  position: relative;
  z-index: 1;
  min-height: 0;
}

.screen-topbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px 260px;
  gap: 16px;
  align-items: center;
}

.screen-kicker,
.panel-kicker {
  display: block;
  font-size: 11px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(125, 211, 252, 0.75);
}

.screen-title {
  margin: 8px 0 0;
  font-size: 32px;
  line-height: 1.1;
}

.screen-clock {
  display: grid;
  justify-items: center;
}

.screen-clock strong {
  font-size: 38px;
}

.screen-clock span,
.hero-sub,
.stage-summary,
.rank-meta span,
.region-row small,
.user-row small,
.model-row small,
.failure-row small,
.failure-row p {
  color: rgba(203, 213, 225, 0.76);
}

.screen-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.action-btn {
  height: 40px;
  padding: 0 16px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.04);
  color: #edf6ff;
  cursor: pointer;
}

.action-btn-primary {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.92), rgba(37, 99, 235, 0.76));
  border-color: rgba(56, 189, 248, 0.28);
}

.hero-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.hero-card,
.panel-card {
  border-radius: 22px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.055), rgba(255, 255, 255, 0.02)),
    rgba(8, 15, 28, 0.84);
  box-shadow: 0 18px 40px rgba(2, 6, 23, 0.24);
}

.hero-card {
  padding: 16px;
}

.hero-label {
  font-size: 12px;
  color: rgba(186, 230, 253, 0.7);
}

.hero-value {
  display: block;
  margin-top: 12px;
  font-size: 40px;
}

.screen-grid {
  display: grid;
  grid-template-columns: 440px minmax(0, 1fr) 430px;
  gap: 14px;
  overflow: hidden;
}

.panel-column {
  min-height: 0;
  display: grid;
  gap: 14px;
  grid-template-rows: repeat(3, minmax(0, 1fr));
}

.panel-center {
  grid-template-rows: 1.28fr 0.72fr;
}

.panel-card {
  min-height: 0;
  padding: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
  flex: none;
}

.panel-header h2 {
  margin: 8px 0 0;
  font-size: 22px;
  line-height: 1.1;
}

.panel-chip {
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.12);
  color: #cbd5e1;
  font-size: 12px;
}

.stage-card {
  background:
    radial-gradient(circle at 0 0, rgba(56, 189, 248, 0.16), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.055), rgba(255, 255, 255, 0.02)),
    rgba(8, 15, 28, 0.84);
}

.stage-summary {
  margin: 12px 0 0;
  line-height: 1.7;
  font-size: 13px;
}

.stage-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.stage-metrics div,
.region-row,
.user-row,
.model-row,
.failure-row {
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.stage-metrics span {
  display: block;
  font-size: 12px;
  color: rgba(191, 219, 254, 0.7);
}

.stage-metrics strong {
  display: block;
  margin-top: 6px;
  font-size: 24px;
}

.traffic-chart {
  display: grid;
  grid-template-columns: repeat(24, minmax(0, 1fr));
  gap: 6px;
  height: 100%;
  min-height: 0;
  margin-top: 12px;
  align-items: end;
}

.traffic-column {
  display: grid;
  justify-items: center;
  gap: 6px;
  height: 100%;
}

.traffic-track {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: end;
}

.traffic-bar,
.traffic-error {
  position: absolute;
  bottom: 0;
  width: 100%;
  border-radius: 999px 999px 12px 12px;
}

.traffic-bar {
  background: linear-gradient(180deg, #38bdf8, rgba(37, 99, 235, 0.25));
}

.traffic-error {
  width: 45%;
  left: 27.5%;
  background: linear-gradient(180deg, #fb7185, rgba(244, 63, 94, 0.22));
}

.scroll-shell {
  flex: 1;
  min-height: 0;
  margin-top: 12px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 6px;
  scrollbar-width: none;
}

.scroll-shell::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.scroll-track {
  display: grid;
  gap: 10px;
}

.rank-row {
  display: grid;
  gap: 6px;
}

.rank-meta,
.region-metrics,
.model-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.rank-track {
  height: 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.05);
  overflow: hidden;
}

.rank-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2dd4bf, #38bdf8);
}

.map-card {
  min-height: 0;
}

.china-chart {
  flex: 1;
  min-height: 0;
  margin-top: 10px;
}

.chart-error {
  margin-top: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(127, 29, 29, 0.22);
  border: 1px solid rgba(251, 113, 133, 0.3);
  color: #fecaca;
}

.region-row,
.user-row,
.model-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.user-index {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: rgba(56, 189, 248, 0.16);
  color: #7dd3fc;
  font-weight: 700;
}

.user-row {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
}

.model-row.is-healthy {
  border-color: rgba(74, 222, 128, 0.18);
}

.model-row.is-degraded {
  border-color: rgba(251, 113, 133, 0.22);
}

.failure-row p {
  margin: 6px 0 0;
  line-height: 1.6;
  font-size: 12px;
}
</style>
