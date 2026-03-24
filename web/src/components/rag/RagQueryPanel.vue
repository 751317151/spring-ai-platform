<template>
  <div class="query-panel">
    <div class="query-context-card">
      <div>
        <div class="query-context-title">当前问答上下文</div>
        <div class="query-context-subtitle">
          {{ ragStore.currentKbName || '未选择知识库' }}
        </div>
      </div>
      <div class="query-context-meta">
        <span class="query-context-chip">已加载 {{ ragStore.documents.length }} 份文档</span>
        <span class="query-context-chip">{{ topKLabel }}</span>
        <span class="query-context-chip">{{ streamMode ? '流式模式' : '标准模式' }}</span>
      </div>
    </div>

    <div class="form-group">
      <div class="query-input-head">
        <label class="form-label">问题</label>
        <div class="query-input-hint">当前浏览器会按知识库记住你的草稿和查询参数。</div>
      </div>
      <textarea
        v-model="question"
        class="form-input"
        rows="3"
        placeholder="请输入针对当前知识库的问题..."
      ></textarea>
    </div>

    <div class="query-toolbar">
      <button class="btn btn-primary btn-sm" :disabled="ragStore.isQuerying || !ragStore.currentKb" @click="doQuery(false)">
        {{ ragStore.isQuerying && !streamMode ? '检索中...' : '检索答案' }}
      </button>
      <button class="btn btn-ghost btn-sm" :disabled="ragStore.isQuerying || !ragStore.currentKb" @click="doQuery(true)">
        {{ ragStore.isQuerying && streamMode ? '生成中...' : '流式回答' }}
      </button>
      <select v-model="topK" class="form-select topk-select">
        <option :value="3">TopK: 3</option>
        <option :value="5">TopK: 5</option>
        <option :value="10">TopK: 10</option>
      </select>
      <button class="btn btn-ghost btn-sm" :disabled="!ragStore.currentKb" @click="resetWorkspaceState">重置草稿</button>
    </div>

    <div v-if="recommendedQuestions.length" class="query-helper-card">
      <div class="query-helper-header">
        <div>
          <div class="query-helper-title">推荐问题</div>
          <div class="query-helper-subtitle">可作为当前知识库的快速提问起点。</div>
        </div>
      </div>
      <div class="query-chip-list">
        <button
          v-for="item in recommendedQuestions"
          :key="item"
          class="query-helper-chip"
          @click="applyQuestion(item)"
        >
          {{ item }}
        </button>
      </div>
    </div>

    <div v-if="recentQuestions.length" class="query-helper-card recent-query-card">
      <div class="query-helper-header">
        <div>
          <div class="query-helper-title">最近提问</div>
          <div class="query-helper-subtitle">最近的问题会保存在本地，方便快速复用。</div>
        </div>
        <button class="btn btn-ghost btn-sm" @click="clearRecentQuestions">清空</button>
      </div>
      <div class="query-chip-list">
        <button
          v-for="item in recentQuestions"
          :key="item"
          class="query-helper-chip recent"
          @click="applyQuestion(item)"
        >
          {{ item }}
        </button>
      </div>
    </div>

    <div v-if="ragStore.isQuerying" class="query-stage-card">
      <div class="query-stage-title">{{ stageTitle }}</div>
      <div class="query-stage-desc">{{ stageDescription }}</div>
    </div>

    <div v-if="ragStore.queryError" class="query-error-card">
      <div class="query-error-title">本次查询未成功完成</div>
      <div class="query-error-desc">{{ ragStore.queryError }}</div>
      <button class="btn btn-ghost btn-sm" :disabled="!question.trim()" @click="retryLastQuery">重试</button>
    </div>

    <div class="query-result-shell">
      <div class="query-result-header">
        <div>
          <div class="query-result-title">回答</div>
          <div class="query-result-subtitle">基于当前所选知识库生成的回复。</div>
        </div>
        <div class="query-result-side">
          <div class="query-result-meta">
            <span class="query-meta-chip">{{ streamMode ? '流式' : '标准' }}</span>
            <span class="query-meta-chip">TopK {{ topK }}</span>
            <span class="query-meta-chip">{{ ragStore.querySources.length }} 条证据</span>
            <span v-if="ragStore.querySources.length" class="query-meta-chip">最高 {{ topEvidenceScore }}</span>
          </div>
          <div v-if="ragStore.queryResult" class="query-result-actions">
            <button class="query-action-btn" @click="copyAnswer">复制回答</button>
            <button class="query-action-btn" @click="copyEvidenceSummary">复制证据摘要</button>
          </div>
        </div>
      </div>

      <div class="query-result" :class="{ empty: !ragStore.queryResult && !ragStore.isQuerying }">
        <div v-if="!ragStore.queryResult && !ragStore.isQuerying" class="empty">
          {{ ragStore.currentKb ? '在上方输入问题后，即可生成回答并查看匹配证据。' : '请先选择知识库，再开始查询。' }}
        </div>
        <div v-else class="query-answer-card">
          <div class="query-answer-content" v-html="formattedResult"></div>
          <div v-if="ragStore.queryResponseId" class="feedback-bar">
            <span class="feedback-label">这条回答是否有帮助？</span>
            <button class="feedback-btn" :class="{ active: ragStore.queryFeedback === 'up' }" @click="submitFeedback('up')">有帮助</button>
            <button class="feedback-btn" :class="{ active: ragStore.queryFeedback === 'down' }" @click="submitFeedback('down')">待改进</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="ragStore.queryResult && !ragStore.isQuerying && !ragStore.querySources.length" class="query-no-evidence">
      <div class="query-no-evidence-title">未返回证据</div>
      <div class="query-no-evidence-desc">回答已生成，但没有返回来源分段。可以适当提高 TopK，或使用更明确的关键词重新提问。</div>
    </div>

    <div v-if="ragStore.querySources.length" class="evidence-panel">
      <div class="evidence-header">
        <div>
          <div class="evidence-title">证据</div>
          <div class="evidence-subtitle">以下来源分段用于支撑当前回答。</div>
        </div>
        <div class="evidence-summary">
          <span class="evidence-count">{{ ragStore.querySources.length }} 条</span>
          <span class="evidence-count">分数范围 {{ evidenceScoreRange }}</span>
        </div>
      </div>

      <div class="evidence-list">
        <div v-for="(src, idx) in ragStore.querySources" :key="src.chunkId || idx" class="evidence-card">
          <div class="evidence-card-topline">
            <span class="evidence-topline-label">证据 {{ idx + 1 }}</span>
            <span class="evidence-topline-score">分数 {{ formatScore(src.score) }}</span>
          </div>

          <div class="evidence-meta">
            <div class="evidence-file">
              <span class="evidence-index">#{{ idx + 1 }}</span>
              <span>{{ src.filename }}</span>
              <span v-if="src.chunkIndex != null" class="evidence-chunk">分段 {{ src.chunkIndex }}</span>
            </div>
            <div class="evidence-actions">
              <div class="evidence-score">相关度 {{ formatScore(src.score) }}</div>
              <button class="evidence-mini-btn" @click="copyEvidence(src)">复制</button>
            </div>
          </div>

          <div class="evidence-insight-row">
            <div class="evidence-insight-card">
              <div class="evidence-insight-label">来源摘要</div>
              <div class="evidence-insight-value">{{ summarizeSource(src) }}</div>
            </div>
            <div class="evidence-insight-card">
              <div class="evidence-insight-label">命中原因</div>
              <div class="evidence-insight-value">{{ matchReason(src.score) }}</div>
            </div>
          </div>

          <div class="evidence-preview">{{ src.preview || summarize(src.content) }}</div>

          <div v-if="src.chunkId && ragStore.queryResponseId" class="evidence-feedback">
            <span class="feedback-label">这条证据是否有用？</span>
            <button class="feedback-btn" :class="{ active: src.feedback === 'up' }" @click="submitEvidenceFeedback(src.chunkId, 'up')">准确</button>
            <button class="feedback-btn" :class="{ active: src.feedback === 'down' }" @click="submitEvidenceFeedback(src.chunkId, 'down')">不准确</button>
          </div>

          <details class="evidence-details">
            <summary>查看完整分段</summary>
            <pre>{{ src.content }}</pre>
          </details>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRagStore } from '@/stores/rag'
