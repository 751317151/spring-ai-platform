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
          <td colspan="7" class="doc-empty">{{ ragStore.currentKb ? '暂无文档。' : '请先选择知识库。' }}</td>
        </tr>
        <template v-for="doc in ragStore.documents" :key="doc.id">
          <tr>
            <td class="doc-filename">{{ doc.filename }}</td>
            <td class="doc-size">{{ formatFileSize(doc.fileSize ?? 0) }}</td>
            <td>{{ doc.chunkCount ?? '-' }}</td>
            <td>{{ doc.uploadedBy || '-' }}</td>
            <td class="doc-date">{{ formatTime(doc.createdAt) }}</td>
            <td>
              <span class="pill" :class="statusClass(doc.status)">
                {{ statusLabel(doc.status) }}
              </span>
            </td>
            <td>
              <div class="doc-actions">
                <button @click="ragStore.downloadDocument(doc.id, doc.filename)">下载</button>
                <button @click="ragStore.previewDocument(doc.id)">预览</button>
                <button
                  v-if="doc.status === 'INDEXED' && (doc.chunkCount ?? 0) > 0"
                  @click="openChunkPreview(doc)"
                >
                  查看分块
                </button>
                <button v-if="doc.status === 'FAILED'" @click="handleRetry(doc.id)">重试</button>
                <button v-if="doc.status === 'INDEXED' || doc.status === 'FAILED'" @click="handleReindex(doc.id)">重建索引</button>
                <button class="del" @click="handleDelete(doc.id, doc.filename)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="doc.errorMessage" class="doc-error-row">
            <td colspan="7">
              <div class="doc-error-message">
                <strong>失败原因：</strong>{{ doc.errorMessage }}
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>

    <div v-if="chunkModalVisible" class="chunk-modal-mask" @click.self="closeChunkPreview">
      <div class="chunk-modal">
        <div class="chunk-modal-header">
          <div>
            <div class="chunk-modal-title">文档分块预览</div>
            <div class="chunk-modal-subtitle">
              {{ ragStore.activeChunkDocument?.filename }}
              <span v-if="ragStore.activeDocumentChunks.length">· {{ ragStore.activeDocumentChunks.length }} 个分块</span>
            </div>
          </div>
          <button class="btn btn-ghost btn-sm" @click="closeChunkPreview">关闭</button>
        </div>

        <div v-if="ragStore.activeDocumentChunks.length" class="chunk-list">
          <div v-for="chunk in ragStore.activeDocumentChunks" :key="chunk.id" class="chunk-card">
            <div class="chunk-card-meta">
              <span>Chunk {{ chunk.chunkIndex }}</span>
              <span>{{ chunk.charCount ?? chunk.content.length }} 字符</span>
            </div>
            <div class="chunk-card-preview">{{ chunk.preview || chunk.content }}</div>
            <details class="chunk-card-details">
              <summary>查看全文</summary>
              <pre>{{ chunk.content }}</pre>
            </details>
          </div>
        </div>
        <div v-else class="chunk-empty">当前文档还没有可查看的分块数据。</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { DocumentMeta } from '@/api/types'
import { useRagStore } from '@/stores/rag'
import { useToast } from '@/composables/useToast'
import { formatFileSize, formatTime } from '@/utils/format'
import { ref } from 'vue'

const ragStore = useRagStore()
const { showToast } = useToast()
const chunkModalVisible = ref(false)

function statusClass(status?: string) {
  if (status === 'INDEXED') return 'green'
  if (status === 'FAILED') return 'red'
  return 'amber'
}

function statusLabel(status?: string) {
  if (status === 'INDEXED') return '已索引'
  if (status === 'FAILED') return '失败'
  if (status === 'PROCESSING') return '处理中'
  return status || '-'
}

async function handleDelete(docId: string, filename: string) {
  if (!confirm(`确定删除 "${filename}" 吗？`)) {
    return
  }
  const ok = await ragStore.deleteDocument(docId)
  if (ok) {
    showToast(`${filename} 已删除`)
  }
}

async function handleRetry(docId: string) {
  const ok = await ragStore.retryDocument(docId)
  if (ok) {
    showToast('已发起文档重试。')
  }
}

async function handleReindex(docId: string) {
  const ok = await ragStore.reindexDocument(docId)
  if (ok) {
    showToast('已发起重建索引。')
  }
}

async function openChunkPreview(doc: DocumentMeta) {
  const ok = await ragStore.loadDocumentChunks(doc)
  if (ok) {
    chunkModalVisible.value = true
  }
}

function closeChunkPreview() {
  chunkModalVisible.value = false
  ragStore.clearDocumentChunks()
}
</script>

<style scoped>
.doc-error-row td {
  padding-top: 0;
}

.doc-error-message {
  border-left: 3px solid #f04438;
  background: #fff3f2;
  color: #b42318;
  padding: 8px 10px;
  font-size: 12px;
}

.chunk-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(15, 23, 42, 0.48);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.chunk-modal {
  width: min(880px, 100%);
  max-height: 80vh;
  overflow: auto;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r2);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.22);
  padding: 20px;
}

.chunk-modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.chunk-modal-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}

.chunk-modal-subtitle {
  margin-top: 6px;
  color: var(--text3);
  font-size: 12px;
}

.chunk-list {
  display: grid;
  gap: 12px;
}

.chunk-card {
  border: 1px solid var(--border);
  border-radius: var(--r2);
  background: var(--bg);
  padding: 14px;
}

.chunk-card-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--text3);
  font-size: 12px;
  margin-bottom: 8px;
}

.chunk-card-preview {
  color: var(--text);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.chunk-card-details {
  margin-top: 10px;
}

.chunk-card-details summary {
  cursor: pointer;
  color: var(--brand);
  font-size: 12px;
}

.chunk-card-details pre {
  margin: 10px 0 0;
  padding: 12px;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
}

.chunk-empty {
  padding: 24px 12px;
  text-align: center;
  color: var(--text3);
}
</style>
