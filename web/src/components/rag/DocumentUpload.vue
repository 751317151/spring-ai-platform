<template>
  <div>
    <div class="upload-options">
      <label class="replace-toggle">
        <input v-model="replaceExisting" type="checkbox">
        <span>同名文档自动替换</span>
      </label>
      <div class="upload-tip">对 `csv/json/xml/mol/sdf/cdx` 会保留源文件并标记为未支持解析。</div>
    </div>

    <div
      class="upload-zone"
      :class="{ drag: isDragging }"
      @dragover.prevent="isDragging = true"
      @dragleave="isDragging = false"
      @drop.prevent="handleDrop"
      @click="fileInput?.click()"
    >
      <div class="upload-icon">上传</div>
      <div class="upload-text">拖拽文件到此处或点击上传</div>
      <div class="upload-sub">支持 PDF / Word / Excel / TXT / Markdown，也可接收 csv / json / xml 等待后续解析</div>
      <input
        ref="fileInput"
        type="file"
        style="display: none"
        multiple
        accept=".pdf,.doc,.docx,.xlsx,.xls,.txt,.md,.csv,.json,.xml,.mol,.sdf,.cdx"
        @change="handleFileSelect"
      >
    </div>

    <div v-if="uploadingFiles.length" class="file-list">
      <div v-for="(f, idx) in uploadingFiles" :key="idx" class="file-item">
        <div class="file-icon">文件</div>
        <div class="file-name">{{ f.name }}</div>
        <div class="file-size">{{ formatFileSize(f.size) }}</div>
        <span class="file-status" :class="f.status">
          {{ f.status === 'done' ? '完成' : f.status === 'error' ? '失败' : '上传中...' }}
        </span>
        <div v-if="f.status === 'indexing'" class="file-progress">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: f.progress + '%' }"></div>
          </div>
        </div>
        <div v-if="f.message" class="file-message">{{ f.message }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRagStore } from '@/stores/rag'
import { useToast } from '@/composables/useToast'
import { formatFileSize } from '@/utils/format'

interface UploadFile {
  name: string
  size: number
  status: 'indexing' | 'done' | 'error'
  progress: number
  message?: string
}

const ragStore = useRagStore()
const { showToast } = useToast()

const fileInput = ref<HTMLInputElement>()
const isDragging = ref(false)
const replaceExisting = ref(true)
const uploadingFiles = reactive<UploadFile[]>([])

function handleDrop(e: DragEvent) {
  isDragging.value = false
  const files = e.dataTransfer?.files
  if (files) processFiles(files)
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) processFiles(input.files)
  input.value = ''
}

async function processFiles(files: FileList) {
  for (const file of Array.from(files)) {
    const uf: UploadFile = { name: file.name, size: file.size, status: 'indexing', progress: 0 }
    uploadingFiles.push(uf)
    const idx = uploadingFiles.length - 1

    const timer = setInterval(() => {
      if (uploadingFiles[idx].progress < 60) {
        uploadingFiles[idx].progress += 5
      }
    }, 200)

    try {
      const result = await ragStore.uploadFile(file, replaceExisting.value)
      clearInterval(timer)
      uploadingFiles[idx].progress = 100

      if (!result) {
        uploadingFiles[idx].status = 'error'
        uploadingFiles[idx].message = '上传请求失败。'
        showToast(`${file.name} 上传失败`)
        continue
      }

      if (result.status === 'FAILED') {
        uploadingFiles[idx].status = 'error'
        uploadingFiles[idx].message = result.errorMessage || '文档已保存，但当前无法解析。'
        showToast(`${file.name} 已保存，但当前无法解析`)
        continue
      }

      uploadingFiles[idx].status = 'done'
      showToast(`${file.name} 上传成功`)
    } catch {
      clearInterval(timer)
      uploadingFiles[idx].status = 'error'
      uploadingFiles[idx].message = '上传过程中发生异常。'
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

.file-progress {
  width: 100%;
}

.file-message {
  width: 100%;
  margin-top: 6px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.5;
}
</style>
