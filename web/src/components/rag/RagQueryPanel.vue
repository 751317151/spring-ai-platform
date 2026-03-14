<template>
  <div class="query-panel">
    <div class="form-group">
      <label class="form-label">提问</label>
      <textarea class="form-input" v-model="question" rows="3" placeholder="输入问题，从知识库检索答案..."></textarea>
    </div>
    <div style="display: flex; gap: 8px; margin-bottom: 12px">
      <button class="btn btn-primary btn-sm" @click="doQuery(false)" :disabled="ragStore.isQuerying">检索问答</button>
      <button class="btn btn-ghost btn-sm" @click="doQuery(true)" :disabled="ragStore.isQuerying">流式输出</button>
      <select class="form-select" v-model="topK" style="padding: 5px 8px; font-size: 11px; width: auto">
        <option :value="5">TopK: 5</option>
        <option :value="10">TopK: 10</option>
        <option :value="3">TopK: 3</option>
      </select>
    </div>
    <div class="query-result" :class="{ empty: !ragStore.queryResult }" style="min-height: 160px">
      <div v-if="!ragStore.queryResult" class="empty">选择知识库后在上方输入问题</div>
      <div v-else v-html="formattedResult"></div>
    </div>
    <div class="source-chips" v-if="ragStore.querySources.length">
      <div v-for="(src, idx) in ragStore.querySources" :key="idx" class="source-chip">
        📄 {{ src.filename }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRagStore } from '@/stores/rag'
import { formatMarkdown } from '@/utils/format'

const ragStore = useRagStore()
const question = ref('')
const topK = ref(5)

const formattedResult = computed(() => formatMarkdown(ragStore.queryResult))

function doQuery(stream: boolean) {
  if (!question.value.trim()) return
  ragStore.ragQuery(question.value, stream, topK.value)
}
</script>
