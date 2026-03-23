<template>
  <div>
    <BackendStatusBanner
      service="rag"
      demo-message="知识库页当前运行在演示模式，列表和答案来自本地 mock 数据。"
      unavailable-message="知识库后端暂不可用，页面不会再自动回退到模拟答案。"
    />

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
import BackendStatusBanner from '@/components/common/BackendStatusBanner.vue'
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
