<template>
  <div class="card section-card session-config-panel">
    <div class="card-header">
      <div>
        <div class="card-title">会话运行参数</div>
        <div class="card-subtitle">这些配置会跟随当前会话保存，并在后端调用时直接生效。</div>
      </div>
      <span class="config-status" :class="{ dirty: isDirty }">{{ isDirty ? '待保存' : '已同步' }}</span>
    </div>

    <div class="config-grid">
      <label class="form-group">
        <span class="form-label">模型偏好</span>
        <select v-model="draft.model" class="form-select">
          <option v-for="option in modelOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>

      <label class="form-group">
        <span class="form-label">上下文窗口</span>
        <input v-model.number="draft.maxContextMessages" class="form-input" type="number" min="1" max="20">
      </label>

      <label class="form-group form-group-wide">
        <span class="form-label">温度 {{ temperatureLabel }}</span>
        <input v-model.number="draft.temperature" class="form-range" type="range" min="0" max="1" step="0.1">
      </label>

      <label class="form-group form-group-checkbox">
        <input v-model="draft.knowledgeEnabled" type="checkbox">
        <span>允许结合知识库或外部证据增强回答</span>
      </label>

      <label class="form-group form-group-full">
        <span class="form-label">附加系统提示词</span>
        <textarea
          v-model.trim="draft.systemPromptTemplate"
          class="form-textarea"
          rows="4"
          placeholder="例如：回答尽量先给结论，再给步骤，必要时标注风险和前置条件。"
        />
      </label>
    </div>

    <div class="config-summary">
      <span class="tag">模型：{{ modelLabel }}</span>
      <span class="tag">温度：{{ temperatureLabel }}</span>
      <span class="tag">上下文：最近 {{ draft.maxContextMessages }} 条</span>
      <span class="tag">{{ draft.knowledgeEnabled ? '已启用知识增强' : '仅基于当前对话' }}</span>
    </div>

    <div class="config-actions">
      <button class="btn btn-primary btn-sm" :disabled="!isDirty || saving" @click="handleSave">
        {{ saving ? '保存中...' : '保存配置' }}
      </button>
      <button class="btn btn-ghost btn-sm" :disabled="saving" @click="handleReset">恢复默认</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useToast } from '@/composables/useToast'
import { useChatStore } from '@/stores/chat'
import { MODEL_OPTIONS } from '@/utils/constants'
import type { SessionConfig } from '@/api/types'

const chatStore = useChatStore()
const { showToast } = useToast()

const saving = ref(false)

const draft = reactive<SessionConfig>({
  model: 'auto',
  temperature: 0.7,
  maxContextMessages: 10,
  knowledgeEnabled: true,
  systemPromptTemplate: ''
})

const modelOptions = computed(() => MODEL_OPTIONS)

const temperatureLabel = computed(() => Number(draft.temperature ?? 0.7).toFixed(1))
const modelLabel = computed(() => {
  const matched = modelOptions.value.find((item) => item.value === draft.model)
  return matched?.label || draft.model || '自动路由'
})

const isDirty = computed(() => JSON.stringify(normalize(draft)) !== JSON.stringify(normalize(chatStore.sessionConfig)))

function normalize(config?: SessionConfig | null): SessionConfig {
  return {
    model: config?.model || 'auto',
    temperature: typeof config?.temperature === 'number' ? Number(config.temperature.toFixed(1)) : 0.7,
    maxContextMessages: typeof config?.maxContextMessages === 'number' ? Math.min(20, Math.max(1, Math.round(config.maxContextMessages))) : 10,
    knowledgeEnabled: typeof config?.knowledgeEnabled === 'boolean' ? config.knowledgeEnabled : true,
    systemPromptTemplate: config?.systemPromptTemplate?.trim() || ''
  }
}

function syncFromStore() {
  Object.assign(draft, normalize(chatStore.sessionConfig))
}

async function handleSave() {
  saving.value = true
  const ok = await chatStore.saveCurrentSessionConfig(normalize(draft))
  saving.value = false
  if (ok) {
    syncFromStore()
    showToast('会话配置已保存，并会在后续问答中生效。')
  } else {
    showToast('会话配置保存失败，请稍后重试。')
  }
}

async function handleReset() {
  Object.assign(draft, {
    model: 'auto',
    temperature: 0.7,
    maxContextMessages: 10,
    knowledgeEnabled: true,
    systemPromptTemplate: ''
  })
  await handleSave()
}

watch(() => chatStore.sessionConfig, syncFromStore, { deep: true, immediate: true })
</script>

<style scoped>
.session-config-panel {
  overflow: hidden;
}

.config-status {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.12);
  color: #059669;
  font-size: 12px;
  font-weight: 700;
}

.config-status.dirty {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 16px;
}

.form-group {
  margin: 0;
}

.form-group-wide,
.form-group-full {
  grid-column: 1 / -1;
}

.form-group-checkbox {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding-top: 24px;
  color: var(--text2);
}

.form-range {
  width: 100%;
}

.config-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.config-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

@media (max-width: 820px) {
  .config-grid {
    grid-template-columns: 1fr;
  }

  .config-actions {
    justify-content: stretch;
  }

  .config-actions > .btn {
    flex: 1 1 0;
  }
}
</style>
