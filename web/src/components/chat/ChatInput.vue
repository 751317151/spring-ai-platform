<template>
  <div class="chat-input-area">
    <div class="prompt-toolbar">
      <button
        v-for="prompt in QUICK_PROMPTS"
        :key="prompt.label"
        class="prompt-chip"
        @click="insertPrompt(prompt.text)"
      >
        {{ prompt.label }}
      </button>
      <button class="prompt-chip secondary" @click="toggleTemplatePanel">
        {{ showTemplatePanel ? '收起模板' : '常用模板' }}
      </button>
    </div>

    <div v-if="showTemplatePanel" class="template-panel">
      <div class="template-panel-head">
        <div>
          <div class="template-panel-title">常用提问模板</div>
          <div class="template-panel-subtitle">先把目标、上下文、限制条件说清楚，回复会更稳定。</div>
        </div>
      </div>
      <div class="template-list">
        <button
          v-for="template in promptTemplates"
          :key="template.label"
          class="template-card"
          type="button"
          @click="insertPrompt(template.text)"
        >
          <strong>{{ template.label }}</strong>
          <span>{{ template.description }}</span>
        </button>
      </div>
    </div>

    <div
      class="chat-input-wrap"
      :class="{ 'drag-active': isDragActive }"
      @dragenter.prevent="handleDragEnter"
      @dragover.prevent="handleDragOver"
      @dragleave.prevent="handleDragLeave"
      @drop.prevent="handleDrop"
    >
      <div class="input-meta-rail">
        <span class="input-mode-pill">{{ chatStore.isThinking ? '思考中' : '就绪' }}</span>
        <span class="input-mode-text">
          回车发送，Shift + Enter 换行，`/` 聚焦输入框，`Ctrl/Cmd + Shift + N` 新建会话
        </span>
      </div>

      <div class="draft-switcher">
        <button
          v-for="draft in draftOptions"
          :key="draft.key"
          class="draft-chip"
          :class="{ active: activeDraftKey === draft.key }"
          type="button"
          @click="switchDraft(draft.key)"
        >
          {{ draft.label }}
        </button>
      </div>

      <div v-if="checkHints.length" class="input-checklist">
        <div class="input-checklist-title">发送前建议</div>
        <ul class="input-checklist-list">
          <li v-for="hint in checkHints" :key="hint">{{ hint }}</li>
        </ul>
      </div>

      <div class="chat-input-main">
        <textarea
          ref="inputRef"
          v-model="message"
          class="chat-input"
          placeholder="请输入消息、补充上下文，或直接描述你希望助手处理的任务..."
          rows="1"
          @keydown="handleKey"
          @input="autoResize"
          @paste="handlePaste"
        ></textarea>

        <div class="chat-input-actions">
          <button class="btn btn-ghost btn-sm" :disabled="isReadingFile" @click="triggerFilePick">
            {{ isReadingFile ? '读取中...' : '导入文本' }}
          </button>
          <button v-if="message.trim()" class="btn btn-ghost btn-sm" @click="clearMessage">清空</button>
          <button v-if="chatStore.isThinking" class="btn btn-ghost btn-sm" @click="chatStore.stopStreaming()">停止</button>
          <button class="send-btn" :disabled="!message.trim() || chatStore.isThinking" @click="send">
            发送
          </button>
        </div>
      </div>

      <div class="input-helper-row">
        <span class="input-helper-tip">支持拖拽 `.txt`、`.md`、`.json`、`.csv` 文件，或直接粘贴长文本。</span>
        <span class="input-helper-count">{{ message.trim().length }} 字</span>
      </div>

      <div v-if="isDragActive" class="drop-overlay">
        <strong>释放鼠标即可导入文本</strong>
        <span>仅处理文本类文件，导入后会自动追加到当前输入框。</span>
      </div>

      <input
        ref="fileInputRef"
        class="hidden-file-input"
        type="file"
        accept=".txt,.md,.json,.csv,.log,text/plain"
        @change="handleFileChange"
      >
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useToast } from '@/composables/useToast'
import { QUICK_PROMPTS } from '@/utils/constants'

const emit = defineEmits<{ send: [message: string] }>()

const DRAFT_VARIANTS = [
  { key: 'main', label: '主草稿' },
  { key: 'plan', label: '计划草稿' },
  { key: 'notes', label: '补充说明' }
] as const

