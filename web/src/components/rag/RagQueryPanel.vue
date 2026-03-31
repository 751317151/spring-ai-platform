<template>
  <div class="query-panel" :class="{ 'query-panel-minimal': minimal }">
    <div v-if="!minimal" class="query-context-card">
      <div>
        <div class="query-context-title">当前问答上下文</div>
        <div class="query-context-subtitle">{{ ragStore.currentKbName || '未选择知识库' }}</div>
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
        <div v-if="!minimal" class="query-input-hint">系统会按知识库保存草稿、TopK 和问答模式，方便继续追问。</div>
      </div>
      <textarea v-model="question" class="form-input query-textarea" :rows="minimal ? 5 : 3" placeholder="请输入针对当前知识库的问题..."></textarea>
    </div>

    <div class="query-toolbar">
      <button class="btn btn-primary btn-sm" :disabled="ragStore.isQuerying || !ragStore.currentKb" @click="doQuery(false)">{{ ragStore.isQuerying && !streamMode ? '检索中...' : '检索答案' }}</button>
      <button class="btn btn-ghost btn-sm" :disabled="ragStore.isQuerying || !ragStore.currentKb" @click="doQuery(true)">{{ ragStore.isQuerying && streamMode ? '生成中...' : '流式回答' }}</button>
      <select v-model="topK" class="form-select topk-select"><option :value="3">TopK: 3</option><option :value="5">TopK: 5</option><option :value="10">TopK: 10</option></select>
      <button v-if="!minimal" class="btn btn-ghost btn-sm" :disabled="!ragStore.currentKb" @click="resetWorkspaceState">重置草稿</button>
    </div>

    <div v-if="!minimal && recommendedQuestions.length" class="query-helper-card">
      <div class="query-helper-header">
        <div><div class="query-helper-title">推荐问题</div><div class="query-helper-subtitle">可以作为当前知识库的快速提问起点。</div></div>
      </div>
      <div class="query-chip-list"><button v-for="item in recommendedQuestions" :key="item" class="query-helper-chip" @click="applyQuestion(item)">{{ item }}</button></div>
    </div>

    <div v-if="!minimal && recentQuestions.length" class="query-helper-card recent-query-card">
      <div class="query-helper-header">
        <div><div class="query-helper-title">最近提问</div><div class="query-helper-subtitle">最近的问题会保存在本地，便于重复追问和复盘。</div></div>
        <button class="btn btn-ghost btn-sm" @click="clearRecentQuestions">清空</button>
      </div>
      <div class="query-chip-list"><button v-for="item in recentQuestions" :key="item" class="query-helper-chip recent" @click="applyQuestion(item)">{{ item }}</button></div>
    </div>

    <div v-if="ragStore.isQuerying" class="query-stage-card"><div class="query-stage-title">{{ stageTitle }}</div><div class="query-stage-desc">{{ stageDescription }}</div></div>
    <div v-if="ragStore.queryError" class="query-error-card"><div class="query-error-title">本次查询未成功完成</div><div class="query-error-desc">{{ ragStore.queryError }}</div><button class="btn btn-ghost btn-sm" :disabled="!question.trim()" @click="retryLastQuery">重试</button></div>

    <div class="query-result-shell">
      <div class="query-result-header">
        <div><div class="query-result-title">回答</div><div v-if="!minimal" class="query-result-subtitle">基于当前知识库生成的问答结果。</div></div>
        <div class="query-result-side">
          <div v-if="!minimal" class="query-result-meta">
            <span class="query-meta-chip">{{ streamMode ? '流式' : '标准' }}</span>
            <span class="query-meta-chip">TopK {{ topK }}</span>
            <span class="query-meta-chip">{{ ragStore.querySources.length }} 条证据</span>
            <span v-if="ragStore.querySources.length" class="query-meta-chip">最高 {{ topEvidenceScore }}</span>
          </div>
          <div v-if="ragStore.queryResult && !minimal" class="query-result-actions">
            <button class="query-action-btn" @click="copyAnswer">复制回答</button>
            <button class="query-action-btn" @click="copyEvidenceSummary">复制证据摘要</button>
            <button class="query-action-btn" @click="copyQuerySnapshot">复制问答快照</button>
          </div>
        </div>
      </div>

      <div class="query-result">
        <EmptyState
          v-if="!ragStore.queryResult && !ragStore.isQuerying"
          :icon="ragStore.currentKb ? 'Q' : 'KB'"
          :title="ragStore.currentKb ? '输入问题后开始查询' : '请先选择知识库'"
          :description="ragStore.currentKb ? '系统会结合当前知识库生成回答，并展示命中的证据分段。' : '先确定知识库范围，再进行提问、重试和证据定位。'"
          :action-text="ragStore.currentKb && question.trim() ? '立即查询' : undefined"
          variant="compact"
          @action="doQuery(false)"
        />
        <div v-else class="query-answer-card">
          <div v-if="answerInsights.length && !minimal" class="answer-insight-row">
            <div v-for="item in answerInsights" :key="item.label" class="answer-insight-card"><div class="answer-insight-label">{{ item.label }}</div><div class="answer-insight-value">{{ item.value }}</div></div>
          </div>
          <div class="query-answer-content" v-html="formattedResult"></div>
          <div v-if="ragStore.queryResponseId" class="feedback-bar">
            <span class="feedback-label">这条回答是否有帮助？</span>
            <button class="feedback-btn" :class="{ active: ragStore.queryFeedback === 'up' }" @click="submitFeedback('up')">有帮助</button>
            <button class="feedback-btn" :class="{ active: ragStore.queryFeedback === 'down' }" @click="submitFeedback('down')">待改进</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!minimal && followUpSuggestions.length" class="query-helper-card follow-up-card">
      <div class="query-helper-header"><div><div class="query-helper-title">继续追问</div><div class="query-helper-subtitle">基于当前问题和证据，快速补充下一轮更具体的提问。</div></div></div>
      <div class="query-chip-list"><button v-for="item in followUpSuggestions" :key="item" class="query-helper-chip follow-up" @click="applyQuestion(item)">{{ item }}</button></div>
    </div>

    <div v-if="!minimal && ragStore.queryResult && !ragStore.isQuerying && !ragStore.querySources.length" class="query-no-evidence">
      <EmptyState icon="RAG" title="本次回答未返回证据" description="回答已生成，但没有返回来源分段。可以先根据下方建议调整提问方式后再试。" variant="compact" align="left" />
      <div class="query-no-evidence-guide">
        <div class="query-no-evidence-guide-title">建议先这样调整问题</div>
        <div class="query-no-evidence-guide-list">
          <div v-for="item in noEvidenceGuideItems" :key="item.title" class="query-no-evidence-guide-item">
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </div>
        </div>
      </div>
      <div class="query-no-evidence-actions">
        <button class="query-action-btn" @click="applySuggestion('expand-topk')">提高 TopK 到 10</button>
        <button class="query-action-btn" @click="applySuggestion('add-keywords')">补充关键词重试</button>
        <button class="query-action-btn" @click="applySuggestion('narrow-question')">改成更具体的问题</button>
      </div>
    </div>

    <div v-if="ragStore.querySources.length" class="evidence-panel">
      <div class="evidence-header">
        <div><div class="evidence-title">证据</div><div v-if="!minimal" class="evidence-subtitle">以下来源分段用于支撑当前回答。</div></div>
        <div class="evidence-summary"><span class="evidence-count">{{ ragStore.querySources.length }} 条</span><span v-if="!minimal" class="evidence-count">分数范围 {{ evidenceScoreRange }}</span></div>
      </div>
      <div class="evidence-list">
        <div v-for="(src, idx) in ragStore.querySources" :key="src.chunkId || idx" class="evidence-card">
          <div class="evidence-card-topline"><span class="evidence-topline-label">证据 {{ idx + 1 }}</span><span class="evidence-topline-score">分数 {{ formatScore(src.score) }}</span></div>
          <div class="evidence-meta">
            <div class="evidence-file"><span class="evidence-index">#{{ idx + 1 }}</span><span>{{ src.filename }}</span><span v-if="src.chunkIndex != null" class="evidence-chunk">分段 {{ src.chunkIndex }}</span></div>
            <div class="evidence-actions"><div v-if="!minimal" class="evidence-score">相关度 {{ formatScore(src.score) }}</div><button class="evidence-mini-btn" @click="focusEvidenceSource(src)">定位</button><button v-if="!minimal" class="evidence-mini-btn" @click="copyEvidence(src)">复制</button></div>
          </div>
          <div v-if="!minimal" class="evidence-insight-row">
            <div class="evidence-insight-card"><div class="evidence-insight-label">来源摘要</div><div class="evidence-insight-value">{{ summarizeSource(src) }}</div></div>
            <div class="evidence-insight-card"><div class="evidence-insight-label">命中原因</div><div class="evidence-insight-value">{{ matchReason(src.score) }}</div></div>
          </div>
          <div v-if="matchedTerms(src).length && !minimal" class="evidence-hit-terms">
            <span class="evidence-hit-label">命中关键词</span>
            <span v-for="term in matchedTerms(src)" :key="term" class="evidence-hit-chip">{{ term }}</span>
          </div>
          <div class="evidence-preview" v-html="highlightEvidence(src.preview || summarize(src.content))"></div>
          <div v-if="src.chunkId && ragStore.queryResponseId" class="evidence-feedback">
            <span class="feedback-label">这条证据是否有用？</span>
            <button class="feedback-btn" :class="{ active: src.feedback === 'up' }" @click="submitEvidenceFeedback(src.chunkId, 'up')">准确</button>
            <button class="feedback-btn" :class="{ active: src.feedback === 'down' }" @click="submitEvidenceFeedback(src.chunkId, 'down')">偏弱</button>
          </div>
          <details v-if="!minimal" class="evidence-details"><summary>查看完整分段</summary><pre>{{ src.content }}</pre></details>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useToast } from '@/composables/useToast'