import { formatMarkdown } from '@/utils/format'
import { useToast } from '@/composables/useToast'

interface QueryWorkspaceState {
  question: string
  topK: number
  streamMode: boolean
}

const ragStore = useRagStore()
const { showToast } = useToast()
const question = ref('')
const topK = ref(5)
const streamMode = ref(false)
const recentQuestions = ref<string[]>([])
const RECENT_QUERY_KEY = 'rag_recent_queries'
const WORKSPACE_KEY_PREFIX = 'rag_query_workspace:'

const formattedResult = computed(() => formatMarkdown(ragStore.queryResult))
const topKLabel = computed(() => `TopK ${topK.value}`)
const topEvidenceScore = computed(() => formatScore(ragStore.querySources[0]?.score ?? Number.NaN))
const evidenceScoreRange = computed(() => {
  if (!ragStore.querySources.length) return '-'
  const scores = ragStore.querySources.map((item) => item.score).filter((item) => !Number.isNaN(item))
  if (!scores.length) return '-'
  return `${formatScore(Math.max(...scores))} - ${formatScore(Math.min(...scores))}`
})
const stageTitle = computed(() => (ragStore.queryStage === 'answering' ? '正在生成回答' : '正在检索证据'))
const stageDescription = computed(() =>
  ragStore.queryStage === 'answering'
    ? '已找到相关分段，正在组织回答内容。'
    : '系统正在对当前知识库中最相关的分段进行排序。'
)
const recommendedQuestions = computed(() => {
  const kbName = ragStore.currentKbName || '当前知识库'
  return [
    `请概括 ${kbName} 涵盖的核心主题。`,
    `请说明 ${kbName} 中最重要的规则或约束。`,
    `请列出学习 ${kbName} 最值得优先阅读的来源文档。`
  ]
})

