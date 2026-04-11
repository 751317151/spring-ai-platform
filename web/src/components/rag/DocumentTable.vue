<template>
  <div class="card doc-section">
    <div class="card-header">
      <div>
        <div class="card-title">文档列表</div>
        <div class="card-subtitle">当前知识库共 {{ ragStore.documents.length }} 个文档，可按状态筛选、搜索、查看分段和执行批量操作。</div>
      </div>
      <button class="btn btn-ghost btn-sm" :disabled="ragStore.loadingDocuments" @click="ragStore.loadDocuments()">
        {{ ragStore.loadingDocuments ? '刷新中...' : '刷新列表' }}
      </button>
    </div>

    <div v-if="ragStore.documents.length" class="doc-tools">
      <div class="doc-filter-row">
        <div class="doc-filter-group">
          <button v-for="item in statusFilters" :key="item.value" class="doc-filter-chip" :class="{ active: activeStatus === item.value }" type="button" @click="setActiveStatus(item.value)">
            {{ item.label }} <span>{{ item.count }}</span>
          </button>
        </div>
        <div class="doc-filter-summary">
          <span>{{ visibleDocuments.length }} 个可见</span>
          <span>{{ selectedDocIds.length }} 个已选</span>
          <span>{{ reindexableSelectedCount }} 个可重建索引</span>
        </div>
      </div>
      <div class="doc-search-row">
        <input v-model.trim="searchKeyword" class="doc-search-input" type="text" placeholder="按文件名、上传人或文件类型搜索">
        <button v-if="searchKeyword" class="doc-search-clear" type="button" @click="searchKeyword = ''">清空</button>
      </div>
    </div>

    <div v-if="highlightSummary" class="doc-highlight-banner">
      <span>{{ highlightSummary }}</span>
      <button class="doc-highlight-btn" type="button" @click="clearHighlightState">清除定位</button>
    </div>

    <div v-if="statusActionTitle" class="status-action-banner" :class="`status-${activeStatus.toLowerCase()}`">
      <div class="status-action-copy">
        <div class="status-action-title">{{ statusActionTitle }}</div>
        <div class="status-action-desc">{{ statusActionDesc }}</div>
      </div>
      <div class="status-action-buttons">
        <button v-if="activeStatus === 'FAILED' && visibleDocuments.length" class="btn btn-ghost btn-sm" type="button" :disabled="batchBusy" @click="selectedDocIds = visibleDocuments.map((doc) => doc.id)">全选失败项</button>
        <button v-if="activeStatus === 'FAILED'" class="btn btn-ghost btn-sm" type="button" :disabled="batchBusy" @click="handleBatchReindex">一键重建失败索引</button>
        <button v-if="activeStatus === 'PROCESSING'" class="btn btn-ghost btn-sm" type="button" :disabled="ragStore.loadingDocuments" @click="ragStore.loadDocuments()">刷新处理状态</button>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="batchBusy" @click="resetFilters">查看全部</button>
      </div>
    </div>

    <div v-if="selectedDocIds.length" class="bulk-toolbar">
      <div class="bulk-info">已选择 {{ selectedDocIds.length }} 个文档</div>
      <div class="bulk-actions">
        <button class="btn btn-ghost btn-sm" :disabled="batchBusy" @click="selectedDocIds = []">清空</button>
        <button class="btn btn-ghost btn-sm" :disabled="batchBusy" @click="handleBatchReindex">{{ batchBusy && batchAction === 'reindex' ? '提交中...' : '批量重建索引' }}</button>
        <button class="btn btn-danger btn-sm" :disabled="batchBusy" @click="handleBatchDelete">{{ batchBusy && batchAction === 'delete' ? '删除中...' : '批量删除' }}</button>
      </div>
    </div>

    <div v-if="lastBatchMessage" class="batch-feedback" :class="lastBatchState">{{ lastBatchMessage }}</div>

    <SkeletonBlock v-if="ragStore.loadingDocuments" :count="4" :height="58" />
    <EmptyState v-else-if="ragStore.documentError" icon="D" title="文档加载失败" :description="ragStore.documentError" action-text="重试" @action="ragStore.loadDocuments()" />
    <EmptyState v-else-if="!ragStore.documents.length" icon="DOC" :title="ragStore.currentKb ? '暂无文档' : '请先选择知识库'" :description="ragStore.currentKb ? '先上传文档，随后可在这里预览、重试和查看分段内容。' : '选择知识库后才能进行文档操作。'" :action-text="ragStore.currentKb ? '刷新' : undefined" @action="ragStore.loadDocuments()" />
    <EmptyState v-else-if="!visibleDocuments.length" icon="F" title="当前筛选条件下没有文档" description="可以切换其他状态筛选、清空关键词，或显示全部文档。" action-text="显示全部" @action="resetFilters" />

    <table v-else class="doc-table">
      <thead>
        <tr>
          <th class="check-col"><input type="checkbox" :checked="allSelected" :indeterminate.prop="indeterminate" @change="toggleAll(($event.target as HTMLInputElement).checked)"></th>
          <th>文件</th><th>大小</th><th>分段数</th><th>上传人</th><th>时间</th><th>状态</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <template v-for="doc in visibleDocuments" :key="doc.id">
          <tr :data-doc-id="doc.id" :class="{ 'doc-row-highlight': isHighlighted(doc) }">
            <td class="check-col"><input type="checkbox" :checked="selectedDocIds.includes(doc.id)" @change="toggleOne(doc.id, ($event.target as HTMLInputElement).checked)"></td>
            <td><div class="doc-file-cell"><div class="doc-file-name">{{ doc.filename }}</div><div class="doc-file-meta">{{ doc.contentType || '未知类型' }}</div></div></td>
            <td>{{ formatFileSize(doc.fileSize ?? 0) }}</td>
            <td>{{ doc.chunkCount ?? '-' }}</td>
            <td>{{ doc.uploadedBy || '-' }}</td>
            <td class="doc-date"><div class="doc-time-main">{{ formatTime(doc.indexedAt || doc.createdAt) }}</div><div class="doc-time-sub">{{ doc.indexedAt ? `索引于 ${formatTime(doc.indexedAt)}` : `创建于 ${formatTime(doc.createdAt)}` }}</div></td>
            <td><span class="pill" :class="statusClass(doc.status)">{{ statusLabel(doc.status) }}</span></td>
            <td>
              <div class="doc-actions">
                <button class="doc-action-btn" :disabled="isBusy(doc.id) || batchBusy" @click="handlePreview(doc.id)">预览</button>
                <button class="doc-action-btn" :disabled="isBusy(doc.id) || batchBusy" @click="handleDownload(doc.id, doc.filename)">下载</button>
                <button v-if="doc.status === 'INDEXED' && (doc.chunkCount ?? 0) > 0" class="doc-action-btn" :disabled="isBusy(doc.id) || batchBusy" @click="openChunkPreview(doc)">分段</button>
                <button v-if="doc.status === 'FAILED'" class="doc-action-btn" :disabled="isBusy(doc.id) || batchBusy" @click="handleRetry(doc.id)">{{ busyAction[doc.id] === 'retry' ? '重试中...' : '重试' }}</button>
                <button v-if="doc.status === 'INDEXED' || doc.status === 'FAILED'" class="doc-action-btn" :disabled="isBusy(doc.id) || batchBusy" @click="handleReindex(doc.id)">{{ busyAction[doc.id] === 'reindex' ? '提交中...' : '重建索引' }}</button>
                <button class="doc-action-btn danger" :disabled="isBusy(doc.id) || batchBusy" @click="handleDelete(doc.id, doc.filename)">{{ busyAction[doc.id] === 'delete' ? '删除中...' : '删除' }}</button>
              </div>
            </td>
          </tr>
          <tr v-if="doc.errorMessage" class="doc-error-row"><td colspan="8"><div class="doc-error-message"><strong>失败原因：</strong>{{ doc.errorMessage }}</div></td></tr>
        </template>
      </tbody>
    </table>

    <div v-if="chunkModalVisible" class="chunk-modal-mask">
      <div class="chunk-modal">
        <div class="chunk-modal-header">
          <div>
            <div class="chunk-modal-title">文档分段预览</div>
            <div class="chunk-modal-subtitle">{{ ragStore.activeChunkDocument?.filename }}<span v-if="ragStore.activeDocumentChunks.length">共 {{ ragStore.activeDocumentChunks.length }} 个分段</span></div>
          </div>
          <div class="chunk-modal-actions">
            <button class="btn btn-ghost btn-sm" :disabled="!currentChunk" @click="copyCurrentChunk">复制当前分段</button>
            <button class="btn btn-ghost btn-sm" @click="closeChunkPreview">关闭</button>
          </div>
        </div>
        <div v-if="ragStore.activeDocumentChunks.length" class="chunk-list">
          <div class="chunk-overview">
            <div class="chunk-overview-card"><div class="chunk-overview-label">当前分段</div><div class="chunk-overview-value">{{ currentChunk?.chunkIndex ?? '-' }} / {{ ragStore.activeDocumentChunks.length }}</div></div>
            <div class="chunk-overview-card"><div class="chunk-overview-label">字符数</div><div class="chunk-overview-value">{{ currentChunkCharCount }}</div></div>
            <div class="chunk-overview-card"><div class="chunk-overview-label">查看范围</div><div class="chunk-overview-value">{{ currentChunkWindow }}</div></div>
          </div>
          <div class="chunk-nav">
            <button class="doc-action-btn" :disabled="activeChunkIndex <= 0" @click="goToPreviousChunk">上一个</button>
            <div class="chunk-nav-track">
              <button v-for="(chunk, idx) in ragStore.activeDocumentChunks" :key="chunk.id" class="chunk-nav-pill" :class="{ active: idx === activeChunkIndex }" @click="activeChunkIndex = idx">{{ chunk.chunkIndex }}</button>
            </div>
            <button class="doc-action-btn" :disabled="activeChunkIndex >= ragStore.activeDocumentChunks.length - 1" @click="goToNextChunk">下一个</button>
          </div>
          <div v-if="currentChunk" class="chunk-card active">
            <div class="chunk-card-meta"><span>分段 {{ currentChunk.chunkIndex }}</span><span>{{ currentChunkCharCount }} 字</span></div>
            <div class="chunk-card-preview" v-html="highlightChunkText(currentChunk.preview || currentChunk.content)"></div>
            <details class="chunk-card-details" open><summary>查看完整内容</summary><pre v-html="highlightedChunkContent"></pre></details>
          </div>
        </div>
        <div v-else class="chunk-empty">当前文档暂无可用分段数据。</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import type { DocumentMeta } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import { useRagStore } from '@/stores/rag'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { formatFileSize, formatTime } from '@/utils/format'

