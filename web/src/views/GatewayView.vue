<template>
  <div class="space-admin">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">模型网关</div>
        <div class="page-title">模型服务网关</div>
        <div class="page-subtitle">
          统一管理模型目录、路由策略、健康状态和成本信息，并预览当前场景会如何选模型。
        </div>
        <div class="hero-tags">
          <span class="tag">{{ gatewayStore.models.length }} 个模型</span>
          <span class="tag">{{ routeCount }} 个场景</span>
          <span class="tag">{{ strategyLabel }}</span>
        </div>
      </div>
      <div class="page-hero-actions">
        <button class="btn btn-ghost btn-sm" @click="copyOverview">复制概览</button>
        <select v-model="strategy" class="form-select strategy-select">
          <option value="round-robin">轮询</option>
          <option value="weighted">加权路由</option>
          <option value="least-latency">最低延迟优先</option>
        </select>
        <button class="btn btn-primary" :disabled="!hasStrategyChanged" @click="handleSave">
          {{ hasStrategyChanged ? '保存配置' : '配置已同步' }}
        </button>
      </div>
    </div>

    <div class="summary-grid section-spacing">
      <div class="card summary-card">
        <div class="summary-label">可路由模型</div>
        <div class="summary-value">{{ onlineModels }} / {{ gatewayStore.models.length || 0 }}</div>
        <div class="summary-subtitle">当前仍可参与路由的模型数量</div>
      </div>
      <div class="card summary-card">
        <div class="summary-label">降级模型</div>
        <div class="summary-value">{{ degradedModels }}</div>
        <div class="summary-subtitle">连续失败过多，已临时退出优先路由的模型</div>
      </div>
      <div class="card summary-card">
        <div class="summary-label">平均延迟</div>
        <div class="summary-value">{{ avgLatencyLabel }}</div>
        <div class="summary-subtitle">只统计启用模型的平均响应时长</div>
      </div>
      <div class="card summary-card">
        <div class="summary-label">累计成本</div>
        <div class="summary-value">{{ totalEstimatedCostLabel }}</div>
        <div class="summary-subtitle">按模型单价和累计 Token 估算</div>
      </div>
    </div>

    <div class="card section-spacing">
      <div class="card-header">
        <div>
          <div class="card-title">当前策略说明</div>
          <div class="card-subtitle">先看模型健康和稳定性，再决定是否切换负载均衡策略。</div>
        </div>
        <span class="strategy-pill">{{ strategyLabel }}</span>
      </div>
      <div class="strategy-description">{{ strategyDescription }}</div>
    </div>

    <div class="card section-spacing">
      <div class="card-header">
        <div>
          <div class="card-title">路由决策预览</div>
          <div class="card-subtitle">模拟不同场景下的候选池、命中结果、降级回退和估算成本。</div>
        </div>
        <button class="btn btn-ghost btn-sm" @click="refreshRoutePreview">刷新预览</button>
      </div>

      <div class="route-preview-toolbar">
        <label class="form-group">
          <span class="form-label">业务场景</span>
          <select v-model="previewScene" class="form-select" @change="handleSceneChange">
            <option v-for="scene in gatewayStore.availableScenes" :key="scene" :value="scene">{{ scene }}</option>
          </select>
        </label>
        <label class="form-group">
          <span class="form-label">指定模型</span>
          <select v-model="previewModelId" class="form-select" @change="refreshRoutePreview">
            <option value="">自动选择</option>
            <option v-for="model in gatewayStore.models" :key="model.id" :value="model.id">{{ model.name }}</option>
          </select>
        </label>
        <label class="form-group">
          <span class="form-label">请求样本</span>
          <select v-model="selectedSampleKey" class="form-select" @change="refreshRoutePreview">
            <option v-for="sample in currentSceneSamples" :key="sample.key" :value="sample.key">{{ sample.label }}</option>
          </select>
        </label>
      </div>

      <div v-if="activeSample" class="sample-preview-card">
        <div class="sample-preview-head">
          <div>
            <div class="sample-preview-title">{{ activeSample.label }}</div>
            <div class="sample-preview-desc">{{ activeSample.description }}</div>
          </div>
          <div class="sample-preview-tags">
            <span class="tag">输入长度 {{ activeSample.prompt.length }}</span>
            <span class="tag">{{ activeSample.goal }}</span>
          </div>
        </div>
        <div class="sample-preview-body">
          <div class="sample-preview-block">
            <div class="sample-preview-label">模拟请求</div>
            <pre class="sample-preview-content">{{ activeSample.prompt }}</pre>
          </div>
          <div class="sample-preview-block">
            <div class="sample-preview-label">关注点</div>
            <ul class="sample-preview-list">
              <li v-for="item in activeSample.checkpoints" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>
      </div>

      <template v-if="routePreview">
        <div class="route-preview-summary">
          <div class="route-summary-card">
            <div class="route-summary-label">最终选中</div>
            <div class="route-summary-value">{{ routePreview.selectedModelId }}</div>
            <div class="route-summary-sub">{{ routePreview.reason }}</div>
          </div>
          <div class="route-summary-card">
            <div class="route-summary-label">回退状态</div>
            <div class="route-summary-value">{{ routePreview.fallbackTriggered ? '已触发回退' : '正常路由' }}</div>
            <div class="route-summary-sub">{{ routePreview.estimatedCostNote || '当前没有单独成本备注' }}</div>
          </div>
        </div>

        <div class="candidate-grid">
          <div
            v-for="item in routePreview.candidateModels"
            :key="item.id"
            class="candidate-card"
            :class="{ selected: item.selected, degraded: item.degraded }"
          >
            <div class="candidate-head">
              <div>
                <div class="candidate-name">{{ item.name }}</div>
                <div class="candidate-meta">{{ item.id }} / {{ item.provider }}</div>
              </div>
              <span class="candidate-status" :class="{ selected: item.selected, degraded: item.degraded }">
                {{ item.selected ? '已选中' : item.degraded ? '已降级' : item.healthy ? '可用' : '待定' }}
              </span>
            </div>
            <div class="candidate-stats">
              <span class="tag">权重 {{ item.weight }}</span>
              <span class="tag">成功率 {{ formatPercent(item.successRate) }}</span>
              <span class="tag">延迟 {{ item.avgLatencyMs ?? 0 }}ms</span>
            </div>
            <div class="candidate-cost">
              输入 {{ item.promptCostPer1kTokens ?? 0 }}/1K
              <span class="candidate-divider">/</span>
              输出 {{ item.completionCostPer1kTokens ?? 0 }}/1K
            </div>
            <div class="candidate-reason">{{ item.reason }}</div>
          </div>
        </div>
      </template>
      <div v-else class="empty-preview">当前无法加载路由预览，请稍后重试。</div>
    </div>

    <div class="card section-spacing">
      <div class="card-header">
        <div>
          <div class="card-title">模型目录</div>
          <div class="card-subtitle">对比健康状态、成功率、延迟、Token 和估算成本，判断是否需要调权或降级。</div>
        </div>
      </div>
      <div class="model-grid">
        <ModelCard v-for="item in gatewayStore.models" :key="item.id" :model="item" />
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div>
          <div class="card-title">场景路由</div>
          <div class="card-subtitle">查看每个业务场景的候选模型和兜底链路。</div>
        </div>
      </div>
      <SceneRouteTable :routes="gatewayStore.sceneRoutes" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import ModelCard from '@/components/gateway/ModelCard.vue'
