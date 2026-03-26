<template>
  <div>
    <div class="kb-actions">
      <button class="btn btn-primary btn-sm" @click="openCreateModal">新建知识库</button>
      <button class="btn btn-ghost btn-sm" :disabled="!currentKnowledgeBase" @click="openEditModal">编辑当前</button>
      <button class="btn btn-ghost btn-sm" :disabled="!canDeleteCurrent" :title="deleteDisabledReason" @click="handleDeleteCurrent">删除当前</button>
    </div>

    <div class="kb-toolbar">
      <input v-model.trim="keyword" class="form-input kb-search" placeholder="按名称、描述、部门或负责人搜索知识库">
      <select v-model="scopeFilter" class="form-input kb-select">
        <option value="ALL">全部范围</option>
        <option value="PUBLIC">仅看公共</option>
        <option value="DEPARTMENT">仅看部门内</option>
        <option value="PRIVATE">仅看私有</option>
      </select>
      <select v-model="sortMode" class="form-input kb-select">
        <option value="documents">按文档数排序</option>
        <option value="chunks">按分段数排序</option>
        <option value="name">按名称排序</option>
      </select>
      <button v-if="hasFilters" class="btn btn-ghost btn-sm" @click="clearFilters">重置筛选</button>
    </div>

    <div v-if="ragStore.knowledgeBases.length" class="kb-summary-row">
      <span class="kb-summary-chip">{{ filteredKnowledgeBases.length }} 个可见知识库</span>
      <span class="kb-summary-chip">{{ totalDocuments }} 份文档</span>
      <span class="kb-summary-chip">{{ totalChunks }} 个分段</span>
      <span class="kb-summary-chip">{{ scopeFilterLabel }}</span>
      <span class="kb-summary-chip active">{{ activeKnowledgeBaseLabel }}</span>
    </div>

    <SkeletonBlock v-if="ragStore.loadingKnowledgeBases" variant="grid" :count="6" :height="248" :min-width="220" :gap="12" />

    <EmptyState
      v-else-if="ragStore.knowledgeBaseError"
      icon="K"
      badge="知识库状态"
      title="知识库加载失败"
      :description="ragStore.knowledgeBaseError"
      action-text="重试"
      @action="ragStore.loadKnowledgeBases()"
    />

    <EmptyState
      v-else-if="!filteredKnowledgeBases.length"
      icon="KB"
      badge="筛选结果"
      title="当前筛选条件下没有匹配的知识库"
      description="可以尝试其他关键词，或者重置当前搜索与排序条件。"
      action-text="清除搜索"
      @action="clearFilters"
    />

    <div v-else class="kb-grid">
      <button
        v-for="kb in filteredKnowledgeBases"
        :key="kb.id"
        type="button"
        class="kb-card kb-card-button"
        :class="{ selected: ragStore.currentKb === kb.id }"
        @click="ragStore.selectKb(kb.id)"
      >
        <div class="kb-card-head">
          <div class="kb-icon-shell"><div class="kb-icon">{{ kbIcons[kb.id] || 'KB' }}</div></div>
          <span class="kb-status" :class="statusTone(kb.status)">{{ statusLabel(kb.status) }}</span>
        </div>

        <div class="kb-name-row">
          <div class="kb-name">{{ kb.name }}</div>
          <span v-if="ragStore.currentKb === kb.id" class="kb-current-badge">当前使用中</span>
        </div>

        <div class="kb-desc">{{ kb.description || '暂无描述。' }}</div>

        <div class="kb-tags">
          <span class="kb-tag kb-tag-scope" :class="scopeTone(kb.visibilityScope)">{{ visibilityScopeLabel(kb.visibilityScope) }}</span>
          <span class="kb-tag">{{ kb.department || '公共知识空间' }}</span>
          <span class="kb-tag">{{ kb.createdBy || '未记录负责人' }}</span>
        </div>

        <div class="kb-meta-grid">
          <div class="kb-meta-card"><div class="kb-meta-label">文档数</div><div class="kb-meta-value">{{ kb.documentCount ?? 0 }}</div></div>
          <div class="kb-meta-card"><div class="kb-meta-label">分段数</div><div class="kb-meta-value">{{ kb.totalChunks ?? 0 }}</div></div>
        </div>

        <div class="kb-detail-grid">
          <div class="kb-detail-item"><div class="kb-detail-label">分块策略</div><div class="kb-detail-value">{{ kb.chunkStrategy || (kb.chunkSize ? `${kb.chunkSize} / overlap ${kb.chunkOverlap ?? 0}` : '默认策略') }}</div></div>
          <div class="kb-detail-item"><div class="kb-detail-label">创建时间</div><div class="kb-detail-value">{{ formatDisplayTime(kb.createdAt) }}</div></div>
        </div>
      </button>
    </div>

    <KnowledgeBaseModal v-if="modalVisible" :knowledge-base-id="editingKnowledgeBaseId" @close="closeModal" @saved="handleSaved" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import KnowledgeBaseModal from '@/components/rag/KnowledgeBaseModal.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useToast } from '@/composables/useToast'
