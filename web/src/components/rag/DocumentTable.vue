<template>
  <div class="card doc-section">
    <div class="card-title">
      <span>文档列表 <span class="doc-count-badge">{{ ragStore.documents.length ? `(${ragStore.documents.length})` : '' }}</span></span>
      <button class="btn btn-ghost btn-sm" @click="ragStore.loadDocuments()">刷新</button>
    </div>
    <table class="doc-table">
      <thead>
        <tr>
          <th>文件名</th>
          <th>大小</th>
          <th>分块数</th>
          <th>上传人</th>
          <th>上传时间</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!ragStore.documents.length">
          <td colspan="7" class="doc-empty">{{ ragStore.currentKb ? '暂无文档' : '请先选择知识库' }}</td>
        </tr>
        <tr v-for="doc in ragStore.documents" :key="doc.id">
          <td class="doc-filename">{{ doc.filename }}</td>
          <td class="doc-size">{{ formatFileSize(doc.fileSize ?? 0) }}</td>
          <td>{{ doc.chunkCount ?? '—' }}</td>
          <td>{{ doc.uploadedBy || '—' }}</td>
          <td class="doc-date">{{ formatTime(doc.createdAt) }}</td>
          <td>
            <span class="pill" :class="doc.status === 'COMPLETED' ? 'green' : doc.status === 'FAILED' ? 'red' : 'amber'">
              {{ doc.status === 'COMPLETED' ? '已索引' : doc.status === 'FAILED' ? '失败' : doc.status || '—' }}
            </span>
          </td>
          <td>
            <div class="doc-actions">
              <button @click="ragStore.downloadDocument(doc.id, doc.filename)">下载</button>
              <button @click="ragStore.previewDocument(doc.id)">预览</button>
              <button class="del" @click="handleDelete(doc.id, doc.filename)">删除</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { useRagStore } from '@/stores/rag'
import { useToast } from '@/composables/useToast'
import { formatFileSize, formatTime } from '@/utils/format'

const ragStore = useRagStore()
const { showToast } = useToast()

function handleDelete(docId: string, filename: string) {
  if (confirm(`确定删除 "${filename}" 吗？`)) {
    ragStore.deleteDocument(docId)
    showToast(`${filename} 已删除`)
  }
}
</script>