async function doQuery(stream: boolean) {
  if (!question.value.trim()) {
    showToast('请输入问题')
    return
  }
  saveRecentQuestion(question.value)
  streamMode.value = stream
  persistWorkspaceState()
  await ragStore.ragQuery(question.value, stream, topK.value)
}

function retryLastQuery() {
  if (question.value.trim()) doQuery(streamMode.value)
}

async function submitFeedback(feedback: 'up' | 'down') {
  try {
    const saved = await ragStore.submitQueryFeedback(feedback)
    if (saved) showToast(feedback === 'up' ? '已记录回答反馈' : '已记录改进建议')
  } catch {
    showToast('提交反馈失败')
  }
}

async function submitEvidenceFeedback(chunkId: string, feedback: 'up' | 'down') {
  try {
    const saved = await ragStore.submitEvidenceFeedback(chunkId, feedback)
    if (saved) showToast(feedback === 'up' ? '已记录证据反馈' : '已记录证据问题')
  } catch {
    showToast('提交证据反馈失败')
  }
}

function summarize(content: string) {
  const normalized = content.replace(/\s+/g, ' ').trim()
  return normalized.length <= 180 ? normalized : `${normalized.slice(0, 180)}...`
}

function formatScore(score: number) {
  return Number.isNaN(score) ? '-' : score.toFixed(3)
}

function applyQuestion(value: string) {
  question.value = value
}

function getWorkspaceStateKey(kbId: string) {
  return `${WORKSPACE_KEY_PREFIX}${kbId}`
}

function persistWorkspaceState() {
  if (!ragStore.currentKb) return
  const state: QueryWorkspaceState = { question: question.value, topK: topK.value, streamMode: streamMode.value }
  window.sessionStorage.setItem(getWorkspaceStateKey(ragStore.currentKb), JSON.stringify(state))
}

function loadWorkspaceState(kbId: string) {
  if (!kbId) return false
  try {
    const raw = window.sessionStorage.getItem(getWorkspaceStateKey(kbId))
    if (!raw) return false
    const parsed = JSON.parse(raw) as Partial<QueryWorkspaceState>
    question.value = parsed.question ?? ''
    topK.value = parsed.topK === 3 || parsed.topK === 10 ? parsed.topK : 5
    streamMode.value = Boolean(parsed.streamMode)
    return true
  } catch {
    return false
  }
}

function resetWorkspaceState() {
  if (!ragStore.currentKb) return
  question.value = recommendedQuestions.value[0] || ''
  topK.value = 5
  streamMode.value = false
  persistWorkspaceState()
  showToast('当前知识库的提问草稿已重置')
}

function saveRecentQuestion(value: string) {
  const normalized = value.trim()
  if (!normalized) return
  recentQuestions.value = [normalized, ...recentQuestions.value.filter((item) => item !== normalized)].slice(0, 6)
  window.sessionStorage.setItem(RECENT_QUERY_KEY, JSON.stringify(recentQuestions.value))
}

function clearRecentQuestions() {
  recentQuestions.value = []
  window.sessionStorage.removeItem(RECENT_QUERY_KEY)
}

function summarizeSource(src: { filename: string; chunkIndex?: number; preview?: string; content: string }) {
  const basis = src.preview || src.content || src.filename
  const normalized = basis.replace(/\s+/g, ' ').trim()
  if (!normalized) return src.filename
  return normalized.length <= 72 ? normalized : `${normalized.slice(0, 72)}...`
}

function matchReason(score: number) {
  if (score >= 0.9) return '与问题语义高度匹配，内容重合度很高。'
  if (score >= 0.75) return '属于较强支撑证据，主题相关性明确。'
  if (score >= 0.6) return '可作为上下文参考，但建议结合相邻分段交叉确认。'
  return '相关度偏弱，更适合作为补充信息而非核心证据。'
}

