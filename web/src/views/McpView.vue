<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">MCP 管理</div>
        <div class="page-subtitle">
          当前页面用于展示 agent-service 已加载的 MCP servers 配置，不支持在线编辑。
        </div>
      </div>
      <button class="btn btn-primary btn-sm" :disabled="loading" @click="loadData">
        {{ loading ? '刷新中...' : '刷新配置' }}
      </button>
    </div>

    <div class="summary-grid">
      <div class="card summary-card">
        <div class="summary-label">MCP Client</div>
        <div :class="['summary-value', clientEnabled ? 'status-on' : 'status-off']">
          {{ clientEnabled ? '已启用' : '未启用' }}
        </div>
      </div>
      <div class="card summary-card">
        <div class="summary-label">Server 数量</div>
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
          <div class="card-title">Server 列表</div>
          <div class="card-subtitle">用于核对当前 MCP server 命令、参数和启用状态。</div>
        </div>
      </div>

      <table class="compact-table">
        <thead>
          <tr>
            <th>编码</th>
            <th>命令</th>
            <th>参数</th>
            <th>Server 状态</th>
            <th>Client 状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="5" class="empty-cell">正在加载 MCP 配置...</td>
          </tr>
          <tr v-else-if="error">
            <td colspan="5" class="empty-cell error-text">{{ error }}</td>
          </tr>
          <tr v-else-if="!data?.servers?.length">
            <td colspan="5" class="empty-cell">当前没有可展示的 MCP server 配置</td>
          </tr>
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
                {{ server.enabled ? '启用' : '禁用' }}
              </span>
            </td>
            <td>
              <span :class="['status-chip', server.clientEnabled ? 'status-on' : 'status-off']">
                {{ server.clientEnabled ? '启用' : '禁用' }}
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
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.page-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text3);
  line-height: 1.6;
}

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

.error-text {
  color: #b91c1c;
}

@media (max-width: 960px) {
  .page-header {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
