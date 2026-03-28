<template>
  <div class="space-rag">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">知识库工作台</div>
        <div class="page-title">知识库管理</div>
        <div class="page-subtitle">围绕知识库选择、文档处理、问答验证和效果评估，组织完整的检索增强工作流。</div>
        <div class="hero-tags">
          <span class="tag">{{ ragStore.knowledgeBases.length }} 个知识库</span>
          <span class="tag">{{ ragStore.documents.length }} 份文档</span>
          <span class="tag">{{ ragStore.currentKbName || '未选择知识库' }}</span>
        </div>
      </div>
      <div class="page-hero-actions">
        <button v-if="documentStatusFilter !== 'FAILED'" class="btn btn-ghost" @click="documentStatusFilter = 'FAILED'">查看失败文档</button>
        <button class="btn btn-ghost btn-sm" @click="copyOverview">复制概览</button>
        <button class="btn btn-ghost" @click="refreshData">刷新数据</button>
      </div>
    </div>

    <BackendStatusBanner
      service="rag"
      demo-message="当前知识库页面运行在演示模式，列表和结果来自本地模拟数据。"
      unavailable-message="知识库后端当前不可用，页面不会自动回退到模拟结果。"
    />

    <div v-if="dashboardContextMessage" class="dashboard-context-banner section-spacing">
      <div class="dashboard-context-copy">
        <div class="dashboard-context-title">当前沿用总览页的处理上下文</div>
        <div class="dashboard-context-desc">{{ dashboardContextMessage }}</div>
      </div>
      <div class="dashboard-context-actions">
        <button class="btn btn-ghost btn-sm" type="button" @click="scrollToSection(tableRef)">查看文档列表</button>
        <button class="btn btn-ghost btn-sm" type="button" @click="clearDashboardContext">清除上下文</button>
      </div>
    </div>

    <div class="summary-grid compact-summary-grid section-spacing">
      <button class="card summary-item summary-button elevated-summary-card interactive-metric-card" type="button" @click="focusSummaryAction('kb')">
        <span class="summary-label">当前知识库</span>
        <span class="summary-value">{{ ragStore.currentKbName || '未选择' }}</span>
      </button>
      <button class="card summary-item summary-button elevated-summary-card interactive-metric-card" type="button" @click="focusSummaryAction('status')">
        <span class="summary-label">文档状态</span>
        <span class="summary-value">{{ statusLabelMap[documentStatusFilter] }}</span>
      </button>
      <button class="card summary-item summary-button elevated-summary-card interactive-metric-card" type="button" @click="focusSummaryAction('documents')">
        <span class="summary-label">当前结果</span>
        <span class="summary-value">{{ filteredDocumentCount }} 份文档</span>
      </button>
      <button class="card summary-item summary-button elevated-summary-card interactive-metric-card" type="button" @click="focusSummaryAction('next')">
        <span class="summary-label">下一步建议</span>
        <span class="summary-value">{{ nextStepTip }}</span>
      </button>
    </div>

    <div class="status-focus-grid section-spacing">
      <button class="card status-focus-card failed elevated-summary-card interactive-metric-card" type="button" @click="documentStatusFilter = 'FAILED'">
        <div class="status-focus-label">失败文档</div>
        <div class="status-focus-value">{{ failedDocumentCount }}</div>
        <div class="status-focus-desc">优先处理上传或索引失败的文件</div>
      </button>
      <button class="card status-focus-card processing elevated-summary-card interactive-metric-card" type="button" @click="documentStatusFilter = 'PROCESSING'">
        <div class="status-focus-label">处理中</div>
        <div class="status-focus-value">{{ processingDocumentCount }}</div>
        <div class="status-focus-desc">关注仍在索引队列中的文件</div>
      </button>
    </div>

    <div class="evaluation-grid section-spacing">
      <div class="card evaluation-card">
        <div class="card-header">
          <div>
            <div class="card-title">检索效果评估</div>
            <div class="card-subtitle">基于问答反馈和证据反馈，快速判断当前知识库的可用性。</div>
          </div>
        </div>
        <div class="evaluation-metrics">
          <div class="evaluation-metric"><span>总查询数</span><strong>{{ ragStore.evaluationOverview.totalQueries }}</strong></div>
          <div class="evaluation-metric"><span>反馈数</span><strong>{{ ragStore.evaluationOverview.feedbackCount }}</strong></div>
          <div class="evaluation-metric"><span>正反馈率</span><strong>{{ formatRate(ragStore.evaluationOverview.positiveFeedbackRate) }}</strong></div>
          <div class="evaluation-metric"><span>证据正反馈率</span><strong>{{ formatRate(ragStore.evaluationOverview.positiveEvidenceRate) }}</strong></div>
          <div class="evaluation-metric"><span>低评分样本</span><strong>{{ ragStore.evaluationOverview.lowRatedQueryCount }}</strong></div>
        </div>
      </div>
      <div class="card evaluation-card">
        <div class="card-header">
          <div>
            <div class="card-title">低评分样本</div>
            <div class="card-subtitle">优先回看负反馈问答，确认是召回不足、证据偏弱还是答案组织问题。</div>
          </div>
        </div>
        <div v-if="ragStore.loadingEvaluation" class="evaluation-loading">正在加载评估数据...</div>
        <div v-else-if="!ragStore.lowRatedSamples.length" class="evaluation-empty">当前没有低评分样本</div>
        <div v-else class="evaluation-sample-list">
          <div v-for="item in ragStore.lowRatedSamples.slice(0, 5)" :key="item.responseId" class="evaluation-sample-item">
            <div class="evaluation-sample-question">{{ item.question }}</div>
            <div class="evaluation-sample-meta">
              <span>反馈：{{ item.feedback || '未记录' }}</span>
              <span>证据负反馈：{{ item.evidenceNegativeCount }}</span>
              <span>{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <div v-if="item.comment" class="evaluation-sample-comment">{{ item.comment }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="failureReasonSummary.length" class="card section-spacing failure-summary-card">
      <div class="card-header">
        <div>
          <div class="card-title">失败原因聚合</div>
          <div class="card-subtitle">先看失败主要集中在哪类问题，再决定是重试、改文档还是检查后端服务。</div>
        </div>
      </div>
      <div class="failure-summary-list">
        <button v-for="item in failureReasonSummary" :key="item.label" class="failure-summary-item interactive-metric-card" type="button" @click="documentStatusFilter = 'FAILED'">
          <span class="failure-summary-label">{{ item.label }}</span>
          <strong class="failure-summary-count">{{ item.count }}</strong>
        </button>
      </div>
    </div>

    <div class="card section-spacing structured-summary-card">
      <div class="card-header">
        <div>
          <div class="card-title">结构化文件解析概览</div>
          <div class="card-subtitle">聚焦 CSV、JSON、XML 等结构化文件，确认当前知识库里哪些文档已经进入可检索链路。</div>
        </div>
      </div>
      <div class="structured-summary-grid">
        <div class="structured-summary-item">
          <span class="structured-summary-label">结构化文档</span>
          <strong class="structured-summary-value">{{ structuredDocumentCount }}</strong>
        </div>
        <div class="structured-summary-item">
          <span class="structured-summary-label">已索引结构化文档</span>
          <strong class="structured-summary-value">{{ indexedStructuredDocumentCount }}</strong>
        </div>
        <div class="structured-summary-item">
          <span class="structured-summary-label">待处理结构化文档</span>
          <strong class="structured-summary-value">{{ processingStructuredDocumentCount }}</strong>
        </div>
      </div>
      <div class="structured-type-list">
        <button
          v-for="item in structuredTypeSummary"
          :key="item.label"
          class="structured-type-chip"
          type="button"
          @click="focusStructuredType(item.extension)"
        >
          {{ item.label }} · {{ item.count }}
        </button>
        <span v-if="!structuredTypeSummary.length" class="structured-type-empty">当前知识库还没有结构化文件。</span>
      </div>
    </div>

    <div class="card section-spacing quick-actions-card">
      <div class="card-header">
        <div>
          <div class="card-title">快捷处理</div>
          <div class="card-subtitle">根据当前状态直接跳转到常用文档视图，减少来回筛选。</div>
        </div>
      </div>
      <div class="quick-actions">
        <button class="quick-action-btn" type="button" @click="documentStatusFilter = 'ALL'">查看全部文档</button>
        <button class="quick-action-btn" type="button" @click="documentStatusFilter = 'INDEXED'">只看已索引</button>
        <button class="quick-action-btn" type="button" @click="documentStatusFilter = 'PROCESSING'">跳到处理中</button>
        <button class="quick-action-btn warning" type="button" @click="documentStatusFilter = 'FAILED'">优先处理失败项</button>
      </div>
    </div>

    <div ref="onboardingRef" class="card onboarding-card section-spacing">
      <div class="card-header">
        <div>
          <div class="card-title">快速开始</div>
          <div class="card-subtitle">首次使用时，按下面 3 个步骤操作，可以更快拿到可验证的检索结果。</div>
        </div>
      </div>
      <div class="rag-onboarding-grid">
        <div class="rag-onboarding-item"><div class="rag-onboarding-index">1</div><div class="rag-onboarding-body"><div class="rag-onboarding-title">选择知识库</div><div class="rag-onboarding-desc">先确认当前业务空间，避免文档上传到错误的知识域。</div></div></div>
        <div class="rag-onboarding-item"><div class="rag-onboarding-index">2</div><div class="rag-onboarding-body"><div class="rag-onboarding-title">上传文档</div><div class="rag-onboarding-desc">建议等状态变成“已索引”后再提问，这样答案和证据会更完整。</div></div></div>
        <div class="rag-onboarding-item"><div class="rag-onboarding-index">3</div><div class="rag-onboarding-body"><div class="rag-onboarding-title">验证回答</div><div class="rag-onboarding-desc">结合证据分块和反馈结果，确认检索质量是否可靠。</div></div></div>
      </div>
    </div>

    <div ref="summaryRef" class="card section-spacing summary-card">
      <div class="card-header">
        <div>
          <div class="card-title">当前筛选摘要</div>
          <div class="card-subtitle">文档表格、上传跳转和证据定位都会复用这里的上下文。</div>
        </div>
        <div class="header-actions">
          <button v-for="item in filterChips" :key="item.value" class="filter-chip" :class="{ active: documentStatusFilter === item.value }" @click="documentStatusFilter = item.value">
            {{ item.label }}
          </button>
          <button class="btn btn-ghost btn-sm" :disabled="documentStatusFilter === 'ALL' && !highlightDocumentName && !highlightTerms.length" @click="clearHighlights">清空定位</button>
          <button class="btn btn-ghost btn-sm" :disabled="!highlightDocumentName && !highlightTerms.length" @click="copyHighlightSummary">复制定位摘要</button>
        </div>
      </div>
      <div class="summary-grid">
        <div class="summary-item"><span class="summary-label">当前知识库</span><span class="summary-value">{{ ragStore.currentKbName || '未选择' }}</span></div>
        <div class="summary-item"><span class="summary-label">文档状态</span><span class="summary-value">{{ statusLabelMap[documentStatusFilter] }}</span></div>
        <div class="summary-item"><span class="summary-label">高亮文档</span><span class="summary-value">{{ highlightDocumentName || '未定位' }}</span></div>
        <div class="summary-item"><span class="summary-label">高亮关键词</span><span class="summary-value">{{ highlightTerms.length ? highlightTerms.join('、') : '无' }}</span></div>
      </div>
    </div>

    <div class="card section-spacing">
      <div class="card-header">
        <div>
          <div class="card-title">步骤 1：选择知识库</div>
          <div class="card-subtitle">先确认当前操作范围，再在该范围内上传、重试、提问和检查证据。</div>
        </div>
      </div>
      <KnowledgeBaseGrid />
    </div>

    <div class="grid-2 section-spacing">
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">步骤 2：上传与处理</div>
            <div class="card-subtitle">支持上传文档、失败重试、索引重建，并保留最近上传任务记录。</div>
          </div>
        </div>
        <DocumentUpload @jump-status="handleJumpStatus" @focus-document="handleFocusDocument" />
      </div>
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">步骤 3：提问与验证</div>
            <div class="card-subtitle">查看回答、证据分块和反馈结果，验证知识库检索质量。</div>
          </div>
        </div>
        <RagQueryPanel @focus-document="handleFocusEvidenceDocument" />
      </div>
    </div>

    <div ref="tableRef">
      <DocumentTable
        :external-status="documentStatusFilter"
        :highlight-document-id="highlightDocumentId"
        :highlight-document-name="highlightDocumentName"
        :highlight-chunk-index="highlightChunkIndex"
        :highlight-terms="highlightTerms"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BackendStatusBanner from '@/components/common/BackendStatusBanner.vue'
import DocumentTable from '@/components/rag/DocumentTable.vue'
import DocumentUpload from '@/components/rag/DocumentUpload.vue'
import KnowledgeBaseGrid from '@/components/rag/KnowledgeBaseGrid.vue'
import RagQueryPanel from '@/components/rag/RagQueryPanel.vue'
import { useToast } from '@/composables/useToast'
import { useRagStore } from '@/stores/rag'

type DocumentStatusFilter = 'ALL' | 'INDEXED' | 'FAILED' | 'PROCESSING'

const statusLabelMap: Record<DocumentStatusFilter, string> = {
  ALL: '全部状态',
  INDEXED: '已索引',
  FAILED: '失败',
  PROCESSING: '处理中'
}

const ragStore = useRagStore()
const router = useRouter()
const route = useRoute()
const { showToast } = useToast()

const documentStatusFilter = ref<DocumentStatusFilter>('ALL')
const highlightDocumentId = ref('')
const highlightDocumentName = ref('')
const highlightChunkIndex = ref<number | null>(null)
const highlightTerms = ref<string[]>([])
const onboardingRef = ref<HTMLElement | null>(null)
const summaryRef = ref<HTMLElement | null>(null)
const tableRef = ref<HTMLElement | null>(null)
const entrySource = computed(() => typeof route.query.source === 'string' ? route.query.source : '')

const filterChips = [
  { label: '全部', value: 'ALL' },
  { label: '已索引', value: 'INDEXED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '失败', value: 'FAILED' }
] as const

const filteredDocumentCount = computed(() => documentStatusFilter.value === 'ALL' ? ragStore.documents.length : ragStore.documents.filter((item) => item.status === documentStatusFilter.value).length)
const failedDocumentCount = computed(() => ragStore.documents.filter((item) => item.status === 'FAILED').length)
const processingDocumentCount = computed(() => ragStore.documents.filter((item) => item.status === 'PROCESSING').length)
const structuredExtensions = ['csv', 'json', 'xml']
const structuredDocuments = computed(() => ragStore.documents.filter((item) => structuredExtensions.includes(resolveDocumentExtension(item.filename))))
const structuredDocumentCount = computed(() => structuredDocuments.value.length)
const indexedStructuredDocumentCount = computed(() => structuredDocuments.value.filter((item) => item.status === 'INDEXED').length)
const processingStructuredDocumentCount = computed(() => structuredDocuments.value.filter((item) => item.status === 'PROCESSING').length)
const structuredTypeSummary = computed(() =>
  structuredExtensions
    .map((extension) => ({
      extension,
      label: extension.toUpperCase(),
      count: structuredDocuments.value.filter((item) => resolveDocumentExtension(item.filename) === extension).length
    }))
    .filter((item) => item.count > 0)
)
const failureReasonSummary = computed(() => {
  const counts = new Map<string, number>()
  ragStore.documents.filter((item) => item.status === 'FAILED').forEach((item) => {
    const raw = item.errorMessage?.trim() || '未返回明确失败原因'
    const label = raw.length > 28 ? `${raw.slice(0, 28)}...` : raw
    counts.set(label, (counts.get(label) || 0) + 1)
  })
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4).map(([label, count]) => ({ label, count }))
})
const nextStepTip = computed(() => {
  if (!ragStore.currentKb) return '先选择知识库'
  if (!ragStore.documents.length) return '上传首批文档'
  if (ragStore.documents.some((item) => item.status === 'PROCESSING')) return '等待索引完成后再提问'
  if (ragStore.documents.some((item) => item.status === 'FAILED')) return '优先处理失败文档'
  return '可以直接开始问答验证'
})
const dashboardContextMessage = computed(() => {
  if (entrySource.value !== 'dashboard') return ''
  const parts: string[] = []
  if (documentStatusFilter.value !== 'ALL') parts.push(`已自动筛选为“${statusLabelMap[documentStatusFilter.value]}”`)
  if (ragStore.currentKbName) parts.push(`当前知识库为“${ragStore.currentKbName}”`)
  return parts.length ? `${parts.join('，')}，你可以直接继续批量处理、重建索引或检查上传结果。` : '当前页面来自总览页，你可以直接继续处理待关注文档。'
})

async function refreshData() {
  await ragStore.loadKnowledgeBases()
  await ragStore.refreshCurrentKnowledgeBase()
}

function formatRate(value: number) {
  return `${(value * 100).toFixed(1)}%`
}

function formatDateTime(value: string) {
  try {
    return value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-'
  } catch {
    return value
  }
}

function resolveDocumentExtension(filename: string) {
  const normalized = (filename || '').toLowerCase()
  const index = normalized.lastIndexOf('.')
  return index >= 0 ? normalized.slice(index + 1) : ''
}

function focusStructuredType(extension: string) {
  documentStatusFilter.value = 'ALL'
  highlightDocumentName.value = structuredDocuments.value.find((item) => resolveDocumentExtension(item.filename) === extension)?.filename || ''
  scrollToSection(tableRef)
}

async function copyOverview() {
  const lines = [
    '知识库管理概览',
    `当前知识库：${ragStore.currentKbName || '未选择'}`,
    `知识库数量：${ragStore.knowledgeBases.length}`,
    `文档数量：${ragStore.documents.length}`,
    `当前状态筛选：${statusLabelMap[documentStatusFilter.value]}`,
    `失败文档：${failedDocumentCount.value}`,
    `处理中：${processingDocumentCount.value}`,
    `总查询数：${ragStore.evaluationOverview.totalQueries}`,
    `正反馈率：${formatRate(ragStore.evaluationOverview.positiveFeedbackRate)}`,
    `证据正反馈率：${formatRate(ragStore.evaluationOverview.positiveEvidenceRate)}`,
    `下一步建议：${nextStepTip.value}`
  ]
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    showToast('已复制知识库概览')
  } catch {
    showToast('复制知识库概览失败')
  }
}

function scrollToSection(target: HTMLElement | null) {
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function focusSummaryAction(action: 'kb' | 'status' | 'documents' | 'next') {
  if (action === 'kb') return scrollToSection(onboardingRef.value)
  if (action === 'status') return scrollToSection(summaryRef.value)
  if (action === 'documents') return scrollToSection(tableRef.value)
  if (ragStore.documents.some((item) => item.status === 'FAILED')) {
    documentStatusFilter.value = 'FAILED'
  } else if (ragStore.documents.some((item) => item.status === 'PROCESSING')) {
    documentStatusFilter.value = 'PROCESSING'
  } else {
    documentStatusFilter.value = 'INDEXED'
  }
  scrollToSection(tableRef.value)
}

function normalizeStatusFilter(value?: string | null): DocumentStatusFilter {
  return value === 'INDEXED' || value === 'FAILED' || value === 'PROCESSING' ? value : 'ALL'
}

function syncRouteQuery() {
  router.replace({
    query: {
      ...route.query,
      kb: ragStore.currentKb || undefined,
      status: documentStatusFilter.value !== 'ALL' ? documentStatusFilter.value : undefined
    }
  })
}

function clearHighlights() {
  documentStatusFilter.value = 'ALL'
  highlightDocumentId.value = ''
  highlightDocumentName.value = ''
  highlightChunkIndex.value = null
  highlightTerms.value = []
}

async function copyHighlightSummary() {
  const lines = [
    '知识库定位摘要',
    `当前知识库：${ragStore.currentKbName || '未选择'}`,
    `文档状态：${statusLabelMap[documentStatusFilter.value]}`,
    `高亮文档：${highlightDocumentName.value || '未定位'}`,
    `高亮关键词：${highlightTerms.value.length ? highlightTerms.value.join('、') : '无'}`,
    typeof highlightChunkIndex.value === 'number' ? `高亮分段：${highlightChunkIndex.value}` : ''
  ].filter(Boolean)

  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    showToast('已复制定位摘要')
  } catch {
    showToast('复制定位摘要失败')
  }
}

function clearDashboardContext() {
  clearHighlights()
  router.replace({
    query: {
      ...route.query,
      source: undefined,
      status: undefined,
      kb: ragStore.currentKb || undefined
    }
  })
}

function handleJumpStatus(status: DocumentStatusFilter) {
  documentStatusFilter.value = status
}

function handleFocusDocument(payload: { documentId?: string; filename: string; status: DocumentStatusFilter }) {
  documentStatusFilter.value = payload.status
  highlightDocumentId.value = payload.documentId || ''
  highlightDocumentName.value = payload.filename
  highlightChunkIndex.value = null
  highlightTerms.value = []
}

function handleFocusEvidenceDocument(payload: { documentId?: string; filename: string; chunkIndex?: number; highlightTerms: string[] }) {
  documentStatusFilter.value = 'ALL'
  highlightDocumentId.value = payload.documentId || ''
  highlightDocumentName.value = payload.filename
  highlightChunkIndex.value = typeof payload.chunkIndex === 'number' ? payload.chunkIndex : null
  highlightTerms.value = payload.highlightTerms || []
}

watch(() => route.query, (query) => {
  const routeStatus = normalizeStatusFilter(typeof query.status === 'string' ? query.status : null)
  if (routeStatus !== documentStatusFilter.value) documentStatusFilter.value = routeStatus
  const routeKb = typeof query.kb === 'string' ? query.kb : ''
  if (routeKb && routeKb !== ragStore.currentKb && ragStore.knowledgeBases.some((kb) => kb.id === routeKb)) {
    ragStore.selectKb(routeKb)
  }
}, { immediate: true })

watch([() => ragStore.currentKb, documentStatusFilter], () => { syncRouteQuery() })

onMounted(async () => {
  await ragStore.loadKnowledgeBases()
  const routeKb = typeof route.query.kb === 'string' ? route.query.kb : ''
  if (routeKb && routeKb !== ragStore.currentKb && ragStore.knowledgeBases.some((kb) => kb.id === routeKb)) {
    ragStore.selectKb(routeKb)
  }
  documentStatusFilter.value = normalizeStatusFilter(typeof route.query.status === 'string' ? route.query.status : null)
  syncRouteQuery()
})
</script>

<style scoped>
.section-spacing { margin-bottom: 16px; }
.header-actions, .dashboard-context-actions, .quick-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dashboard-context-banner { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 16px; border-radius: 18px; border: 1px solid rgba(59, 130, 246, 0.2); background: linear-gradient(135deg, rgba(59, 130, 246, 0.12), rgba(255, 255, 255, 0.03)); }
.dashboard-context-title { color: var(--text); font-size: 13px; font-weight: 600; }
.dashboard-context-desc { margin-top: 6px; color: var(--text2); font-size: 12px; line-height: 1.6; }
.status-focus-grid, .evaluation-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.rag-onboarding-grid, .summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.summary-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.compact-summary-grid .summary-item { min-height: 112px; }
.summary-button, .status-focus-card, .failure-summary-item { width: 100%; text-align: left; cursor: pointer; }
.summary-button:hover, .status-focus-card:hover, .failure-summary-item:hover { transform: translateY(-2px); }
.status-focus-card { padding: 18px; border-radius: 16px; transition: transform var(--transition), border-color var(--transition); }
.status-focus-card.failed { border-color: rgba(239, 68, 68, 0.28); background: linear-gradient(180deg, rgba(239, 68, 68, 0.12), rgba(255, 255, 255, 0.03)); }
.status-focus-card.processing { border-color: rgba(245, 158, 11, 0.28); background: linear-gradient(180deg, rgba(245, 158, 11, 0.14), rgba(255, 255, 255, 0.03)); }
.status-focus-label, .summary-label { color: var(--text3); font-size: 12px; }
.status-focus-value { margin-top: 10px; color: var(--text); font-size: 34px; font-weight: 700; }
.status-focus-desc, .rag-onboarding-desc, .failure-summary-label { margin-top: 8px; color: var(--text2); font-size: 13px; line-height: 1.6; }
.evaluation-card { overflow: hidden; }
.evaluation-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.evaluation-metric { padding: 12px 14px; border: 1px solid var(--border); border-radius: 14px; background: rgba(255, 255, 255, 0.03); }
.evaluation-metric span { display: block; color: var(--text3); font-size: 12px; }
.evaluation-metric strong { display: block; margin-top: 6px; color: var(--text); font-size: 20px; }
.evaluation-loading, .evaluation-empty { color: var(--text3); font-size: 12px; }
.evaluation-sample-list { display: grid; gap: 10px; }
.evaluation-sample-item { padding: 12px 14px; border: 1px solid var(--border); border-radius: 14px; background: rgba(255, 255, 255, 0.02); }
.evaluation-sample-question { color: var(--text); font-size: 13px; font-weight: 600; }
.evaluation-sample-meta { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 6px; color: var(--text3); font-size: 12px; }
.evaluation-sample-comment { margin-top: 6px; color: var(--text2); font-size: 12px; }
.failure-summary-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.failure-summary-item { padding: 14px; border-radius: 14px; border: 1px solid var(--border); background: rgba(239, 68, 68, 0.06); transition: transform var(--transition), border-color var(--transition); }
.failure-summary-count { display: block; margin-top: 8px; color: var(--text); font-size: 22px; }
.structured-summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.structured-summary-item { padding: 14px; border-radius: 14px; border: 1px solid var(--border); background: rgba(16, 185, 129, 0.06); }
.structured-summary-label { display: block; font-size: 12px; color: var(--text3); }
.structured-summary-value { display: block; margin-top: 8px; font-size: 24px; color: var(--text); }
.structured-type-list { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.structured-type-chip { min-height: 34px; padding: 0 12px; border-radius: 999px; border: 1px solid var(--border); background: rgba(255, 255, 255, 0.04); color: var(--text2); cursor: pointer; }
.structured-type-empty { color: var(--text3); font-size: 12px; }
.quick-action-btn, .filter-chip { min-height: 36px; padding: 0 12px; border-radius: 12px; border: 1px solid var(--border); background: transparent; color: var(--text2); cursor: pointer; transition: all var(--transition); }
.quick-action-btn:hover, .filter-chip.active { border-color: var(--accent); background: var(--accent-dim); color: var(--accent2); }
.quick-action-btn.warning:hover { border-color: #f59e0b; background: rgba(245, 158, 11, 0.14); color: #b45309; }
.rag-onboarding-item, .summary-item { display: flex; gap: 12px; padding: 14px; border: 1px solid var(--border); border-radius: 14px; background: rgba(255, 255, 255, 0.03); }
.summary-item { flex-direction: column; gap: 6px; }
.summary-value { font-size: 13px; font-weight: 600; color: var(--text); line-height: 1.5; word-break: break-word; }
.rag-onboarding-index { width: 28px; height: 28px; border-radius: 999px; background: var(--accent-dim); color: var(--accent2); display: flex; align-items: center; justify-content: center; font-weight: 700; flex-shrink: 0; }
.rag-onboarding-title { font-size: 13px; font-weight: 600; color: var(--text); }
@media (max-width: 960px) {
  .rag-onboarding-grid, .summary-grid, .status-focus-grid, .evaluation-grid, .evaluation-metrics, .structured-summary-grid { grid-template-columns: 1fr; }
  .dashboard-context-banner { flex-direction: column; align-items: stretch; }
  .failure-summary-list { grid-template-columns: 1fr; }
}
@media (max-width: 680px) {
  .page-hero-actions, .header-actions, .quick-actions, .dashboard-context-actions { width: 100%; }
  .page-hero-actions .btn, .header-actions .btn, .quick-action-btn, .dashboard-context-actions .btn { width: 100%; justify-content: center; }
  .summary-item, .status-focus-card, .failure-summary-item { padding: 12px; }
  .compact-summary-grid .summary-item { min-height: 96px; }
}
</style>
