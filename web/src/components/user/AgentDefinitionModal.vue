<template>
  <div class="modal-mask">
    <div class="modal-shell">
      <div class="modal-header">
        <div>
          <div class="modal-title">{{ isEdit ? '编辑助手' : '新建助手' }}</div>
          <div class="modal-subtitle">
            助手与角色直接关联，工具组和 MCP 服务也在这里统一配置。
          </div>
        </div>
        <div v-if="definition?.systemDefined" class="special-chip">特殊助手</div>
      </div>

      <div class="modal-body">
        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">助手编码</label>
            <input
              v-model.trim="form.agentCode"
              class="form-input"
              :disabled="isEdit"
              placeholder="例如 legal-assistant"
            >
          </div>
          <div class="modal-field">
            <label class="modal-label">助手名称</label>
            <input v-model.trim="form.agentName" class="form-input" placeholder="例如 法务助手">
          </div>
        </div>

        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">默认模型</label>
            <input v-model.trim="form.defaultModel" class="form-input" placeholder="例如 auto 或 gpt-4o-mini">
          </div>
          <div class="modal-field">
            <label class="modal-label">排序</label>
            <input v-model.number="form.sortOrder" class="form-input" type="number" step="1">
          </div>
        </div>

        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">图标</label>
            <input v-model.trim="form.icon" class="form-input" maxlength="16" placeholder="例如 LG">
          </div>
          <div class="modal-field">
            <label class="modal-label">颜色</label>
            <input v-model.trim="form.color" class="form-input" placeholder="例如 #2563eb">
          </div>
        </div>

        <div class="modal-grid">
          <div class="modal-field">
            <label class="modal-label">默认每日 Token 配额</label>
            <input v-model.number="form.dailyTokenLimit" class="form-input" type="number" min="0" step="1000">
          </div>
          <label class="switch-row">
            <input v-model="form.enabled" type="checkbox">
            <span>启用该助手</span>
          </label>
        </div>

        <div class="modal-field">
          <label class="modal-label">允许角色</label>
          <div class="choice-grid">
            <label v-for="role in roleOptions" :key="role.roleName" class="choice-item">
              <input v-model="selectedRoles" type="checkbox" :value="role.roleName">
              <div class="choice-card" :class="{ active: selectedRoles.includes(role.roleName) }">
                <strong>{{ role.roleName }}</strong>
                <span>{{ role.description || '未填写角色说明' }}</span>
              </div>
            </label>
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">工具组</label>
          <div class="choice-grid">
            <label v-for="tool in toolOptions" :key="tool.code" class="choice-item">
              <input v-model="selectedToolCodes" type="checkbox" :value="tool.code">
              <div class="choice-card" :class="{ active: selectedToolCodes.includes(tool.code) }">
                <strong>{{ tool.name }}</strong>
                <span>{{ tool.description || tool.code }}</span>
              </div>
            </label>
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">MCP 服务</label>
          <div class="choice-grid">
            <label v-for="server in mcpServerOptions" :key="server.code" class="choice-item">
              <input v-model="selectedMcpServerCodes" type="checkbox" :value="server.code">
              <div class="choice-card" :class="{ active: selectedMcpServerCodes.includes(server.code) }">
                <strong>{{ server.code }}</strong>
                <span>{{ server.runtimeHint || server.commandLinePreview || server.source }}</span>
              </div>
            </label>
          </div>
        </div>

        <div class="modal-field">
          <label class="modal-label">描述</label>
          <textarea
            v-model.trim="form.description"
            class="form-input textarea-input"
            rows="3"
            placeholder="用于列表展示和助手说明"
          />
        </div>

        <div class="modal-field">
          <label class="modal-label">系统提示词</label>
          <textarea
            v-model.trim="form.systemPrompt"
            class="form-input textarea-input prompt-input"
            rows="8"
            placeholder="定义助手职责、边界和回答要求"
          />
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn btn-ghost btn-sm" @click="emit('close')">取消</button>
        <button class="btn btn-primary btn-sm" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中...' : isEdit ? '保存修改' : '创建助手' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { getAssistantToolCatalog, getMcpServers } from '@/api/agent'
import type { AgentDefinition, AssistantToolCatalogItem, McpServerInfo, RoleOption } from '@/api/types'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ definition: AgentDefinition | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const userStore = useUserStore()
const { showToast } = useToast()
const saving = ref(false)
const selectedRoles = ref<string[]>([])
const selectedToolCodes = ref<string[]>([])
const selectedMcpServerCodes = ref<string[]>([])
const toolOptions = ref<AssistantToolCatalogItem[]>([])
const mcpServerOptions = ref<McpServerInfo[]>([])

const form = reactive({
  agentCode: '',
  agentName: '',
  description: '',
  icon: 'AI',
  color: '#6b7280',
  systemPrompt: '',
  defaultModel: 'auto',
  enabled: true,
  sortOrder: 0,
  dailyTokenLimit: 100000
})

const isEdit = computed(() => Boolean(props.definition))
const roleOptions = computed<RoleOption[]>(() => userStore.roles)

onMounted(async () => {
  await Promise.all([
    userStore.roles.length ? Promise.resolve() : userStore.loadRoles(),
    loadCapabilityCatalog()
  ])
})