type DocumentStatusFilter = 'ALL' | 'INDEXED' | 'FAILED' | 'PROCESSING'

const props = withDefaults(defineProps<{ externalStatus?: DocumentStatusFilter; highlightDocumentId?: string; highlightDocumentName?: string; highlightChunkIndex?: number | null; highlightTerms?: string[] }>(), {
  externalStatus: 'ALL',
  highlightDocumentId: '',
  highlightDocumentName: '',
  highlightChunkIndex: null,
  highlightTerms: () => []
})

const ragStore = useRagStore()
const { showToast } = useToast()
const { confirm } = useConfirm()
const chunkModalVisible = ref(false)
const selectedDocIds = ref<string[]>([])
const activeStatus = ref<DocumentStatusFilter>('ALL')
const searchKeyword = ref('')
const batchBusy = ref(false)
const batchAction = ref<'delete' | 'reindex' | ''>('')
const lastBatchMessage = ref('')
const lastBatchState = ref<'success' | 'warning'>('success')
const busyAction = reactive<Record<string, '' | 'delete' | 'retry' | 'reindex' | 'download' | 'preview'>>({})
const activeChunkIndex = ref(0)
const localHighlightDocumentId = ref('')
const localHighlightDocumentName = ref('')
const highlightDismissed = ref(false)