import { useRagStore } from '@/stores/rag'
import { formatMarkdown } from '@/utils/format'

interface QueryWorkspaceState { question: string; topK: number; streamMode: boolean }
type SuggestionType = 'expand-topk' | 'add-keywords' | 'narrow-question'

const emit = defineEmits<{ (e: 'focus-document', payload: { documentId?: string; filename: string; chunkIndex?: number; highlightTerms: string[] }): void }>()
const { minimal = false } = defineProps<{ minimal?: boolean }>()
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
const answerInsights = computed(() => !ragStore.queryResult ? [] : [{ label: '回答长度', value: `${ragStore.queryResult.trim().length} 字` }, { label: '证据条数', value: `${ragStore.querySources.length} 条` }, { label: '当前模式', value: streamMode.value ? '流式回答' : '标准回答' }])
const followUpSuggestions = computed(() => {
  if (!ragStore.queryResult || !question.value.trim()) return []
  const base = question.value.trim()
  const firstSource = ragStore.querySources[0]?.filename
  return [`${base}，请按步骤展开说明。`, `${base}，请只保留最关键的规则和注意事项。`, firstSource ? `请结合《${firstSource}》进一步解释刚才的结论。` : `${base}，请给出可以直接执行的检查清单。`]
})
const evidenceScoreRange = computed(() => {
  if (!ragStore.querySources.length) return '-'
  const scores = ragStore.querySources.map((item) => item.score).filter((item) => !Number.isNaN(item))
  return scores.length ? `${formatScore(Math.max(...scores))} - ${formatScore(Math.min(...scores))}` : '-'
})
const noEvidenceGuideItems = computed(() => [
  {
    title: '补充业务名词',
    description: '把系统名、字段名、文档标题或流程节点直接写进问题里。'
  },
  {
    title: '缩小问题范围',
    description: '先只问一个规则、一个报错或一个步骤，避免一次覆盖整条链路。'
  },
  {
    title: '扩大召回范围',
    description: '如果知识库里确定有资料，可以先把 TopK 提高到 10 再重试。'
  }
])
const stageTitle = computed(() => ragStore.queryStage === 'answering' ? '正在生成回答' : '正在检索证据')
const stageDescription = computed(() => ragStore.queryStage === 'answering' ? '已经找到相关分段，正在整理回答内容。' : '系统正在当前知识库中查找最相关的证据分段。')
const recommendedQuestions = computed(() => {
  const kbName = ragStore.currentKbName || '当前知识库'
  return [`请概括 ${kbName} 覆盖的核心主题。`, `请说明 ${kbName} 中最重要的规则或约束。`, `请列出学习 ${kbName} 时最值得优先阅读的文档。`]
})

