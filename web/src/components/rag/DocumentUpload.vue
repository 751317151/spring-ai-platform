<template>
  <div>
    <div class="upload-options">
      <label class="replace-toggle">
        <input v-model="replaceExisting" type="checkbox">
        <span>自动覆盖同名文档</span>
      </label>
      <div class="upload-tip">支持 PDF、Word、Excel、TXT、Markdown，以及 CSV、JSON、XML 等结构化文件。</div>
    </div>

    <div class="upload-format-tags">
      <span v-for="item in ['PDF', 'Word', 'Excel', 'TXT', 'Markdown', 'CSV', 'JSON', 'XML']" :key="item" class="upload-format-tag">{{ item }}</span>
    </div>

    <div class="upload-zone" :class="{ drag: isDragging, disabled: !ragStore.currentKb }" @dragover.prevent="handleDragOver" @dragleave="handleDragLeave" @drop.prevent="handleDrop" @click="triggerFileInput">
      <div class="upload-icon">{{ isDragging ? '释放' : '上传' }}</div>
      <div class="upload-text">{{ ragStore.currentKb ? (isDragging ? '释放文件后开始导入' : '将文件拖到这里，或点击上传') : '请先选择知识库' }}</div>
      <div class="upload-sub">{{ ragStore.currentKb ? '上传后会自动进入解析和索引流程，处理结果会在下方实时显示。' : '选择知识库后才能使用上传功能。' }}</div>
      <div v-if="ragStore.currentKb" class="upload-shortcuts">
        <span class="upload-shortcut">点击选择文件</span>
        <span class="upload-shortcut">支持多文件批量上传</span>
      </div>
      <input ref="fileInput" type="file" style="display: none" multiple accept=".pdf,.doc,.docx,.xlsx,.xls,.txt,.md,.csv,.json,.xml,.mol,.sdf,.cdx" @change="handleFileSelect">
    </div>

    <div v-if="tasks.length" class="upload-summary">
      <div class="summary-pill">{{ processingCount }} 个处理中</div>
      <div class="summary-pill success">{{ successCount }} 个已完成</div>
      <div v-if="failedCount" class="summary-pill danger">{{ failedCount }} 个失败</div>
      <div class="summary-pill neutral">最近批次 {{ tasks.length }} 个任务</div>
    </div>

    <div v-if="tasks.length" class="recent-caption">最近上传任务会保留在这里，方便快速定位对应文档状态。</div>

    <div v-if="tasks.length" class="upload-actions-card">
      <div class="upload-actions-copy">
        <div class="upload-actions-title">批量处理入口</div>
        <div class="upload-actions-desc">{{ uploadActionDesc }}</div>
      </div>
      <div class="upload-actions-buttons">
        <button v-if="processingCount" class="task-action-btn" type="button" @click="jumpToStatus('PROCESSING')">查看处理中</button>
        <button v-if="failedCount" class="task-action-btn" type="button" @click="jumpToStatus('FAILED')">查看失败项</button>
        <button v-if="successCount" class="task-action-btn" type="button" @click="clearFinishedTasks">清理已完成</button>
        <button class="task-action-btn" type="button" @click="copyTaskSummary">复制上传摘要</button>
      </div>
    </div>

    <div v-if="tasks.length" class="file-list">
      <div v-for="task in tasks" :key="task.id" class="file-item task-card" :class="`task-${task.status}`">
        <div class="file-icon">{{ task.status === 'error' ? '异常' : task.status === 'done' ? '完成' : '处理中' }}</div>
        <div class="task-main">
          <div class="task-header"><div class="file-name">{{ task.name }}</div><div class="file-size">{{ formatFileSize(task.size) }}</div></div>
          <div class="task-stage-row"><span class="file-status" :class="task.status">{{ statusLabel(task.status) }}</span><span class="task-phase">{{ task.phase }}</span></div>
          <div class="file-progress"><div class="progress-bar"><div class="progress-fill" :style="{ width: `${task.progress}%` }"></div></div><span class="task-progress-text">{{ task.progress }}%</span></div>
          <div v-if="task.message" class="file-message">{{ task.message }}</div>
          <div v-if="taskActions(task).length" class="task-actions">
            <button v-for="action in taskActions(task)" :key="action.label" class="task-action-btn" type="button" @click.stop="action.handler">{{ action.label }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRagStore } from '@/stores/rag'
import { useToast } from '@/composables/useToast'
import { formatFileSize } from '@/utils/format'

