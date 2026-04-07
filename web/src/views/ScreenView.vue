<template>
  <div class="screen-page">
    <div class="screen-backdrop"></div>

    <header class="screen-topbar">
      <div>
        <div class="screen-kicker">AI PLATFORM COMMAND CENTER</div>
        <h1>AI 平台运营指挥中心</h1>
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
      <section class="panel">
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
              <span>反馈正向率</span>
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
          <div class="rank-list">
            <div v-for="item in topAgentStats" :key="item.agent_type" class="rank-row">
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
        </article>
      </section>

      <section class="panel panel-center">
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
              <h2>热点省市</h2>
            </div>
          </div>
          <div class="region-list">
            <div v-for="item in regionHeat" :key="item.regionName" class="region-row">
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
        </article>
      </section>

      <section class="panel">
        <article class="panel-card">
          <div class="panel-header">
            <div>
              <span class="panel-kicker">TOP USERS</span>
              <h2>高频用户</h2>
            </div>
          </div>
          <div class="user-list">
            <div v-for="(item, index) in topUsers" :key="`${item.user_id}-${index}`" class="user-row">
              <span class="user-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <div>
                <strong>{{ item.user_id }}</strong>
                <small>{{ item.agent_type }} / {{ item.avg_latency }} ms</small>
              </div>
              <strong>{{ item.calls }}</strong>
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
          <div class="model-list">
            <div
              v-for="model in topModels"
              :key="model.id"
              class="model-row"
              :class="model.healthStatus === 'degraded' ? 'is-degraded' : 'is-healthy'"
            >
              <div>
                <strong>{{ model.name || model.id }}</strong>
                <small>{{ model.provider }}</small>
              </div>
              <div class="model-meta">
                <span>{{ model.avgLatencyMs }} ms</span>
                <span>{{ Math.round(model.successRate || 0) }}%</span>
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
          <div class="failure-list">
            <div v-for="item in failureSamples" :key="item.id" class="failure-row">
              <strong>{{ item.agent_type || 'unknown' }}</strong>
              <small>{{ item.user_id || 'anonymous' }} · {{ formatTime(item.created_at) }}</small>
              <p>{{ item.error_message || '未记录异常详情' }}</p>
            </div>
          </div>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import type { EChartsCoreOption } from 'echarts/core'
import { useMonitorStore } from '@/stores/monitor'

type LonLat = [number, number]
type EChartsModule = typeof import('echarts/core')
type EChartsInstance = import('echarts/core').ECharts

const monitorStore = useMonitorStore()
const chartRef = ref<HTMLDivElement | null>(null)
const now = ref(new Date())
const isFullscreen = ref(Boolean(document.fullscreenElement))
const chartError = ref('')

let echartsModule: EChartsModule | null = null
let chart: EChartsInstance | null = null
let timer: ReturnType<typeof setInterval> | null = null
let resizeObserver: ResizeObserver | null = null
let chinaMapReady = false

const overview = computed(() => monitorStore.screenSnapshot?.overview ?? monitorStore.overview)
const feedbackOverview = computed(() => monitorStore.screenSnapshot?.feedbackOverview ?? monitorStore.feedbackOverview)
const screenAlerts = computed(() => monitorStore.screenSnapshot?.alerts ?? { activeAlerts: 0, alerts: [] })
const topUsers = computed(() => (monitorStore.screenSnapshot?.topUsers ?? []).slice(0, 6))
const failureSamples = computed(() => (monitorStore.screenSnapshot?.failureSamples ?? []).slice(0, 5))
const regionHeat = computed(() => (monitorStore.screenSnapshot?.regionHeat ?? []).slice(0, 10))
const models = computed(() => monitorStore.models ?? [])
const topAgentStats = computed(() =>
  [...(monitorStore.screenSnapshot?.agentStats ?? [])].sort((a, b) => (b.count ?? 0) - (a.count ?? 0)).slice(0, 6)
)
const topModels = computed(() =>
  [...models.value].sort((a, b) => (b.totalCalls ?? 0) - (a.totalCalls ?? 0)).slice(0, 6)
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
  now.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
)
const currentDate = computed(() =>
  now.value.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
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
    sub: `Prompt ${formatCompactNumber(overview.value?.totalPromptTokens ?? 0)} / Completion ${formatCompactNumber(overview.value?.totalCompletionTokens ?? 0)}`
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
    label: '反馈正向率',
    value: positiveRateText.value,
    sub: `总反馈 ${formatCompactNumber(feedbackOverview.value?.totalCount ?? 0)}`
  }
])