async function copyEvidence(src: { filename: string; content: string; chunkIndex?: number }) {
  const payload = [`来源：${src.filename}`, src.chunkIndex != null ? `分段：${src.chunkIndex}` : '', src.content].filter(Boolean).join('\n')
  try {
    await navigator.clipboard.writeText(payload)
    showToast('已复制证据内容')
  } catch {
    showToast('复制证据内容失败')
  }
}

async function copyAnswer() {
  if (!ragStore.queryResult) return
  try {
    await navigator.clipboard.writeText(ragStore.queryResult)
    showToast('已复制回答')
  } catch {
    showToast('复制回答失败')
  }
}

async function copyEvidenceSummary() {
  if (!ragStore.querySources.length) return
  const payload = ragStore.querySources
    .map((src, idx) => [`证据 ${idx + 1}`, `文件：${src.filename}`, src.chunkIndex != null ? `分段：${src.chunkIndex}` : '', `分数：${formatScore(src.score)}`, src.preview || summarize(src.content)].filter(Boolean).join('\n'))
    .join('\n\n')
  try {
    await navigator.clipboard.writeText(payload)
    showToast('已复制证据摘要')
  } catch {
    showToast('复制证据摘要失败')
  }
}

watch([question, topK, streamMode], () => {
  persistWorkspaceState()
})

watch(
  () => ragStore.currentKb,
  (kbId, previousKb) => {
    if (previousKb && previousKb !== kbId) persistWorkspaceState()
    const loaded = loadWorkspaceState(kbId)
    if (!loaded && !question.value.trim()) {
      question.value = recommendedQuestions.value[0] || ''
      topK.value = 5
      streamMode.value = false
    }
  },
  { immediate: true }
)

watch(
  () => ragStore.currentKbName,
  () => {
    if (!question.value.trim()) question.value = recommendedQuestions.value[0] || ''
  }
)

onMounted(() => {
  try {
    const stored = window.sessionStorage.getItem(RECENT_QUERY_KEY)
    recentQuestions.value = stored ? JSON.parse(stored) : []
  } catch {
    recentQuestions.value = []
  }
  if (!loadWorkspaceState(ragStore.currentKb) && !question.value.trim()) question.value = recommendedQuestions.value[0] || ''
})
</script>

