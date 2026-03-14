<template>
  <div>
    <div
      class="upload-zone"
      :class="{ drag: isDragging }"
      @dragover.prevent="isDragging = true"
      @dragleave="isDragging = false"
      @drop.prevent="handleDrop"
      @click="fileInput?.click()"
    >
      <div class="upload-icon">📂</div>
      <div class="upload-text">拖拽文件到此处或点击上传</div>
      <div class="upload-sub">支持 PDF · Word · Excel · TXT · Markdown</div>
      <input
        ref="fileInput"
        type="file"
        style="display: none"
        multiple
        accept=".pdf,.doc,.docx,.xlsx,.xls,.txt,.md"
        @change="handleFileSelect"
      >
    </div>
    <div class="file-list" v-if="uploadingFiles.length">
      <div v-for="(f, idx) in uploadingFiles" :key="idx" class="file-item">
        <div class="file-icon">📄</div>
        <div class="file-name">{{ f.name }}</div>
        <div class="file-size">{{ formatFileSize(f.size) }}</div>
        <span class="file-status" :class="f.status">
          {{ f.status === 'done' ? '完成' : f.status === 'error' ? '失败' : '上传中...' }}
        </span>
        <div v-if="f.status === 'indexing'" style="width: 100%">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: f.progress + '%' }"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRagStore } from '@/stores/rag'
import { useToast } from '@/composables/useToast'
import { formatFileSize } from '@/utils/format'

interface UploadFile {
  name: string
  size: number
  status: 'indexing' | 'done' | 'error'
  progress: number
}

const ragStore = useRagStore()
const { showToast } = useToast()

const fileInput = ref<HTMLInputElement>()
const isDragging = ref(false)
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

    // Simulate progress
    const timer = setInterval(() => {
      if (uploadingFiles[idx].progress < 60) {
        uploadingFiles[idx].progress += 5
      }
    }, 200)

    try {
      const result = await ragStore.uploadFile(file)
      clearInterval(timer)
      uploadingFiles[idx].progress = 100
      uploadingFiles[idx].status = result ? 'done' : 'error'
      if (result) showToast(`${file.name} 上传成功`)
    } catch {
      clearInterval(timer)
      uploadingFiles[idx].status = 'error'
      showToast(`${file.name} 上传失败`)
    }
  }
}
</script>