async function doQuery(stream: boolean) {
  if (!question.value.trim()) return showToast('请输入问题')
  saveRecentQuestion(question.value)
  streamMode.value = stream
  persistWorkspaceState()
  await ragStore.ragQuery(question.value, stream, topK.value)
}
function retryLastQuery() { if (question.value.trim()) void doQuery(streamMode.value) }
async function submitFeedback(feedback: 'up' | 'down') { try { if (await ragStore.submitQueryFeedback(feedback)) showToast(feedback === 'up' ? '已记录回答反馈' : '已记录改进建议') } catch { showToast('提交反馈失败') } }
async function submitEvidenceFeedback(chunkId: string, feedback: 'up' | 'down') { try { if (await ragStore.submitEvidenceFeedback(chunkId, feedback)) showToast(feedback === 'up' ? '已记录证据反馈' : '已记录证据问题') } catch { showToast('提交证据反馈失败') } }
function summarize(content: string) { const normalized = content.replace(/\s+/g, ' ').trim(); return normalized.length <= 180 ? normalized : `${normalized.slice(0, 180)}...` }
function formatScore(score: number) { return Number.isNaN(score) ? '-' : score.toFixed(3) }
function applyQuestion(value: string) { question.value = value }
function escapeHtml(value: string) { return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;') }
function escapeRegExp(value: string) { return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') }
function getHighlightTerms() { return question.value.split(/[\s,，。；;、]+/).map((item) => item.trim()).filter((item) => item.length >= 2).slice(0, 6) }
function highlightEvidence(value: string) { let output = escapeHtml(value); for (const term of getHighlightTerms()) output = output.replace(new RegExp(`(${escapeRegExp(term)})`, 'gi'), '<mark class="evidence-highlight">$1</mark>'); return output }
function matchedTerms(src: { preview?: string; content: string }) { const source = `${src.preview || ''}\n${src.content || ''}`.toLowerCase(); return getHighlightTerms().filter((term) => source.includes(term.toLowerCase())) }
function getWorkspaceStateKey(kbId: string) { return `${WORKSPACE_KEY_PREFIX}${kbId}` }
function persistWorkspaceState() { if (ragStore.currentKb) window.sessionStorage.setItem(getWorkspaceStateKey(ragStore.currentKb), JSON.stringify({ question: question.value, topK: topK.value, streamMode: streamMode.value } satisfies QueryWorkspaceState)) }
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
  } catch { return false }
}
function resetWorkspaceState() { if (!ragStore.currentKb) return; question.value = recommendedQuestions.value[0] || ''; topK.value = 5; streamMode.value = false; persistWorkspaceState(); showToast('当前知识库的提问草稿已重置') }
function saveRecentQuestion(value: string) { const normalized = value.trim(); if (!normalized) return; recentQuestions.value = [normalized, ...recentQuestions.value.filter((item) => item !== normalized)].slice(0, 6); window.sessionStorage.setItem(RECENT_QUERY_KEY, JSON.stringify(recentQuestions.value)) }
function clearRecentQuestions() { recentQuestions.value = []; window.sessionStorage.removeItem(RECENT_QUERY_KEY) }
function summarizeSource(src: { filename: string; preview?: string; content: string }) { const normalized = (src.preview || src.content || src.filename).replace(/\s+/g, ' ').trim(); return normalized.length <= 72 ? normalized : `${normalized.slice(0, 72)}...` }
function matchReason(score: number) { return score >= 0.9 ? '与问题语义高度匹配，内容重合度较高。' : score >= 0.75 ? '属于较强支撑证据，主题相关性明确。' : score >= 0.6 ? '可作为上下文参考，建议结合相邻分段交叉确认。' : '相关度偏弱，更适合作为补充信息。' }
function applySuggestion(type: SuggestionType) { if (type === 'expand-topk') { topK.value = 10; return showToast('已将 TopK 提高到 10') } if (type === 'add-keywords') { question.value = `${question.value.trim()} 请结合文档标题、关键词和具体规则回答。`.trim(); return showToast('已补充关键词提示') } question.value = `${question.value.trim()} 请只回答最直接相关的条款、步骤或定义。`.trim(); showToast('已收窄问题范围') }
function focusEvidenceSource(src: { documentId?: string; filename: string; chunkIndex?: number }) { emit('focus-document', { documentId: src.documentId, filename: src.filename, chunkIndex: src.chunkIndex, highlightTerms: getHighlightTerms() }); showToast(`已定位到 ${src.filename}`) }
async function copyEvidence(src: { filename: string; content: string; chunkIndex?: number }) { try { await navigator.clipboard.writeText([`来源：${src.filename}`, src.chunkIndex != null ? `分段：${src.chunkIndex}` : '', src.content].filter(Boolean).join('\n')); showToast('已复制证据内容') } catch { showToast('复制证据内容失败') } }
async function copyAnswer() { try { await navigator.clipboard.writeText(ragStore.queryResult); showToast('已复制回答') } catch { showToast('复制回答失败') } }
async function copyEvidenceSummary() {
  const payload = ragStore.querySources.map((src, idx) => [`证据 ${idx + 1}`, `文件：${src.filename}`, src.chunkIndex != null ? `分段：${src.chunkIndex}` : '', `分数：${formatScore(src.score)}`, src.preview || summarize(src.content)].filter(Boolean).join('\n')).join('\n\n')
  try { await navigator.clipboard.writeText(payload); showToast('已复制证据摘要') } catch { showToast('复制证据摘要失败') }
}
async function copyQuerySnapshot() {
  const payload = [`问题：${question.value.trim() || '-'}`, `知识库：${ragStore.currentKbName || ragStore.currentKb || '未选择'}`, `模式：${streamMode.value ? '流式回答' : '标准回答'}`, `TopK：${topK.value}`, '', '回答：', ragStore.queryResult, '', '证据概览：', ragStore.querySources.length ? ragStore.querySources.map((src, idx) => `${idx + 1}. ${src.filename}${src.chunkIndex != null ? ` / 分段 ${src.chunkIndex}` : ''} / 分数 ${formatScore(src.score)}`).join('\n') : '无'].join('\n')
  try { await navigator.clipboard.writeText(payload); showToast('已复制问答快照') } catch { showToast('复制问答快照失败') }
}

