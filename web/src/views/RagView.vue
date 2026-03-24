<template>
  <div class="space-rag">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">知识库工作台</div>
        <div class="page-title">知识库管理</div>
        <div class="page-subtitle">围绕知识库选择、文档处理和问答验证，组织完整的检索增强工作流。</div>
        <div class="hero-tags">
          <span class="tag">{{ ragStore.knowledgeBases.length }} 个知识库</span>
          <span class="tag">{{ ragStore.documents.length }} 份文档</span>
          <span class="tag">{{ ragStore.currentKbName || '未选择知识库' }}</span>
        </div>
      </div>
      <div class="page-hero-actions">
        <button class="btn btn-ghost" @click="refreshData">刷新数据</button>
      </div>
    </div>

    <BackendStatusBanner
      service="rag"
      demo-message="当前知识库页面运行在演示模式，列表和结果来自本地模拟数据。"
      unavailable-message="知识库后端当前不可用，页面不会自动回退到模拟结果。"
    />

    <div class="card onboarding-card section-spacing">
      <div class="card-header">
        <div>
          <div class="card-title">快速开始</div>
          <div class="card-subtitle">首次使用时，按下面 3 个步骤操作，可以更快拿到可验证的检索结果。</div>
        </div>
      </div>
      <div class="rag-onboarding-grid">
        <div class="rag-onboarding-item">
          <div class="rag-onboarding-index">1</div>
          <div class="rag-onboarding-body">
            <div class="rag-onboarding-title">选择知识库</div>
            <div class="rag-onboarding-desc">先确认当前业务空间，避免文档上传到错误的知识域。</div>
          </div>
        </div>
        <div class="rag-onboarding-item">
          <div class="rag-onboarding-index">2</div>
          <div class="rag-onboarding-body">
            <div class="rag-onboarding-title">上传文档</div>
            <div class="rag-onboarding-desc">建议等状态变成“已索引”后再提问，这样答案和证据会更完整。</div>
          </div>
        </div>
        <div class="rag-onboarding-item">
          <div class="rag-onboarding-index">3</div>
          <div class="rag-onboarding-body">
            <div class="rag-onboarding-title">验证回答</div>
            <div class="rag-onboarding-desc">结合证据分块和反馈结果，确认检索质量是否可靠。</div>
          </div>
        </div>
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
        <RagQueryPanel />
      </div>
    </div>

    <DocumentTable
      :external-status="documentStatusFilter"
      :highlight-document-id="highlightDocumentId"
      :highlight-document-name="highlightDocumentName"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BackendStatusBanner from '@/components/common/BackendStatusBanner.vue'
import KnowledgeBaseGrid from '@/components/rag/KnowledgeBaseGrid.vue'
import DocumentUpload from '@/components/rag/DocumentUpload.vue'
import DocumentTable from '@/components/rag/DocumentTable.vue'
import RagQueryPanel from '@/components/rag/RagQueryPanel.vue'
import { useRagStore } from '@/stores/rag'

type DocumentStatusFilter = 'ALL' | 'INDEXED' | 'FAILED' | 'PROCESSING'

const ragStore = useRagStore()
const router = useRouter()
const route = useRoute()

const documentStatusFilter = ref<DocumentStatusFilter>('ALL')
const highlightDocumentId = ref('')
const highlightDocumentName = ref('')

async function refreshData() {
  await ragStore.loadKnowledgeBases()
  await ragStore.loadDocuments()
}

function normalizeStatusFilter(value?: string | null): DocumentStatusFilter {
  if (value === 'INDEXED' || value === 'FAILED' || value === 'PROCESSING') {
    return value
  }
  return 'ALL'
}

function syncRouteQuery() {
  const nextQuery = {
    ...route.query,
    kb: ragStore.currentKb || undefined,
    status: documentStatusFilter.value !== 'ALL' ? documentStatusFilter.value : undefined
  }

  router.replace({ query: nextQuery })
}

function handleJumpStatus(status: DocumentStatusFilter) {
  documentStatusFilter.value = status
}

function handleFocusDocument(payload: { documentId?: string; filename: string; status: DocumentStatusFilter }) {
  documentStatusFilter.value = payload.status
  highlightDocumentId.value = payload.documentId || ''
  highlightDocumentName.value = payload.filename
}

watch(
  () => route.query,
  (query) => {
    const routeStatus = normalizeStatusFilter(typeof query.status === 'string' ? query.status : null)
    if (routeStatus !== documentStatusFilter.value) {
      documentStatusFilter.value = routeStatus
    }

    const routeKb = typeof query.kb === 'string' ? query.kb : ''
    if (routeKb && routeKb !== ragStore.currentKb && ragStore.knowledgeBases.some((kb) => kb.id === routeKb)) {
      ragStore.selectKb(routeKb)
    }
  },
  { immediate: true }
)

watch([() => ragStore.currentKb, documentStatusFilter], () => {
  syncRouteQuery()
})

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
.section-spacing {
  margin-bottom: 16px;
}

.onboarding-card {
  overflow: hidden;
}

.rag-onboarding-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.rag-onboarding-item {
  display: flex;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.03);
}

.rag-onboarding-index {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: var(--accent-dim);
  color: var(--accent2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}

.rag-onboarding-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.rag-onboarding-desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text3);
  line-height: 1.6;
}

@media (max-width: 960px) {
  .rag-onboarding-grid {
    grid-template-columns: 1fr;
  }
}
</style>
