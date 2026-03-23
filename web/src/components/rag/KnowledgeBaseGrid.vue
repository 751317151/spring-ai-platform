<template>
  <div class="kb-grid">
    <div
      v-for="kb in ragStore.knowledgeBases"
      :key="kb.id"
      class="kb-card"
      :class="{ selected: ragStore.currentKb === kb.id }"
      @click="ragStore.selectKb(kb.id)"
    >
      <div class="kb-card-head">
        <div class="kb-icon">{{ kbIcons[kb.id] || '📚' }}</div>
        <span class="kb-status" :class="kb.status === 'active' ? 'active' : ''">
          {{ kb.status === 'active' ? '运行中' : kb.status || '未知' }}
        </span>
      </div>
      <div class="kb-name">{{ kb.name }}</div>
      <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
      <div class="kb-meta">
        <span>📄 {{ kb.documentCount ?? 0 }} 文档</span>
        <span>🧩 {{ kb.totalChunks ?? 0 }} 分块</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRagStore } from '@/stores/rag'
import { KB_ICONS } from '@/utils/constants'

const ragStore = useRagStore()
const kbIcons = KB_ICONS
</script>
