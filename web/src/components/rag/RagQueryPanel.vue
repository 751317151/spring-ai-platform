<template>
  <div class="query-panel">
    <div class="form-group">
      <label class="form-label">提问</label>
      <textarea
        v-model="question"
        class="form-input"
        rows="3"
        placeholder="输入问题，从知识库中检索答案..."
      ></textarea>
    </div>

    <div class="query-toolbar">
      <button class="btn btn-primary btn-sm" :disabled="ragStore.isQuerying" @click="doQuery(false)">
        检索问答
      </button>
      <button class="btn btn-ghost btn-sm" :disabled="ragStore.isQuerying" @click="doQuery(true)">
        流式输出
      </button>
      <select v-model="topK" class="form-select topk-select">
        <option :value="3">TopK: 3</option>
        <option :value="5">TopK: 5</option>
        <option :value="10">TopK: 10</option>
      </select>
    </div>

    <div class="query-result" :class="{ empty: !ragStore.queryResult }">
      <div v-if="!ragStore.queryResult" class="empty">选择知识库后，在上方输入问题。</div>
      <div v-else>
        <div v-html="formattedResult"></div>
        <div v-if="ragStore.queryResponseId" class="feedback-bar">
          <span class="feedback-label">这条回答是否有帮助？</span>
          <button
            class="feedback-btn"
            :class="{ active: ragStore.queryFeedback === 'up' }"
            @click="submitFeedback('up')"
          >
            👍 有帮助
          </button>
          <button
            class="feedback-btn"
            :class="{ active: ragStore.queryFeedback === 'down' }"
            @click="submitFeedback('down')"
          >
            👎 需优化
          </button>
        </div>
      </div>
    </div>

    <div v-if="ragStore.querySources.length" class="evidence-panel">
      <div class="evidence-header">
        <div>
          <div class="evidence-title">检索证据</div>
          <div class="evidence-subtitle">以下内容是本次回答实际命中的知识片段。</div>
        </div>
        <div class="evidence-count">{{ ragStore.querySources.length }} 条</div>
      </div>

      <div class="evidence-list">
        <div v-for="(src, idx) in ragStore.querySources" :key="src.chunkId || idx" class="evidence-card">
          <div class="evidence-meta">
            <div class="evidence-file">
              <span class="evidence-index">#{{ idx + 1 }}</span>
              <span>{{ src.filename }}</span>
              <span v-if="src.chunkIndex" class="evidence-chunk">Chunk {{ src.chunkIndex }}</span>
            </div>
            <div class="evidence-score">相关度 {{ formatScore(src.score) }}</div>
          </div>

          <div class="evidence-preview">{{ src.preview || summarize(src.content) }}</div>

          <div v-if="src.chunkId && ragStore.queryResponseId" class="evidence-feedback">
            <span class="feedback-label">这个证据片段是否有帮助？</span>
            <button
              class="feedback-btn"
              :class="{ active: src.feedback === 'up' }"
              @click="submitEvidenceFeedback(src.chunkId, 'up')"
            >
              👍 命中准确
            </button>
            <button
              class="feedback-btn"
              :class="{ active: src.feedback === 'down' }"
              @click="submitEvidenceFeedback(src.chunkId, 'down')"
            >
              👎 命中偏差
            </button>
          </div>

          <details class="evidence-details">
            <summary>查看命中原文</summary>
            <pre>{{ src.content }}</pre>
          </details>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRagStore } from '@/stores/rag'
import { formatMarkdown } from '@/utils/format'
import { useToast } from '@/composables/useToast'

const ragStore = useRagStore()
const { showToast } = useToast()
const question = ref('')
const topK = ref(5)

const formattedResult = computed(() => formatMarkdown(ragStore.queryResult))

function doQuery(stream: boolean) {
  if (!question.value.trim()) {
    return
  }
  ragStore.ragQuery(question.value, stream, topK.value)
}

async function submitFeedback(feedback: 'up' | 'down') {
  try {
    const saved = await ragStore.submitQueryFeedback(feedback)
    if (saved) {
      showToast(feedback === 'up' ? '已记录正向反馈' : '已记录负向反馈')
    }
  } catch {
    showToast('反馈提交失败，请稍后重试')
  }
}

async function submitEvidenceFeedback(chunkId: string, feedback: 'up' | 'down') {
  try {
    const saved = await ragStore.submitEvidenceFeedback(chunkId, feedback)
    if (saved) {
      showToast(feedback === 'up' ? '已记录证据正向反馈' : '已记录证据负向反馈')
    }
  } catch {
    showToast('证据反馈提交失败，请稍后重试')
  }
}

function summarize(content: string) {
  const normalized = content.replace(/\s+/g, ' ').trim()
  if (normalized.length <= 180) {
    return normalized
  }
  return `${normalized.slice(0, 180)}...`
}

function formatScore(score: number) {
  if (Number.isNaN(score)) {
    return '-'
  }
  return score.toFixed(3)
}
</script>

<style scoped>
.query-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.topk-select {
  padding: 5px 8px;
  font-size: 11px;
  width: auto;
}

.query-result {
  min-height: 160px;
}

.feedback-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.feedback-label {
  color: var(--text3);
  font-size: 12px;
}

.feedback-btn {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text2);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
}

.feedback-btn.active {
  border-color: var(--brand);
  color: var(--brand);
  background: rgba(59, 130, 246, 0.08);
}

.evidence-panel {
  margin-top: 16px;
  border-top: 1px solid var(--border);
  padding-top: 16px;
}

.evidence-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.evidence-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.evidence-subtitle {
  margin-top: 4px;
  color: var(--text3);
  font-size: 12px;
}

.evidence-count {
  color: var(--text3);
  font-size: 12px;
  white-space: nowrap;
}

.evidence-list {
  display: grid;
  gap: 10px;
}

.evidence-card {
  border: 1px solid var(--border);
  border-radius: var(--r2);
  background: var(--bg);
  padding: 12px;
}

.evidence-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.evidence-file {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text);
  font-size: 12px;
}

.evidence-index,
.evidence-chunk {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
  font-size: 11px;
}

.evidence-score {
  color: var(--text3);
  font-size: 12px;
}

.evidence-preview {
  color: var(--text2);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.evidence-feedback {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.evidence-details {
  margin-top: 10px;
}

.evidence-details summary {
  cursor: pointer;
  color: var(--brand);
  font-size: 12px;
}

.evidence-details pre {
  margin: 10px 0 0;
  padding: 12px;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
}
</style>
