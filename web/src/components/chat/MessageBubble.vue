<template>
  <article class="msg" :class="[role, { 'is-highlighted': highlighted }]" :data-message-index="messageIndex">
    <div class="msg-avatar" :class="role === 'user' ? 'user-av' : 'ai'">
      {{ role === 'user' ? 'U' : 'AI' }}
    </div>

    <div class="msg-content-wrap">
      <div class="msg-header-line">
        <div class="msg-header-main">
          <span class="msg-role-label">{{ role === 'user' ? '用户' : '助手' }}</span>
          <span v-if="role === 'assistant' && responseId" class="msg-response-chip">已生成</span>
          <button
            v-if="role === 'assistant' && traceId"
            class="msg-trace-chip"
            type="button"
            @click="emit('open-trace', traceId)"
          >
            Trace
          </button>
          <span v-if="derivedFromLabel" class="msg-derived-chip">{{ derivedFromLabel }}</span>
        </div>
        <span class="msg-time">{{ timeLabel }}</span>
      </div>

      <div v-if="runtimeConfigItems.length" class="msg-runtime-config">
        <span v-for="item in runtimeConfigItems" :key="item" class="msg-runtime-chip">{{ item }}</span>
      </div>

      <div v-if="summaryItems.length" class="msg-summary-card">
        <div class="msg-summary-title">快速摘要</div>
        <div class="msg-summary-list">
          <button
            v-for="item in summaryItems"
            :key="item"
            class="msg-summary-item"
            type="button"
            @click="emit('insert-prompt', item)"
          >
            {{ item }}
          </button>
        </div>
      </div>

      <div class="msg-bubble">
        <template v-for="(block, index) in contentBlocks" :key="`${role}-${index}`">
          <div v-if="block.type === 'text'" class="msg-rich-text" v-html="block.html"></div>
          <div v-else class="msg-code-block">
            <div class="msg-code-head">
              <span class="msg-code-lang">{{ block.language || '代码片段' }}</span>
              <button class="msg-code-copy" type="button" @click="copyCodeBlock(index, block.code)">
                {{ copiedCodeIndex === index ? '已复制' : '复制代码' }}
              </button>
            </div>
            <pre class="msg-code-content"><code>{{ block.code }}</code></pre>
          </div>
        </template>
      </div>

      <div class="msg-meta">
        <button v-if="content" class="msg-text-btn" type="button" @click="copyContent">复制</button>
        <button v-if="traceId" class="msg-text-btn" type="button" @click="copyTraceId">复制 TraceId</button>
        <button v-if="traceId" class="msg-text-btn" type="button" @click="emit('open-trace', traceId)">查看轨迹</button>
        <button v-if="content" class="msg-text-btn" type="button" @click="quoteContent">引用</button>
        <button v-if="content" class="msg-text-btn" type="button" @click="toggleFavorite">
          {{ isFavorite ? '已收藏' : '收藏' }}
        </button>
        <button v-if="content" class="msg-text-btn" type="button" @click="exportMessage">导出</button>
        <button
          v-if="role === 'assistant' && content"
          class="msg-text-btn"
          type="button"
          @click="emit('branch-session')"
        >
          分支会话
        </button>
        <button
          v-if="role === 'assistant' && content"
          class="msg-text-btn"
          type="button"
          @click="emit('continue-response')"
        >
          继续生成
        </button>
        <button
          v-if="role === 'assistant' && content"
          class="msg-text-btn"
          type="button"
          @click="emit('regenerate-response')"
        >
          重新回答
        </button>
        <button v-if="role === 'assistant' && content" class="msg-text-btn accent" type="button" @click="followUp">
          继续追问
        </button>

        <div v-if="showFeedback" class="msg-actions">
          <button class="feedback-btn" :class="{ active: feedback === 'up' }" type="button" @click="$emit('feedback', 'up')">
            有帮助
          </button>
          <button class="feedback-btn" :class="{ active: feedback === 'down' }" type="button" @click="$emit('feedback', 'down')">
            待改进
          </button>
        </div>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MessageDerivedFrom, SessionConfig } from '@/api/types'
import { useToast } from '@/composables/useToast'
import { formatMarkdown } from '@/utils/format'
import { isFavoriteMessage, removeFavoriteMessage, saveFavoriteMessage } from '@/utils/learning'

interface TextBlock {
  type: 'text'
  html: string
}

interface CodeBlock {
  type: 'code'
  language: string
  code: string
}

type ContentBlock = TextBlock | CodeBlock

const props = defineProps<{
  role: 'user' | 'assistant'
  content: string
  responseId?: string
  traceId?: string
  feedback?: 'up' | 'down' | null
  sessionConfigSnapshot?: SessionConfig | null
  agentType?: string
  sessionId?: string
  sessionSummary?: string
  messageIndex?: number
  highlighted?: boolean
  derivedFrom?: MessageDerivedFrom | null
}>()