const highlightSummary = computed(() => highlightDismissed.value ? '' : (props.highlightDocumentName || localHighlightDocumentName.value) ? `已定位到文档“${props.highlightDocumentName || localHighlightDocumentName.value}”，你可以直接查看状态、预览或操作该文档。` : '')
const statusActionTitle = computed(() => activeStatus.value === 'FAILED' ? (visibleDocuments.value.length ? `当前聚焦 ${visibleDocuments.value.length} 个失败文档` : '当前失败筛选下没有文档') : activeStatus.value === 'PROCESSING' ? (visibleDocuments.value.length ? `当前有 ${visibleDocuments.value.length} 个文档仍在处理中` : '当前处理中筛选下没有文档') : '')
const statusActionDesc = computed(() => activeStatus.value === 'FAILED' ? '可以先全选失败项，再批量重建索引或逐个检查失败原因。' : activeStatus.value === 'PROCESSING' ? '建议刷新列表确认处理进度，避免长时间卡在索引队列。' : '')
const visibleDocuments = computed(() => {
  const search = searchKeyword.value.trim().toLowerCase()
  const base = activeStatus.value === 'ALL' ? ragStore.documents : ragStore.documents.filter((doc) => normalizeStatus(doc.status) === activeStatus.value)
  return search ? base.filter((doc) => `${doc.filename} ${doc.uploadedBy || ''} ${doc.contentType || ''}`.toLowerCase().includes(search)) : base
})
const allSelected = computed(() => visibleDocuments.value.length > 0 && visibleDocuments.value.every((doc) => selectedDocIds.value.includes(doc.id)))
const indeterminate = computed(() => selectedDocIds.value.length > 0 && !allSelected.value)
const reindexableSelectedCount = computed(() => ragStore.documents.filter((doc) => selectedDocIds.value.includes(doc.id) && ['INDEXED', 'FAILED'].includes(normalizeStatus(doc.status))).length)
const statusFilters = computed(() => [
  { value: 'ALL' as const, label: '全部', count: ragStore.documents.length },
  { value: 'INDEXED' as const, label: '已索引', count: ragStore.documents.filter((doc) => normalizeStatus(doc.status) === 'INDEXED').length },
  { value: 'FAILED' as const, label: '失败', count: ragStore.documents.filter((doc) => normalizeStatus(doc.status) === 'FAILED').length },
  { value: 'PROCESSING' as const, label: '处理中', count: ragStore.documents.filter((doc) => normalizeStatus(doc.status) === 'PROCESSING').length }
])
const currentChunk = computed(() => ragStore.activeDocumentChunks[activeChunkIndex.value] || null)
const currentChunkCharCount = computed(() => currentChunk.value?.charCount ?? currentChunk.value?.content.length ?? '-')
const currentChunkWindow = computed(() => !ragStore.activeDocumentChunks.length ? '-' : `${Math.max(activeChunkIndex.value - 1, 0) + 1}-${Math.min(activeChunkIndex.value + 1, ragStore.activeDocumentChunks.length - 1) + 1}`)
const highlightedChunkContent = computed(() => currentChunk.value ? highlightChunkText(currentChunk.value.content) : '')