import { useRagStore } from '@/stores/rag'
import { KB_ICONS } from '@/utils/constants'
import { formatTime } from '@/utils/format'

const ragStore = useRagStore()
const { confirm } = useConfirm()
const { showToast } = useToast()
const kbIcons = KB_ICONS
const keyword = ref('')
const scopeFilter = ref<'ALL' | 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE'>('ALL')
const sortMode = ref<'documents' | 'chunks' | 'name'>('documents')
const modalVisible = ref(false)
const editingKnowledgeBaseId = ref<string | null>(null)

const hasFilters = computed(() => Boolean(keyword.value || scopeFilter.value !== 'ALL' || sortMode.value !== 'documents'))
const scopeFilterLabel = computed(() => scopeFilter.value === 'PUBLIC' ? '范围：公共可见' : scopeFilter.value === 'PRIVATE' ? '范围：仅创建人可见' : scopeFilter.value === 'DEPARTMENT' ? '范围：部门内可见' : '范围：全部')
const filteredKnowledgeBases = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  const filtered = ragStore.knowledgeBases.filter((kb) => {
    const normalizedScope = (kb.visibilityScope || 'DEPARTMENT').toUpperCase()
    if (scopeFilter.value !== 'ALL' && normalizedScope !== scopeFilter.value) return false
    if (!normalizedKeyword) return true
    return [kb.name, kb.description, kb.department, kb.id, kb.createdBy].filter(Boolean).some((value) => String(value).toLowerCase().includes(normalizedKeyword))
  })
  return [...filtered].sort((left, right) => {
    if (sortMode.value === 'name') return (left.name || '').localeCompare(right.name || '')
    if (sortMode.value === 'chunks') return (right.totalChunks ?? 0) - (left.totalChunks ?? 0)
    return (right.documentCount ?? 0) - (left.documentCount ?? 0)
  })
})
const totalDocuments = computed(() => filteredKnowledgeBases.value.reduce((sum, kb) => sum + (kb.documentCount ?? 0), 0))
const totalChunks = computed(() => filteredKnowledgeBases.value.reduce((sum, kb) => sum + (kb.totalChunks ?? 0), 0))
const currentKnowledgeBase = computed(() => ragStore.knowledgeBases.find((kb) => kb.id === ragStore.currentKb) || null)
const canDeleteCurrent = computed(() => Boolean(currentKnowledgeBase.value && (currentKnowledgeBase.value.documentCount ?? 0) === 0))
const deleteDisabledReason = computed(() => !currentKnowledgeBase.value ? '请先选择知识库' : (currentKnowledgeBase.value.documentCount ?? 0) > 0 ? '请先删除当前知识库中的文档' : '')
const activeKnowledgeBaseLabel = computed(() => ragStore.currentKbName ? `当前知识库：${ragStore.currentKbName}` : '当前未选中知识库')

