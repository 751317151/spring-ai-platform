<template>
  <div class="rag-detail-page">
    <section v-if="currentKnowledgeBase" class="rag-detail-shell">
      <aside class="rag-info-column">
        <div class="info-card info-card-primary">
          <div class="info-topbar">
            <button class="text-link" type="button" @click="router.push({ name: 'rag' })">
              返回知识库列表
            </button>
            <button class="ghost-toggle" type="button" @click="togglePanels">
              {{ panelsCollapsed ? '显示资料' : '收起资料' }}
            </button>
          </div>

          <div class="info-kicker">知识库</div>
          <h1 class="info-title">{{ currentKnowledgeBase.name }}</h1>
          <p class="info-desc">
            {{ currentKnowledgeBase.description || '围绕当前知识库直接提问，资料和文档操作都收在左侧。' }}
          </p>

          <div class="info-tags">
            <span class="info-tag strong">{{ currentKnowledgeBase.documentCount ?? 0 }} 份文档</span>
            <span class="info-tag">{{ currentKnowledgeBase.totalChunks ?? 0 }} 个分段</span>
            <span class="info-tag">{{ currentKnowledgeBase.department || '公共空间' }}</span>
          </div>

          <div class="info-actions">
            <button class="action-btn primary" type="button" @click="openEditModal">编辑知识库</button>
            <button class="action-btn" type="button" @click="openCreateModal">新建知识库</button>
            <button
              class="action-btn danger"
              type="button"
              :disabled="!canDeleteCurrent"
              :title="deleteDisabledReason"
              @click="handleDeleteCurrent"
            >
              删除知识库
            </button>
          </div>
        </div>

        <div v-if="!panelsCollapsed" class="info-stack">
          <section class="info-card">
            <div class="section-head">
              <div>
                <div class="section-title">基础信息</div>
                <div class="section-subtitle">只保留当前最有用的元数据。</div>
              </div>
            </div>

            <div class="summary-grid">
              <div class="summary-item">
                <span class="summary-label">状态</span>
                <strong class="summary-value">{{ currentKnowledgeBase.status || 'ACTIVE' }}</strong>
              </div>
              <div class="summary-item">
                <span class="summary-label">创建人</span>
                <strong class="summary-value">{{ currentKnowledgeBase.createdBy || '未记录' }}</strong>
              </div>
              <div class="summary-item">
                <span class="summary-label">可见范围</span>
                <strong class="summary-value">{{ currentKnowledgeBase.visibilityScope || 'DEPARTMENT' }}</strong>
              </div>
              <div class="summary-item">
                <span class="summary-label">分块策略</span>
                <strong class="summary-value">{{ chunkStrategyLabel }}</strong>
              </div>
            </div>
          </section>

          <details class="info-card collapsible-card">
            <summary class="collapsible-head">
              <div>
                <div class="section-title">上传文档</div>
                <div class="section-subtitle">需要更新知识库时再展开。</div>
              </div>
            </summary>
            <div class="collapsible-body">
              <DocumentUpload @jump-status="handleJumpStatus" @focus-document="handleFocusDocument" />
            </div>
          </details>

          <details class="info-card collapsible-card">
            <summary class="collapsible-head">
              <div>
                <div class="section-title">文档列表</div>
                <div class="section-subtitle">预览、重试、删除都收纳在这里。</div>
              </div>
            </summary>
            <div class="collapsible-body">
              <DocumentTable
                :external-status="documentStatusFilter"
                :highlight-document-id="highlightDocumentId"
                :highlight-document-name="highlightDocumentName"
                :highlight-chunk-index="highlightChunkIndex"
                :highlight-terms="highlightTerms"
              />
            </div>
          </details>
        </div>
      </aside>

      <main class="rag-chat-column">
        <div class="chat-card">
          <div class="chat-head">
            <div>
              <div class="chat-title">知识库问答</div>
              <div class="chat-subtitle">聊天窗口优先，其余操作都放到左侧收纳。</div>
            </div>
          </div>

          <div class="chat-body">
            <RagQueryPanel minimal @focus-document="handleFocusEvidenceDocument" />
          </div>
        </div>
      </main>
    </section>

    <section v-else class="rag-empty card">
      <div class="section-title">未找到知识库</div>
      <div class="section-subtitle">当前知识库不存在或已删除，请返回列表重新选择。</div>
      <button class="action-btn primary" type="button" @click="router.push({ name: 'rag' })">返回列表</button>
    </section>

    <KnowledgeBaseModal
      v-if="modalVisible"
      :knowledge-base-id="editingKnowledgeBaseId"
      @close="closeModal"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DocumentTable from '@/components/rag/DocumentTable.vue'