watch(
  () => props.definition,
  (definition) => {
    form.agentCode = definition?.agentCode || ''
    form.agentName = definition?.agentName || ''
    form.description = definition?.description || ''
    form.icon = definition?.icon || 'AI'
    form.color = definition?.color || '#6b7280'
    form.systemPrompt = definition?.systemPrompt || ''
    form.defaultModel = definition?.defaultModel || 'auto'
    form.enabled = definition?.enabled ?? true
    form.sortOrder = definition?.sortOrder ?? 0
    form.dailyTokenLimit = definition?.dailyTokenLimit ?? 100000
    selectedRoles.value = splitCsv(definition?.allowedRoles)
    selectedToolCodes.value = splitCsv(definition?.toolCodes)
    selectedMcpServerCodes.value = splitCsv(definition?.mcpServerCodes)
  },
  { immediate: true }
)

async function handleSave() {
  if (!form.agentCode.trim()) return showToast('助手编码不能为空')
  if (!form.agentName.trim()) return showToast('助手名称不能为空')
  if (!selectedRoles.value.length) return showToast('请至少选择一个角色')
  if (!form.systemPrompt.trim()) return showToast('系统提示词不能为空')
  if (form.dailyTokenLimit < 0 || Number.isNaN(form.dailyTokenLimit)) return showToast('默认 Token 配额不能为空')

  saving.value = true
  const payload = {
    agentCode: form.agentCode.trim(),
    agentName: form.agentName.trim(),
    assistantProfile: props.definition?.systemDefined ? props.definition.assistantProfile : 'generic',
    allowedRoles: selectedRoles.value.join(','),
    description: form.description.trim(),
    icon: form.icon.trim(),
    color: form.color.trim(),
    systemPrompt: form.systemPrompt.trim(),
    defaultModel: form.defaultModel.trim(),
    toolCodes: selectedToolCodes.value.join(','),
    mcpServerCodes: selectedMcpServerCodes.value.join(','),
    enabled: form.enabled,
    sortOrder: Number.isFinite(form.sortOrder) ? form.sortOrder : 0,
    dailyTokenLimit: form.dailyTokenLimit
  }

  const success = isEdit.value
    ? await userStore.updateAgentDefinition(props.definition!.agentCode, payload)
    : await userStore.createAgentDefinition(payload)

  saving.value = false
  if (success) {
    emit('saved')
  } else {
    showToast(userStore.agentDefinitionError || '助手保存失败')
  }
}

function splitCsv(value?: string): string[] {
  if (!value) return []
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

async function loadCapabilityCatalog() {
  try {
    const [tools, mcpResponse] = await Promise.all([
      getAssistantToolCatalog(),
      getMcpServers()
    ])
    toolOptions.value = tools || []
    mcpServerOptions.value = mcpResponse?.servers || []
  } catch (error) {
    showToast(error instanceof Error ? error.message : '助手能力目录加载失败')
  }
}
</script>

<style scoped>
.modal-mask { position: fixed; inset: 0; z-index: 1600; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(15, 23, 42, 0.56); backdrop-filter: blur(8px); }
.modal-shell { width: min(820px, 100%); max-height: calc(100vh - 40px); display: flex; flex-direction: column; overflow: hidden; border: 1px solid rgba(148, 163, 184, 0.24); border-radius: 24px; background: linear-gradient(180deg, rgba(15, 23, 42, 0.98), rgba(15, 23, 42, 0.92)); box-shadow: 0 24px 80px rgba(15, 23, 42, 0.36); }
.modal-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; padding: 24px 24px 0; flex-shrink: 0; }
.modal-title { color: var(--text); font-size: 20px; font-weight: 700; }
.modal-subtitle { margin-top: 6px; color: var(--text3); font-size: 13px; line-height: 1.7; }
.special-chip { padding: 6px 10px; border-radius: 999px; background: rgba(249, 115, 22, 0.18); color: #fdba74; font-size: 12px; }
.modal-body { display: grid; gap: 14px; padding: 0 24px 24px; overflow-y: auto; min-height: 0; }
.modal-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; align-items: end; }
.modal-field { display: grid; gap: 8px; }
.modal-label { color: var(--text3); font-size: 12px; }
.textarea-input { resize: vertical; min-height: 96px; }
.prompt-input { min-height: 160px; }
.switch-row { display: inline-flex; align-items: center; gap: 10px; color: var(--text2); font-size: 13px; min-height: 40px; }
.choice-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.choice-item { display: block; cursor: pointer; }
.choice-item input { display: none; }
.choice-card { display: grid; gap: 6px; padding: 14px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 16px; background: rgba(15, 23, 42, 0.54); min-height: 84px; }
.choice-card strong { color: var(--text); font-size: 13px; }
.choice-card span { color: var(--text3); font-size: 12px; line-height: 1.6; word-break: break-word; }
.choice-card.active { border-color: rgba(52, 211, 153, 0.48); background: rgba(6, 78, 59, 0.66); transform: translateY(-1px); }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: auto; padding: 16px 24px 24px; flex-shrink: 0; border-top: 1px solid rgba(148, 163, 184, 0.12); background: linear-gradient(180deg, rgba(15, 23, 42, 0.72), rgba(15, 23, 42, 0.96)); }
@media (max-width: 720px) { .modal-mask { padding: 12px; } .modal-shell { max-height: calc(100vh - 24px); } .modal-grid, .choice-grid { grid-template-columns: 1fr; } .modal-header, .modal-body, .modal-actions { padding-left: 16px; padding-right: 16px; } .modal-header { padding-top: 18px; } .modal-actions { padding-bottom: 18px; } }
</style>