const emit = defineEmits<{
  (e: 'feedback', value: 'up' | 'down'): void
  (e: 'insert-prompt', value: string): void
  (e: 'branch-session'): void
  (e: 'continue-response'): void
  (e: 'regenerate-response'): void
  (e: 'open-trace', traceId: string): void
}>()

const { showToast } = useToast()
const copiedCodeIndex = ref<number | null>(null)
const isFavorite = ref(false)
const renderedAt = new Date()

const contentBlocks = computed<ContentBlock[]>(() => {
  const source = props.content || ''
  if (!source) {
    return [{ type: 'text', html: '' }]
  }

  const blocks: ContentBlock[] = []
  const regex = /```([\w-]*)\n([\s\S]*?)```/g
  let lastIndex = 0

  for (const match of source.matchAll(regex)) {
    const start = match.index ?? 0
    if (start > lastIndex) {
      const text = source.slice(lastIndex, start).trim()
      if (text) {
        blocks.push({ type: 'text', html: formatMarkdown(text) })
      }
    }

    blocks.push({
      type: 'code',
      language: (match[1] || '').trim(),
      code: (match[2] || '').replace(/\n$/, '')
    })
    lastIndex = start + match[0].length
  }

  const tail = source.slice(lastIndex).trim()
  if (tail) {
    blocks.push({ type: 'text', html: formatMarkdown(tail) })
  }

  return blocks.length ? blocks : [{ type: 'text', html: formatMarkdown(source) }]
})

const runtimeConfigItems = computed(() => {
  const config = props.sessionConfigSnapshot
  if (!config) {
    return []
  }

  return [
    config.model ? `模型 ${config.model}` : '',
    typeof config.temperature === 'number' ? `温度 ${config.temperature}` : '',
    typeof config.maxContextMessages === 'number' ? `上下文 ${config.maxContextMessages} 条` : '',
    typeof config.knowledgeEnabled === 'boolean' ? `知识增强 ${config.knowledgeEnabled ? '开启' : '关闭'}` : ''
  ].filter(Boolean)
})

const derivedFromLabel = computed(() => {
  if (!props.derivedFrom) {
    return ''
  }
  const messageNumber = props.derivedFrom.messageIndex + 1
  if (props.derivedFrom.action === 'continue') {
    return `继续生成自 #${messageNumber}`
  }
  if (props.derivedFrom.action === 'regenerate') {
    return `重新回答自 #${messageNumber}`
  }
  return `分支自 #${messageNumber}`
})

const summaryItems = computed(() => {
  if (props.role !== 'assistant') {
    return []
  }
  const normalized = (props.content || '').replace(/```[\s\S]*?```/g, ' ').replace(/\s+/g, ' ').trim()
  if (normalized.length < 90) {
    return []
  }
  return normalized
    .split(/[。！？；\n]/)
    .map((item) => item.trim())
    .filter((item) => item.length >= 12)
    .slice(0, 3)
})

const showFeedback = computed(() => props.role === 'assistant' && Boolean(props.responseId) && Boolean(props.content))
const timeLabel = computed(() => renderedAt.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }))
const favoriteStorageKey = computed(() => props.responseId || `${props.role}:${compactContent(props.content, 80)}`)

function syncFavoriteState() {
  isFavorite.value = isFavoriteMessage(favoriteStorageKey.value)
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(props.content || '')
    showToast('消息已复制')
  } catch {
    showToast('复制失败，请重试')
  }
}

async function copyTraceId() {
  if (!props.traceId) {
    return
  }
  try {
    await navigator.clipboard.writeText(props.traceId)
    showToast('TraceId 已复制')
  } catch {
    showToast('TraceId 复制失败')
  }
}

async function copyCodeBlock(index: number, value: string) {
  try {
    await navigator.clipboard.writeText(value)
    copiedCodeIndex.value = index
    showToast('代码已复制')
    window.setTimeout(() => {
      if (copiedCodeIndex.value === index) {
        copiedCodeIndex.value = null
      }
    }, 1500)
  } catch {
    showToast('复制失败，请重试')
  }
}

function quoteContent() {
  emit('insert-prompt', `> ${compactContent(props.content)}\n\n`)
}

function followUp() {
  emit('insert-prompt', `基于这段回答，继续帮我推进：${compactContent(props.content, 160)}`)
}