import DocumentUpload from '@/components/rag/DocumentUpload.vue'
import KnowledgeBaseModal from '@/components/rag/KnowledgeBaseModal.vue'
import RagQueryPanel from '@/components/rag/RagQueryPanel.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useToast } from '@/composables/useToast'
import { useRagStore } from '@/stores/rag'

type DocumentStatusFilter = 'ALL' | 'INDEXED' | 'FAILED' | 'PROCESSING'

const ragStore = useRagStore()
const route = useRoute()
const router = useRouter()
const { confirm } = useConfirm()
const { showToast } = useToast()

const documentStatusFilter = ref<DocumentStatusFilter>('ALL')
const highlightDocumentId = ref('')
const highlightDocumentName = ref('')
const highlightChunkIndex = ref<number | null>(null)
const highlightTerms = ref<string[]>([])
const panelsCollapsed = ref(false)
const modalVisible = ref(false)
const editingKnowledgeBaseId = ref<string | null>(null)

const kbId = computed(() => String(route.params.kbId || ''))
const currentKnowledgeBase = computed(() =>
  ragStore.knowledgeBases.find((kb) => String(kb.id) === kbId.value) || null
)
const canDeleteCurrent = computed(() =>
  Boolean(currentKnowledgeBase.value && (currentKnowledgeBase.value.documentCount ?? 0) === 0)
)
const deleteDisabledReason = computed(() => {
  if (!currentKnowledgeBase.value) return '请先选择知识库'
  if ((currentKnowledgeBase.value.documentCount ?? 0) > 0) return '请先删除当前知识库中的文档'
  return ''
})

const chunkStrategyLabel = computed(() => {
  if (!currentKnowledgeBase.value) return '默认策略'
  if (currentKnowledgeBase.value.chunkStrategy) return currentKnowledgeBase.value.chunkStrategy
  if (currentKnowledgeBase.value.chunkSize) {
    return `${currentKnowledgeBase.value.chunkSize} / overlap ${currentKnowledgeBase.value.chunkOverlap ?? 0}`
  }
  return '默认策略'
})

async function ensureKnowledgeBaseLoaded(targetKbId: string) {
  await ragStore.loadKnowledgeBases()
  if (!targetKbId) {
    await router.replace({ name: 'rag' })
    return
  }

  const exists = ragStore.knowledgeBases.some((kb) => String(kb.id) === targetKbId)
  if (!exists) {
    await router.replace({ name: 'rag' })
    return
  }

  if (String(ragStore.currentKb) !== targetKbId) {
    ragStore.selectKb(targetKbId)
  }
}

function togglePanels() {
  panelsCollapsed.value = !panelsCollapsed.value
}

function openCreateModal() {
  editingKnowledgeBaseId.value = null
  modalVisible.value = true
}

function openEditModal() {
  if (!currentKnowledgeBase.value) {
    return
  }
  editingKnowledgeBaseId.value = String(currentKnowledgeBase.value.id)
  modalVisible.value = true
}

function closeModal() {
  modalVisible.value = false
  editingKnowledgeBaseId.value = null
}

async function handleSaved() {
  const previousEditingId = editingKnowledgeBaseId.value
  closeModal()
  await ragStore.loadKnowledgeBases()
  const targetId = previousEditingId || String(ragStore.currentKb || '')
  if (targetId) {
    await router.replace({ name: 'rag-detail', params: { kbId: targetId } })
  }
  showToast(previousEditingId ? '知识库已更新' : '知识库已创建')
}

async function handleDeleteCurrent() {
  if (!currentKnowledgeBase.value) {
    return
  }
  const accepted = await confirm({
    title: '删除知识库',
    description: `确认删除“${currentKnowledgeBase.value.name}”吗？删除前需要先清空该知识库中的文档。`,
    confirmText: '删除',
    intent: 'danger'
  })
  if (!accepted) {
    return
  }

  const deletedId = String(currentKnowledgeBase.value.id)
  const ok = await ragStore.deleteKnowledgeBase(deletedId)
  if (!ok) {
    return
  }

  showToast('知识库已删除')
  if (ragStore.currentKb && String(ragStore.currentKb) !== deletedId) {
    await router.replace({ name: 'rag-detail', params: { kbId: String(ragStore.currentKb) } })
  } else {
    await router.replace({ name: 'rag' })
  }
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
  panelsCollapsed.value = false
}