watch([question, topK, streamMode], () => { persistWorkspaceState() })
watch(() => ragStore.currentKb, (kbId, previousKb) => { if (previousKb && previousKb !== kbId) persistWorkspaceState(); const loaded = loadWorkspaceState(kbId); if (!loaded && !question.value.trim()) { question.value = recommendedQuestions.value[0] || ''; topK.value = 5; streamMode.value = false } }, { immediate: true })
watch(() => ragStore.currentKbName, () => { if (!question.value.trim()) question.value = recommendedQuestions.value[0] || '' })
onMounted(() => { try { recentQuestions.value = JSON.parse(window.sessionStorage.getItem(RECENT_QUERY_KEY) || '[]') } catch { recentQuestions.value = [] } if (!loadWorkspaceState(ragStore.currentKb) && !question.value.trim()) question.value = recommendedQuestions.value[0] || '' })
</script>

<style scoped>
.query-context-card, .query-helper-header, .query-result-header, .evidence-header, .evidence-meta, .evidence-card-topline { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.query-context-card { margin-bottom: 12px; padding: 16px; border: 1px solid var(--border); border-radius: 18px; background: linear-gradient(135deg, rgba(16,185,129,0.06), rgba(255,255,255,0.02)); }
.query-context-title, .answer-insight-label, .evidence-insight-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--text3); }
.query-context-subtitle, .query-result-title, .evidence-title { color: var(--text); font-size: 14px; font-weight: 600; }
.query-context-meta, .query-result-meta, .query-result-actions, .query-chip-list, .query-toolbar, .query-no-evidence-actions, .feedback-bar, .evidence-summary, .evidence-actions, .evidence-feedback, .evidence-hit-terms { display: flex; gap: 8px; flex-wrap: wrap; }
.query-context-chip, .query-meta-chip, .query-helper-chip, .query-action-btn, .feedback-btn, .evidence-mini-btn { border: 1px solid var(--border); background: rgba(255,255,255,0.03); color: var(--text2); border-radius: 999px; }
.query-context-chip, .query-meta-chip { padding: 5px 10px; font-size: 12px; }
.query-input-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 6px; }
.query-input-hint, .query-helper-subtitle, .query-stage-desc, .query-error-desc, .query-result-subtitle, .evidence-subtitle, .evidence-count, .feedback-label, .evidence-score, .evidence-insight-value { color: var(--text3); font-size: 12px; }
.query-textarea { min-height: 96px; border-radius: 16px; }
.topk-select { padding: 5px 8px; font-size: 11px; width: auto; }
.query-helper-card { border: 1px solid var(--border); border-radius: 16px; padding: 14px 16px; margin-bottom: 12px; background: rgba(255,255,255,0.02); }
.query-helper-title, .query-stage-title, .query-error-title { font-size: 13px; font-weight: 600; color: var(--text); }
.query-helper-chip, .query-action-btn, .feedback-btn, .evidence-mini-btn { padding: 6px 12px; font-size: 12px; cursor: pointer; transition: all var(--transition); }
.query-helper-chip:hover, .query-action-btn:hover, .evidence-mini-btn:hover { border-color: var(--accent); color: var(--accent2); background: var(--accent-dim); }
.query-stage-card, .query-error-card { border-radius: 16px; padding: 14px 16px; margin-bottom: 12px; }
.query-stage-card { background: rgba(59,130,246,0.08); border: 1px solid rgba(59,130,246,0.18); }
.query-error-card { background: rgba(239,68,68,0.08); border: 1px solid rgba(239,68,68,0.16); }
.query-result-shell { border: 1px solid var(--border); border-radius: 20px; background: linear-gradient(180deg, rgba(255,255,255,0.02), rgba(255,255,255,0.01)); overflow: hidden; }
.query-result-header, .query-result { padding: 16px; }
.query-result-header { border-bottom: 1px solid var(--border); }
.query-result-side { display: flex; flex-direction: column; align-items: flex-end; gap: 10px; }
.query-answer-card { display: flex; flex-direction: column; gap: 12px; }
.answer-insight-row, .evidence-insight-row { display: grid; gap: 10px; }
.answer-insight-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.evidence-insight-row { grid-template-columns: repeat(2, minmax(0, 1fr)); margin-bottom: 10px; }
.answer-insight-card, .evidence-insight-card, .evidence-card { border: 1px solid var(--border); border-radius: 14px; padding: 12px; background: rgba(255,255,255,0.03); }
.answer-insight-value { margin-top: 6px; color: var(--text); font-size: 13px; font-weight: 600; }
.query-answer-content, .evidence-preview { color: var(--text2); line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
.feedback-btn.active { border-color: var(--brand); color: var(--brand); background: rgba(59,130,246,0.08); }
.query-no-evidence { margin-top: 16px; border: 1px dashed var(--border); border-radius: 16px; padding: 14px; background: rgba(255,255,255,0.02); }
.query-no-evidence-guide { margin: 12px 0; display: grid; gap: 10px; }
.query-no-evidence-guide-title { color: var(--text); font-size: 13px; font-weight: 600; }
.query-no-evidence-guide-list { display: grid; gap: 8px; }
.query-no-evidence-guide-item { display: grid; gap: 4px; padding: 10px 12px; border-radius: 12px; border: 1px solid var(--border); background: rgba(255,255,255,0.03); }
.query-no-evidence-guide-item strong { color: var(--text); font-size: 12px; }
.query-no-evidence-guide-item span { color: var(--text3); font-size: 12px; line-height: 1.6; }
.evidence-panel { margin-top: 16px; border-top: 1px solid var(--border); padding-top: 16px; }
.evidence-list { display: grid; gap: 10px; }
.evidence-file { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; color: var(--text); font-size: 12px; }
.evidence-index, .evidence-chunk { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 999px; background: rgba(59,130,246,0.12); color: #2563eb; font-size: 11px; }
.evidence-hit-label { color: var(--text3); font-size: 12px; }
.evidence-hit-chip { display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 999px; border: 1px solid rgba(59,130,246,0.16); background: rgba(59,130,246,0.08); color: #1d4ed8; font-size: 11px; }
:deep(.evidence-highlight) { background: rgba(250,204,21,0.35); color: inherit; padding: 0 2px; border-radius: 4px; }
.evidence-details { margin-top: 10px; }
.evidence-details summary { cursor: pointer; color: var(--brand); font-size: 12px; }
.evidence-details pre { margin: 10px 0 0; padding: 12px; border-radius: 10px; background: #0f172a; color: #e2e8f0; white-space: pre-wrap; word-break: break-word; font-size: 12px; line-height: 1.6; }
.query-panel-minimal {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: min(72vh, 920px);
}
.query-panel-minimal .form-group {
  margin-bottom: 0;
  order: 3;
  position: sticky;
  bottom: 58px;
  z-index: 3;
  padding: 14px 16px 0;
  margin: 0 -4px;
  background: linear-gradient(180deg, rgba(20,24,31,0), rgba(20,24,31,0.92) 18%, rgba(20,24,31,0.98));
  backdrop-filter: blur(12px);
}
.query-panel-minimal .query-input-head {
  margin-bottom: 10px;
}
.query-panel-minimal .query-textarea {
  min-height: 148px;
  padding: 18px 20px;
  font-size: 15px;
  line-height: 1.7;
  background: linear-gradient(180deg, rgba(255,255,255,0.04), rgba(255,255,255,0.02));
  border-color: rgba(148,163,184,0.22);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.04);
}
.query-panel-minimal .query-toolbar {
  order: 4;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  position: sticky;
  bottom: 0;
  z-index: 4;
  padding: 10px 16px 2px;
  margin: 0 -4px;
  background: rgba(20,24,31,0.98);
  border-top: 1px solid rgba(255,255,255,0.06);
  backdrop-filter: blur(12px);
}
.query-panel-minimal .query-result-shell {
  order: 1;
  flex: 1;
  min-height: 0;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(255,255,255,0.03), rgba(255,255,255,0.015));
  box-shadow: 0 18px 34px rgba(15, 23, 42, 0.08);
}
.query-panel-minimal .query-result-header,
.query-panel-minimal .query-result {
  padding: 18px 20px;
}
.query-panel-minimal .query-result-title,
.query-panel-minimal .evidence-title {
  font-size: 16px;
}
.query-panel-minimal .query-answer-content {
  font-size: 14px;
  line-height: 1.9;
}
.query-panel-minimal .query-result-header {
  background: linear-gradient(180deg, rgba(255,255,255,0.03), rgba(255,255,255,0.01));
}
.query-panel-minimal .evidence-panel {
  order: 2;
  margin-top: 0;
  padding-top: 14px;
  padding-bottom: 10px;
}
.query-panel-minimal .evidence-list {
  gap: 12px;
}
.query-panel-minimal .evidence-card {
  padding: 14px;
  border-radius: 16px;
}
.query-panel-minimal .evidence-preview {
  margin-top: 10px;
}
.query-panel-minimal .query-stage-card,
.query-panel-minimal .query-error-card {
  order: 1;
}
.query-panel-minimal .query-result {
  overflow: auto;
}
@media (max-width: 820px) {
  .query-context-card, .query-input-head, .query-result-header, .query-helper-header, .evidence-header, .evidence-meta, .evidence-card-topline { flex-direction: column; align-items: flex-start; }
  .evidence-insight-row, .answer-insight-row { grid-template-columns: 1fr; }
  .query-result-side { align-items: flex-start; }
  .query-panel-minimal .query-toolbar { align-items: stretch; }
  .query-panel-minimal .form-group {
    bottom: 100px;
  }
}
</style>
