<template>
  <div class="space-admin">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">基础设施</div>
        <div class="page-title">MCP 管理</div>
        <div class="page-subtitle">
          检查 `agent-service` 实际加载到的 MCP 服务配置，并按助手查看哪些服务可见、已授权、存在异常。
        </div>
        <div class="hero-tags">
          <span class="tag">客户端 {{ clientEnabled ? '已启用' : '已停用' }}</span>
          <span class="tag">{{ selectedAgentLabel }}</span>
          <span class="tag">{{ data?.count ?? 0 }} 个服务</span>
          <span class="tag">已授权 {{ data?.authorizedCount ?? data?.count ?? 0 }}</span>
          <span class="tag">{{ readyServerCount }} 个就绪</span>
          <span class="tag">{{ issueCount }} 个待处理</span>
        </div>
      </div>
      <div class="page-hero-actions">
        <select v-model="selectedAgent" class="form-select agent-filter-select" @change="loadData">
          <option value="all">全部助手</option>
          <option v-for="item in agentOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <button class="btn btn-ghost btn-sm" :disabled="loading || !data" @click="copyOverview">复制概览</button>
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
        <div class="summary-subtitle">关闭客户端时，所有 MCP 工具都不会进入聊天链路。</div>
      </div>
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">助手视角</div>
        <div class="summary-value">{{ selectedAgentLabel }}</div>
        <div class="summary-subtitle">可以切换不同助手，检查它们各自能看到哪些 MCP 服务。</div>
      </div>
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">已授权服务</div>
        <div class="summary-value status-on">{{ data?.authorizedCount ?? data?.count ?? 0 }}</div>
        <div class="summary-subtitle">由后端权限规则决定当前助手可访问的 MCP 服务。</div>
      </div>
      <div class="card summary-card elevated-summary-card">
        <div class="summary-label">异常服务</div>
        <div :class="['summary-value', issueCount ? 'status-off' : 'status-on']">{{ issueCount }}</div>
        <div class="summary-subtitle">优先处理命令缺失、入口文件不存在或客户端停用等问题。</div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div>
          <div class="card-title">服务诊断列表</div>
          <div class="card-subtitle">按“就绪 / 异常 / 禁用”切换，点击操作按钮复制命令或排障摘要。</div>
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
        如果聊天页里缺少某个 MCP 工具，先看这里是不是“未授权”或“异常”，再排查脚本、命令路径和 Spring MCP 客户端配置。
      </div>

      <div v-if="data?.servers?.length" class="filter-summary">
        当前显示 {{ filteredServers.length }} / {{ data?.servers?.length || 0 }} 个服务
        <span v-if="serverFilter !== 'all'">，筛选条件：{{ activeFilterLabel }}</span>
      </div>

      <div v-if="issueCount" class="issue-banner">
        检测到 {{ issueCount }} 个待处理服务，优先处理入口文件不存在、命令缺失或客户端停用的问题。
        <div class="issue-banner-actions">
          <button class="table-action-btn" type="button" @click="serverFilter = 'issues'">只看异常</button>
          <button class="table-action-btn" type="button" @click="copyIssueSummary">复制异常清单</button>
        </div>
      </div>

      <SkeletonBlock v-if="loading" :count="3" :height="88" />

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
        title="当前没有可见 MCP 服务"
        description="请检查后端 MCP 配置文件、当前助手授权规则和客户端启用状态。"
        action-text="刷新配置"
        @action="loadData"
      />

      <table v-else class="compact-table">
        <thead>
          <tr>
            <th>服务</th>
            <th>状态</th>
            <th>命令</th>
            <th>入口文件</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!filteredServers.length">
            <td colspan="5" class="empty-cell">当前筛选条件下没有匹配的 MCP 服务。</td>
          </tr>
          <tr v-for="server in filteredServers" :key="server.code">
            <td>
              <div class="server-code">{{ server.code }}</div>
              <div class="subtle-text">{{ server.source }}</div>
              <div class="status-stack service-flags">
                <span :class="['status-chip', server.authorized === false ? 'status-off' : 'status-on']">
                  {{ server.authorized === false ? '未授权' : '已授权' }}
                </span>
                <span :class="['status-chip', server.enabled ? 'status-on' : 'status-off']">
                  {{ server.enabled ? '服务启用' : '服务禁用' }}
                </span>
              </div>
              <div v-if="server.issueReason" class="issue-reason">{{ formatIssueReason(server.issueReason) }}</div>
            </td>
            <td>
              <div class="status-stack">
                <span :class="['status-chip', statusClass(server)]">{{ statusText(server) }}</span>
                <span :class="['status-chip', server.clientEnabled ? 'status-on' : 'status-off']">
                  {{ server.clientEnabled ? '客户端启用' : '客户端停用' }}
                </span>
                <span :class="['status-chip', server.commandAvailable ? 'status-on' : 'status-off']">
                  {{ server.commandAvailable ? '命令可执行' : '命令缺失' }}
                </span>
              </div>
            </td>
            <td>
              <div class="command-block">
                <code>{{ server.commandLinePreview || buildCommandText(server) || '-' }}</code>
                <div class="subtle-text">主命令：{{ server.command || '-' }}</div>
                <div v-if="server.runtimeHint" class="subtle-text">{{ server.runtimeHint }}</div>
              </div>
            </td>
            <td>
              <div class="entry-block">
                <code>{{ server.entryFile || '无入口文件参数' }}</code>
                <div v-if="server.entryFile" :class="['entry-status', server.entryFileExists ? 'status-on' : 'status-off']">
                  {{ server.entryFileExists ? '文件存在' : '文件不存在' }}
                </div>
              </div>
            </td>
            <td>
              <div class="action-list">
                <button class="table-action-btn" type="button" @click="copyText(server.commandLinePreview || buildCommandText(server), '完整命令')">
                  复制命令
                </button>
                <button class="table-action-btn" type="button" @click="copyServerSummary(server)">
                  复制摘要
                </button>
                <button
                  class="table-action-btn"
                  type="button"
                  :disabled="!server.entryFile"
                  @click="copyText(server.entryFile || '', '入口文件路径')"
                >
                  复制路径
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getMcpServers, getMcpServersByAgent } from '@/api/agent'
import type { McpServerInfo, McpServerListResponse } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import { useToast } from '@/composables/useToast'
import { AGENT_CONFIG } from '@/utils/constants'

