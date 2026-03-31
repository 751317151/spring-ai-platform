<template>
  <div class="rag-page">
    <section class="rag-head">
      <div>
        <div class="eyebrow">知识库</div>
        <h1 class="rag-title">先选择知识库，再进入详情页问答</h1>
        <p class="rag-subtitle">
          列表页只保留选库和基础管理，不在这里堆叠问答区。进入具体知识库后，再围绕该知识库完成提问、上传和排查。
        </p>
      </div>
      <div class="rag-stats">
        <div class="rag-stat">
          <span class="rag-stat-label">知识库</span>
          <strong class="rag-stat-value">{{ ragStore.knowledgeBases.length }}</strong>
        </div>
        <div class="rag-stat">
          <span class="rag-stat-label">文档总数</span>
          <strong class="rag-stat-value">{{ totalDocuments }}</strong>
        </div>
      </div>
    </section>

    <BackendStatusBanner
      service="rag"
      demo-message="当前知识库页面运行在演示模式，列表与结果可能来自本地模拟数据。"
      unavailable-message="知识库后端当前不可用，页面不会自动切回模拟结果。"
    />

    <section class="card rag-list-card">
      <div class="rag-list-head">
        <div>
          <div class="card-title">知识库列表</div>
          <div class="card-subtitle">点击任意知识库进入详情页，再进行问答、上传和文档处理。</div>
        </div>
      </div>
      <KnowledgeBaseGrid selection-mode="route" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import BackendStatusBanner from '@/components/common/BackendStatusBanner.vue'
import KnowledgeBaseGrid from '@/components/rag/KnowledgeBaseGrid.vue'
import { useRagStore } from '@/stores/rag'

const ragStore = useRagStore()

const totalDocuments = computed(() =>
  ragStore.knowledgeBases.reduce((sum, kb) => sum + (kb.documentCount ?? 0), 0)
)

onMounted(async () => {
  await ragStore.loadKnowledgeBases()
})
</script>

<style scoped>
.rag-page {
  display: grid;
  gap: 16px;
}

.rag-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
}

.eyebrow {
  color: var(--text3);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.rag-title {
  margin: 8px 0 0;
  color: var(--text);
  font-size: 28px;
  line-height: 1.15;
}

.rag-subtitle {
  margin: 10px 0 0;
  color: var(--text2);
  font-size: 14px;
  line-height: 1.8;
  max-width: 760px;
}

.rag-stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.rag-stat {
  min-width: 112px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
}

.rag-stat-label {
  display: block;
  color: var(--text3);
  font-size: 11px;
}

.rag-stat-value {
  display: block;
  margin-top: 6px;
  color: var(--text);
  font-size: 20px;
}

.rag-list-card {
  padding: 18px;
}

.rag-list-head {
  margin-bottom: 14px;
}

@media (max-width: 860px) {
  .rag-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .rag-title {
    font-size: 24px;
  }
}
</style>