type UploadTaskStatus = 'uploading' | 'processing' | 'done' | 'error'
type DocumentStatusFilter = 'ALL' | 'INDEXED' | 'FAILED' | 'PROCESSING'
interface UploadTask { id: string; name: string; size: number; status: UploadTaskStatus; phase: string; progress: number; message?: string; documentId?: string; documentStatus?: DocumentStatusFilter }

const emit = defineEmits<{ jumpStatus: [status: DocumentStatusFilter]; focusDocument: [payload: { documentId?: string; filename: string; status: DocumentStatusFilter }] }>()
const ragStore = useRagStore()
const { showToast } = useToast()
const fileInput = ref<HTMLInputElement>()
const isDragging = ref(false)
const replaceExisting = ref(true)
const tasks = reactive<UploadTask[]>([])

const processingCount = computed(() => tasks.filter((task) => task.status === 'uploading' || task.status === 'processing').length)
const successCount = computed(() => tasks.filter((task) => task.status === 'done').length)
const failedCount = computed(() => tasks.filter((task) => task.status === 'error').length)
const uploadActionDesc = computed(() => failedCount.value ? '有失败项时建议先进入失败筛选，检查解析错误并重试。' : processingCount.value ? '仍有文档在处理中，可以切到处理中列表观察索引进度。' : '当前批次已完成，可复制摘要或清理已完成任务。')

function statusLabel(status: UploadTaskStatus) { return status === 'uploading' ? '上传中' : status === 'processing' ? '处理中' : status === 'done' ? '已完成' : '失败' }
function normalizeDocumentStatus(status?: string): DocumentStatusFilter { return status === 'INDEXED' || status === 'FAILED' || status === 'PROCESSING' ? status : 'PROCESSING' }
function triggerFileInput() { if (!ragStore.currentKb) return showToast('请先选择知识库'); fileInput.value?.click() }
function handleDragOver() { if (ragStore.currentKb) isDragging.value = true }
function handleDragLeave() { isDragging.value = false }
function handleDrop(event: DragEvent) { isDragging.value = false; const files = event.dataTransfer?.files; if (!ragStore.currentKb) return showToast('请先选择知识库'); if (files) processFiles(files) }
function handleFileSelect(event: Event) { const input = event.target as HTMLInputElement; if (input.files) processFiles(input.files); input.value = '' }
function createTask(file: File): UploadTask { return { id: `${file.name}-${Date.now()}-${Math.random().toString(16).slice(2)}`, name: file.name, size: file.size, status: 'uploading', phase: '文件上传中', progress: 12 } }
function jumpToStatus(status: DocumentStatusFilter) { emit('jumpStatus', status) }
function focusDocument(task: UploadTask) { emit('focusDocument', { documentId: task.documentId, filename: task.name, status: task.documentStatus || 'ALL' }) }
function taskActions(task: UploadTask) {
  const actions: Array<{ label: string; handler: () => void }> = []
  if (task.documentStatus === 'INDEXED') actions.push({ label: '查看已索引', handler: () => jumpToStatus('INDEXED') })
  if (task.documentStatus === 'FAILED') actions.push({ label: '查看失败项', handler: () => jumpToStatus('FAILED') })
  if (task.documentStatus === 'PROCESSING') actions.push({ label: '查看处理中', handler: () => jumpToStatus('PROCESSING') })
  if (task.documentStatus) actions.push({ label: '在列表中定位', handler: () => focusDocument(task) })
  return actions
}
function clearFinishedTasks() { for (let i = tasks.length - 1; i >= 0; i -= 1) if (tasks[i]?.status === 'done') tasks.splice(i, 1); showToast('已清理完成任务') }
async function copyTaskSummary() {
  const lines = ['知识库上传摘要', `当前知识库：${ragStore.currentKbName || ragStore.currentKb || '未选择'}`, `任务总数：${tasks.length}`, `处理中：${processingCount.value}`, `已完成：${successCount.value}`, `失败：${failedCount.value}`]
  tasks.slice(0, 8).forEach((task, index) => lines.push(`${index + 1}. ${task.name} | ${statusLabel(task.status)} | ${task.phase}`))
  try { await navigator.clipboard.writeText(lines.join('\n')); showToast('已复制上传摘要') } catch { showToast('复制上传摘要失败') }
}
async function processFiles(files: FileList) {
  for (const file of Array.from(files)) {
    const task = createTask(file)
    tasks.unshift(task)
    const timer = window.setInterval(() => {
      if (task.status === 'uploading' && task.progress < 48) task.progress += 6
      else if (task.status === 'processing' && task.progress < 88) task.progress += 3
    }, 220)
    try {
      const uploadPromise = ragStore.uploadFile(file, replaceExisting.value)
      window.setTimeout(() => { if (task.status === 'uploading') { task.status = 'processing'; task.phase = '等待解析与索引'; task.progress = Math.max(task.progress, 58) } }, 700)
      const result = await uploadPromise
      window.clearInterval(timer)
      task.progress = 100
      if (!result) {
        task.status = 'error'; task.phase = '上传失败'; task.message = '上传请求失败，请检查知识库服务后重试。'; showToast(`${file.name} 上传失败`); continue
      }
      task.documentId = result.id
      task.documentStatus = normalizeDocumentStatus(result.status)
      if (result.status === 'FAILED') {
        task.status = 'error'; task.phase = '解析失败'; task.message = result.errorMessage || '文件已保存，但解析流程未成功完成。'; showToast(`${file.name} 解析失败`); continue
      }
      task.status = 'done'
      task.phase = result.status === 'PROCESSING' ? '已进入索引队列' : '已索引，可直接使用'
      task.message = result.status === 'PROCESSING' ? '文件已上传，后台仍在继续处理。' : '文件已可用于检索和问答。'
      showToast(`${file.name} 上传成功`)
    } catch {
      window.clearInterval(timer)
      task.status = 'error'; task.phase = '上传失败'; task.message = '上传过程中发生异常，请稍后重试。'; showToast(`${file.name} 上传失败`)
    }
  }
}
</script>

