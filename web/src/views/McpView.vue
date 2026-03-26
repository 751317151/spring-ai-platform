<template>
  <div class="space-admin">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">基础设施</div>
        <div class="page-title">MCP 管理</div>
        <div class="page-subtitle">查看 `agent-service` 加载的 MCP 服务，检查启动命令与参数，并确认运行时启用状态。</div>
        <div class="hero-tags">
          <span class="tag">MCP 客户端 {{ clientEnabled ? '开启' : '关闭' }}</span>
          <span class="tag">{{ data?.count ?? 0 }} 个服务</span>
          <span class="tag">{{ enabledServerCount }} 个服务端已启用</span>
          <span class="tag">{{ lastUpdatedLabel }}</span>
          <span class="tag">{{ data?.source || 'classpath:mcp-servers.json' }}</span>
        </div>
      </div>
      <div class="page-hero-actions">
        <button class="btn btn-ghost btn-sm" :disabled="loading || !data" @click="copyOverview">
          复制概览
        </button>
        <button class="btn btn-primary btn-sm" :disabled="loading" @click="loadData">
          {{ loading ? '刷新中...' : '刷新配置' }}
        </button>
      </div>
    </div>

    <div class="summary-grid">
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">MCP 客户端</div>
        <div :class="['summary-value', clientEnabled ? 'status-on' : 'status-off']">
          {{ clientEnabled ? '已启用' : '已停用' }}
        </div>
      </div>
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">服务数量</div>
        <div class="summary-value">{{ data?.count ?? 0 }}</div>
      </div>
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">配置来源</div>
        <div class="summary-value source-text">{{ data?.source || 'classpath:mcp-servers.json' }}</div>
      </div>
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">需处理项</div>
        <div :class="['summary-value', issueCount ? 'status-off' : 'status-on']">{{ issueCount }}</div>
        <div class="summary-subtitle">服务端停用或客户端未启用的项目</div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div>
          <div class="card-title">服务列表</div>
          <div class="card-subtitle">检查启动命令、参数，以及服务端和客户端两侧的启用状态。</div>
        </div>
        <div class="filter-tabs filter-pill-group">
          <button
            v-for="option in filterOptions"
            :key="option.value"
            class="filter-tab filter-pill"
            :class="{ active: serverFilter === option.value }"
            @click="serverFilter = option.value"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <div v-if="data?.servers?.length" class="mcp-summary-note">
        可以将此表作为运行时核查面板。如果聊天中缺少某个工具，先确认这里的 MCP 客户端已启用，同时对应服务也处于启用状态。
      </div>

      <div v-if="data?.servers?.length" class="filter-summary">
        当前显示 {{ filteredServers.length }} / {{ data?.servers?.length || 0 }} 个服务
        <span v-if="serverFilter !== 'all'">，筛选条件：{{ activeFilterLabel }}</span>
      </div>

      <div v-if="issueCount" class="issue-banner">
        检测到 {{ issueCount }} 个待处理服务。优先处理“服务端停用”或“客户端未启用”的项目，否则聊天页可能缺少工具。
        <div class="issue-banner-actions">
          <button class="table-action-btn" type="button" @click="serverFilter = 'issues'">只看异常</button>
          <button class="table-action-btn" type="button" @click="copyIssueSummary">复制异常清单</button>
        </div>
      </div>

      <SkeletonBlock v-if="loading" :count="3" :height="64" />

      <EmptyState
        v-else-if="!!error"
        icon="!"
        badge="配置状态"
        title="MCP 配置加载失败"
        :description="error"
        action-text="重试"
        @action="loadData"
      />

      <EmptyState
        v-else-if="!data?.servers?.length"
        icon="M"
        badge="等待服务"
        title="暂无 MCP 服务"
        description="请检查 `agent-service` 是否存在有效的 `mcp-servers.json` 文件，以及 MCP 客户端是否已启用。"
        action-text="刷新配置"
        @action="loadData"
      />

      <table v-else class="compact-table">
        <thead>
          <tr>
            <th>编码</th>
            <th>命令</th>
            <th>参数</th>
            <th>服务端状态</th>
            <th>客户端状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!filteredServers.length">
            <td colspan="5" class="empty-cell">
              当前筛选下没有匹配的 MCP 服务。
            </td>
          </tr>
          <tr v-for="server in filteredServers" :key="server.code">
            <td>
              <div class="server-code">{{ server.code }}</div>
              <div class="subtle-text">{{ server.source }}</div>
              <div v-if="getIssueReason(server)" class="issue-reason">{{ getIssueReason(server) }}</div>
            </td>
            <td>
              <div class="command-cell">
                <code>{{ server.command || '-' }}</code>
                <button
                  v-if="server.command"
                  class="table-action-btn"
                  type="button"
                  @click="copyServerCommand(server, 'command')"
                >
                  复制命令
                </button>
                <button
                  v-if="buildCommandText(server)"
                  class="table-action-btn"
                  type="button"
                  @click="copyServerCommand(server, 'full')"
                >
                  复制完整行
                </button>
              </div>
            </td>
            <td>
              <div v-if="server.args?.length" class="args-list">
                <code v-for="arg in server.args" :key="arg" class="arg-chip">{{ arg }}</code>
              </div>
              <div class="arg-actions">
                <span v-if="!server.args?.length" class="subtle-text">无参数</span>
                <button
                  v-else
                  class="table-action-btn"
                  type="button"
                  @click="copyServerCommand(server, 'args')"
                >
                  复制参数
                </button>
              </div>
            </td>
            <td>
              <span :class="['status-chip', server.enabled ? 'status-on' : 'status-off']">
                {{ server.enabled ? '启用' : '停用' }}
              </span>
            </td>
            <td>
              <span :class="['status-chip', server.clientEnabled ? 'status-on' : 'status-off']">
                {{ server.clientEnabled ? '启用' : '停用' }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getMcpServers } from '@/api/agent'
