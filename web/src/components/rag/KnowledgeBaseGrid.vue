<template>
  <div>
    <div class="kb-toolbar">
      <input v-model="keyword" class="form-input kb-search" placeholder="搜索知识库名称、描述、部门">
      <select v-model="sortMode" class="form-input kb-select">
        <option value="documents">按文档数排序</option>
        <option value="chunks">按分块数排序</option>
        <option value="name">按名称排序</option>
      </select>
    </div>

    <div class="kb-grid">
      <div
        v-for="kb in filteredKnowledgeBases"
        :key="kb.id"
        class="kb-card"
        :class="{ selected: ragStore.currentKb === kb.id }"
        @click="ragStore.selectKb(kb.id)"
      >
        <div class="kb-card-head">
          <div class="kb-icon">{{ kbIcons[kb.id] || '知识' }}</div>
          <span class="kb-status" :class="(kb.status || '').toUpperCase() === 'ACTIVE' ? 'active' : ''">
            {{ (kb.status || '').toUpperCase() === 'ACTIVE' ? '运行中' : kb.status || '未知' }}
          </span>
        </div>
        <div class="kb-name">{{ kb.name }}</div>
        <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
        <div class="kb-department">{{ kb.department || '未设置部门' }}</div>
        <div class="kb-meta">
          <span>文档 {{ kb.documentCount ?? 0 }}</span>
          <span>分块 {{ kb.totalChunks ?? 0 }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
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
    return [kb.name, kb.description, kb.department]
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
  min-width: 160px;
}

.kb-department {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text3);
}
</style>