type ServerFilter = 'all' | 'issues' | 'ready' | 'disabled'

const loading = ref(false)
const error = ref('')
const data = ref<McpServerListResponse | null>(null)
const serverFilter = ref<ServerFilter>('all')
const selectedAgent = ref('all')
const { showToast } = useToast()

const filterOptions = [
  { label: '全部服务', value: 'all' },
  { label: '只看异常', value: 'issues' },
  { label: '只看就绪', value: 'ready' },
  { label: '只看禁用', value: 'disabled' }
] as const

const agentOptions = Object.entries(AGENT_CONFIG).map(([value, config]) => ({
  value,
  label: config.name
}))

const clientEnabled = computed(() => Boolean(data.value?.clientEnabled))
const selectedAgentLabel = computed(() => selectedAgent.value === 'all' ? '全部助手' : (AGENT_CONFIG[selectedAgent.value]?.name || selectedAgent.value))
const readyServerCount = computed(() => (data.value?.servers || []).filter((item) => item.diagnosticStatus === 'ready').length)
const issueCount = computed(() => data.value?.issueCount ?? (data.value?.servers || []).filter((item) => item.diagnosticStatus !== 'ready').length)
const activeFilterLabel = computed(() => filterOptions.find((item) => item.value === serverFilter.value)?.label || '全部服务')
const filteredServers = computed(() => {
  const servers = data.value?.servers || []
  const base =
    serverFilter.value === 'issues'
      ? servers.filter((item) => item.diagnosticStatus === 'issue')
      : serverFilter.value === 'ready'
        ? servers.filter((item) => item.diagnosticStatus === 'ready')
        : serverFilter.value === 'disabled'
          ? servers.filter((item) => item.diagnosticStatus === 'disabled')
          : servers

  return [...base].sort((left, right) => {
    const priority = (value: McpServerInfo) => value.diagnosticStatus === 'issue' ? 0 : value.diagnosticStatus === 'disabled' ? 1 : 2
    const priorityDiff = priority(left) - priority(right)
    if (priorityDiff !== 0) {
      return priorityDiff
    }
    return left.code.localeCompare(right.code)
  })
})