import SceneRouteTable from '@/components/gateway/SceneRouteTable.vue'
import { useGatewayStore } from '@/stores/gateway'
import { useToast } from '@/composables/useToast'

interface RoutePreviewSample {
  key: string
  label: string
  description: string
  goal: string
  prompt: string
  checkpoints: string[]
}

const SAMPLE_LIBRARY: Record<string, RoutePreviewSample[]> = {
  default: [
    {
      key: 'general-qa',
      label: '通用问答',
      description: '适合观察默认场景下的稳定路由结果。',
      goal: '稳定优先',
      prompt: '请用简洁清晰的方式解释什么是检索增强生成，以及它和纯大模型回答的差异。',
      checkpoints: ['是否优先命中默认稳定模型', '是否给出清晰的回退原因', '成本说明是否可读']
    },
    {
      key: 'summary',
      label: '长文总结',
      description: '更适合测试长输入在默认场景下的模型选择。',
      goal: '长上下文',
      prompt: '请把下面一段较长的项目周报整理成摘要、风险、待办三部分，并输出成条列格式。',
      checkpoints: ['是否更偏向长上下文模型', '是否在延迟和成本之间做出平衡']
    }
  ],
  code: [
    {
      key: 'bugfix',
      label: '代码修复',
      description: '适合看代码场景是否偏向推理更稳定的模型。',
      goal: '代码推理',
      prompt: '请分析一个 Vue 页面切换后黑屏的问题，定位可能的路由状态同步和懒加载渲染问题，并给出修复思路。',
      checkpoints: ['是否命中代码场景专用模型', '是否保留高成功率模型作为候选', '是否避免已降级模型']
    },
    {
      key: 'review',
      label: '代码评审',
      description: '更适合测试低延迟和高可靠之间的权衡。',
      goal: '质量审查',
      prompt: '请对这段 Spring Boot 控制器做代码评审，重点指出鉴权、异常处理和日志埋点方面的风险。',
      checkpoints: ['是否给出清晰的命中原因', '是否展示候选池中的高质量模型']
    }
  ],
  rag: [
    {
      key: 'knowledge-search',
      label: '知识检索',
      description: '适合验证知识增强类请求在场景路由中的倾向。',
      goal: '检索问答',
      prompt: '请基于内部知识库回答员工报销流程，并给出引用来源和注意事项。',
      checkpoints: ['是否更偏向低幻觉模型', '是否展示成本与成功率的平衡']
    }
  ]
}