<style scoped>
.upload-options, .upload-format-tags, .upload-summary, .upload-actions-buttons, .task-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.upload-options, .upload-format-tags { margin-bottom: 12px; }
.replace-toggle { display: inline-flex; align-items: center; gap: 8px; color: var(--text2); font-size: 12px; }
.upload-tip, .recent-caption, .file-message, .task-phase { color: var(--text3); font-size: 12px; }
.upload-format-tag, .upload-shortcut, .summary-pill { padding: 4px 10px; border-radius: 999px; border: 1px solid var(--border); background: rgba(255,255,255,0.03); color: var(--text3); font-size: 11px; }
.upload-shortcuts { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 12px; }
.upload-shortcut { background: rgba(59,130,246,0.08); color: var(--accent2); }
.upload-zone.disabled { opacity: 0.72; }
.summary-pill { padding: 6px 12px; }
.summary-pill.success { background: rgba(13,148,136,0.12); color: #0f766e; border-color: rgba(13,148,136,0.12); }
.summary-pill.danger { background: rgba(239,68,68,0.12); color: #b91c1c; border-color: rgba(239,68,68,0.12); }
.summary-pill.neutral { background: rgba(59,130,246,0.1); color: #1d4ed8; border-color: rgba(59,130,246,0.12); }
.upload-actions-card { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 12px; padding: 14px 16px; border-radius: 16px; border: 1px solid var(--border); background: linear-gradient(135deg, rgba(59,130,246,0.08), rgba(255,255,255,0.02)); }
.upload-actions-title { color: var(--text); font-size: 13px; font-weight: 600; }
.upload-actions-desc { margin-top: 6px; color: var(--text3); font-size: 12px; line-height: 1.6; }
.task-card { align-items: flex-start; gap: 14px; padding: 14px 16px; border-radius: 18px; }
.task-card.task-done { border-color: rgba(13,148,136,0.14); }
.task-card.task-error { border-color: rgba(239,68,68,0.16); }
.task-main { flex: 1; min-width: 0; }
.task-header, .task-stage-row, .file-progress { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.task-header { justify-content: space-between; }
.task-stage-row, .file-progress { margin-top: 8px; }
.task-progress-text { color: var(--text3); font-size: 11px; min-width: 36px; text-align: right; }
.task-action-btn { border: 1px solid var(--border); border-radius: 999px; background: transparent; color: var(--text2); font-size: 12px; padding: 5px 10px; cursor: pointer; transition: all var(--transition); }
.task-action-btn:hover { border-color: var(--accent); color: var(--accent2); background: var(--accent-dim); }
@media (max-width: 820px) { .task-header, .file-progress, .upload-actions-card { flex-direction: column; align-items: flex-start; } .task-progress-text { text-align: left; } }
</style>
