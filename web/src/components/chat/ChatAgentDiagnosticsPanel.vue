<template>
  <div class="card section-card agent-diagnostics-card">
    <div class="card-header diagnostics-head">
      <div>
        <div class="card-title">Agent 诊断</div>
        <div class="card-subtitle">
          查看当前助手的权限解释、工具调用审计、MCP 状态和多智能体执行轨迹，用来快速回答“为什么不能调用”和“这次到底执行了什么”。
        </div>
      </div>
      <div class="diagnostics-actions">
        <button class="btn btn-ghost btn-sm" type="button" :disabled="loading" @click="reloadAll">
          {{ loading ? '刷新中...' : '刷新诊断' }}
        </button>
      </div>
    </div>

    <div v-if="error" class="diagnostics-error">{{ error }}</div>

    <div class="diagnostics-grid">
      <div class="diagnostics-metric">
        <span class="diagnostics-label">当前助手</span>
        <strong>{{ currentAgentLabel }}</strong>
        <small>{{ diagnostics?.summary || '等待加载诊断信息' }}</small>
      </div>
      <div class="diagnostics-metric">
        <span class="diagnostics-label">可用工具</span>
        <strong>{{ diagnostics?.allowedTools?.length ?? 0 }}</strong>
        <small>{{ summarizeList(diagnostics?.allowedTools) }}</small>
      </div>
      <div class="diagnostics-metric">
        <span class="diagnostics-label">可用连接器</span>
        <strong>{{ diagnostics?.allowedConnectors?.length ?? 0 }}</strong>
        <small>{{ summarizeList(diagnostics?.allowedConnectors) }}</small>
      </div>
      <div class="diagnostics-metric">
        <span class="diagnostics-label">MCP 服务</span>
        <strong>{{ diagnostics?.availableMcpServerCount ?? 0 }}</strong>
        <small>异常 {{ diagnostics?.mcpIssueCount ?? 0 }} 个</small>
      </div>
    </div>

    <div class="diagnostics-section">
      <div class="diagnostics-section-head">
        <div class="diagnostics-section-title">权限解释</div>
        <div class="diagnostics-section-subtitle">
          {{ accessOverview?.summary || '解释当前助手对工具、连接器和 MCP 服务的授权、资源边界与禁用原因。' }}
        </div>
      </div>

      <div class="access-panels">
        <section class="access-panel">
          <div class="access-panel-title">工具</div>
          <div v-if="accessOverview?.tools?.length" class="access-rule-list">
            <article
              v-for="item in accessOverview.tools"
              :key="`tool-${item.code}`"
              class="access-rule-item"
              :class="statusClassName(item.status)"
            >
              <div class="access-rule-head">
                <strong>{{ item.name }}</strong>
                <span class="access-rule-status">{{ formatStatus(item.status) }}</span>
              </div>
              <div class="access-rule-reason">{{ item.reasonMessage || item.reason || '未提供原因' }}</div>
              <div class="access-rule-detail">{{ item.detail || '无额外说明' }}</div>
              <div v-if="item.reasonCode || item.resource" class="access-rule-meta">
                <span v-if="item.reasonCode">代码 {{ item.reasonCode }}</span>
                <span v-if="item.resource">资源 {{ item.resource }}</span>
              </div>
            </article>
          </div>
          <div v-else class="diagnostics-empty">当前没有可展示的工具权限解释。</div>
        </section>

        <section class="access-panel">
          <div class="access-panel-title">连接器</div>
          <div v-if="accessOverview?.connectors?.length" class="access-rule-list">
            <article
              v-for="item in accessOverview.connectors"
              :key="`connector-${item.code}`"
              class="access-rule-item"
              :class="statusClassName(item.status)"
            >
              <div class="access-rule-head">
                <strong>{{ item.name }}</strong>
                <span class="access-rule-status">{{ formatStatus(item.status) }}</span>
              </div>
              <div class="access-rule-reason">{{ item.reasonMessage || item.reason || '未提供原因' }}</div>
              <div class="access-rule-detail">{{ item.detail || '无额外说明' }}</div>
              <div v-if="item.reasonCode || item.resource" class="access-rule-meta">
                <span v-if="item.reasonCode">代码 {{ item.reasonCode }}</span>
                <span v-if="item.resource">资源 {{ item.resource }}</span>
              </div>
            </article>
          </div>
          <div v-else class="diagnostics-empty">当前没有配置连接器，或没有可展示的连接器解释。</div>
        </section>

        <section class="access-panel">
          <div class="access-panel-title">MCP 服务</div>
          <div v-if="accessOverview?.mcpServers?.length" class="access-rule-list">
            <article
              v-for="item in accessOverview.mcpServers"
              :key="`mcp-${item.code}`"
              class="access-rule-item"
              :class="statusClassName(item.status)"
            >
              <div class="access-rule-head">
                <strong>{{ item.name }}</strong>
                <span class="access-rule-status">{{ formatStatus(item.status) }}</span>
              </div>
              <div class="access-rule-reason">{{ item.reasonMessage || item.reason || '未提供原因' }}</div>
              <div class="access-rule-detail">{{ item.detail || '无额外说明' }}</div>
              <div v-if="item.reasonCode || item.resource" class="access-rule-meta">
                <span v-if="item.reasonCode">代码 {{ item.reasonCode }}</span>
                <span v-if="item.resource">资源 {{ item.resource }}</span>
              </div>
            </article>
          </div>
          <div v-else class="diagnostics-empty">当前没有可展示的 MCP 权限解释。</div>
        </section>
      </div>
    </div>

    <div class="diagnostics-section">
      <div class="diagnostics-section-title">权限概览</div>
      <div class="diagnostics-tags">
        <span v-for="tool in diagnostics?.allowedTools || []" :key="`tool-${tool}`" class="diagnostics-tag">
          工具: {{ tool }}
        </span>
        <span
          v-for="connector in diagnostics?.allowedConnectors || []"
          :key="`connector-${connector}`"
          class="diagnostics-tag connector"
        >
          连接器: {{ connector }}
        </span>
        <span
          v-for="server in diagnostics?.allowedMcpServers || []"
          :key="`mcp-${server}`"
          class="diagnostics-tag mcp"
        >
          MCP: {{ server }}
        </span>
        <span
          v-if="!(diagnostics?.allowedTools?.length || diagnostics?.allowedConnectors?.length || diagnostics?.allowedMcpServers?.length)"
          class="diagnostics-empty"
        >
          当前没有返回显式白名单，默认按后端安全配置判断。
        </span>
      </div>
    </div>

    <div class="diagnostics-section">
      <div class="diagnostics-section-head">
        <div class="diagnostics-section-title">工具调用审计</div>
        <div class="diagnostics-section-subtitle">{{ toolAuditSectionSubtitle }}</div>
      </div>

      <div class="diagnostics-grid tool-audit-grid">
        <div class="diagnostics-metric">
          <span class="diagnostics-label">最近调用</span>
          <strong>{{ recentToolAudits.length }}</strong>
          <small>{{ latestToolAuditLabel }}</small>
        </div>
        <div class="diagnostics-metric">
          <span class="diagnostics-label">失败次数</span>
          <strong>{{ failedToolAuditCount }}</strong>
          <small>{{ failedToolAuditCount ? '建议优先查看失败工具与输入摘要' : '最近工具调用全部成功' }}</small>
        </div>
        <div class="diagnostics-metric">
          <span class="diagnostics-label">平均耗时</span>
          <strong>{{ averageToolLatency }} ms</strong>
          <small>{{ slowestToolLabel }}</small>
        </div>
        <div class="diagnostics-metric">
          <span class="diagnostics-label">当前视角</span>
          <strong>{{ visibleToolAudits.length }}</strong>
          <small>{{ selectedTraceId ? `已聚焦 Trace ${selectedTraceId}` : '当前展示该助手最近工具调用' }}</small>
        </div>
      </div>

      <div v-if="visibleToolAudits.length" class="diagnostics-list">
        <button
          v-for="item in visibleToolAudits"
          :key="item.id"
          type="button"
          class="diagnostics-list-item"
          :class="{ issue: item.success === false }"
          @click="copyToolAuditSummary(item)"
        >
          <strong>{{ item.toolName || item.toolClass || '未命名工具' }}</strong>
          <span>
            {{ item.success === false ? '失败' : '成功' }}
            <template v-if="item.latencyMs != null"> · {{ item.latencyMs }} ms</template>
          </span>
          <small>{{ item.outputSummary || item.errorMessage || item.inputSummary || '点击复制调用摘要' }}</small>
          <div class="tool-audit-meta">
            <span v-if="item.traceId">Trace {{ item.traceId }}</span>
            <span v-if="item.reasonCode">代码 {{ item.reasonCode }}</span>
            <span v-if="item.deniedResource">资源 {{ item.deniedResource }}</span>
            <span>{{ item.createdAt || '刚刚' }}</span>
          </div>
        </button>
      </div>
      <div v-else class="diagnostics-empty">当前助手还没有可展示的工具调用审计记录。</div>
    </div>

    <div class="diagnostics-section">
      <div class="diagnostics-section-title">MCP 状态</div>
      <div v-if="mcpServers.length" class="diagnostics-list">
        <button
          v-for="server in mcpServers"
          :key="server.code"
          type="button"
          class="diagnostics-list-item"
          :class="{ issue: server.diagnosticStatus !== 'ready' }"
          @click="copyMcpSummary(server)"
        >
          <strong>{{ server.code }}</strong>
          <span>{{ server.authorized === false ? '未授权' : server.diagnosticStatus || '未知' }}</span>
          <small>{{ server.runtimeHint || server.issueReason || '点击复制排障摘要' }}</small>
          <div class="tool-audit-meta">
            <span v-if="server.authorizedTools?.length">工具 {{ server.authorizedTools.join('、') }}</span>
          </div>
        </button>
      </div>
      <div v-else class="diagnostics-empty">当前助手没有可见的 MCP 服务。</div>
    </div>

    <div v-if="activeAgentType === 'multi'" class="diagnostics-section">
      <div class="diagnostics-section-head">
        <div class="diagnostics-section-title">多智能体执行轨迹</div>
        <div class="diagnostics-section-subtitle">
          当前会话最近 {{ traces.length }} 条轨迹，最近一条 TraceId：{{ latestTraceId || '暂无' }}
        </div>
      </div>

      <div v-if="traces.length" class="trace-list">
        <button
          v-for="trace in traces"
          :key="trace.traceId"
          type="button"
          class="trace-item"
          :class="{ active: selectedTraceId === trace.traceId }"
          @click="selectTrace(trace.traceId)"
        >
          <div class="trace-item-head">
            <strong>{{ trace.traceId }}</strong>
            <span>{{ trace.status }}</span>
          </div>
          <div class="trace-item-meta">
            <span>{{ trace.totalLatencyMs || 0 }} ms</span>
            <span>步骤 {{ trace.stepCount || 0 }}</span>
            <span>Token {{ (trace.totalPromptTokens || 0) + (trace.totalCompletionTokens || 0) }}</span>
          </div>
          <div class="trace-item-summary">{{ trace.requestSummary || trace.finalSummary || '暂无摘要' }}</div>
          <div class="tool-audit-meta">
            <span v-if="trace.recoveryAction">恢复动作 {{ trace.recoveryAction }}</span>
            <span v-if="trace.parentTraceId">来源 {{ trace.parentTraceId }}</span>
          </div>
        </button>
      </div>
      <div v-else class="diagnostics-empty">当前会话还没有多智能体执行轨迹。</div>

      <div v-if="selectedTrace?.steps?.length" class="trace-detail">
        <div class="trace-detail-head">
          <div>
            <div class="trace-detail-title">轨迹明细</div>
            <div class="trace-detail-subtitle">{{ selectedTrace.traceId }}</div>
          </div>
          <div class="diagnostics-actions">
            <button class="btn btn-ghost btn-sm" type="button" @click="copyTraceId(selectedTrace.traceId)">
              复制 TraceId
            </button>
            <button class="btn btn-ghost btn-sm" type="button" :disabled="recovering" @click="recoverTrace(null, 'replay')">
              整体回放
            </button>
          </div>
        </div>

        <div class="trace-step-list">
          <article v-for="step in selectedTrace.steps" :key="`${selectedTrace.traceId}-${step.stepOrder}`" class="trace-step">
            <div class="trace-step-head">
              <strong>{{ step.agentName }} / {{ step.stage }}</strong>
              <span>{{ step.latencyMs || 0 }} ms</span>
            </div>
            <div class="trace-step-status" :class="{ fail: !step.success }">
              {{ step.skipped ? '已跳过' : step.success ? '成功' : '失败' }}
            </div>
            <div class="trace-step-block">
              <label>输入摘要</label>
              <div>{{ step.inputSummary || '暂无' }}</div>
            </div>
            <div class="trace-step-block">
              <label>输出摘要</label>
              <div>{{ step.outputSummary || step.errorMessage || '暂无' }}</div>
            </div>
            <div class="trace-step-meta">
              <span v-if="step.recoveryAction">恢复 {{ step.recoveryAction }}</span>
              <span v-if="step.sourceTraceId">来源 Trace {{ step.sourceTraceId }}</span>
              <span v-if="step.sourceStepOrder != null">来源步骤 {{ step.sourceStepOrder }}</span>
            </div>
            <div class="trace-step-actions">
              <button class="btn btn-ghost btn-sm" type="button" :disabled="recovering || !step.recoverable" @click="recoverTrace(step.stepOrder, 'retry')">
                重试该步
              </button>
              <button class="btn btn-ghost btn-sm" type="button" :disabled="recovering || !step.recoverable" @click="recoverTrace(step.stepOrder, 'replay')">
                从此回放
              </button>
              <button class="btn btn-ghost btn-sm" type="button" :disabled="recovering || !step.recoverable" @click="recoverTrace(step.stepOrder, 'skip')">
                跳过继续
              </button>
            </div>
          </article>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  getAgentAccessOverview,
  getAgentDiagnostics,
  getMcpServersByAgent,
  getMultiAgentTrace,
  getMultiAgentTraces,
  getToolAuditLogs,
  recoverMultiAgentTrace
} from '@/api/agent'
import type {
  AgentAccessOverviewResponse,
  AgentToolAuditLog,
  AgentDiagnosticsResponse,
  McpServerInfo,
  MultiAgentTraceResponse
} from '@/api/types'
import { useAgentMetadata } from '@/composables/useAgentMetadata'
import { useToast } from '@/composables/useToast'
import { useChatStore } from '@/stores/chat'

