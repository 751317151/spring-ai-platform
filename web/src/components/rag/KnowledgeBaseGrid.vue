<template>
  <div>
    <div class="kb-toolbar">
      <input v-model="keyword" class="form-input kb-search" placeholder="按名称、描述或所属部门搜索" />
      <select v-model="sortMode" class="form-input kb-select">
        <option value="documents">按文档数排序</option>
        <option value="chunks">按分段数排序</option>
        <option value="name">按名称排序</option>
      </select>
    </div>

    <div v-if="ragStore.knowledgeBases.length" class="kb-summary-row">
      <span class="kb-summary-chip">{{ filteredKnowledgeBases.length }} 个可见</span>
      <span class="kb-summary-chip">{{ totalDocuments }} 份文档</span>
      <span class="kb-summary-chip">{{ totalChunks }} 个分段</span>
      <span class="kb-summary-chip active">{{ activeKnowledgeBaseLabel }}</span>
    </div>

    <div v-if="ragStore.loadingKnowledgeBases" class="kb-skeleton-grid">
      <div v-for="idx in 6" :key="idx" class="kb-skeleton-card skeleton"></div>
    </div>

    <EmptyState
      v-else-if="ragStore.knowledgeBaseError"
      icon="K"
      title="知识库加载失败"
      :description="ragStore.knowledgeBaseError"
      action-text="重试"
      @action="ragStore.loadKnowledgeBases()"
    />

    <EmptyState
      v-else-if="!filteredKnowledgeBases.length"
      icon="KB"
      title="当前筛选条件下没有匹配的知识库"
      description="可以尝试其他关键词，或重置当前搜索和排序条件。"
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
          <div class="kb-icon-shell">
            <div class="kb-icon">{{ kbIcons[kb.id] || 'KB' }}</div>
          </div>
          <span class="kb-status" :class="statusTone(kb.status)">
            {{ statusLabel(kb.status) }}
          </span>
        </div>
        <div class="kb-name-row">
          <div class="kb-name">{{ kb.name }}</div>
          <span v-if="ragStore.currentKb === kb.id" class="kb-current-badge">当前使用中</span>
        </div>
        <div class="kb-desc">{{ kb.description || '暂无描述。' }}</div>
        <div class="kb-department">{{ kb.department || '共享知识空间' }}</div>
        <div class="kb-meta-grid">
          <div class="kb-meta-card">
            <div class="kb-meta-label">文档数</div>
            <div class="kb-meta-value">{{ kb.documentCount ?? 0 }}</div>
          </div>
          <div class="kb-meta-card">
            <div class="kb-meta-label">分段数</div>
            <div class="kb-meta-value">{{ kb.totalChunks ?? 0 }}</div>
          </div>
        </div>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useRagStore } from '@/stores/rag'
import { KB_ICONS } from '@/utils/constants'

const ragStore = useRagStore()
const kbIcons = KB_ICONS
const keyword = ref('')
const sortMode = ref<'documents' | 'chunks' | 'name'>('documents')

const filteredKnowledgeBases = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  const filtered = ragStore.knowledgeBases.filter((kb) => {
    if (!normalizedKeyword) return true
    return [kb.name, kb.description, kb.department, kb.id]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(normalizedKeyword))
  })

  return [...filtered].sort((left, right) => {
    if (sortMode.value === 'name') {
      return (left.name || '').localeCompare(right.name || '')
    }
    if (sortMode.value === 'chunks') {
      return (right.totalChunks ?? 0) - (left.totalChunks ?? 0)
    }
    return (right.documentCount ?? 0) - (left.documentCount ?? 0)
  })
})

const totalDocuments = computed(() =>
  filteredKnowledgeBases.value.reduce((sum, kb) => sum + (kb.documentCount ?? 0), 0)
)

const totalChunks = computed(() =>
  filteredKnowledgeBases.value.reduce((sum, kb) => sum + (kb.totalChunks ?? 0), 0)
)

const activeKnowledgeBaseLabel = computed(() => {
  if (!ragStore.currentKbName) {
    return '当前未激活知识库'
  }
  return `当前知识库：${ragStore.currentKbName}`
})

function clearFilters() {
  keyword.value = ''
  sortMode.value = 'documents'
}

function statusLabel(status?: string) {
  const normalized = (status || '').toUpperCase()
  if (normalized === 'ACTIVE') return '启用中'
  if (normalized) return normalized
  return '未知'
}

function statusTone(status?: string) {
  return (status || '').toUpperCase() === 'ACTIVE' ? 'active' : 'muted'
}
</script>

<style scoped>
.kb-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.kb-search {
  min-width: 260px;
  flex: 1;
}

.kb-select {
  width: auto;
  min-width: 190px;
}

.kb-summary-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.kb-summary-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text3);
  font-size: 12px;
}

.kb-summary-chip.active {
  color: var(--accent2);
  background: var(--accent-dim);
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.kb-card-button {
  width: 100%;
  text-align: left;
  border: 1px solid var(--border);
  cursor: pointer;
  transition: transform var(--transition), border-color var(--transition), background var(--transition);
}

.kb-card-button:hover {
  transform: translateY(-1px);
  border-color: var(--accent);
}

.kb-card-button.selected {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent) 50%, transparent);
}

.kb-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.kb-icon-shell {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.16), rgba(20, 184, 166, 0.08));
  border: 1px solid rgba(16, 185, 129, 0.14);
}

.kb-icon {
  font-size: 18px;
}

.kb-status {
  display: inline-flex;
  align-items: center;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 11px;
  letter-spacing: 0.06em;
}

.kb-status.active {
  background: rgba(16, 185, 129, 0.12);
  color: #059669;
}

.kb-status.muted {
  background: rgba(148, 163, 184, 0.12);
  color: var(--text3);
}

.kb-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.kb-name {
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
}

.kb-current-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--accent-dim);
  color: var(--accent2);
  font-size: 11px;
}

.kb-desc {
  margin-top: 8px;
  color: var(--text2);
  font-size: 12px;
  line-height: 1.7;
  min-height: 40px;
}

.kb-department {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text3);
}

.kb-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.kb-meta-card {
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.02);
}

.kb-meta-label {
  font-size: 11px;
  color: var(--text3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.kb-meta-value {
  margin-top: 6px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}

.kb-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.kb-skeleton-card {
  height: 208px;
  border-radius: 16px;
}

@media (max-width: 960px) {
  .kb-grid,
  .kb-skeleton-grid {
    grid-template-columns: 1fr;
  }
}
</style>