const provinceCoordMap: Record<string, LonLat> = {
  北京: [116.4, 39.9],
  天津: [117.2, 39.12],
  河北: [114.48, 38.03],
  山西: [112.55, 37.87],
  内蒙古: [111.67, 40.82],
  辽宁: [123.43, 41.8],
  吉林: [125.32, 43.9],
  黑龙江: [126.64, 45.75],
  上海: [121.47, 31.23],
  江苏: [118.78, 32.04],
  浙江: [120.15, 30.28],
  安徽: [117.27, 31.86],
  福建: [119.3, 26.08],
  江西: [115.89, 28.68],
  山东: [117.0, 36.65],
  河南: [113.62, 34.75],
  湖北: [114.31, 30.52],
  湖南: [112.93, 28.23],
  广东: [113.27, 23.13],
  广西: [108.32, 22.82],
  海南: [110.35, 20.02],
  重庆: [106.55, 29.57],
  四川: [104.06, 30.67],
  贵州: [106.71, 26.57],
  云南: [102.71, 25.04],
  西藏: [91.11, 29.97],
  陕西: [108.95, 34.27],
  甘肃: [103.73, 36.03],
  青海: [101.78, 36.62],
  宁夏: [106.27, 38.47],
  新疆: [87.62, 43.82],
  香港: [114.17, 22.28],
  澳门: [113.54, 22.19],
  台湾: [121.51, 25.04],
  未设置省份: [104.19, 35.86]
}

const cityCoordMap: Record<string, LonLat> = {
  北京: [116.4, 39.9],
  上海: [121.47, 31.23],
  广州: [113.27, 23.13],
  深圳: [114.05, 22.55],
  杭州: [120.15, 30.28],
  南京: [118.78, 32.04],
  苏州: [120.62, 31.32],
  武汉: [114.31, 30.52],
  成都: [104.06, 30.67],
  重庆: [106.55, 29.57],
  西安: [108.95, 34.27],
  郑州: [113.62, 34.75],
  长沙: [112.93, 28.23],
  天津: [117.2, 39.12],
  青岛: [120.38, 36.07],
  济南: [117.0, 36.65],
  合肥: [117.27, 31.86],
  福州: [119.3, 26.08],
  厦门: [118.1, 24.46],
  南昌: [115.89, 28.68],
  贵阳: [106.71, 26.57],
  昆明: [102.71, 25.04],
  南宁: [108.32, 22.82],
  海口: [110.35, 20.02],
  沈阳: [123.43, 41.8],
  大连: [121.62, 38.92],
  长春: [125.32, 43.9],
  哈尔滨: [126.64, 45.75],
  石家庄: [114.48, 38.03],
  太原: [112.55, 37.87],
  呼和浩特: [111.67, 40.82],
  乌鲁木齐: [87.62, 43.82],
  拉萨: [91.11, 29.97],
  兰州: [103.73, 36.03],
  西宁: [101.78, 36.62],
  银川: [106.27, 38.47],
  香港: [114.17, 22.28],
  澳门: [113.54, 22.19],
  台北: [121.51, 25.04],
  未设置城市: [104.19, 35.86]
}