function toggleFavorite() {
  const next = !isFavorite.value
  try {
    if (next) {
      const result = saveFavoriteMessage({
        id: favoriteStorageKey.value,
        responseId: props.responseId,
        role: props.role,
        content: props.content,
        agentType: props.agentType,
        sessionId: props.sessionId,
        sessionSummary: props.sessionSummary,
        sourceMessageIndex: props.messageIndex ?? null,
        createdAt: Date.now(),
        sessionConfigSnapshot: props.sessionConfigSnapshot || null
      })
      showToast(
        result.status === 'deduplicated'
          ? '检测到相似收藏，已合并到原记录'
          : result.status === 'updated'
            ? '收藏内容已更新'
            : '消息已收藏'
      )
    } else {
      removeFavoriteMessage(favoriteStorageKey.value)
      showToast('已取消收藏')
    }
    isFavorite.value = next
  } catch {
    showToast('收藏状态保存失败')
  }
}

function exportMessage() {
  const payload = [
    `角色: ${props.role === 'assistant' ? '助手' : '用户'}`,
    `时间: ${timeLabel.value}`,
    props.traceId ? `TraceId: ${props.traceId}` : '',
    runtimeConfigItems.value.length ? `参数: ${runtimeConfigItems.value.join(' / ')}` : '',
    '',
    props.content
  ].filter(Boolean).join('\n')

  const fileName = `${props.role === 'assistant' ? 'assistant' : 'user'}-message-${Date.now()}.txt`
  const blob = new Blob([payload], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
  showToast('单条消息已导出')
}

function compactContent(value: string, maxLength = 120) {
  const normalized = (value || '').replace(/\s+/g, ' ').trim()
  if (normalized.length <= maxLength) {
    return normalized
  }
  return `${normalized.slice(0, maxLength)}...`
}

syncFavoriteState()
</script>

<style scoped>
.msg-content-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.msg-header-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 4px;
}

.msg-header-main {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.msg-role-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text2);
  letter-spacing: 0.08em;
}

.msg-response-chip,
.msg-trace-chip,
.msg-derived-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 1;
}

.msg-response-chip {
  background: rgba(79, 142, 247, 0.08);
  border: 1px solid rgba(79, 142, 247, 0.14);
  color: var(--accent2);
}

.msg-trace-chip {
  border: 1px solid rgba(245, 158, 11, 0.18);
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  cursor: pointer;
}

.msg-derived-chip {
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.16);
  color: #0f766e;
}

.msg-time {
  font-size: 11px;
  color: var(--text3);
  white-space: nowrap;
}

.msg-runtime-config {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.msg-runtime-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(79, 142, 247, 0.14);
  background: rgba(79, 142, 247, 0.06);
  color: var(--accent2);
  font-size: 11px;
  line-height: 1;
}

.msg-summary-card {
  margin-bottom: 10px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(79, 142, 247, 0.18);
  background: rgba(79, 142, 247, 0.05);
}

.msg-summary-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text);
}

.msg-summary-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.msg-summary-item {
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.75);
  color: var(--text2);
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
}

.msg-rich-text + .msg-code-block,
.msg-code-block + .msg-rich-text,
.msg-code-block + .msg-code-block,
.msg-rich-text + .msg-rich-text {
  margin-top: 10px;
}

.msg-code-block {
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  background: #0f172a;
}

.msg-code-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.msg-code-lang,
.msg-code-copy {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.82);
}

.msg-code-copy {
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  border-radius: 999px;
  padding: 4px 10px;
  cursor: pointer;
}

.msg-code-content {
  margin: 0;
  padding: 14px;
  overflow-x: auto;
  font-family: var(--mono);
  font-size: 12px;
  line-height: 1.7;
  color: #e2e8f0;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.msg-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.msg-text-btn,
.feedback-btn {
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.02);
  color: var(--text3);
  border-radius: 999px;
  padding: 5px 11px;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition);
}

.msg-text-btn:hover,
.feedback-btn:hover,
.msg-summary-item:hover,
.msg-trace-chip:hover {
  color: var(--text);
  border-color: var(--border2);
  background: var(--surface2);
  transform: translateY(-1px);
}

.msg-text-btn.accent {
  color: var(--accent2);
  border-color: rgba(79, 142, 247, 0.16);
  background: rgba(79, 142, 247, 0.06);
}

.msg-text-btn.accent:hover {
  border-color: var(--accent);
  background: rgba(79, 142, 247, 0.12);
}

.feedback-btn.active {
  border-color: var(--accent);
  color: var(--accent2);
  background: rgba(59, 130, 246, 0.08);
}

.msg.is-highlighted {
  position: relative;
}

.msg.is-highlighted::after {
  content: '';
  position: absolute;
  inset: -6px -8px;
  border-radius: 22px;
  border: 2px solid rgba(79, 142, 247, 0.26);
  box-shadow: 0 0 0 6px rgba(79, 142, 247, 0.08);
  pointer-events: none;
}

@media (max-width: 820px) {
  .msg-header-line {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
}
</style>
