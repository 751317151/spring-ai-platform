import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, reactive, ref } from 'vue'
import AgentWorkbenchView from './AgentWorkbenchView.vue'

const replace = vi.fn().mockResolvedValue(undefined)
const push = vi.fn().mockResolvedValue(undefined)
const showToast = vi.fn()
const getAgentWorkbenchSummary = vi.fn()
const compareAgentWorkbench = vi.fn()
const getAgentLogLifecycleSummary = vi.fn()
const getLatestAgentLogArchive = vi.fn()
const previewLatestAgentLogArchive = vi.fn()
const findLatestArchivedTrace = vi.fn()
const replayLatestArchivedTrace = vi.fn()
const cleanupAgentLogs = vi.fn()

const route = reactive({
  query: {
    agent: 'rd'
  } as Record<string, string>,
  name: 'agents'
})

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => route,
    useRouter: () => ({
      replace,
      push
    })
  }
})

vi.mock('@/api/agent', () => ({
  getAgentWorkbenchSummary,
  compareAgentWorkbench,
  getAgentLogLifecycleSummary,
  getLatestAgentLogArchive,
  previewLatestAgentLogArchive,
  findLatestArchivedTrace,
  replayLatestArchivedTrace,
  cleanupAgentLogs
}))

vi.mock('@/components/chat/ChatAgentDiagnosticsPanel.vue', () => ({
  default: defineComponent({
    name: 'ChatAgentDiagnosticsPanelStub',
    props: {
      agentTypeOverride: {
        type: String,
        default: ''
      }
    },
    setup(props) {
      return () =>
        h('div', {
          class: 'chat-agent-diagnostics-stub',
          'data-agent': props.agentTypeOverride
        })
    }
  })
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

vi.mock('@/composables/useAgentMetadata', () => {
  const agentList = ref([
    { agentType: 'rd', name: 'RD Assistant', icon: 'R', color: '#2563eb', desc: 'debug flow' },
    { agentType: 'multi', name: 'Multi Agent', icon: 'M', color: '#0f766e', desc: 'multi step flow' },
    { agentType: 'ops', name: 'Ops Assistant', icon: 'O', color: '#b45309', desc: 'ops governance' }
  ])
  return {
    useAgentMetadata: () => ({
      agentList,
      loadAgentMetadata: vi.fn(),
      getAgentConfig: (agentType: string) =>
        agentList.value.find((item) => item.agentType === agentType) || agentList.value[0]
    })
  }
})

function buildSummary(agentType: string) {
  return {
    agentType,
    windowLabel: 'last 24h',
    totalCalls: agentType === 'rd' ? 8 : 12,
    failureCalls: agentType === 'rd' ? 1 : 3,
    successRate: agentType === 'rd' ? 0.875 : 0.75,
    avgLatencyMs: agentType === 'rd' ? 120 : 180,
    toolCallCount: 10,
    toolFailureCount: 2,
    avgToolLatencyMs: 80,
    slowestToolName: agentType === 'rd' ? 'searchDocuments' : 'plannerDispatch',
    slowestToolLatencyMs: 260,
    recentTraceCount: 2,
    latestTraceId: 'trace-latest',
    latestErrorMessage: 'timeout',
    runtimePolicySummary: {
      securityEnabled: true,
      connectorResourceIsolationEnabled: true,
      mcpToolIsolationEnabled: true,
      dataScopeIsolationEnabled: true,
      restrictedResourceCount: 3,
      riskCount: 1,
      riskLevel: 'medium',
      summary: 'policy-summary'
    },
    healthSummary: {
      accessible: true,
      failureSpike: agentType === 'multi',
      toolFailureSpike: false,
      warning: agentType === 'multi',
      summary: agentType === 'multi' ? 'failure spike' : 'stable'
    },
    last24hTrend: [{ label: '09:00', totalCalls: 3, failureCalls: 1, toolCalls: 2, avgLatencyMs: 100 }],
    last7dTrend: [{ label: '03-27', totalCalls: 8, failureCalls: 1, toolCalls: 6, avgLatencyMs: 110 }],
    last4wTrend: [{ label: 'W1', totalCalls: 20, failureCalls: 2, toolCalls: 12, avgLatencyMs: 115 }],
    toolRanking: [{ toolName: 'searchDocuments', callCount: 6, failureCount: 1, avgLatencyMs: 70, latestTraceId: 'trace-tool' }],
    errorTypes: [{ type: 'PERMISSION_DENIED', label: 'Permission denied', count: 2 }],
    recentChanges: [{
      type: 'failure',
      label: 'Failure rate up',
      direction: 'up',
      severity: 'high',
      summary: 'Compared with last week'
    }],
    weeklyDigest: 'weekly digest',
    recentFailures: [{
      traceId: 'trace-failure',
      sessionId: 'session-1',
      userId: 'u-1',
      summary: 'request',
      errorMessage: 'boom',
      latencyMs: 190,
      createdAt: '2026-03-27 09:00:00'
    }]
  }
}

describe('AgentWorkbenchView', () => {
  enableAutoUnmount(afterEach)

  beforeEach(() => {
    route.query = { agent: 'rd' }
    replace.mockReset()
    push.mockReset()
    showToast.mockReset()
    getAgentWorkbenchSummary.mockReset()
    compareAgentWorkbench.mockReset()
    getAgentLogLifecycleSummary.mockReset()
    getLatestAgentLogArchive.mockReset()
    previewLatestAgentLogArchive.mockReset()
    findLatestArchivedTrace.mockReset()
    replayLatestArchivedTrace.mockReset()
    cleanupAgentLogs.mockReset()

    getAgentWorkbenchSummary.mockImplementation(async (agentType: string) => buildSummary(agentType))
    compareAgentWorkbench.mockImplementation(async (left: string, right: string) => ({
      left: buildSummary(left),
      right: buildSummary(right),
      summary: `${left} vs ${right}`,
      metrics: [
        {
          key: 'failure-rate',
          label: 'Failure rate',
          leftValue: '13%',
          rightValue: '25%',
          delta: '-12%',
          trend: 'left-better',
          winnerAgentType: left,
          summary: 'Lower failure rate means the agent is currently more stable'
        }
      ],
      insights: [
        {
          type: 'failure-rate',
          severity: 'high',
          winnerAgentType: right,
          loserAgentType: left,
          metricKey: 'failure-rate',
          title: 'Failure rate gap',
          summary: 'The worse side is higher by 18% failure rate',
          leftEvidence: '13%',
          rightEvidence: '31%',
          whyItMatters: 'Failure rate directly affects user-visible stability',
          suggestedAction: 'Inspect recent failure samples first'
        }
      ],
      leftDetail: {
        agentType: left,
        summary: 'left summary',
        healthSummary: 'left health',
        policySummary: 'left policy',
        totalCalls: 8,
        failureRateLabel: '13%',
        riskLevel: 'medium',
        topErrorTypes: ['Permission denied=2'],
        highlights: ['left-highlight']
      },
      rightDetail: {
        agentType: right,
        summary: 'right summary',
        healthSummary: 'right health',
        policySummary: 'right policy',
        totalCalls: 12,
        failureRateLabel: '25%',
        riskLevel: 'low',
        topErrorTypes: ['Dependency error=1'],
        highlights: ['right-highlight']
      },
      changeComparison: [
        {
          type: 'failure',
          label: 'Failure rate up',
          leftSummary: 'left failure change',
          rightSummary: 'right failure change',
          direction: 'diverged',
          severity: 'high',
          suggestedAction: 'inspect failure'
        },
        {
          type: 'latency',
          label: 'Latency steady',
          leftSummary: 'left latency stable',
          rightSummary: 'right latency stable',
          direction: 'flat',
          severity: 'low',
          suggestedAction: 'no action'
        }
      ]
    }))
    getAgentLogLifecycleSummary.mockResolvedValue({
      agentType: 'rd',
      totalActiveCount: 8,
      totalArchiveCandidateCount: 3,
      totalDeleteCandidateCount: 1,
      automationEnabled: true,
      automationDryRun: true,
      automationIntervalMs: 3600000,
      summary: 'active=8, archiveCandidates=3, deleteCandidates=1',
      buckets: [
        { type: 'audit', archiveAfterDays: 14, deleteAfterDays: 60, activeCount: 5, archiveCandidateCount: 2, deleteCandidateCount: 1 },
        { type: 'tool-audit', archiveAfterDays: 14, deleteAfterDays: 45, activeCount: 2, archiveCandidateCount: 1, deleteCandidateCount: 0 }
      ]
    })
    getLatestAgentLogArchive.mockResolvedValue({
      agentType: 'rd',
      enabled: true,
      manifestDir: 'data/agent-lifecycle-archive',
      bundleDir: 'data/agent-lifecycle-archive/rd-20260327220000',
      manifestPath: 'data/agent-lifecycle-archive/rd-20260327220000/manifest.json',
      generatedAt: '2026-03-27T22:00:00',
      dryRun: false,
      exportedRecordCount: 4,
      sampleLimit: 5,
      exportBatchSize: 200,
      operationHints: ['lookup', 'replay'],
      artifacts: [
        { type: 'audit', path: 'data/agent-lifecycle-archive/rd-20260327220000/audit.jsonl', recordCount: 2 }
      ],
      samples: [
        { type: 'audit', id: 'audit-1', traceId: 'trace-a1', summary: 'old request', createdAt: '2026-03-27T21:00:00' }
      ]
    })
    previewLatestAgentLogArchive.mockResolvedValue({
      agentType: 'rd',
      artifactType: 'audit',
      bundleDir: 'data/agent-lifecycle-archive/rd-20260327220000',
      artifactPath: 'data/agent-lifecycle-archive/rd-20260327220000/audit.jsonl',
      previewLimit: 5,
      items: [
        { lineNumber: 1, content: '{"id":"audit-1"}' }
      ]
    })
    findLatestArchivedTrace.mockResolvedValue({
      agentType: 'rd',
      found: true,
      artifactType: 'trace',
      artifactPath: 'data/agent-lifecycle-archive/rd-20260327220000/trace.jsonl',
      traceId: 'trace-latest',
      archivedAt: '2026-03-27T22:00:00',
      summary: 'archived multi-agent trace',
      replayable: true,
      trace: {
        traceId: 'trace-latest',
        sessionId: 'session-1',
        userId: 'u-1',
        agentType: 'multi',
        status: 'RECOVERED_SUCCESS',
        createdAt: '2026-03-27T20:00:00',
        steps: []
      }
    })
    replayLatestArchivedTrace.mockResolvedValue({
      traceId: 'trace-replayed',
      sessionId: 'session-replayed',
      userId: 'u-1',
      agentType: 'multi',
      requestSummary: 'archived multi-agent trace',
      finalSummary: 'final',
      status: 'SUCCESS',
      recoverySourceTraceId: 'trace-latest',
      recoveryAction: 'archive-replay',
      steps: []
    })
    cleanupAgentLogs.mockResolvedValue({
      agentType: 'rd',
      dryRun: true,
      deletedAuditLogs: 1,
      deletedToolAuditLogs: 2,
      deletedTraceSteps: 3,
      deletedTraces: 1,
      summary: 'dryRun, auditLogs=1, toolAuditLogs=2, traceSteps=3, traces=1'
    })

    Object.defineProperty(window, 'location', {
      value: new URL('http://localhost/agents'),
      configurable: true
    })
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: vi.fn().mockResolvedValue(undefined)
      },
      configurable: true
    })
  })

  it('switches agent and reloads summary', async () => {
    const wrapper = mount(AgentWorkbenchView)
    await flushPromises()

    const select = wrapper.get('select.agent-select')
    await select.setValue('multi')
    await flushPromises()

    expect(getAgentWorkbenchSummary).toHaveBeenCalledWith('multi')
    expect(replace).toHaveBeenCalledWith({ name: 'agents', query: { agent: 'multi' } })
    expect(wrapper.get('.chat-agent-diagnostics-stub').attributes('data-agent')).toBe('multi')
  })

  it('navigates to chat and monitor with trace context', async () => {
    const wrapper = mount(AgentWorkbenchView)
    await flushPromises()

    await wrapper.findAll('.failure-actions .btn')[1]!.trigger('click')
    await wrapper.findAll('.failure-actions .btn')[2]!.trigger('click')

    expect(push).toHaveBeenCalledWith({
      name: 'chat',
      query: {
        agent: 'rd',
        source: 'agent-workbench',
        traceId: 'trace-failure',
        sessionId: 'session-1'
      }
    })
    expect(push).toHaveBeenCalledWith({
      name: 'monitor',
      query: {
        agent: 'rd',
        source: 'agent-workbench',
        traceId: 'trace-failure'
      }
    })
  })

  it('copies current workbench link', async () => {
    const wrapper = mount(AgentWorkbenchView)
    await flushPromises()

    await wrapper.findAll('.page-hero-actions .btn')[2]!.trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('http://localhost/agents?agent=rd')
    expect(showToast).toHaveBeenCalled()
  })

  it('loads compare data when switching compare agent', async () => {
    const wrapper = mount(AgentWorkbenchView)
    await flushPromises()

    const compareSelect = wrapper.get('select.compare-select')
    await compareSelect.setValue('ops')
    await flushPromises()

    expect(compareAgentWorkbench).toHaveBeenCalledWith('rd', 'ops')
    expect(wrapper.text()).toContain('Agent')
    expect(wrapper.text()).toContain('Difference hints')
    expect(wrapper.text()).toContain('Metric deltas')
    expect(wrapper.text()).toContain('Failure rate gap')
    expect(wrapper.text()).toContain('Archive manifest')
  })

  it('opens compare detail panel', async () => {
    const wrapper = mount(AgentWorkbenchView)
    await flushPromises()

    const toggleButton = wrapper.findAll('button').find((item) => item.text().includes('Open details'))
    expect(toggleButton).toBeTruthy()
    await toggleButton!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Compare details')
    expect(wrapper.text()).toContain('Inspect recent failure samples first')
    expect(wrapper.text()).toContain('Calls 8 | Failure 13% | Risk medium')
    expect(wrapper.text()).toContain('Permission denied=2')
  })

  it('filters compare changes', async () => {
    const wrapper = mount(AgentWorkbenchView)
    await flushPromises()

    const compareSelect = wrapper.get('select.compare-select')
    await compareSelect.setValue('ops')
    await flushPromises()

    const filterSelect = wrapper.findAll('select').find((item) => item.classes().includes('compare-filter-select'))
    expect(filterSelect).toBeTruthy()
    await filterSelect!.setValue('high')
    await flushPromises()

    expect(wrapper.text()).toContain('Failure rate up')
    expect(wrapper.text()).not.toContain('Latency steady')
  })

  it('loads archive preview for artifact', async () => {
    const wrapper = mount(AgentWorkbenchView)
    await flushPromises()

    const previewButton = wrapper.findAll('button').find((item) => item.text().includes('Preview'))
    expect(previewButton).toBeTruthy()
    await previewButton!.trigger('click')
    await flushPromises()

    expect(previewLatestAgentLogArchive).toHaveBeenCalledWith('rd', 'audit', 5)
    expect(wrapper.text()).toContain('Archive preview')
    expect(wrapper.text()).toContain('Line 1')
    expect(wrapper.text()).toContain('Archive operations')
  })

  it('looks up archived trace', async () => {
    const wrapper = mount(AgentWorkbenchView)
    await flushPromises()

    const lookupButton = wrapper.findAll('button').find((item) => item.text().includes('Find archived trace'))
    expect(lookupButton).toBeTruthy()
    await lookupButton!.trigger('click')
    await flushPromises()

    expect(findLatestArchivedTrace).toHaveBeenCalledWith('rd', 'trace-latest')
    expect(wrapper.text()).toContain('Archived trace lookup')
    expect(wrapper.text()).toContain('Replay source')
  })

  it('replays archived trace', async () => {
    const wrapper = mount(AgentWorkbenchView)
    await flushPromises()

    const lookupButton = wrapper.findAll('button').find((item) => item.text().includes('Find archived trace'))
    expect(lookupButton).toBeTruthy()
    await lookupButton!.trigger('click')
    await flushPromises()

    const replayButton = wrapper.findAll('button').find((item) => item.text().includes('Replay archived trace'))
    expect(replayButton).toBeTruthy()
    await replayButton!.trigger('click')
    await flushPromises()

    expect(replayLatestArchivedTrace).toHaveBeenCalledWith('rd', 'trace-latest')
    expect(showToast).toHaveBeenCalled()
  })

  it('previews lifecycle cleanup', async () => {
    const wrapper = mount(AgentWorkbenchView)
    await flushPromises()

    const previewButton = wrapper.findAll('button').find((item) => item.text().includes('预览') || item.text().includes('棰勮'))
    expect(previewButton).toBeTruthy()
    await previewButton!.trigger('click')

    expect(cleanupAgentLogs).toHaveBeenCalledWith('rd', { dryRun: true })
    expect(showToast).toHaveBeenCalled()
  })
})