const issueServers = computed(() => (data.value?.servers || []).filter((item) => item.diagnosticStatus !== 'ready'))

function statusText(server: McpServerInfo) {
  if (server.diagnosticStatus === 'ready') {
    return '就绪'
  }
  if (server.diagnosticStatus === 'disabled') {
    return '已禁用'
  }
  return '异常'
}

function statusClass(server: McpServerInfo) {
  return server.diagnosticStatus === 'ready' ? 'status-on' : 'status-off'
}

function formatIssueReason(reason?: string) {
  const map: Record<string, string> = {
    'client disabled': 'MCP 客户端未启用',
    'server disabled': '该服务在配置中被禁用',
    'client disabled, server disabled': '客户端和服务端都处于禁用状态',
    'missing command': '缺少启动命令',
    'command not found': '启动命令在当前环境不可执行',
    'entry file not found': '入口文件不存在'
  }
  return reason ? (map[reason] || reason) : ''
}

function buildCommandText(server: McpServerInfo) {
  return [server.command, ...(server.args || [])]
    .filter(Boolean)
    .map((item) => item.includes(' ') ? `"${item}"` : item)
    .join(' ')
}

async function copyText(text: string, label: string) {
  if (!text) {
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    showToast(`已复制${label}`)
  } catch {
    showToast(`复制${label}失败`)
  }
}

async function copyIssueSummary() {
  const lines = issueServers.value.map((server) => {
    const parts = [
      server.code,
      statusText(server),
      server.authorized === false ? '未授权' : '已授权',
      formatIssueReason(server.issueReason),
      server.commandLinePreview || buildCommandText(server) || '-'
    ].filter(Boolean)
    return parts.join(' | ')
  })
  if (!lines.length) {
    showToast('当前没有异常服务')
    return
  }
  await copyText(lines.join('\n'), '异常清单')
}

async function copyServerSummary(server: McpServerInfo) {
  const summary = [
    `服务: ${server.code}`,
    `状态: ${statusText(server)}`,
    `授权: ${server.authorized === false ? '未授权' : '已授权'}`,
    `问题: ${formatIssueReason(server.issueReason) || '无'}`,
    `命令: ${server.commandLinePreview || buildCommandText(server) || '-'}`,
    `入口文件: ${server.entryFile || '-'}`,
    `提示: ${server.runtimeHint || '无'}`
  ].join('\n')
  await copyText(summary, '服务摘要')
}

async function copyOverview() {
  const lines = [
    'MCP 管理概览',
    `客户端状态：${clientEnabled.value ? '已启用' : '已停用'}`,
    `诊断助手：${selectedAgentLabel.value}`,
    `服务数量：${data.value?.count ?? 0}`,
    `已授权服务：${data.value?.authorizedCount ?? data.value?.count ?? 0}`,
    `就绪服务：${readyServerCount.value}`,
    `待处理项：${issueCount.value}`,
    `配置来源：${data.value?.source || 'classpath:mcp-servers.json'}`
  ]
  await copyText(lines.join('\n'), 'MCP 概览')
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    data.value = selectedAgent.value === 'all'
      ? await getMcpServers()
      : await getMcpServersByAgent(selectedAgent.value)
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
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
}

.summary-subtitle {
  margin-top: 8px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}

.agent-filter-select {
  min-width: 160px;
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

.issue-banner-actions,
.action-list,
.service-flags,
.status-stack {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.issue-banner-actions {
  margin-top: 10px;
}

.server-code {
  font-weight: 700;
  color: var(--text);
}

.subtle-text {
  margin-top: 4px;
  color: var(--text3);
  font-size: 12px;
  word-break: break-all;
}

.issue-reason {
  margin-top: 6px;
  color: #b45309;
  font-size: 12px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
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

.command-block,
.entry-block {
  display: grid;
  gap: 6px;
}

.entry-status {
  font-size: 12px;
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

.table-action-btn:hover:not(:disabled) {
  background: var(--surface2);
  color: var(--text);
  border-color: var(--border2);
}

.table-action-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .page-hero-actions {
    width: 100%;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .agent-filter-select {
    width: 100%;
  }
}
</style>