const promptTemplates = [
  {
    label: '需求澄清',
    description: '适合先整理目标、约束和验收标准。',
    text: '请先帮我澄清这个需求：\n1. 目标是什么\n2. 已知上下文有哪些\n3. 限制条件是什么\n4. 最终交付物要长什么样'
  },
  {
    label: '方案设计',
    description: '适合让助手先拆方案和风险。',
    text: '请给我一份可执行方案，格式包含：\n1. 总体思路\n2. 分步骤执行计划\n3. 关键风险\n4. 回滚或兜底方案'
  },
  {
    label: '问题排查',
    description: '适合贴日志或异常后继续分析。',
    text: '请按下面结构帮我排查问题：\n1. 现象\n2. 最可能原因\n3. 验证步骤\n4. 修复建议\n5. 需要补充的信息'
  }
]

const chatStore = useChatStore()
const { showToast } = useToast()

const message = ref('')
const inputRef = ref<HTMLTextAreaElement>()
const fileInputRef = ref<HTMLInputElement>()
const isDragActive = ref(false)
const isReadingFile = ref(false)
const dragCounter = ref(0)
const showTemplatePanel = ref(false)
const activeDraftKey = ref<(typeof DRAFT_VARIANTS)[number]['key']>('main')

const currentDraftStorageKey = computed(() =>
  chatStore.currentSessionId ? `${chatStore.currentSessionId}::${activeDraftKey.value}` : null
)

const draftOptions = computed(() =>
  DRAFT_VARIANTS.map((item) => ({
    ...item,
    label: chatStore.getDraft(buildDraftKey(item.key)).trim() ? `${item.label} *` : item.label
  }))
)

const checkHints = computed(() => {
  const value = message.value.trim()
  if (!value) return []
  const hints: string[] = []
  if (value.length < 12) hints.push('内容偏短，可以补充目标、上下文或预期输出。')
  if (!/[。！？\n:：]/.test(value) && value.length > 20) hints.push('可以适当分段或列点，方便助手识别结构。')
  if (!/(目标|背景|上下文|限制|要求|输出)/.test(value) && value.length > 24) {
    hints.push('建议补充目标、背景或限制条件，减少来回追问。')
  }
  return hints.slice(0, 3)
})

function buildDraftKey(variant: string) {
  return chatStore.currentSessionId ? `${chatStore.currentSessionId}::${variant}` : null
}

function focusInput() {
  nextTick(() => {
    inputRef.value?.focus()
    autoResize()
  })
}

function setMessage(value: string, mode: 'replace' | 'append' = 'replace') {
  message.value = mode === 'append' && message.value ? `${message.value}${value}` : value
  persistCurrentDraft()
  focusInput()
}

function clearMessage() {
  message.value = ''
  persistCurrentDraft()
  nextTick(() => autoResize())
  focusInput()
}

function toggleTemplatePanel() {
  showTemplatePanel.value = !showTemplatePanel.value
}

function switchDraft(key: (typeof DRAFT_VARIANTS)[number]['key']) {
  persistCurrentDraft()
  activeDraftKey.value = key
  syncDraftFromSession()
  focusInput()
}

function persistCurrentDraft() {
  chatStore.setDraft(currentDraftStorageKey.value, message.value)
  if (activeDraftKey.value === 'main') {
    chatStore.setDraft(chatStore.currentSessionId, message.value)
  }
}

function appendImportedText(value: string, sourceLabel: string) {
  const normalized = value.replace(/\r\n/g, '\n').trim()
  if (!normalized) {
    showToast(`${sourceLabel} 中没有可导入的文本`)
    return
  }

  const prefix = message.value.trim() ? '\n\n' : ''
  const wrapped = `${prefix}[导入内容 - ${sourceLabel}]\n${normalized}`
  setMessage(wrapped, 'append')
  showToast(`已导入 ${sourceLabel}`)
}

function insertPrompt(text: string) {
  setMessage(text)
}