import type { McpServerInfo, McpServerListResponse } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import { useToast } from '@/composables/useToast'

type ServerFilter = 'all' | 'issues' | 'enabled'

const loading = ref(false)
const error = ref('')
const data = ref<McpServerListResponse | null>(null)
const serverFilter = ref<ServerFilter>('all')
const lastLoadedAt = ref<number | null>(null)
const { showToast } = useToast()

const filterOptions = [
  { label: '全部服务', value: 'all' },
  { label: '仅看异常', value: 'issues' },
  { label: '仅看已启用', value: 'enabled' }
] as const

const clientEnabled = computed(() => Boolean(data.value?.clientEnabled))
const enabledServerCount = computed(() => (data.value?.servers || []).filter((item) => item.enabled).length)
const issueCount = computed(() => (data.value?.servers || []).filter((item) => !item.enabled || !item.clientEnabled).length)
const activeFilterLabel = computed(() => filterOptions.find((item) => item.value === serverFilter.value)?.label || '全部服务')
const lastUpdatedLabel = computed(() => {
  if (!lastLoadedAt.value) return '尚未刷新'
  return `更新于 ${new Date(lastLoadedAt.value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}`
})
const filteredServers = computed(() => {
  const servers = data.value?.servers || []
  const filtered =
    serverFilter.value === 'issues'
      ? servers.filter((item) => !item.enabled || !item.clientEnabled)
      : serverFilter.value === 'enabled'
        ? servers.filter((item) => item.enabled && item.clientEnabled)
        : servers

  return [...filtered].sort((a, b) => {
    const aIssue = Number(Boolean(getIssueReason(a)))
    const bIssue = Number(Boolean(getIssueReason(b)))
    if (aIssue !== bIssue) return bIssue - aIssue
    return a.code.localeCompare(b.code)
  })
})
const issueServers = computed(() => (data.value?.servers || []).filter((item) => Boolean(getIssueReason(item))))

