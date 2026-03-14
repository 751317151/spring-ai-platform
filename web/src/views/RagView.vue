<template>
  <div>
    <KnowledgeBaseGrid />

    <div class="grid-2">
      <div class="card">
        <div class="card-title">文档上传</div>
        <DocumentUpload />
      </div>
      <div class="card">
        <div class="card-title">知识库问答</div>
        <RagQueryPanel />
      </div>
    </div>

    <DocumentTable />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import KnowledgeBaseGrid from '@/components/rag/KnowledgeBaseGrid.vue'
import DocumentUpload from '@/components/rag/DocumentUpload.vue'
import DocumentTable from '@/components/rag/DocumentTable.vue'
import RagQueryPanel from '@/components/rag/RagQueryPanel.vue'
import { useRagStore } from '@/stores/rag'

const ragStore = useRagStore()

onMounted(() => {
  ragStore.loadKnowledgeBases()
})
</script>