function handleFocusEvidenceDocument(payload: { documentId?: string; filename: string; chunkIndex?: number; highlightTerms: string[] }) {
  documentStatusFilter.value = 'ALL'
  highlightDocumentId.value = payload.documentId || ''
  highlightDocumentName.value = payload.filename
  highlightChunkIndex.value = typeof payload.chunkIndex === 'number' ? payload.chunkIndex : null
  highlightTerms.value = payload.highlightTerms || []
  panelsCollapsed.value = false
}

watch(
  kbId,
  async (value) => {
    await ensureKnowledgeBaseLoaded(value)
  },
  { immediate: true }
)

onMounted(async () => {
  await ensureKnowledgeBaseLoaded(kbId.value)
})
</script>

<style scoped>
.rag-detail-page {
  display: grid;
  gap: 16px;
}

.rag-detail-shell {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
  min-height: calc(100vh - 124px);
}

.rag-info-column,
.rag-chat-column {
  min-height: 0;
}

.rag-info-column {
  position: sticky;
  top: 18px;
  display: grid;
  gap: 14px;
  max-height: calc(100vh - 118px);
  overflow-y: auto;
  padding-right: 4px;
}

.info-stack {
  display: grid;
  gap: 14px;
}

.info-card,
.chat-card {
  border: 1px solid var(--border);
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(26, 30, 38, 0.96), rgba(19, 22, 27, 0.98));
  box-shadow: 0 18px 40px rgba(2, 6, 23, 0.24);
}

.info-card {
  padding: 18px;
}

.info-card-primary {
  background:
    radial-gradient(circle at top left, rgba(16, 185, 129, 0.14), transparent 34%),
    linear-gradient(180deg, rgba(26, 30, 38, 0.98), rgba(19, 22, 27, 0.99));
}

.info-topbar,
.section-head,
.collapsible-head,
.chat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.text-link {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--accent2);
  cursor: pointer;
  font-size: 13px;
}

.ghost-toggle,
.action-btn {
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
  cursor: pointer;
  transition: border-color var(--transition), box-shadow var(--transition), transform var(--transition);
}

.ghost-toggle:hover,
.action-btn:hover {
  transform: translateY(-1px);
  border-color: rgba(16, 185, 129, 0.24);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.ghost-toggle {
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
}

.info-kicker,
.summary-label {
  color: var(--text3);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.info-title {
  margin: 12px 0 0;
  color: var(--text);
  font-size: 28px;
  line-height: 1.15;
}

.info-desc,
.section-subtitle,
.chat-subtitle {
  margin: 10px 0 0;
  color: var(--text2);
  font-size: 13px;
  line-height: 1.7;
}

.info-tags,
.info-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.info-tags {
  margin-top: 16px;
}

.info-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text2);
  font-size: 12px;
}

.info-tag.strong {
  border-color: rgba(16, 185, 129, 0.22);
  background: rgba(16, 185, 129, 0.1);
  color: var(--text);
}

.info-actions {
  margin-top: 16px;
}

.action-btn {
  min-height: 38px;
  padding: 0 14px;
  border-radius: 12px;
}

.action-btn.primary {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  border-color: transparent;
  color: #fff;
}

.action-btn.danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.section-title,
.chat-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 700;
}

.summary-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.summary-item {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.04);
}

.summary-value {
  display: block;
  margin-top: 6px;
  color: var(--text);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.collapsible-card {
  padding: 0;
  overflow: hidden;
}

.collapsible-head {
  list-style: none;
  padding: 16px 18px;
  cursor: pointer;
}

.collapsible-head::-webkit-details-marker {
  display: none;
}

.collapsible-body {
  padding: 0 16px 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}

.chat-card {
  min-height: calc(100vh - 124px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
}

.chat-head {
  padding: 18px 22px 14px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
}

.chat-body {
  min-height: 0;
  padding: 0 12px 12px;
  overflow: hidden;
}

.rag-empty {
  display: grid;
  gap: 12px;
  justify-items: start;
  padding: 22px;
}

@media (max-width: 1080px) {
  .rag-detail-shell {
    grid-template-columns: 300px minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .rag-detail-shell {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .rag-info-column {
    position: static;
    max-height: none;
    overflow: visible;
  }

  .chat-card {
    min-height: calc(100vh - 220px);
  }
}

@media (max-width: 680px) {
  .info-topbar,
  .chat-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .info-title {
    font-size: 24px;
  }
}
</style>