<style scoped>
.query-context-card { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; padding: 14px; border: 1px solid var(--border); border-radius: 14px; background: rgba(255, 255, 255, 0.02); }
.query-context-title { font-size: 12px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--text3); }
.query-context-subtitle { margin-top: 6px; color: var(--text); font-size: 15px; font-weight: 600; }
.query-context-meta { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.query-context-chip { display: inline-flex; align-items: center; padding: 5px 10px; border-radius: 999px; border: 1px solid var(--border); background: rgba(255, 255, 255, 0.03); color: var(--text3); font-size: 12px; }
.query-input-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 6px; }
.query-input-hint { color: var(--text3); font-size: 12px; text-align: right; }
.query-toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.topk-select { padding: 5px 8px; font-size: 11px; width: auto; }
.query-helper-card { border: 1px solid var(--border); border-radius: 14px; padding: 12px 14px; margin-bottom: 12px; background: rgba(255, 255, 255, 0.02); }
.query-helper-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.query-helper-title { font-size: 13px; font-weight: 600; color: var(--text); }
.query-helper-subtitle { margin-top: 4px; color: var(--text3); font-size: 12px; }
.query-chip-list { display: flex; gap: 8px; flex-wrap: wrap; }
.query-helper-chip { border: 1px solid var(--border); background: transparent; color: var(--text2); border-radius: 999px; padding: 6px 12px; font-size: 12px; text-align: left; cursor: pointer; transition: all var(--transition); }
.query-helper-chip:hover { color: var(--text); }
.recent-query-card { background: rgba(255, 255, 255, 0.015); }
.query-stage-card, .query-error-card { border-radius: 14px; padding: 12px 14px; margin-bottom: 12px; }
.query-stage-card { background: rgba(59, 130, 246, 0.08); border: 1px solid rgba(59, 130, 246, 0.18); }
.query-error-card { background: rgba(239, 68, 68, 0.08); border: 1px solid rgba(239, 68, 68, 0.16); }
.query-stage-title, .query-error-title { font-size: 13px; font-weight: 600; color: var(--text); }
.query-stage-desc, .query-error-desc { margin-top: 6px; color: var(--text3); font-size: 12px; line-height: 1.6; }
.query-result-shell { border: 1px solid var(--border); border-radius: var(--r2); background: linear-gradient(180deg, rgba(255,255,255,0.02), rgba(255,255,255,0.01)); overflow: hidden; }
.query-result-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 14px 16px 12px; border-bottom: 1px solid var(--border); }
.query-result-title { font-size: 14px; font-weight: 600; color: var(--text); }
.query-result-subtitle { margin-top: 4px; color: var(--text3); font-size: 12px; }
.query-result-meta { display: flex; gap: 8px; flex-wrap: wrap; }
.query-result-side { display: flex; flex-direction: column; align-items: flex-end; gap: 10px; }
.query-meta-chip { display: inline-flex; align-items: center; padding: 4px 9px; border-radius: 999px; font-size: 11px; color: var(--text3); background: rgba(255, 255, 255, 0.04); border: 1px solid var(--border); }
.query-result-actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.query-action-btn { border: 1px solid var(--border); background: transparent; color: var(--text2); border-radius: 999px; padding: 5px 10px; font-size: 12px; cursor: pointer; transition: all var(--transition); }
.query-action-btn:hover { border-color: var(--accent); color: var(--accent2); background: var(--accent-dim); }
.query-result { min-height: 160px; }
.query-answer-card { display: flex; flex-direction: column; gap: 12px; }
.query-answer-content { color: var(--text); }
.feedback-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--border); }
.feedback-label { color: var(--text3); font-size: 12px; }
.feedback-btn { border: 1px solid var(--border); background: transparent; color: var(--text2); border-radius: 999px; padding: 4px 10px; font-size: 12px; cursor: pointer; }
.feedback-btn.active { border-color: var(--brand); color: var(--brand); background: rgba(59, 130, 246, 0.08); }
.query-no-evidence { margin-top: 16px; border: 1px dashed var(--border); border-radius: 14px; padding: 14px; background: rgba(255, 255, 255, 0.02); }
.query-no-evidence-title { font-size: 13px; font-weight: 600; color: var(--text); }
.query-no-evidence-desc { margin-top: 6px; color: var(--text3); font-size: 12px; line-height: 1.6; }
.evidence-panel { margin-top: 16px; border-top: 1px solid var(--border); padding-top: 16px; }
.evidence-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.evidence-title { font-size: 14px; font-weight: 600; color: var(--text); }
.evidence-subtitle { margin-top: 4px; color: var(--text3); font-size: 12px; }
.evidence-count { color: var(--text3); font-size: 12px; white-space: nowrap; }
.evidence-summary { display: flex; gap: 10px; flex-wrap: wrap; justify-content: flex-end; }
.evidence-list { display: grid; gap: 10px; }
.evidence-card { border: 1px solid var(--border); border-radius: var(--r2); background: var(--bg); padding: 12px; }
.evidence-card-topline { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.evidence-topline-label, .evidence-topline-score { font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--text3); }
.evidence-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
.evidence-file { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; color: var(--text); font-size: 12px; }
.evidence-index, .evidence-chunk { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 999px; background: rgba(59, 130, 246, 0.12); color: #2563eb; font-size: 11px; }
.evidence-actions { display: flex; align-items: center; gap: 8px; }
.evidence-score { color: var(--text3); font-size: 12px; }
.evidence-mini-btn { border: 1px solid var(--border); background: transparent; color: var(--text2); border-radius: 999px; padding: 4px 10px; font-size: 11px; cursor: pointer; }
.evidence-insight-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 10px; }
.evidence-insight-card { border: 1px solid var(--border); border-radius: 12px; padding: 10px 12px; background: rgba(255, 255, 255, 0.02); }
.evidence-insight-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--text3); margin-bottom: 6px; }
.evidence-insight-value { color: var(--text2); font-size: 12px; line-height: 1.6; }
.evidence-preview { color: var(--text2); line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.evidence-feedback { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
.evidence-details { margin-top: 10px; }
.evidence-details summary { cursor: pointer; color: var(--brand); font-size: 12px; }
.evidence-details pre { margin: 10px 0 0; padding: 12px; border-radius: 10px; background: #0f172a; color: #e2e8f0; white-space: pre-wrap; word-break: break-word; font-size: 12px; line-height: 1.6; }
@media (max-width: 820px) {
  .query-context-card, .query-input-head, .query-result-header, .query-helper-header, .evidence-header, .evidence-meta, .evidence-card-topline { flex-direction: column; align-items: flex-start; }
  .evidence-insight-row { grid-template-columns: 1fr; }
  .query-context-meta, .query-result-side { align-items: flex-start; justify-content: flex-start; }
  .query-input-hint, .query-result-actions { text-align: left; justify-content: flex-start; }
}
</style>