watch(() => props.externalStatus, (status) => { if (status && status !== activeStatus.value) activeStatus.value = status }, { immediate: true })
watch(() => ragStore.documents, (docs) => { const allowed = new Set(docs.map((doc) => doc.id)); selectedDocIds.value = selectedDocIds.value.filter((id) => allowed.has(id)) }, { deep: true })
watch(() => [props.highlightDocumentId, props.highlightDocumentName, ragStore.documents.length] as const, async ([highlightId, highlightName]) => {
  const target = ragStore.documents.find((doc) => isMatchingDocument(doc, highlightId, highlightName))
  if (!target) return
  highlightDismissed.value = false
  localHighlightDocumentId.value = target.id
  localHighlightDocumentName.value = target.filename
  searchKeyword.value = target.filename
  if (activeStatus.value !== 'ALL' && normalizeStatus(target.status) !== activeStatus.value) activeStatus.value = normalizeStatus(target.status)
  await nextTick()
  document.querySelector<HTMLElement>(`[data-doc-id="${target.id}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}, { immediate: true })
watch(() => [props.highlightDocumentId, props.highlightChunkIndex, ragStore.documents.length] as const, async ([highlightId, highlightChunkIndex]) => {
  if (!highlightId || highlightChunkIndex == null) return
  const target = ragStore.documents.find((doc) => doc.id === highlightId)
  if (!target) return
  const ok = await ragStore.loadDocumentChunks(target)
  if (!ok) return
  const chunkPosition = ragStore.activeDocumentChunks.findIndex((chunk) => chunk.chunkIndex === highlightChunkIndex)
  activeChunkIndex.value = chunkPosition >= 0 ? chunkPosition : 0
  chunkModalVisible.value = true
}, { immediate: true })
watch(() => chunkModalVisible.value, (visible) => { if (!visible) activeChunkIndex.value = 0 })

function normalizeStatus(status?: string) { return status === 'INDEXED' || status === 'FAILED' || status === 'PROCESSING' ? status : 'PROCESSING' }
function resetFilters() { activeStatus.value = 'ALL'; searchKeyword.value = '' }
function setActiveStatus(status: DocumentStatusFilter) { activeStatus.value = status }
function clearHighlightState() { highlightDismissed.value = true; localHighlightDocumentId.value = ''; localHighlightDocumentName.value = ''; searchKeyword.value = '' }
function escapeHtml(value: string) { return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;') }
function escapeRegExp(value: string) { return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') }
function highlightChunkText(value: string) {
  let output = escapeHtml(value)
  for (const term of props.highlightTerms) {
    if (!term?.trim()) continue
    output = output.replace(new RegExp(`(${escapeRegExp(term.trim())})`, 'gi'), '<mark class="chunk-highlight">$1</mark>')
  }
  return output
}
function isMatchingDocument(doc: DocumentMeta, highlightId?: string, highlightName?: string) { return (highlightId && doc.id === highlightId) || Boolean(highlightName && doc.filename === highlightName) }
function isHighlighted(doc: DocumentMeta) { return !highlightDismissed.value && isMatchingDocument(doc, props.highlightDocumentId || localHighlightDocumentId.value, props.highlightDocumentName || localHighlightDocumentName.value) }
function toggleAll(checked: boolean) { selectedDocIds.value = checked ? visibleDocuments.value.map((doc) => doc.id) : [] }
function toggleOne(id: string, checked: boolean) { selectedDocIds.value = checked ? [...selectedDocIds.value, id] : selectedDocIds.value.filter((item) => item !== id) }
function statusClass(status?: string) { return status === 'INDEXED' ? 'green' : status === 'FAILED' ? 'red' : 'amber' }
function statusLabel(status?: string) { return status === 'INDEXED' ? '已索引' : status === 'FAILED' ? '失败' : '处理中' }
function isBusy(docId: string) { return Boolean(busyAction[docId]) }
function setBusy(docId: string, action: '' | 'delete' | 'retry' | 'reindex' | 'download' | 'preview') { busyAction[docId] = action }
function setBatchResult(message: string, state: 'success' | 'warning') { lastBatchMessage.value = message; lastBatchState.value = state }
async function handleDelete(docId: string, filename: string) {
  const accepted = await confirm({ title: '删除文档', description: `确认删除“${filename}”吗？删除后如需恢复，需要重新上传。`, confirmText: '删除', intent: 'danger' })
  if (!accepted) return
  setBusy(docId, 'delete')
  const ok = await ragStore.deleteDocument(docId)
  setBusy(docId, '')
  if (ok) { selectedDocIds.value = selectedDocIds.value.filter((item) => item !== docId); showToast(`${filename} 已删除`) }
}
async function handleBatchDelete() {
  const accepted = await confirm({ title: '批量删除文档', description: `确认删除已选择的 ${selectedDocIds.value.length} 个文档吗？该操作不可撤销。`, confirmText: '删除', intent: 'danger' })
  if (!accepted) return
  batchBusy.value = true; batchAction.value = 'delete'
  const results = await Promise.all(selectedDocIds.value.map((id) => ragStore.deleteDocument(id)))
  batchBusy.value = false; batchAction.value = ''
  const successCount = results.filter(Boolean).length
  selectedDocIds.value = []
  setBatchResult(successCount === results.length ? `已删除 ${successCount} 个文档。` : `已删除 ${successCount} 个文档，部分删除失败。`, successCount === results.length ? 'success' : 'warning')
  showToast(successCount === results.length ? `已删除 ${successCount} 个文档` : `已删除 ${successCount} 个文档，部分失败`)
}
async function handleRetry(docId: string) { setBusy(docId, 'retry'); const ok = await ragStore.retryDocument(docId); setBusy(docId, ''); if (ok) showToast('已提交重试任务') }
async function handleReindex(docId: string) { setBusy(docId, 'reindex'); const ok = await ragStore.reindexDocument(docId); setBusy(docId, ''); if (ok) showToast('已提交重建索引任务') }
async function handleBatchReindex() {
  const ids = ragStore.documents.filter((doc) => selectedDocIds.value.includes(doc.id) && ['INDEXED', 'FAILED'].includes(normalizeStatus(doc.status))).map((doc) => doc.id)
  if (!ids.length) return showToast('当前所选文档中没有可重建索引的项目')
  batchBusy.value = true; batchAction.value = 'reindex'
  const results = await Promise.all(ids.map((id) => ragStore.reindexDocument(id)))
  batchBusy.value = false; batchAction.value = ''
  const successCount = results.filter(Boolean).length
  setBatchResult(successCount === results.length ? `已提交 ${successCount} 个文档的重建索引任务。` : `已提交 ${successCount} 个文档的重建索引任务，部分请求失败。`, successCount === results.length ? 'success' : 'warning')
  showToast(successCount === results.length ? `已提交 ${successCount} 个重建索引任务` : `已提交 ${successCount} 个重建索引任务，部分失败`)
}
async function handleDownload(docId: string, filename: string) { setBusy(docId, 'download'); const ok = await ragStore.downloadDocument(docId, filename); setBusy(docId, ''); if (!ok) showToast('下载失败，请重试') }
async function handlePreview(docId: string) { setBusy(docId, 'preview'); const ok = await ragStore.previewDocument(docId); setBusy(docId, ''); if (!ok) showToast('预览失败，请重试') }
async function openChunkPreview(doc: DocumentMeta) { const ok = await ragStore.loadDocumentChunks(doc); if (ok) { activeChunkIndex.value = 0; chunkModalVisible.value = true } }
function closeChunkPreview() { chunkModalVisible.value = false; activeChunkIndex.value = 0; ragStore.clearDocumentChunks() }
function goToPreviousChunk() { activeChunkIndex.value = Math.max(activeChunkIndex.value - 1, 0) }
function goToNextChunk() { activeChunkIndex.value = Math.min(activeChunkIndex.value + 1, ragStore.activeDocumentChunks.length - 1) }
async function copyCurrentChunk() {
  if (!currentChunk.value) return
  const payload = [`文档：${ragStore.activeChunkDocument?.filename || '-'}`, `分段：${currentChunk.value.chunkIndex}`, currentChunk.value.content].join('\n')
  try { await navigator.clipboard.writeText(payload); showToast('已复制分段内容') } catch { showToast('复制分段内容失败') }
}
</script>

<style scoped>
.check-col { width: 42px; }
.doc-tools { display: grid; gap: 12px; margin-bottom: 14px; }
.doc-filter-row, .doc-filter-group, .doc-search-row, .bulk-toolbar, .bulk-actions, .chunk-modal-header, .chunk-nav { display: flex; gap: 8px; flex-wrap: wrap; }
.doc-filter-row, .bulk-toolbar, .chunk-modal-header, .chunk-nav { align-items: center; justify-content: space-between; }
.doc-filter-chip, .doc-search-clear, .doc-highlight-btn, .doc-action-btn, .chunk-nav-pill { border: 1px solid var(--border); background: rgba(255,255,255,0.03); color: var(--text2); border-radius: 999px; cursor: pointer; }
.doc-filter-chip { display: inline-flex; align-items: center; gap: 6px; padding: 6px 11px; font-size: 12px; transition: all var(--transition); }
.doc-filter-chip.active, .doc-action-btn:hover:not(:disabled), .chunk-nav-pill.active { border-color: var(--accent); color: var(--accent2); background: var(--accent-dim); }
.doc-filter-summary { display: flex; gap: 12px; flex-wrap: wrap; color: var(--text3); font-size: 12px; }
.doc-search-row { padding: 10px 12px; border-radius: 16px; border: 1px solid var(--border); background: rgba(255,255,255,0.025); align-items: center; }
.doc-search-input { flex: 1; min-width: 0; height: 36px; border-radius: 12px; border: 1px solid var(--border); background: var(--surface2); color: var(--text); padding: 0 12px; outline: none; }
.doc-highlight-banner, .bulk-toolbar, .batch-feedback, .chunk-overview-card, .chunk-card { border: 1px solid var(--border); border-radius: 14px; }
.status-action-banner { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; padding: 14px 16px; border-radius: 16px; border: 1px solid var(--border); background: rgba(255,255,255,0.03); }
.status-action-banner.status-failed { border-color: rgba(239,68,68,0.24); background: linear-gradient(135deg, rgba(239,68,68,0.1), rgba(255,255,255,0.03)); }
.status-action-banner.status-processing { border-color: rgba(245,158,11,0.24); background: linear-gradient(135deg, rgba(245,158,11,0.12), rgba(255,255,255,0.03)); }
.status-action-title { color: var(--text); font-size: 13px; font-weight: 600; }
.status-action-desc { margin-top: 6px; color: var(--text2); font-size: 12px; line-height: 1.6; }
.status-action-buttons { display: flex; gap: 8px; flex-wrap: wrap; }
.doc-highlight-banner { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; padding: 12px 14px; background: linear-gradient(135deg, rgba(59,130,246,0.1), rgba(255,255,255,0.02)); color: var(--text2); font-size: 12px; }
.bulk-toolbar { margin-bottom: 12px; padding: 14px 16px; background: linear-gradient(180deg, rgba(16,185,129,0.05), rgba(255,255,255,0.02)); }
.batch-feedback { margin-bottom: 12px; padding: 12px 14px; font-size: 12px; }
.batch-feedback.success { background: rgba(13,148,136,0.08); color: #0f766e; border-color: rgba(13,148,136,0.2); }
.batch-feedback.warning { background: rgba(245,158,11,0.08); color: #b45309; border-color: rgba(245,158,11,0.2); }
.doc-file-cell { display: flex; flex-direction: column; gap: 3px; }
.doc-file-name { color: var(--text); font-family: var(--mono); max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.doc-file-meta, .doc-time-sub { font-size: 11px; color: var(--text3); }
.doc-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.doc-action-btn { padding: 5px 10px; font-size: 11px; transition: all var(--transition); }
.doc-action-btn.danger { color: #d93025; }
.doc-error-row td { padding-top: 0; }
.doc-row-highlight { box-shadow: inset 3px 0 0 var(--accent); background: color-mix(in srgb, var(--accent-dim) 60%, transparent); }
.doc-error-message { border-left: 3px solid #f04438; background: #fff3f2; color: #b42318; padding: 8px 10px; font-size: 12px; }
.chunk-modal-mask { position: fixed; inset: 0; z-index: 1600; background: rgba(15,23,42,0.54); display: flex; align-items: center; justify-content: center; padding: 24px; }
.chunk-modal { width: min(920px, 100%); max-height: calc(100vh - 48px); overflow: auto; background: var(--surface); border: 1px solid var(--border); border-radius: 22px; box-shadow: 0 20px 60px rgba(15,23,42,0.22); padding: 22px; }
.chunk-modal-title { font-size: 16px; font-weight: 600; color: var(--text); }
.chunk-modal-subtitle { margin-top: 6px; color: var(--text3); font-size: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
.chunk-overview, .chunk-list { display: grid; gap: 12px; }
.chunk-overview { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.chunk-overview-card { padding: 12px 14px; background: rgba(255,255,255,0.02); }
.chunk-overview-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--text3); }
.chunk-overview-value { margin-top: 6px; color: var(--text); font-size: 14px; font-weight: 600; }
.chunk-nav-track { display: flex; gap: 8px; flex: 1; overflow-x: auto; padding-bottom: 4px; }
.chunk-nav-pill { padding: 5px 10px; font-size: 12px; white-space: nowrap; }
.chunk-card { background: var(--bg); padding: 16px; }
.chunk-card-meta { display: flex; justify-content: space-between; gap: 12px; color: var(--text3); font-size: 12px; margin-bottom: 8px; }
.chunk-card-preview, .chunk-card-details pre { white-space: pre-wrap; word-break: break-word; }
.chunk-card-details summary { cursor: pointer; color: var(--accent2); font-size: 12px; }
.chunk-card-details pre { margin: 10px 0 0; padding: 14px; border-radius: 12px; background: #0f172a; color: #e2e8f0; font-size: 12px; line-height: 1.6; }
.chunk-card :deep(.chunk-highlight) { background: rgba(250,204,21,0.35); color: inherit; padding: 0 2px; border-radius: 4px; }
.chunk-empty { padding: 24px 12px; text-align: center; color: var(--text3); }
@media (max-width: 820px) { .doc-search-row, .bulk-toolbar, .doc-highlight-banner, .chunk-modal-header, .chunk-nav { flex-direction: column; align-items: stretch; } .chunk-overview { grid-template-columns: 1fr; } }
</style>