const gatewayStore = useGatewayStore()
const { showToast } = useToast()
const strategy = ref('round-robin')
const previewScene = ref('default')
const previewModelId = ref('')
const selectedSampleKey = ref('general-qa')

const routeCount = computed(() => Object.keys(gatewayStore.sceneRoutes || {}).length)
const onlineModels = computed(() => gatewayStore.models.filter((item) => item.enabled).length)
const degradedModels = computed(() => gatewayStore.models.filter((item) => item.healthStatus === 'degraded').length)
const routePreview = computed(() => gatewayStore.routeDecisionPreview)
const avgLatencyLabel = computed(() => {
  const active = gatewayStore.models.filter((item) => item.enabled && typeof item.avgLatencyMs === 'number')
  if (!active.length) return '--'
  return `${Math.round(active.reduce((sum, item) => sum + Number(item.avgLatencyMs || 0), 0) / active.length)} ms`
})
const totalEstimatedCostLabel = computed(() => {
  const total = gatewayStore.models.reduce((sum, item) => sum + Number(item.totalEstimatedCost || 0), 0)
  return total > 0 ? total.toFixed(4) : '--'
})
const strategyLabel = computed(() =>
  strategy.value === 'weighted' ? '加权路由' : strategy.value === 'least-latency' ? '最低延迟优先' : '轮询'
)
const strategyDescription = computed(() =>
  strategy.value === 'weighted'
    ? '按权重分配流量，适合主备质量差异明显的模型组合。'
    : strategy.value === 'least-latency'
      ? '优先使用响应更快的模型，适合低延迟交互链路。'
      : '按顺序均匀分配流量，适合作为默认稳定策略。'
)
const hasStrategyChanged = computed(() => strategy.value !== gatewayStore.loadBalanceStrategy)
const currentSceneSamples = computed(() => SAMPLE_LIBRARY[previewScene.value] || SAMPLE_LIBRARY.default)
const activeSample = computed(() =>
  currentSceneSamples.value.find((item) => item.key === selectedSampleKey.value) || currentSceneSamples.value[0] || null
)

watch(() => gatewayStore.loadBalanceStrategy, (value) => {
  strategy.value = value
})

watch(() => gatewayStore.availableScenes, (scenes) => {
  if (!scenes.includes(previewScene.value)) {
    previewScene.value = scenes[0] || 'default'
  }
}, { immediate: true })

watch(currentSceneSamples, (samples) => {
  if (!samples.find((item) => item.key === selectedSampleKey.value)) {
    selectedSampleKey.value = samples[0]?.key || ''
  }
}, { immediate: true })

onMounted(async () => {
  await gatewayStore.loadGatewayData()
  previewScene.value = gatewayStore.routePreviewScene || gatewayStore.availableScenes[0] || 'default'
  previewModelId.value = gatewayStore.routePreviewRequestedModelId || ''
  selectedSampleKey.value = currentSceneSamples.value[0]?.key || ''
})

function formatPercent(value?: number) {
  if (typeof value !== 'number') return '--'
  return `${Number(value).toFixed(1)}%`
}

async function refreshRoutePreview() {
  await gatewayStore.loadRouteDecisionPreview(previewScene.value, previewModelId.value || undefined)
}

async function handleSceneChange() {
  selectedSampleKey.value = currentSceneSamples.value[0]?.key || ''
  await refreshRoutePreview()
}

async function handleSave() {
  const ok = await gatewayStore.saveConfig(strategy.value)
  showToast(ok ? '网关配置已保存' : '网关配置保存失败')
}

