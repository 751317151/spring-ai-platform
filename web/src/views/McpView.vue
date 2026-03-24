<template>
  <div class="space-admin">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">基础设施</div>
        <div class="page-title">MCP 管理</div>
        <div class="page-subtitle">
          查看 `agent-service` 加载的 MCP 服务，检查启动命令与参数，并确认运行时启用状态。
        </div>
        <div class="hero-tags">
          <span class="tag">MCP 客户端 {{ clientEnabled ? '开启' : '关闭' }}</span>
          <span class="tag">{{ data?.count ?? 0 }} 个服务</span>
          <span class="tag">{{ data?.source || 'classpath:mcp-servers.json' }}</span>
        </div>
      </div>
      <div class="page-hero-actions">
        <button class="btn btn-primary btn-sm" :disabled="loading" @click="loadData">
          {{ loading ? '刷新中...' : '刷新配置' }}
        </button>
      </div>
    </div>

    <div class="summary-grid">
      <div class="card summary-card">
        <div class="summary-label">MCP 客户端</div>
        <div :class="['summary-value', clientEnabled ? 'status-on' : 'status-off']">
          {{ clientEnabled ? '已启用' : '已停用' }}
        </div>
      </div>
      <div class="card summary-card">
        <div class="summary-label">服务数量</div>
        <div class="summary-value">{{ data?.count ?? 0 }}</div>
      </div>
      <div class="card summary-card">
        <div class="summary-label">配置来源</div>
        <div class="summary-value source-text">{{ data?.source || 'classpath:mcp-servers.json' }}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div>
          <div class="card-title">服务列表</div>
          <div class="card-subtitle">检查启动命令、参数，以及服务端和客户端两侧的启用状态。</div>
        </div>
      </div>

      <div v-if="data?.servers?.length" class="mcp-summary-note">
        可将此表作为运行时核查面板。如果聊天中缺少某个工具，先确认这里的 MCP 客户端已启用，同时对应服务也处于启用状态。
      </div>

      <div v-if="loading" class="mcp-loading-list">
        <div v-for="idx in 3" :key="idx" class="mcp-loading-item skeleton"></div>
      </div>

      <EmptyState
        v-else-if="!!error"
        icon="!"
        title="MCP 配置加载失败"
        :description="error"
        action-text="重试"
        @action="loadData"
      />

      <EmptyState
        v-else-if="!data?.servers?.length"
        icon="M"
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
          <tr v-for="server in data?.servers || []" :key="server.code">
            <td>
              <div class="server-code">{{ server.code }}</div>
              <div class="subtle-text">{{ server.source }}</div>
            </td>
            <td><code>{{ server.command || '-' }}</code></td>
            <td>
              <div v-if="server.args?.length" class="args-list">
                <code v-for="arg in server.args" :key="arg" class="arg-chip">{{ arg }}</code>
              </div>
              <span v-else class="subtle-text">无参数</span>
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
import type { McpServerListResponse } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'

const loading = ref(false)
const error = ref('')
const data = ref<McpServerListResponse | null>(null)

const clientEnabled = computed(() => Boolean(data.value?.clientEnabled))

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    data.value = await getMcpServers()
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.server-code {
  font-weight: 600;
  color: var(--text);
}

.args-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mcp-loading-list {
  display: grid;
  gap: 10px;
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

.mcp-loading-item {
  height: 64px;
  border-radius: 12px;
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

@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
