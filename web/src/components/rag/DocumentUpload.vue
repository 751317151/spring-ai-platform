<template>
  <div>
    <div class="upload-options">
      <label class="replace-toggle">
        <input v-model="replaceExisting" type="checkbox">
        <span>自动覆盖同名文档。</span>
      </label>
      <div class="upload-tip">支持 PDF、Word、Excel、TXT 和 Markdown，也支持上传 CSV、JSON、XML 等文件后续解析。</div>
    </div>

    <div
      class="upload-zone"
      :class="{ drag: isDragging, disabled: !ragStore.currentKb }"
      @dragover.prevent="handleDragOver"
      @dragleave="isDragging = false"
      @drop.prevent="handleDrop"
      @click="triggerFileInput"
    >
      <div class="upload-icon">上传</div>
      <div class="upload-text">
        {{ ragStore.currentKb ? '将文件拖到这里，或点击上传' : '请先选择知识库' }}
      </div>
      <div class="upload-sub">
        {{ ragStore.currentKb ? '上传后会自动进入解析和索引流程。' : '选择知识库后才可使用上传功能。' }}
      </div>
      <input
        ref="fileInput"
        type="file"
        style="display: none"
        multiple
        accept=".pdf,.doc,.docx,.xlsx,.xls,.txt,.md,.csv,.json,.xml,.mol,.sdf,.cdx"
        @change="handleFileSelect"
      >
    </div>

    <div v-if="tasks.length" class="upload-summary">
      <div class="summary-pill">{{ processingCount }} 个处理中</div>
      <div class="summary-pill success">{{ successCount }} 个已完成</div>
      <div v-if="failedCount" class="summary-pill danger">{{ failedCount }} 个失败</div>
    </div>

    <div v-if="tasks.length" class="recent-caption">
      最近上传任务会保留在这里，方便快速定位对应文档状态。
    </div>

    <div v-if="tasks.length" class="file-list">
      <div v-for="task in tasks" :key="task.id" class="file-item task-card">
        <div class="file-icon">文件</div>
        <div class="task-main">
          <div class="task-header">
            <div class="file-name">{{ task.name }}</div>
            <div class="file-size">{{ formatFileSize(task.size) }}</div>
          </div>
          <div class="task-stage-row">
            <span class="file-status" :class="task.status">{{ statusLabel(task.status) }}</span>
            <span class="task-phase">{{ task.phase }}</span>
          </div>
          <div class="file-progress">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: `${task.progress}%` }"></div>
            </div>
          </div>
          <div v-if="task.message" class="file-message">{{ task.message }}</div>

          <div v-if="taskActions(task).length" class="task-actions">
            <button
              v-for="action in taskActions(task)"
              :key="action.label"
              class="task-action-btn"
              type="button"
              @click.stop="action.handler"
            >
              {{ action.label }}
            </button>
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

interface UploadTask {
  id: string
  name: string
  size: number
  status: UploadTaskStatus
  phase: string
  progress: number
  message?: string
  documentId?: string
  documentStatus?: DocumentStatusFilter
}

const emit = defineEmits<{
  jumpStatus: [status: DocumentStatusFilter]
  focusDocument: [payload: { documentId?: string; filename: string; status: DocumentStatusFilter }]
}>()

const ragStore = useRagStore()
const { showToast } = useToast()

const fileInput = ref<HTMLInputElement>()
const isDragging = ref(false)
const replaceExisting = ref(true)
const tasks = reactive<UploadTask[]>([])

const processingCount = computed(() => tasks.filter((task) => task.status === 'uploading' || task.status === 'processing').length)
const successCount = computed(() => tasks.filter((task) => task.status === 'done').length)
const failedCount = computed(() => tasks.filter((task) => task.status === 'error').length)

function statusLabel(status: UploadTaskStatus) {
  if (status === 'uploading') return '上传中'
  if (status === 'processing') return '处理中'
  if (status === 'done') return '已完成'
  return '失败'
}

function normalizeDocumentStatus(status?: string): DocumentStatusFilter {
  if (status === 'INDEXED' || status === 'FAILED' || status === 'PROCESSING') {
    return status
  }
  return 'PROCESSING'
}

function triggerFileInput() {
  if (!ragStore.currentKb) {
    showToast('请先选择知识库')
    return
  }
  fileInput.value?.click()
}

function handleDragOver() {
  if (!ragStore.currentKb) {
    return
  }
  isDragging.value = true
}

function handleDrop(event: DragEvent) {
  isDragging.value = false
  if (!ragStore.currentKb) {
    showToast('请先选择知识库')
    return
  }
  const files = event.dataTransfer?.files
  if (files) processFiles(files)
}

