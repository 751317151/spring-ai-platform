<template>
  <div class="query-panel" :class="{ 'query-panel-minimal': minimal }">
    <section v-if="!minimal" class="card">
      <div class="row">
        <div>
          <div class="label">当前问答上下文</div>
          <div class="title">{{ ragStore.currentKbName || '未选择知识库' }}</div>
        </div>
        <div class="chips">
          <span class="chip">已加载 {{ ragStore.documents.length }} 份文档</span>
          <span class="chip">{{ topKLabel }}</span>
          <span class="chip">{{ streamMode ? '流式模式' : '标准模式' }}</span>
        </div>
      </div>
    </section>

    <div class="form-group">
      <div class="row">
        <label class="form-label">问题</label>
        <div v-if="!minimal" class="muted">系统会按知识库保存提问草稿、TopK 和问答模式。</div>
      </div>
      <textarea
        v-model="question"
        class="form-input query-textarea"
        :rows="minimal ? 5 : 3"
        placeholder="请输入针对当前知识库的问题..."
      />
    </div>

    <div class="toolbar">
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
      <button v-if="!minimal" class="btn btn-ghost btn-sm" :disabled="!ragStore.currentKb" @click="resetWorkspaceState">重置草稿</button>
    </div>

    <section v-if="!minimal && recommendedQuestions.length" class="card">
      <div class="helper-title">推荐问题</div>
      <div class="helper-subtitle">可以作为当前知识库的快速提问起点。</div>
      <div class="chips">
        <button v-for="item in recommendedQuestions" :key="item" class="chip chip-btn" @click="applyQuestion(item)">{{ item }}</button>
      </div>
    </section>

    <section v-if="!minimal && recentQuestions.length" class="card">
      <div class="row">
        <div>
          <div class="helper-title">最近提问</div>
          <div class="helper-subtitle">最近的问题会保存在本地，便于重复追问和复盘。</div>
        </div>
        <button class="btn btn-ghost btn-sm" @click="clearRecentQuestions">清空</button>
      </div>
      <div class="chips">
        <button v-for="item in recentQuestions" :key="item" class="chip chip-btn" @click="applyQuestion(item)">{{ item }}</button>
      </div>
    </section>

    <section v-if="ragStore.isQuerying" class="info-card">
      <div class="helper-title">{{ stageTitle }}</div>
      <div class="muted">{{ stageDescription }}</div>
    </section>

    <section v-if="ragStore.queryError" class="error-card">
      <div class="helper-title">本次查询未成功完成</div>
      <div class="muted">{{ ragStore.queryError }}</div>
      <button class="btn btn-ghost btn-sm" :disabled="!question.trim()" @click="retryLastQuery">重试</button>
    </section>

    <section v-if="retrievalSummary" class="card">
      <div class="helper-title">检索解释</div>
      <div class="helper-subtitle">本次问题改写、多路召回和重排序的实际执行情况。</div>
      <div class="debug-grid">
        <div class="debug-item"><span class="label">检索 Query</span><strong>{{ retrievalSummary.retrievalQuery }}</strong></div>
        <div class="debug-item"><span class="label">关键词</span><strong>{{ retrievalSummary.keywords }}</strong></div>
        <div class="debug-item"><span class="label">候选数</span><strong>{{ retrievalSummary.candidateCount }}</strong></div>
        <div class="debug-item"><span class="label">入模证据</span><strong>{{ retrievalSummary.selectedCount }}</strong></div>
      </div>
      <div v-if="retrievalSteps.length" class="list">
        <div v-for="step in retrievalSteps" :key="`${step.source}-${step.query}`" class="list-item">
          <strong>{{ step.source }}</strong>
          <span>{{ step.query }} 路召回 {{ step.returnedCount }} 条</span>
        </div>
      </div>
    </section>

    <section class="card result-card">
      <div class="row">
        <div>
          <div class="title">回答</div>
          <div v-if="!minimal" class="muted">基于当前知识库生成的问答结果。</div>
        </div>
        <div v-if="!minimal && ragStore.queryResult" class="chips">
          <button class="chip chip-btn" @click="copyAnswer">复制回答</button>
          <button class="chip chip-btn" @click="copyEvidenceSummary">复制证据摘要</button>
          <button class="chip chip-btn" @click="copyQuerySnapshot">复制问答快照</button>
        </div>
      </div>

      <EmptyState
        v-if="!ragStore.queryResult && !ragStore.isQuerying"
        :icon="ragStore.currentKb ? 'Q' : 'KB'"
        :title="ragStore.currentKb ? '输入问题后开始查询' : '请先选择知识库'"
        :description="ragStore.currentKb ? '系统会结合当前知识库生成回答，并展示命中的证据片段。' : '先确定知识库范围，再进行提问、重试和证据定位。'"
        :action-text="ragStore.currentKb && question.trim() ? '立即查询' : undefined"
        variant="compact"
        @action="doQuery(false)"
      />
      <div v-else class="content-wrap">
        <div v-if="answerInsights.length && !minimal" class="debug-grid">
          <div v-for="item in answerInsights" :key="item.label" class="debug-item">
            <span class="label">{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <div class="query-answer-content" v-html="formattedResult"></div>
        <div v-if="ragStore.queryResponseId" class="chips">
          <span class="muted">这条回答是否有帮助？</span>
          <button class="chip chip-btn" :class="{ active: ragStore.queryFeedback === 'up' }" @click="submitFeedback('up')">有帮助</button>
          <button class="chip chip-btn" :class="{ active: ragStore.queryFeedback === 'down' }" @click="submitFeedback('down')">待改进</button>
        </div>
      </div>
    </section>

    <section v-if="!minimal && followUpSuggestions.length" class="card">
      <div class="helper-title">继续追问</div>
      <div class="helper-subtitle">基于当前问题和证据，快速补充下一轮更具体的提问。</div>
      <div class="chips">
        <button v-for="item in followUpSuggestions" :key="item" class="chip chip-btn" @click="applyQuestion(item)">{{ item }}</button>
      </div>
    </section>

    <section v-if="!minimal && ragStore.queryResult && !ragStore.isQuerying && !ragStore.querySources.length" class="card">
      <EmptyState
        icon="RAG"
        title="本次回答未返回证据"
        description="回答已生成，但没有返回来源片段。可以先根据下方建议调整提问方式后再试。"
        variant="compact"
        align="left"
      />
      <div class="helper-title">建议先这样调整问题</div>
      <div class="list">
        <div v-for="item in noEvidenceGuideItems" :key="item.title" class="list-item">
          <strong>{{ item.title }}</strong>
          <span>{{ item.description }}</span>
        </div>
      </div>
      <div class="chips">
        <button class="chip chip-btn" @click="applySuggestion('expand-topk')">提高 TopK 到 10</button>
        <button class="chip chip-btn" @click="applySuggestion('add-keywords')">补充关键词重试</button>
        <button class="chip chip-btn" @click="applySuggestion('narrow-question')">改成更具体的问题</button>
      </div>
    </section>

    <section v-if="ragStore.querySources.length" class="card">
      <div class="row">
        <div>
          <div class="title">证据</div>
          <div v-if="!minimal" class="muted">以下来源片段用于支撑当前回答。</div>
        </div>
        <div class="muted">{{ ragStore.querySources.length }} 条 | {{ evidenceScoreRange }}</div>
      </div>

      <div class="list">
        <div v-for="(src, idx) in ragStore.querySources" :key="src.chunkId || idx" class="evidence-card">
          <div class="row">
            <div class="title-sm">证据 {{ idx + 1 }} · {{ src.filename }}</div>
            <div class="chips">
              <span class="chip">分数 {{ formatScore(src.score) }}</span>
              <span v-if="src.chunkIndex != null" class="chip">分段 {{ src.chunkIndex }}</span>
              <button class="chip chip-btn" @click="focusEvidenceSource(src)">定位</button>
              <button v-if="!minimal" class="chip chip-btn" @click="copyEvidence(src)">复制</button>
            </div>
          </div>
          <div v-if="!minimal" class="debug-grid">
            <div class="debug-item"><span class="label">来源摘要</span><strong>{{ summarizeSource(src) }}</strong></div>
            <div class="debug-item"><span class="label">命中原因</span><strong>{{ matchReason(src.score) }}</strong></div>
          </div>
          <div v-if="!minimal && sourceTags(src).length" class="chips">
            <span class="muted">命中标签</span>
            <span v-for="tag in sourceTags(src)" :key="tag" class="chip">{{ tag }}</span>
          </div>
          <div class="query-answer-content" v-html="highlightEvidence(src.preview || summarize(src.content))"></div>
          <div v-if="src.chunkId && ragStore.queryResponseId" class="chips">
            <span class="muted">这条证据是否有用？</span>
            <button class="chip chip-btn" :class="{ active: src.feedback === 'up' }" @click="submitEvidenceFeedback(src.chunkId, 'up')">准确</button>
            <button class="chip chip-btn" :class="{ active: src.feedback === 'down' }" @click="submitEvidenceFeedback(src.chunkId, 'down')">偏弱</button>
          </div>
          <details v-if="!minimal" class="details">
            <summary>查看完整片段</summary>
            <pre>{{ src.content }}</pre>
          </details>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { RecallTraceItem, SourceDocument } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import { useToast } from '@/composables/useToast'
import { useRagStore } from '@/stores/rag'
import { formatMarkdown } from '@/utils/format'

interface QueryWorkspaceState {
  question: string
  topK: number
  streamMode: boolean
}

type SuggestionType = 'expand-topk' | 'add-keywords' | 'narrow-question'

const emit = defineEmits<{
  (e: 'focus-document', payload: { documentId?: string; filename: string; chunkIndex?: number; highlightTerms: string[] }): void
}>()

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
const retrievalSteps = computed<RecallTraceItem[]>(() => ragStore.queryRetrievalDebug?.recallSteps || [])
const topEvidenceScore = computed(() => formatScore(ragStore.querySources[0]?.score ?? Number.NaN))
const retrievalSummary = computed(() => {
  const debug = ragStore.queryRetrievalDebug
  if (!debug) return null
  return {
    retrievalQuery: debug.retrievalQuery || debug.originalQuery || '-',
    keywords: debug.keywords?.length ? debug.keywords.join(' / ') : '无',
    candidateCount: debug.candidateCount ?? 0,
    selectedCount: debug.selectedCount ?? ragStore.querySources.length
  }
})
const answerInsights = computed(() => !ragStore.queryResult
  ? []
  : [
      { label: '回答长度', value: `${ragStore.queryResult.trim().length} 字` },
      { label: '证据条数', value: `${ragStore.querySources.length} 条` },
      { label: '当前模式', value: streamMode.value ? '流式回答' : '标准回答' },
      { label: '最高分', value: topEvidenceScore.value }
    ])
const followUpSuggestions = computed(() => {
  if (!ragStore.queryResult || !question.value.trim()) return []
  const base = question.value.trim()
  const firstSource = ragStore.querySources[0]?.filename
  return [
    `${base}，请按步骤展开说明。`,
    `${base}，请只保留最关键的规则和注意事项。`,
    firstSource ? `请结合《${firstSource}》进一步解释刚才的结论。` : `${base}，请给出可以直接执行的检查清单。`
  ]
})
const evidenceScoreRange = computed(() => {
  if (!ragStore.querySources.length) return '-'
  const scores = ragStore.querySources.map((item) => item.score).filter((item) => !Number.isNaN(item))
  return scores.length ? `${formatScore(Math.max(...scores))} - ${formatScore(Math.min(...scores))}` : '-'
})
const noEvidenceGuideItems = computed(() => [
  { title: '补充业务名词', description: '把系统名、字段名、文档标题或流程节点直接写进问题里。' },
  { title: '缩小问题范围', description: '先只问一个规则、一个报错或一个步骤，避免一次覆盖整条链路。' },
  { title: '扩大召回范围', description: '如果知识库里确定有资料，可以先把 TopK 提高到 10 再重试。' }
])
const stageTitle = computed(() => ragStore.queryStage === 'answering' ? '正在生成回答' : '正在检索证据')
const stageDescription = computed(() => ragStore.queryStage === 'answering'
  ? '已经找到相关片段，正在整理回答内容。'
  : '系统正在当前知识库中查找最相关的证据分段。')
const recommendedQuestions = computed(() => {
  const kbName = ragStore.currentKbName || '当前知识库'
  return [
    `请概括 ${kbName} 覆盖的核心主题。`,
    `请说明 ${kbName} 中最重要的规则或约束。`,
    `请列出学习 ${kbName} 时最值得优先阅读的文档。`
  ]
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
function sourceTags(src: SourceDocument) { const tags = new Set<string>([...(src.matchedTerms || []), ...(src.recallSources || [])]); if (!tags.size) { const source = `${src.preview || ''}\n${src.content || ''}`.toLowerCase(); getHighlightTerms().filter((term) => source.includes(term.toLowerCase())).forEach((term) => tags.add(term)) } return Array.from(tags).slice(0, 6) }
function getWorkspaceStateKey(kbId: string) { return `${WORKSPACE_KEY_PREFIX}${kbId}` }
function persistWorkspaceState() { if (ragStore.currentKb) window.sessionStorage.setItem(getWorkspaceStateKey(ragStore.currentKb), JSON.stringify({ question: question.value, topK: topK.value, streamMode: streamMode.value } satisfies QueryWorkspaceState)) }
function loadWorkspaceState(kbId: string) { if (!kbId) return false; try { const raw = window.sessionStorage.getItem(getWorkspaceStateKey(kbId)); if (!raw) return false; const parsed = JSON.parse(raw) as Partial<QueryWorkspaceState>; question.value = parsed.question ?? ''; topK.value = parsed.topK === 3 || parsed.topK === 10 ? parsed.topK : 5; streamMode.value = Boolean(parsed.streamMode); return true } catch { return false } }
function resetWorkspaceState() { if (!ragStore.currentKb) return; question.value = recommendedQuestions.value[0] || ''; topK.value = 5; streamMode.value = false; persistWorkspaceState(); showToast('当前知识库的提问草稿已重置') }
function saveRecentQuestion(value: string) { const normalized = value.trim(); if (!normalized) return; recentQuestions.value = [normalized, ...recentQuestions.value.filter((item) => item !== normalized)].slice(0, 6); window.sessionStorage.setItem(RECENT_QUERY_KEY, JSON.stringify(recentQuestions.value)) }
function clearRecentQuestions() { recentQuestions.value = []; window.sessionStorage.removeItem(RECENT_QUERY_KEY) }
function summarizeSource(src: Pick<SourceDocument, 'filename' | 'preview' | 'content'>) { const normalized = (src.preview || src.content || src.filename).replace(/\s+/g, ' ').trim(); return normalized.length <= 72 ? normalized : `${normalized.slice(0, 72)}...` }
function matchReason(score: number) { return score >= 0.9 ? '与问题语义高度匹配，内容重合度较高。' : score >= 0.75 ? '属于较强支撑证据，主题相关性明确。' : score >= 0.6 ? '可作为上下文参考，建议结合相邻分段交叉确认。' : '相关度偏弱，更适合作为补充信息。' }
function applySuggestion(type: SuggestionType) { if (type === 'expand-topk') { topK.value = 10; return showToast('已将 TopK 提高到 10') } if (type === 'add-keywords') { question.value = `${question.value.trim()} 请结合文档标题、关键词和具体规则回答。`.trim(); return showToast('已补充关键词提示') } question.value = `${question.value.trim()} 请只回答最直接相关的条款、步骤或定义。`.trim(); showToast('已收窄问题范围') }
function focusEvidenceSource(src: Pick<SourceDocument, 'documentId' | 'filename' | 'chunkIndex'>) { emit('focus-document', { documentId: src.documentId, filename: src.filename, chunkIndex: src.chunkIndex, highlightTerms: getHighlightTerms() }); showToast(`已定位到 ${src.filename}`) }
async function copyEvidence(src: Pick<SourceDocument, 'filename' | 'content' | 'chunkIndex'>) { try { await navigator.clipboard.writeText([`来源：${src.filename}`, src.chunkIndex != null ? `分段：${src.chunkIndex}` : '', src.content].filter(Boolean).join('\n')); showToast('已复制证据内容') } catch { showToast('复制证据内容失败') } }
async function copyAnswer() { try { await navigator.clipboard.writeText(ragStore.queryResult); showToast('已复制回答') } catch { showToast('复制回答失败') } }
async function copyEvidenceSummary() { const payload = ragStore.querySources.map((src, idx) => [`证据 ${idx + 1}`, `文件：${src.filename}`, src.chunkIndex != null ? `分段：${src.chunkIndex}` : '', `分数：${formatScore(src.score)}`, src.preview || summarize(src.content)].filter(Boolean).join('\n')).join('\n\n'); try { await navigator.clipboard.writeText(payload); showToast('已复制证据摘要') } catch { showToast('复制证据摘要失败') } }
async function copyQuerySnapshot() { const payload = [`问题：${question.value.trim() || '-'}`, `知识库：${ragStore.currentKbName || ragStore.currentKb || '未选择'}`, `模式：${streamMode.value ? '流式回答' : '标准回答'}`, `TopK：${topK.value}`, '', '回答：', ragStore.queryResult || '-', '', '证据概览：', ragStore.querySources.length ? ragStore.querySources.map((src, idx) => `${idx + 1}. ${src.filename}${src.chunkIndex != null ? ` / 分段 ${src.chunkIndex}` : ''} / 分数 ${formatScore(src.score)}`).join('\n') : '无'].join('\n'); try { await navigator.clipboard.writeText(payload); showToast('已复制问答快照') } catch { showToast('复制问答快照失败') } }

watch([question, topK, streamMode], () => { persistWorkspaceState() })
watch(() => ragStore.currentKb, (kbId, previousKb) => { if (previousKb && previousKb !== kbId) persistWorkspaceState(); const loaded = loadWorkspaceState(kbId); if (!loaded && !question.value.trim()) { question.value = recommendedQuestions.value[0] || ''; topK.value = 5; streamMode.value = false } }, { immediate: true })
watch(() => ragStore.currentKbName, () => { if (!question.value.trim()) question.value = recommendedQuestions.value[0] || '' })
onMounted(() => { try { recentQuestions.value = JSON.parse(window.sessionStorage.getItem(RECENT_QUERY_KEY) || '[]') } catch { recentQuestions.value = [] } if (!loadWorkspaceState(ragStore.currentKb) && !question.value.trim()) question.value = recommendedQuestions.value[0] || '' })
</script>

<style scoped>
.query-panel { display: grid; gap: 12px; }
.query-panel-minimal { min-height: min(72vh, 920px); }
.card, .info-card, .error-card, .evidence-card, .debug-item, .list-item { border: 1px solid var(--border); border-radius: 16px; background: rgba(255,255,255,0.03); }
.card, .info-card, .error-card { padding: 14px 16px; }
.info-card { background: rgba(59,130,246,0.08); border-color: rgba(59,130,246,0.18); }
.error-card { background: rgba(239,68,68,0.08); border-color: rgba(239,68,68,0.16); }
.row, .toolbar, .chips { display: flex; gap: 8px; flex-wrap: wrap; justify-content: space-between; align-items: flex-start; }
.toolbar { justify-content: flex-start; }
.label, .muted, .helper-subtitle { color: var(--text3); font-size: 12px; }
.title, .helper-title, .title-sm { color: var(--text); font-weight: 600; }
.title { font-size: 14px; }
.title-sm, .helper-title { font-size: 13px; }
.chip { display: inline-flex; align-items: center; padding: 5px 10px; border-radius: 999px; border: 1px solid var(--border); background: rgba(255,255,255,0.03); color: var(--text2); font-size: 12px; }
.chip-btn { cursor: pointer; }
.chip-btn:hover, .chip-btn.active { border-color: var(--brand); color: var(--brand); background: rgba(59,130,246,0.08); }
.query-textarea { min-height: 96px; border-radius: 16px; }
.topk-select { width: auto; }
.debug-grid, .list, .content-wrap { display: grid; gap: 10px; }
.debug-grid { grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); }
.debug-item { padding: 10px 12px; }
.list-item, .evidence-card { padding: 12px; }
.query-answer-content { color: var(--text2); line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
.details pre { margin: 10px 0 0; padding: 12px; border-radius: 10px; background: #0f172a; color: #e2e8f0; white-space: pre-wrap; word-break: break-word; font-size: 12px; line-height: 1.6; }
:deep(.evidence-highlight) { background: rgba(250,204,21,0.35); color: inherit; padding: 0 2px; border-radius: 4px; }
.query-panel-minimal .toolbar { position: sticky; bottom: 0; z-index: 2; padding-bottom: 4px; background: rgba(20,24,31,0.98); }
.query-panel-minimal .form-group { position: sticky; bottom: 48px; z-index: 2; background: linear-gradient(180deg, rgba(20,24,31,0), rgba(20,24,31,0.96) 24%); padding-top: 12px; }
@media (max-width: 820px) { .row { flex-direction: column; align-items: flex-start; } .query-panel-minimal .form-group { bottom: 94px; } }
</style>
