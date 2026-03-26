<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal-shell">
      <div class="modal-header">
        <div>
          <div class="modal-title">{{ knowledgeBaseId ? '编辑知识库' : '新建知识库' }}</div>
          <div class="modal-subtitle">维护知识库名称、归属范围、分块策略和状态配置。</div>
        </div>
      </div>

      <div class="modal-body">
        <div class="modal-field">
          <label class="modal-label">知识库名称</label>
          <input v-model.trim="form.name" class="form-input" placeholder="例如：研发规范库">
        </div>

        <div class="modal-field">
          <label class="modal-label">描述</label>
          <textarea
            v-model.trim="form.description"
            class="form-input modal-textarea"
            placeholder="简要说明这个知识库的内容范围和使用场景"
          />
        </div>

        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">所属部门</label>
            <input v-model.trim="form.department" class="form-input" placeholder="例如：研发中心">
          </div>
          <div class="modal-field">
            <label class="modal-label">可见范围</label>
            <select v-model="form.visibilityScope" class="form-input">
              <option value="PUBLIC">公共可见</option>
              <option value="DEPARTMENT">部门内可见</option>
              <option value="PRIVATE">仅创建人可见</option>
            </select>
          </div>
        </div>

        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">分块大小</label>
            <input v-model.number="form.chunkSize" class="form-input" type="number" min="100" step="100">
          </div>
          <div class="modal-field">
            <label class="modal-label">重叠大小</label>
            <input v-model.number="form.chunkOverlap" class="form-input" type="number" min="0" step="50">
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">状态</label>
          <select v-model="form.status" class="form-input">
            <option value="ACTIVE">启用中</option>
            <option value="DISABLED">已停用</option>
          </select>
        </div>

        <div class="modal-help">
          创建人默认使用当前登录用户：{{ authStore.userId || '未登录用户' }}
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn btn-ghost btn-sm" @click="emit('close')">取消</button>
        <button class="btn btn-primary btn-sm" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中...' : knowledgeBaseId ? '保存修改' : '创建知识库' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getKnowledgeBase } from '@/api/rag'
import { useAuthStore } from '@/stores/auth'
import { useRagStore } from '@/stores/rag'

const props = defineProps<{ knowledgeBaseId: string | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const ragStore = useRagStore()
const authStore = useAuthStore()
const saving = ref(false)
const form = reactive({
  name: '',
  description: '',
  department: '',
  visibilityScope: 'DEPARTMENT',
  chunkSize: 1000,
  chunkOverlap: 200,
  status: 'ACTIVE'
})

onMounted(async () => {
  if (props.knowledgeBaseId) {
    const kb = await getKnowledgeBase(props.knowledgeBaseId)
    form.name = kb.name || ''
    form.description = kb.description || ''
    form.department = kb.department || ''
    form.visibilityScope = (kb.visibilityScope || 'DEPARTMENT').toUpperCase()
    form.chunkSize = kb.chunkSize || 1000
    form.chunkOverlap = kb.chunkOverlap ?? 200
    form.status = (kb.status || 'ACTIVE').toUpperCase()
    return
  }

  form.department = authStore.department || ''
})

async function handleSave() {
  if (!form.name) {
    return
  }

  saving.value = true
  const payload = {
    name: form.name,
    description: form.description,
    department: form.department,
    visibilityScope: form.visibilityScope,
    chunkSize: Number(form.chunkSize) || 1000,
    chunkOverlap: Math.max(0, Number(form.chunkOverlap) || 0),
    status: form.status,
    createdBy: authStore.userId || undefined
  }

  const result = props.knowledgeBaseId
    ? await ragStore.updateKnowledgeBase(props.knowledgeBaseId, payload)
    : await ragStore.createKnowledgeBase(payload)

  saving.value = false
  if (result) {
    emit('saved')
  }
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.62);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal-shell {
  width: min(560px, 100%);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r2);
  padding: 24px;
}

.modal-header {
  margin-bottom: 16px;
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}

.modal-subtitle {
  margin-top: 6px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}

.modal-body {
  display: grid;
  gap: 12px;
}

.modal-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.modal-field {
  display: grid;
  gap: 6px;
}

.modal-label {
  font-size: 11px;
  color: var(--text3);
}

.modal-textarea {
  min-height: 88px;
  resize: vertical;
}

.modal-help {
  color: var(--text3);
  font-size: 11px;
}

.modal-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 18px;
}

@media (max-width: 640px) {
  .modal-grid {
    grid-template-columns: 1fr;
  }
}
</style>