const provinceNameMap: Record<string, string> = {
  北京: '北京市',
  天津: '天津市',
  河北: '河北省',
  山西: '山西省',
  内蒙古: '内蒙古自治区',
  辽宁: '辽宁省',
  吉林: '吉林省',
  黑龙江: '黑龙江省',
  上海: '上海市',
  江苏: '江苏省',
  浙江: '浙江省',
  安徽: '安徽省',
  福建: '福建省',
  江西: '江西省',
  山东: '山东省',
  河南: '河南省',
  湖北: '湖北省',
  湖南: '湖南省',
  广东: '广东省',
  广西: '广西壮族自治区',
  海南: '海南省',
  重庆: '重庆市',
  四川: '四川省',
  贵州: '贵州省',
  云南: '云南省',
  西藏: '西藏自治区',
  陕西: '陕西省',
  甘肃: '甘肃省',
  青海: '青海省',
  宁夏: '宁夏回族自治区',
  新疆: '新疆维吾尔自治区',
  台湾: '台湾省',
  香港: '香港特别行政区',
  澳门: '澳门特别行政区',
  未设置省份: '未设置省份'
}

function canonicalProvinceName(name?: string) {
  const value = String(name || '').trim()
  if (!value) return ''
  const directMapped = provinceNameMap[value]
  if (directMapped) return directMapped
  return value
    .replace(/特别行政区$/u, '')
    .replace(/维吾尔自治区$/u, '')
    .replace(/壮族自治区$/u, '')
    .replace(/回族自治区$/u, '')
    .replace(/自治区$/u, '')
    .replace(/省$/u, '')
    .replace(/市$/u, '')
}

const provinceSeriesData = computed(() => {
  const grouped = new Map<string, number>()
  regionHeat.value.forEach((item) => {
    const canonicalName = canonicalProvinceName(item.province)
    grouped.set(canonicalName, (grouped.get(canonicalName) || 0) + item.calls)
  })
  return Array.from(grouped.entries()).map(([name, value]) => ({
    name: provinceNameMap[name] || name,
    value
  }))
})

const provinceHeatLookup = computed(() => {
  const lookup = new Map<string, number>()
  provinceSeriesData.value.forEach((item) => {
    const value = Number(item.value) || 0
    lookup.set(item.name, value)
    lookup.set(canonicalProvinceName(item.name), value)
  })
  return lookup
})

const cityScatterData = computed(() =>
  regionHeat.value.map((item) => {
    const coord = cityCoordMap[item.city] || provinceCoordMap[item.province] || provinceCoordMap['未设置省份']
    return {
      name: item.regionName,
      value: [...coord, item.calls],
      raw: item
    }
  })
)

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
  const maxProvinceValue = Math.max(1, ...provinceSeriesData.value.map((item) => Number(item.value) || 0))
  const maxCityValue = Math.max(1, ...cityScatterData.value.map((item) => Number(item.value[2]) || 0))
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
          const provinceName = String(params.name || '')
          const provinceCalls =
            provinceHeatLookup.value.get(provinceName) ||
            provinceHeatLookup.value.get(canonicalProvinceName(provinceName)) ||
            0
          return `<div><strong>${provinceName}</strong></div><div>请求 ${provinceCalls} 次</div>`
        }
      },
    visualMap: {
      min: 0,
      max: maxProvinceValue,
      calculable: true,
      orient: 'horizontal',
      left: 24,
      bottom: 10,
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
        itemStyle: {
          areaColor: '#1d4ed8'
        }
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
        symbolSize: (value: number[]) => 10 + (Number(value[2]) / maxCityValue) * 18,
        itemStyle: {
          color: '#f97316',
          shadowBlur: 16,
          shadowColor: 'rgba(249, 115, 22, 0.65)'
        },
        emphasis: {
          scale: true
        }
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
  } catch (error) {
    chartError.value = error instanceof Error ? error.message : '地图加载失败'
  }
}

function resizeChart() {
  chart?.resize()
}

function buildPercent(value: number, max: number) {
  return Math.max(8, (value / Math.max(1, max)) * 100)
}

function formatCompactNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 }).format(value || 0)
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
  await ensureChart()
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
  [regionHeat, () => monitorStore.loading],
  async () => {
    await nextTick()
    await ensureChart()
  },
  { deep: true }
)

onMounted(async () => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 1000)
  document.addEventListener('fullscreenchange', syncFullscreenState)
  window.addEventListener('resize', resizeChart)
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => resizeChart())
    resizeObserver.observe(chartRef.value)
  }
  await refreshScreen()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  document.removeEventListener('fullscreenchange', syncFullscreenState)
  window.removeEventListener('resize', resizeChart)
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.screen-page {
  position: relative;
  min-height: calc(100vh - 40px);
  padding: 12px;
  color: #edf6ff;
}

.screen-backdrop {
  position: absolute;
  inset: 0;
  border-radius: 30px;
  background:
    radial-gradient(circle at 15% 15%, rgba(56, 189, 248, 0.18), transparent 25%),
    radial-gradient(circle at 85% 10%, rgba(45, 212, 191, 0.16), transparent 22%),
    radial-gradient(circle at 50% 100%, rgba(59, 130, 246, 0.2), transparent 35%),
    linear-gradient(135deg, #020617, #08111f 55%, #0b1220);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.screen-topbar,
.hero-strip,
.screen-grid {
  position: relative;
  z-index: 1;
}

.screen-topbar {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 18px;
  align-items: center;
  margin-bottom: 16px;
}

.screen-kicker,
.panel-kicker {
  display: block;
  font-size: 11px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: rgba(125, 211, 252, 0.75);
}

.screen-topbar h1,
.panel-header h2 {
  margin: 8px 0 0;
  font-size: clamp(24px, 2.2vw, 34px);
  line-height: 1.1;
}

.screen-clock {
  display: grid;
  justify-items: center;
}

.screen-clock strong {
  font-size: clamp(26px, 2.8vw, 40px);
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
  gap: 14px;
  margin-bottom: 16px;
}

.hero-card,
.panel-card {
  border-radius: 24px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.055), rgba(255, 255, 255, 0.02)), rgba(8, 15, 28, 0.84);
  box-shadow: 0 18px 40px rgba(2, 6, 23, 0.24);
}

.hero-card {
  padding: 18px;
  min-height: 140px;
}

.hero-label {
  font-size: 12px;
  color: rgba(186, 230, 253, 0.7);
}

.hero-value {
  display: block;
  margin-top: 16px;
  font-size: clamp(30px, 3vw, 52px);
}

.screen-grid {
  display: grid;
  grid-template-columns: 0.94fr 1.34fr 0.92fr;
  gap: 16px;
}

.panel {
  display: grid;
  gap: 16px;
}

.panel-card {
  padding: 18px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
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
  margin: 16px 0 0;
  line-height: 1.8;
}

.stage-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.stage-metrics div,
.region-row,
.user-row,
.model-row,
.failure-row {
  padding: 12px 14px;
  border-radius: 18px;
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
  margin-top: 8px;
  font-size: 26px;
}

.traffic-chart {
  display: grid;
  grid-template-columns: repeat(24, minmax(0, 1fr));
  gap: 8px;
  height: 248px;
  margin-top: 18px;
  align-items: end;
}

.traffic-column {
  display: grid;
  justify-items: center;
  gap: 8px;
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

.rank-list,
.region-list,
.user-list,
.model-list,
.failure-list {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.rank-row {
  display: grid;
  gap: 8px;
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
  min-height: 720px;
}

.china-chart {
  height: 620px;
  margin-top: 16px;
}

.chart-error {
  margin-top: 16px;
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
  margin: 8px 0 0;
  line-height: 1.7;
}

@media (max-width: 1600px) {
  .hero-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .screen-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1024px) {
  .screen-topbar {
    grid-template-columns: 1fr;
  }

  .hero-strip,
  .stage-metrics {
    grid-template-columns: 1fr;
  }

  .china-chart {
    height: 460px;
  }
}
</style>