const props = withDefaults(defineProps<{
  focusedTraceId?: string | null
  agentTypeOverride?: string | null
  sessionIdOverride?: string | null
}>(), {
  focusedTraceId: null,
  agentTypeOverride: null,
  sessionIdOverride: null
})

const chatStore = useChatStore()
const { showToast } = useToast()
const { getAgentConfig, loadAgentMetadata } = useAgentMetadata()

const loading = ref(false)
const recovering = ref(false)
const error = ref('')
const diagnostics = ref<AgentDiagnosticsResponse | null>(null)
const accessOverview = ref<AgentAccessOverviewResponse | null>(null)
const mcpServers = ref<McpServerInfo[]>([])
const recentToolAudits = ref<AgentToolAuditLog[]>([])
const traceToolAudits = ref<AgentToolAuditLog[]>([])
const traces = ref<MultiAgentTraceResponse[]>([])
const selectedTrace = ref<MultiAgentTraceResponse | null>(null)
const selectedTraceId = ref('')

const activeAgentType = computed(() => props.agentTypeOverride || chatStore.currentAgent)
const activeSessionId = computed(() => props.sessionIdOverride ?? chatStore.currentSessionId)
const currentAgentLabel = computed(() => getAgentConfig(activeAgentType.value).name)
const latestTraceId = computed(() => {
  const reversed = [...chatStore.chatHistory].reverse()
  return reversed.find((item) => item.role === 'assistant' && item.traceId)?.traceId || selectedTraceId.value || ''
})
const visibleToolAudits = computed(() => selectedTraceId.value ? traceToolAudits.value : recentToolAudits.value)
const failedToolAuditCount = computed(() => recentToolAudits.value.filter((item) => item.success === false).length)
const averageToolLatency = computed(() => {
  if (!recentToolAudits.value.length) {
    return 0
  }
  const total = recentToolAudits.value.reduce((sum, item) => sum + (item.latencyMs || 0), 0)
  return Math.round(total / recentToolAudits.value.length)
})
const slowestToolLabel = computed(() => {
  if (!recentToolAudits.value.length) {
    return '暂无工具调用记录'
  }
  const slowest = [...recentToolAudits.value].sort((left, right) => (right.latencyMs || 0) - (left.latencyMs || 0))[0]
  return `${slowest.toolName || slowest.toolClass || '未命名工具'} 最慢`
})
const latestToolAuditLabel = computed(() => {
  const latest = recentToolAudits.value[0]
  if (!latest) {
    return '最近暂无工具调用'
  }
  return `${latest.toolName || latest.toolClass || '未命名工具'} · ${latest.createdAt || '刚刚'}`
})
const toolAuditSectionSubtitle = computed(() => {
  if (selectedTraceId.value) {
    return `当前聚焦 Trace ${selectedTraceId.value} 的工具调用，同时保留该助手最近调用作为对照。`
  }
  return '展示该助手最近工具调用，点击单条记录可复制输入、输出、错误与 Trace 摘要。'
})