function clearFilters() { keyword.value = ''; scopeFilter.value = 'ALL'; sortMode.value = 'documents' }
function openCreateModal() { editingKnowledgeBaseId.value = null; modalVisible.value = true }
function openEditModal() { if (currentKnowledgeBase.value) { editingKnowledgeBaseId.value = currentKnowledgeBase.value.id; modalVisible.value = true } }
function closeModal() { modalVisible.value = false; editingKnowledgeBaseId.value = null }
function handleSaved() { const isEditing = Boolean(editingKnowledgeBaseId.value); closeModal(); showToast(isEditing ? '知识库已更新' : '知识库已创建') }
async function handleDeleteCurrent() {
  if (!currentKnowledgeBase.value) return
  const accepted = await confirm({ title: '删除知识库', description: `确认删除“${currentKnowledgeBase.value.name}”吗？删除前需要先清空该知识库中的文档。`, confirmText: '删除', intent: 'danger' })
  if (!accepted) return
  const ok = await ragStore.deleteKnowledgeBase(currentKnowledgeBase.value.id)
  if (ok) showToast('知识库已删除')
}
function statusLabel(status?: string) { const normalized = (status || '').toUpperCase(); return normalized === 'ACTIVE' ? '启用中' : normalized === 'DISABLED' ? '已停用' : normalized || '未知' }
function statusTone(status?: string) { const normalized = (status || '').toUpperCase(); return normalized === 'ACTIVE' ? 'active' : normalized === 'DISABLED' ? 'danger' : 'muted' }
function visibilityScopeLabel(scope?: string) { const normalized = (scope || 'DEPARTMENT').toUpperCase(); return normalized === 'PUBLIC' ? '公共可见' : normalized === 'PRIVATE' ? '仅创建人可见' : '部门内可见' }
function scopeTone(scope?: string) { const normalized = (scope || 'DEPARTMENT').toUpperCase(); return normalized === 'PUBLIC' ? 'scope-public' : normalized === 'PRIVATE' ? 'scope-private' : 'scope-department' }
function formatDisplayTime(value?: string) { return value ? formatTime(value) : '未记录' }
</script>

<style scoped>
.kb-actions, .kb-toolbar, .kb-summary-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.kb-search { min-width: 260px; flex: 1; }
.kb-select { width: auto; min-width: 190px; }
.kb-summary-chip { display: inline-flex; align-items: center; padding: 5px 10px; border-radius: 999px; border: 1px solid var(--border); background: rgba(255,255,255,0.03); color: var(--text3); font-size: 12px; }
.kb-summary-chip.active { color: var(--accent2); background: var(--accent-dim); }
.kb-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.kb-card-button { width: 100%; text-align: left; border: 1px solid var(--border); cursor: pointer; transition: transform var(--transition), border-color var(--transition), background var(--transition); }
.kb-card-button:hover { transform: translateY(-1px); border-color: var(--accent); }
.kb-card-button.selected { border-color: var(--accent); box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent) 50%, transparent); }
.kb-card-head, .kb-name-row, .kb-tags { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.kb-icon-shell { width: 42px; height: 42px; display: flex; align-items: center; justify-content: center; border-radius: 14px; background: linear-gradient(135deg, rgba(16, 185, 129, 0.16), rgba(20, 184, 166, 0.08)); border: 1px solid rgba(16, 185, 129, 0.14); }
.kb-icon { font-size: 18px; }
.kb-status, .kb-current-badge, .kb-tag { display: inline-flex; align-items: center; padding: 4px 9px; border-radius: 999px; font-size: 11px; }
.kb-status.active { background: rgba(16, 185, 129, 0.12); color: #059669; }
.kb-status.danger { background: rgba(239, 68, 68, 0.12); color: #dc2626; }
.kb-status.muted { background: rgba(148, 163, 184, 0.12); color: var(--text3); }
.kb-name { color: var(--text); font-size: 15px; font-weight: 600; }
.kb-current-badge { background: var(--accent-dim); color: var(--accent2); }
.kb-desc { margin-top: 8px; color: var(--text2); font-size: 12px; line-height: 1.7; min-height: 40px; }
.kb-tag { background: rgba(255,255,255,0.04); color: var(--text3); border: 1px solid var(--border); }
.kb-tag-scope.scope-public { background: rgba(59,130,246,0.12); color: #2563eb; border-color: rgba(59,130,246,0.2); }
.kb-tag-scope.scope-department { background: rgba(16,185,129,0.12); color: #059669; border-color: rgba(16,185,129,0.18); }
.kb-tag-scope.scope-private { background: rgba(245,158,11,0.12); color: #d97706; border-color: rgba(245,158,11,0.2); }
.kb-meta-grid, .kb-detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 12px; }
.kb-meta-card, .kb-detail-item { border: 1px solid var(--border); border-radius: 12px; padding: 10px 12px; background: rgba(255,255,255,0.02); }
.kb-meta-label, .kb-detail-label { font-size: 11px; color: var(--text3); text-transform: uppercase; letter-spacing: 0.08em; }
.kb-meta-value { margin-top: 6px; font-size: 16px; font-weight: 600; color: var(--text); }
.kb-detail-value { margin-top: 6px; font-size: 12px; line-height: 1.6; color: var(--text2); }
@media (max-width: 960px) { .kb-grid, .kb-detail-grid, .kb-meta-grid { grid-template-columns: 1fr; } }
</style>