function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files) processFiles(input.files)
  input.value = ''
}

function createTask(file: File): UploadTask {
  return {
    id: `${file.name}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    name: file.name,
    size: file.size,
    status: 'uploading',
    phase: '文件上传中',
    progress: 12
  }
}

function jumpToStatus(status: DocumentStatusFilter) {
  emit('jumpStatus', status)
}

function focusDocument(task: UploadTask) {
  emit('focusDocument', {
    documentId: task.documentId,
    filename: task.name,
    status: task.documentStatus || 'ALL'
  })
}

function taskActions(task: UploadTask) {
  const actions: Array<{ label: string; handler: () => void }> = []

  if (task.documentStatus === 'INDEXED') {
    actions.push({ label: '查看已索引', handler: () => jumpToStatus('INDEXED') })
  }

  if (task.documentStatus === 'FAILED') {
    actions.push({ label: '查看失败项', handler: () => jumpToStatus('FAILED') })
  }

  if (task.documentStatus === 'PROCESSING') {
    actions.push({ label: '查看处理中', handler: () => jumpToStatus('PROCESSING') })
  }

  if (task.documentStatus) {
    actions.push({ label: '在列表中定位', handler: () => focusDocument(task) })
  }

  return actions
}

async function processFiles(files: FileList) {
  for (const file of Array.from(files)) {
    const task = createTask(file)
    tasks.unshift(task)

    const timer = window.setInterval(() => {
      if (task.status === 'uploading' && task.progress < 48) {
        task.progress += 6
      } else if (task.status === 'processing' && task.progress < 88) {
        task.progress += 3
      }
    }, 220)

    try {
      const uploadPromise = ragStore.uploadFile(file, replaceExisting.value)
      window.setTimeout(() => {
        if (task.status === 'uploading') {
          task.status = 'processing'
          task.phase = '等待解析与索引'
          task.progress = Math.max(task.progress, 58)
        }
      }, 700)

      const result = await uploadPromise
      window.clearInterval(timer)
      task.progress = 100

      if (!result) {
        task.status = 'error'
        task.phase = '上传失败'
        task.message = '上传请求失败，请检查知识库服务后重试。'
        showToast(`${file.name} 上传失败`)
        continue
      }

      task.documentId = result.id
      task.documentStatus = normalizeDocumentStatus(result.status)

      if (result.status === 'FAILED') {
        task.status = 'error'
        task.phase = '解析失败'
        task.message = result.errorMessage || '文件已保存，但解析流程未成功完成。'
        showToast(`${file.name} 解析失败`)
        continue
      }

      task.status = 'done'
      task.phase = result.status === 'PROCESSING' ? '已进入索引队列' : '已索引，可直接使用'
      task.message = result.status === 'PROCESSING'
        ? '文件已上传，后台仍在继续处理。'
        : '文件已可用于检索和问答。'
      showToast(`${file.name} 上传成功`)
    } catch {
      window.clearInterval(timer)
      task.status = 'error'
      task.phase = '上传失败'
      task.message = '上传过程中发生异常，请稍后重试。'
      showToast(`${file.name} 上传失败`)
    }
  }
}
</script>

<style scoped>
.upload-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.replace-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text2);
  font-size: 12px;
}

.upload-tip {
  color: var(--text3);
  font-size: 12px;
}

.upload-zone.disabled {
  opacity: 0.72;
}

.upload-summary {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.summary-pill {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
  font-size: 12px;
}

.summary-pill.success {
  background: rgba(13, 148, 136, 0.12);
  color: #0f766e;
}

.summary-pill.danger {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}

.recent-caption {
  margin-top: 10px;
  color: var(--text3);
  font-size: 12px;
}

.task-card {
  align-items: flex-start;
}

.task-main {
  flex: 1;
  min-width: 0;
}

.task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-stage-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 6px;
}

.task-phase {
  font-size: 12px;
  color: var(--text3);
}

.file-progress {
  width: 100%;
  margin-top: 8px;
}

.file-message {
  width: 100%;
  margin-top: 6px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.5;
}

.task-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.task-action-btn {
  border: 1px solid var(--border);
  border-radius: 999px;
  background: transparent;
  color: var(--text2);
  font-size: 12px;
  padding: 5px 10px;
  cursor: pointer;
  transition: all var(--transition);
}

.task-action-btn:hover {
  border-color: var(--accent);
  color: var(--accent2);
  background: var(--accent-dim);
}
</style>