function getIssueReason(server: McpServerInfo) {
  if (!server.enabled && !server.clientEnabled) return '服务端与客户端都未启用'
  if (!server.enabled) return '服务端未启用'
  if (!server.clientEnabled) return '客户端未启用'
  return ''
}

function buildCommandText(server: McpServerInfo) {
  const parts = [server.command, ...(server.args || [])].filter(Boolean)
  return parts.join(' ').trim()
}

function buildArgsText(server: McpServerInfo) {
  return (server.args || []).join(' ').trim()
}

async function copyServerCommand(server: McpServerInfo, mode: 'command' | 'args' | 'full') {
  const text =
    mode === 'command'
      ? (server.command || '').trim()
      : mode === 'args'
        ? buildArgsText(server)
        : buildCommandText(server)
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    const label = mode === 'command' ? '命令' : mode === 'args' ? '参数' : '完整启动行'
    showToast(`已复制 ${server.code} 的${label}`)
  } catch {
    showToast('复制失败，请手动复制命令')
  }
}

async function copyIssueSummary() {
  const lines = issueServers.value.map((server) => `${server.code}: ${getIssueReason(server)} | ${buildCommandText(server) || '-'}`)
  if (!lines.length) {
    showToast('当前没有异常服务')
    return
  }
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    showToast(`已复制 ${lines.length} 条异常服务清单`)
  } catch {
    showToast('复制异常清单失败')
  }
}

async function copyOverview() {
  const lines = [
    'MCP 管理概览',
    `客户端状态：${clientEnabled.value ? '已启用' : '已停用'}`,
    `服务数量：${data.value?.count ?? 0}`,
    `已启用服务端：${enabledServerCount.value}`,
    `需处理项：${issueCount.value}`,
    `配置来源：${data.value?.source || 'classpath:mcp-servers.json'}`,
    `更新时间：${lastUpdatedLabel.value}`
  ]
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    showToast('已复制 MCP 概览')
  } catch {
    showToast('复制 MCP 概览失败')
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    data.value = await getMcpServers()
    lastLoadedAt.value = Date.now()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'MCP 配置加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 16px;
}

.summary-label {
  font-size: 12px;
  color: var(--text3);
  margin-bottom: 10px;
}

.summary-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
}

.source-text {
  font-size: 14px;
}

.summary-subtitle {
  margin-top: 8px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}

.server-code {
  font-weight: 600;
  color: var(--text);
}

.issue-reason {
  margin-top: 4px;
  color: #b45309;
  font-size: 12px;
}

.filter-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-tab {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text2);
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  cursor: pointer;
  transition: all var(--transition);
}

.filter-tab.active {
  border-color: var(--accent);
  color: var(--accent2);
  background: var(--accent-dim);
}

.args-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mcp-summary-note {
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px dashed var(--border);
  border-radius: 12px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}

.filter-summary {
  margin-bottom: 12px;
  color: var(--text3);
  font-size: 12px;
}

.issue-banner {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
  font-size: 12px;
  line-height: 1.6;
}

.issue-banner-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.command-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.arg-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.arg-chip,
.status-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
}

.arg-chip {
  background: rgba(15, 23, 42, 0.06);
  color: var(--text2);
}

.status-chip {
  font-weight: 600;
}

.status-on {
  color: #0f766e;
  background: rgba(13, 148, 136, 0.12);
}

.status-off {
  color: #b45309;
  background: rgba(245, 158, 11, 0.14);
}

.empty-cell {
  padding: 16px;
  text-align: center;
  color: var(--text3);
}

.table-action-btn {
  padding: 4px 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: transparent;
  color: var(--text2);
  font-size: 11px;
  cursor: pointer;
  transition: all var(--transition);
}

.table-action-btn:hover {
  background: var(--surface2);
  color: var(--text);
  border-color: var(--border2);
}

@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