function handleKey(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

function autoResize() {
  const el = inputRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`
  }
}

function send() {
  const msg = message.value.trim()
  if (!msg || chatStore.isThinking) return
  // if (msg.length < 6 || (checkHints.value.length >= 3 && msg.length < 20)) {
  //   showToast('建议先补充目标或上下文，再发送会更有效。')
  //   return
  // }
  emit('send', msg)
  message.value = ''
  persistCurrentDraft()
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
    inputRef.value.focus()
  }
}

function triggerFilePick() {
  fileInputRef.value?.click()
}

function isTextFile(file: File) {
  if (file.type.startsWith('text/')) return true
  return /\.(txt|md|markdown|json|csv|log)$/i.test(file.name)
}

async function readFileAsText(file: File) {
  if (!isTextFile(file)) {
    showToast(`暂不支持导入 ${file.name}`)
    return
  }

  isReadingFile.value = true
  try {
    const content = await file.text()
    appendImportedText(content, file.name)
  } catch {
    showToast(`读取 ${file.name} 失败`)
  } finally {
    isReadingFile.value = false
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  await readFileAsText(file)
}

function handleDragEnter() {
  dragCounter.value += 1
  isDragActive.value = true
}

function handleDragOver() {
  isDragActive.value = true
}

function handleDragLeave() {
  dragCounter.value = Math.max(0, dragCounter.value - 1)
  if (dragCounter.value === 0) {
    isDragActive.value = false
  }
}

async function handleDrop(event: DragEvent) {
  dragCounter.value = 0
  isDragActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  await readFileAsText(file)
}

function handlePaste(event: ClipboardEvent) {
  const pastedText = event.clipboardData?.getData('text/plain') || ''
  if (!pastedText.trim()) return
  if (pastedText.length >= 500) {
    nextTick(() => {
      autoResize()
      showToast(`已粘贴长文本，当前共 ${message.value.trim().length} 字`)
    })
  }
}

function handleFocusShortcut() {
  focusInput()
}

function syncDraftFromSession() {
  message.value = chatStore.getDraft(currentDraftStorageKey.value)
  if (!message.value && activeDraftKey.value === 'main') {
    message.value = chatStore.getDraft(chatStore.currentSessionId)
  }
  nextTick(() => autoResize())
}

defineExpose({
  focusInput,
  setMessage
})

onMounted(() => {
  syncDraftFromSession()
  window.addEventListener('app:focus-chat-input', handleFocusShortcut as EventListener)
})

onUnmounted(() => {
  window.removeEventListener('app:focus-chat-input', handleFocusShortcut as EventListener)
})

watch(() => message.value, () => {
  persistCurrentDraft()
})

watch(() => [chatStore.currentSessionId, chatStore.currentAgent], () => {
  activeDraftKey.value = 'main'
  syncDraftFromSession()
})
</script>

<style scoped>
.template-panel {
  margin-bottom: 12px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, rgba(79, 142, 247, 0.05), rgba(255, 255, 255, 0.02));
}

.template-panel-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
}

.template-panel-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text3);
}

.template-list {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.template-card {
  display: grid;
  gap: 6px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.72);
  text-align: left;
  cursor: pointer;
}

.template-card strong {
  color: var(--text);
  font-size: 13px;
}

.template-card span {
  color: var(--text3);
  font-size: 12px;
  line-height: 1.5;
}

.prompt-chip.secondary {
  border-style: dashed;
}

.chat-input-wrap {
  position: relative;
  transition: box-shadow var(--transition), border-color var(--transition), background var(--transition);
  overflow: hidden;
}

.chat-input-wrap::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 68px;
  background: linear-gradient(180deg, rgba(79, 142, 247, 0.05), transparent);
  pointer-events: none;
}

.chat-input-wrap.drag-active {
  box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.28), 0 16px 40px rgba(59, 130, 246, 0.12);
}

.input-meta-rail,
.chat-input-main,
.input-helper-row,
.draft-switcher,
.input-checklist {
  position: relative;
  z-index: 1;
}

.draft-switcher {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0 6px;
}

.draft-chip {
  border: 1px solid var(--border);
  border-radius: 999px;
  background: transparent;
  color: var(--text2);
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
}

.draft-chip.active {
  background: rgba(79, 142, 247, 0.1);
  border-color: rgba(79, 142, 247, 0.24);
  color: var(--text);
}

.input-checklist {
  margin-bottom: 8px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(245, 158, 11, 0.22);
  background: rgba(245, 158, 11, 0.06);
}

.input-checklist-title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text);
}

.input-checklist-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text2);
  font-size: 12px;
}

.input-helper-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--text3);
  font-size: 11px;
}

.input-helper-tip {
  max-width: 80%;
}

.input-helper-count {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(79, 142, 247, 0.08);
  color: var(--accent2);
  font-family: var(--mono);
}

.drop-overlay {
  position: absolute;
  inset: 0;
  border-radius: 18px;
  border: 1px dashed rgba(59, 130, 246, 0.45);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.97), rgba(241, 245, 249, 0.94));
  display: grid;
  place-content: center;
  gap: 8px;
  text-align: center;
  z-index: 2;
  padding: 24px;
  box-shadow: inset 0 0 0 1px rgba(79, 142, 247, 0.08);
}

.drop-overlay strong {
  font-size: 15px;
  color: var(--text);
}

.drop-overlay span {
  font-size: 12px;
  color: var(--text2);
}

.hidden-file-input {
  display: none;
}

@media (max-width: 960px) {
  .template-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 820px) {
  .input-meta-rail,
  .input-helper-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .input-helper-tip {
    max-width: none;
  }
}
</style>