function summarizeList(values?: string[] | null) {
  if (!values?.length) {
    return '未配置或由后端默认决定'
  }
  if (values.includes('*')) {
    return '全部可用'
  }
  return values.slice(0, 4).join('、')
}

function formatStatus(status: string) {
  if (status === 'allowed' || status === 'open') return '可用'
  if (status === 'denied') return '未授权'
  if (status === 'disabled') return '未启用'
  if (status === 'issue') return '异常'
  return '说明'
}

function statusClassName(status: string) {
  return `is-${status || 'default'}`
}

async function reloadAll() {
  loading.value = true
  error.value = ''
  try {
    diagnostics.value = await getAgentDiagnostics(activeAgentType.value)
    accessOverview.value = await getAgentAccessOverview(activeAgentType.value)
    const mcpData = await getMcpServersByAgent(activeAgentType.value)
    mcpServers.value = mcpData.servers || []
    recentToolAudits.value = await getToolAuditLogs(12, activeAgentType.value)

    if (activeAgentType.value === 'multi') {
      traces.value = await getMultiAgentTraces(activeSessionId.value, 10)
      const preferredTraceId = props.focusedTraceId || latestTraceId.value || traces.value[0]?.traceId || ''
      if (preferredTraceId) {
        await selectTrace(preferredTraceId)
      } else {
        selectedTrace.value = null
        selectedTraceId.value = ''
        traceToolAudits.value = []
      }
    } else {
      traces.value = []
      selectedTrace.value = null
      selectedTraceId.value = ''
      traceToolAudits.value = []
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Agent 诊断加载失败'
  } finally {
    loading.value = false
  }
}

async function selectTrace(traceId: string) {
  if (!traceId) return
  selectedTraceId.value = traceId
  try {
    selectedTrace.value = await getMultiAgentTrace(traceId)
    traceToolAudits.value = await getToolAuditLogs(20, activeAgentType.value, undefined, traceId)
  } catch (err) {
    showToast(err instanceof Error ? err.message : '轨迹详情加载失败')
  }
}

async function recoverTrace(stepOrder: number | null, action: 'retry' | 'replay' | 'skip') {
  if (!selectedTrace.value?.traceId) return
  recovering.value = true
  try {
    const trace = await recoverMultiAgentTrace(selectedTrace.value.traceId, { stepOrder, action })
    showToast(`已创建恢复轨迹：${trace.traceId}`)
    await reloadAll()
    await selectTrace(trace.traceId)
  } catch (err) {
    showToast(err instanceof Error ? err.message : '恢复轨迹创建失败')
  } finally {
    recovering.value = false
  }
}

async function copyTraceId(traceId: string) {
  try {
    await navigator.clipboard.writeText(traceId)
    showToast('TraceId 已复制')
  } catch {
    showToast('TraceId 复制失败')
  }
}

async function copyMcpSummary(server: McpServerInfo) {
  const summary = [
    `服务: ${server.code}`,
    `状态: ${server.diagnosticStatus || 'unknown'}`,
    `授权: ${server.authorized === false ? '未授权' : '已授权'}`,
    `命令: ${server.commandLinePreview || server.command || '-'}`,
    `工具范围: ${server.authorizedTools?.join(', ') || '-'}`,
    `提示: ${server.runtimeHint || server.issueReason || '-'}`
  ].join('\n')
  try {
    await navigator.clipboard.writeText(summary)
    showToast('MCP 排障摘要已复制')
  } catch {
    showToast('MCP 排障摘要复制失败')
  }
}

async function copyToolAuditSummary(item: AgentToolAuditLog) {
  const summary = [
    `工具: ${item.toolName || item.toolClass || '-'}`,
    `状态: ${item.success === false ? '失败' : '成功'}`,
    `耗时: ${item.latencyMs ?? 0} ms`,
    `TraceId: ${item.traceId || '-'}`,
    `原因码: ${item.reasonCode || '-'}`,
    `拒绝资源: ${item.deniedResource || '-'}`,
    `输入: ${item.inputSummary || '-'}`,
    `输出: ${item.outputSummary || '-'}`,
    `错误: ${item.errorMessage || '-'}`
  ].join('\n')
  try {
    await navigator.clipboard.writeText(summary)
    showToast('工具调用摘要已复制')
  } catch {
    showToast('工具调用摘要复制失败')
  }
}

void loadAgentMetadata()

watch(
  () => [activeAgentType.value, activeSessionId.value, latestTraceId.value],
  () => {
    void reloadAll()
  },
  { immediate: true }
)

watch(
  () => props.focusedTraceId,
  async (traceId) => {
    if (!traceId || activeAgentType.value !== 'multi' || traceId === selectedTraceId.value) {
      return
    }
    await selectTrace(traceId)
  }
)
</script>

<style scoped>
.agent-diagnostics-card { overflow: hidden; }
.diagnostics-head { align-items: flex-start; }
.diagnostics-actions { display: flex; gap: 8px; }
.diagnostics-error {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(239, 68, 68, 0.08);
  color: #b91c1c;
  font-size: 12px;
}
.diagnostics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.diagnostics-metric {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  display: grid;
  gap: 6px;
}
.diagnostics-label {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text3);
}
.diagnostics-metric strong {
  color: var(--text);
  font-size: 18px;
}
.diagnostics-metric small {
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}
.diagnostics-section { margin-top: 16px; }
.diagnostics-section-head { margin-bottom: 10px; }
.diagnostics-section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
}
.diagnostics-section-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text3);
}
.access-panels {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.access-panel {
  border: 1px solid var(--border);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.03);
  padding: 14px;
}
.access-panel-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
}
.access-rule-list {
  display: grid;
  gap: 10px;
}
.access-rule-item {
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.03);
}
.access-rule-item.is-allowed,
.access-rule-item.is-open {
  border-color: rgba(16, 185, 129, 0.22);
  background: rgba(16, 185, 129, 0.06);
}
.access-rule-item.is-denied {
  border-color: rgba(245, 158, 11, 0.22);
  background: rgba(245, 158, 11, 0.08);
}
.access-rule-item.is-disabled,
.access-rule-item.is-issue {
  border-color: rgba(239, 68, 68, 0.2);
  background: rgba(239, 68, 68, 0.06);
}
.access-rule-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}
.access-rule-head strong {
  color: var(--text);
  font-size: 13px;
}
.access-rule-status,
.access-rule-meta,
.tool-audit-meta,
.trace-step-meta {
  color: var(--text3);
  font-size: 11px;
}
.access-rule-reason {
  margin-top: 8px;
  color: var(--text2);
  font-size: 12px;
  line-height: 1.6;
}
.access-rule-detail {
  margin-top: 6px;
  color: var(--text3);
  font-size: 11px;
  line-height: 1.6;
  word-break: break-word;
}
.access-rule-meta,
.trace-step-meta,
.tool-audit-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 6px;
}
.diagnostics-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.diagnostics-tag {
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(79, 142, 247, 0.16);
  background: rgba(79, 142, 247, 0.08);
  color: var(--accent2);
  font-size: 12px;
}
.diagnostics-tag.connector {
  border-color: rgba(16, 185, 129, 0.16);
  background: rgba(16, 185, 129, 0.08);
  color: #0f766e;
}
.diagnostics-tag.mcp {
  border-color: rgba(245, 158, 11, 0.16);
  background: rgba(245, 158, 11, 0.08);
  color: #b45309;
}
.tool-audit-grid { margin-bottom: 12px; }
.diagnostics-list,
.trace-list,
.trace-step-list {
  display: grid;
  gap: 10px;
}
.diagnostics-list-item,
.trace-item {
  text-align: left;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  border-radius: 16px;
  padding: 12px 14px;
  display: grid;
  gap: 6px;
  cursor: pointer;
}
.diagnostics-list-item.issue,
.trace-item.active {
  border-color: rgba(79, 142, 247, 0.24);
  background: rgba(79, 142, 247, 0.06);
}
.diagnostics-list-item strong,
.trace-item strong,
.trace-detail-title,
.trace-step-head strong {
  color: var(--text);
}
.diagnostics-list-item span,
.trace-item span,
.trace-step-head span {
  color: var(--text2);
  font-size: 12px;
}
.diagnostics-list-item small,
.trace-item-summary {
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}
.trace-item-head,
.trace-item-meta,
.trace-detail-head,
.trace-step-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.trace-detail {
  margin-top: 12px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
}
.trace-detail-subtitle,
.trace-step-status,
.trace-step-block label,
.diagnostics-empty {
  color: var(--text3);
  font-size: 12px;
}
.trace-step {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.02);
}
.trace-step-status {
  margin: 6px 0 10px;
}
.trace-step-status.fail { color: #b91c1c; }
.trace-step-block {
  display: grid;
  gap: 6px;
}
.trace-step-block + .trace-step-block {
  margin-top: 10px;
}
.trace-step-block div {
  color: var(--text2);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.trace-step-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}
@media (max-width: 960px) {
  .diagnostics-grid,
  .access-panels {
    grid-template-columns: 1fr;
  }
}
</style>