async function copyOverview() {
  const lines = [
    '模型服务网关概览',
    `模型数量：${gatewayStore.models.length}`,
    `在线模型：${onlineModels.value}`,
    `降级模型：${degradedModels.value}`,
    `场景数量：${routeCount.value}`,
    `当前策略：${strategyLabel.value}`,
    `平均延迟：${avgLatencyLabel.value}`,
    `累计成本：${totalEstimatedCostLabel.value}`,
    activeSample.value ? `当前样本：${activeSample.value.label}` : '当前样本：无',
    routePreview.value ? `路由预览：${routePreview.value.selectedModelId} / ${routePreview.value.reason}` : '路由预览：暂无'
  ]

  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    showToast('已复制网关概览')
  } catch {
    showToast('复制网关概览失败')
  }
}
</script>

<style scoped>
.section-spacing { margin-bottom: 16px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.summary-card { padding: 16px; }
.summary-label { margin-bottom: 8px; color: var(--text3); font-size: 12px; }
.summary-value { color: var(--text); font-size: 22px; font-weight: 700; }
.summary-subtitle { margin-top: 8px; color: var(--text3); font-size: 12px; line-height: 1.6; }
.strategy-select { min-width: 220px; width: auto; }
.strategy-pill {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--accent-dim);
  color: var(--accent2);
  font-size: 12px;
  font-weight: 700;
}
.strategy-description { color: var(--text2); line-height: 1.7; }
.route-preview-toolbar { display: grid; grid-template-columns: repeat(3, minmax(0, 260px)); gap: 16px; margin-bottom: 16px; }
.sample-preview-card {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(79, 142, 247, 0.06), rgba(255, 255, 255, 0.03));
}
.sample-preview-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.sample-preview-title { color: var(--text); font-size: 16px; font-weight: 700; }
.sample-preview-desc { margin-top: 4px; color: var(--text2); font-size: 13px; line-height: 1.6; }
.sample-preview-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.sample-preview-body { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); gap: 14px; margin-top: 14px; }
.sample-preview-block {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(79, 142, 247, 0.12);
  background: rgba(255, 255, 255, 0.04);
}
.sample-preview-label {
  margin-bottom: 8px;
  color: var(--text3);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.sample-preview-content {
  margin: 0;
  white-space: pre-wrap;
  color: var(--text);
  font-size: 13px;
  line-height: 1.7;
  font-family: var(--mono);
}
.sample-preview-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text2);
  font-size: 13px;
  line-height: 1.8;
}
.route-preview-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.route-summary-card { padding: 14px 16px; border-radius: 16px; border: 1px solid var(--border); background: rgba(255, 255, 255, 0.03); }
.route-summary-label { font-size: 11px; color: var(--text3); text-transform: uppercase; letter-spacing: 0.08em; }
.route-summary-value { margin-top: 8px; color: var(--text); font-size: 18px; font-weight: 700; }
.route-summary-sub { margin-top: 6px; color: var(--text2); line-height: 1.6; }
.candidate-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.candidate-card { padding: 16px; border-radius: 18px; border: 1px solid var(--border); background: rgba(255, 255, 255, 0.03); }
.candidate-card.selected { border-color: rgba(79, 142, 247, 0.3); background: rgba(79, 142, 247, 0.08); }
.candidate-card.degraded { border-color: rgba(239, 68, 68, 0.25); }
.candidate-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.candidate-name { color: var(--text); font-size: 16px; font-weight: 700; }
.candidate-meta { margin-top: 4px; color: var(--text3); font-size: 12px; }
.candidate-status { display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 700; background: rgba(16, 185, 129, 0.12); color: #059669; }
.candidate-status.selected { background: rgba(79, 142, 247, 0.14); color: var(--accent2); }
.candidate-status.degraded { background: rgba(239, 68, 68, 0.12); color: #dc2626; }
.candidate-stats { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.candidate-cost, .candidate-reason { margin-top: 10px; color: var(--text2); font-size: 12px; line-height: 1.6; }
.candidate-divider { margin: 0 6px; color: var(--text3); }
.empty-preview { padding: 20px 0; color: var(--text3); text-align: center; }

@media (max-width: 960px) {
  .summary-grid,
  .route-preview-summary,
  .candidate-grid,
  .route-preview-toolbar,
  .sample-preview-body {
    grid-template-columns: 1fr;
  }

  .sample-preview-head {
    flex-direction: column;
  }
}
</style>
