import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ChatAgentDiagnosticsPanel from './ChatAgentDiagnosticsPanel.vue'
import { useChatStore } from '@/stores/chat'

const showToast = vi.fn()
const getAgentDiagnostics = vi.fn()
const getAgentAccessOverview = vi.fn()
const getMcpServersByAgent = vi.fn()
const getToolAuditLogs = vi.fn()
const getMultiAgentTraces = vi.fn()
const getMultiAgentTrace = vi.fn()
const recoverMultiAgentTrace = vi.fn()

vi.mock('@/api/agent', () => ({
  getAgentDiagnostics,
  getAgentAccessOverview,
  getMcpServersByAgent,
  getToolAuditLogs,
  getMultiAgentTraces,
  getMultiAgentTrace,
  recoverMultiAgentTrace
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

vi.mock('@/composables/useAgentMetadata', () => ({
  useAgentMetadata: () => ({
    loadAgentMetadata: vi.fn(),
    getAgentConfig: (agentType: string) => ({
      agentType,
      name: agentType === 'multi' ? '多智能体' : '研发助手'
    })
  })
}))

describe('ChatAgentDiagnosticsPanel', () => {
  enableAutoUnmount(afterEach)

  beforeEach(() => {
    setActivePinia(createPinia())
    const chatStore = useChatStore()
    chatStore.currentAgent = 'multi'
    chatStore.currentSessionId = 'session-1'
    chatStore.chatHistory = [
      { role: 'assistant', content: 'done', traceId: 'trace-chat-latest' }
    ] as never

    showToast.mockReset()
    getAgentDiagnostics.mockReset()
    getAgentAccessOverview.mockReset()
    getMcpServersByAgent.mockReset()
    getToolAuditLogs.mockReset()
    getMultiAgentTraces.mockReset()
    getMultiAgentTrace.mockReset()
    recoverMultiAgentTrace.mockReset()

    getAgentDiagnostics.mockResolvedValue({
      agentType: 'multi',
      accessible: true,
      toolSecurityEnabled: true,
      allowedTools: ['searchDocuments'],
      allowedConnectors: ['knowledge'],
      allowedMcpServers: ['knowledge-mcp'],
      enabledConnectors: ['knowledge'],
      recentMultiTraceCount: 1,
      availableMcpServerCount: 1,
      mcpIssueCount: 0,
      summary: 'stable'
    })
    getAgentAccessOverview.mockResolvedValue({
      agentType: 'multi',
      securityEnabled: true,
      tools: [{
        code: 'searchDocuments',
        name: 'searchDocuments',
        status: 'allowed',
        reason: 'allowed',
        reasonCode: 'TOOL_ALLOWED',
        reasonMessage: 'ok',
        detail: 'detail',
        resource: 'tool:searchDocuments'
      }],
      connectors: [],
      mcpServers: [],
      summary: 'summary'
    })
    getMcpServersByAgent.mockResolvedValue({
      servers: [{
        code: 'knowledge-mcp',
        command: 'node',
        args: [],
        enabled: true,
        clientEnabled: true,
        source: 'config',
        diagnosticStatus: 'ready',
        authorized: true,
        authorizedTools: ['searchDocuments']
      }]
    })
    getToolAuditLogs.mockImplementation(async (_limit: number, _agentType?: string, _toolName?: string, traceId?: string) => {
      if (traceId) {
        return [{
          id: 'audit-trace',
          toolName: 'searchDocuments',
          success: false,
          latencyMs: 120,
          traceId,
          reasonCode: 'MCP_DENIED',
          deniedResource: 'mcp:knowledge-mcp/searchDocuments',
          errorMessage: 'denied',
          createdAt: '2026-03-27 09:10:00'
        }]
      }
      return [{
        id: 'audit-recent',
        toolName: 'searchDocuments',
        success: true,
        latencyMs: 80,
        traceId: 'trace-1',
        createdAt: '2026-03-27 09:00:00'
      }]
    })
    getMultiAgentTraces.mockResolvedValue([{
      traceId: 'trace-1',
      sessionId: 'session-1',
      userId: 'u-1',
      agentType: 'multi',
      requestSummary: 'request',
      finalSummary: 'summary',
      status: 'FAILED',
      totalPromptTokens: 20,
      totalCompletionTokens: 10,
      totalLatencyMs: 300,
      stepCount: 3,
      recoveryAction: '',
      steps: []
    }])
    getMultiAgentTrace.mockResolvedValue({
      traceId: 'trace-1',
      sessionId: 'session-1',
      userId: 'u-1',
      agentType: 'multi',
      requestSummary: 'request',
      finalSummary: 'summary',
      status: 'FAILED',
      totalPromptTokens: 20,
      totalCompletionTokens: 10,
      totalLatencyMs: 300,
      stepCount: 3,
      steps: [{
        stepOrder: 1,
        stage: 'planner',
        agentName: 'Planner',
        inputSummary: 'input',
        outputSummary: 'output',
        promptTokens: 5,
        completionTokens: 3,
        latencyMs: 80,
        success: false,
        errorMessage: 'failed',
        recoverable: true,
        skipped: false
      }]
    })
    recoverMultiAgentTrace.mockResolvedValue({
      traceId: 'trace-recovered',
      sessionId: 'session-1',
      userId: 'u-1',
      agentType: 'multi',
      status: 'RECOVERED_SUCCESS',
      steps: []
    })

    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: vi.fn().mockResolvedValue(undefined)
      },
      configurable: true
    })
  })

  it('loads diagnostics and renders trace detail for multi agent', async () => {
    const wrapper = mount(ChatAgentDiagnosticsPanel, {
      props: {
        agentTypeOverride: 'multi',
        sessionIdOverride: 'session-1',
        focusedTraceId: 'trace-1'
      }
    })
    await flushPromises()

    expect(getAgentDiagnostics).toHaveBeenCalledWith('multi')
    expect(getMultiAgentTrace).toHaveBeenCalledWith('trace-1')
    expect(wrapper.findAll('.trace-item')).toHaveLength(1)
    expect(wrapper.findAll('.trace-step')).toHaveLength(1)
    expect(wrapper.findAll('.diagnostics-list-item')).not.toHaveLength(0)
  })

  it('recovers selected trace step', async () => {
    const wrapper = mount(ChatAgentDiagnosticsPanel, {
      props: {
        agentTypeOverride: 'multi',
        sessionIdOverride: 'session-1',
        focusedTraceId: 'trace-1'
      }
    })
    await flushPromises()

    await wrapper.find('.trace-step-actions .btn').trigger('click')
    await flushPromises()

    expect(recoverMultiAgentTrace).toHaveBeenCalledWith('trace-1', { stepOrder: 1, action: 'retry' })
    expect(showToast).toHaveBeenCalled()
    expect(getMultiAgentTrace).toHaveBeenCalledWith('trace-recovered')
  })
})
