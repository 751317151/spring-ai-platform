import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import McpView from './McpView.vue'
import { getMcpServers, getMcpServersByAgent } from '@/api/agent'

vi.mock('@/api/agent', () => ({
  getMcpServers: vi.fn(),
  getMcpServersByAgent: vi.fn()
}))

describe('McpView', () => {
  beforeEach(() => {
    vi.mocked(getMcpServers).mockReset()
    vi.mocked(getMcpServersByAgent).mockReset()

    Object.defineProperty(window.navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true
    })
  })

  function mountView() {
    return mount(McpView, {
      global: {
        stubs: {
          EmptyState: { template: '<div class="empty-state-stub"><slot /></div>' },
          SkeletonBlock: { template: '<div class="skeleton-stub" />' }
        }
      }
    })
  }

  it('loads and renders MCP diagnostics summary', async () => {
    vi.mocked(getMcpServers).mockResolvedValue({
      clientEnabled: true,
      count: 1,
      authorizedCount: 1,
      issueCount: 0,
      source: 'classpath:mcp-servers.json',
      servers: [
        {
          code: 'gemini',
          source: 'classpath:mcp-servers.json',
          command: 'node',
          args: ['F:\\Java\\gemini-skill\\src\\mcp-server.js'],
          enabled: true,
          clientEnabled: true,
          entryFile: 'F:\\Java\\gemini-skill\\src\\mcp-server.js',
          entryFileExists: true,
          commandAvailable: true,
          diagnosticStatus: 'ready',
          issueReason: '',
          commandLinePreview: 'node F:\\Java\\gemini-skill\\src\\mcp-server.js',
          runtimeHint: '命令与入口文件检查通过，可以继续排查脚本内部异常或 MCP 客户端日志。',
          authorized: true
        }
      ]
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('gemini')
    expect(wrapper.text()).toContain('MCP 管理')
    expect(wrapper.text()).toContain('已授权')
    expect(wrapper.text()).toContain('命令与入口文件检查通过')
  })

  it('copies issue summary for problematic services', async () => {
    vi.mocked(getMcpServers).mockResolvedValue({
      clientEnabled: true,
      count: 1,
      authorizedCount: 0,
      issueCount: 1,
      source: 'classpath:mcp-servers.json',
      servers: [
        {
          code: 'gemini',
          source: 'classpath:mcp-servers.json',
          command: 'node',
          args: ['F:\\Java\\missing\\mcp-server.js'],
          enabled: true,
          clientEnabled: true,
          entryFile: 'F:\\Java\\missing\\mcp-server.js',
          entryFileExists: false,
          commandAvailable: false,
          diagnosticStatus: 'issue',
          issueReason: 'command not found',
          commandLinePreview: 'node F:\\Java\\missing\\mcp-server.js',
          runtimeHint: '启动命令在当前机器上不可执行，请检查 PATH 或改成绝对路径。',
          authorized: false
        }
      ]
    })

    const wrapper = mountView()
    await flushPromises()

    const copyButton = wrapper.findAll('button').find((item) => item.text().includes('异常清单'))
    await copyButton?.trigger('click')

    expect(window.navigator.clipboard.writeText).toHaveBeenCalled()
    expect(String(vi.mocked(window.navigator.clipboard.writeText).mock.calls[0]?.[0])).toContain('gemini')
    expect(wrapper.text()).toContain('启动命令在当前机器上不可执行')
  })

  it('sorts issue servers ahead of healthy servers', async () => {
    vi.mocked(getMcpServers).mockResolvedValue({
      clientEnabled: true,
      count: 2,
      authorizedCount: 2,
      issueCount: 1,
      source: 'classpath:mcp-servers.json',
      servers: [
        {
          code: 'z-healthy',
          source: 'classpath:mcp-servers.json',
          command: 'node',
          args: ['healthy.js'],
          enabled: true,
          clientEnabled: true,
          entryFile: 'healthy.js',
          entryFileExists: true,
          commandAvailable: true,
          diagnosticStatus: 'ready',
          issueReason: '',
          commandLinePreview: 'node healthy.js',
          runtimeHint: '命令与入口文件检查通过。',
          authorized: true
        },
        {
          code: 'a-issue',
          source: 'classpath:mcp-servers.json',
          command: 'node',
          args: ['missing.js'],
          enabled: true,
          clientEnabled: true,
          entryFile: 'missing.js',
          entryFileExists: false,
          commandAvailable: false,
          diagnosticStatus: 'issue',
          issueReason: 'command not found',
          commandLinePreview: 'node missing.js',
          runtimeHint: '启动命令在当前机器上不可执行。',
          authorized: true
        }
      ]
    })

    const wrapper = mountView()
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows[0]?.text()).toContain('a-issue')
    expect(rows[1]?.text()).toContain('z-healthy')
  })

  it('switches agent perspective and loads filtered MCP servers', async () => {
    vi.mocked(getMcpServers).mockResolvedValue({
      clientEnabled: true,
      count: 1,
      authorizedCount: 1,
      issueCount: 0,
      source: 'classpath:mcp-servers.json',
      servers: [
        {
          code: 'gemini',
          source: 'classpath:mcp-servers.json',
          command: 'node',
          args: [],
          enabled: true,
          clientEnabled: true,
          diagnosticStatus: 'ready',
          commandAvailable: true,
          authorized: true
        }
      ]
    } as never)

    vi.mocked(getMcpServersByAgent).mockResolvedValue({
      clientEnabled: true,
      agentType: 'multi',
      count: 2,
      authorizedCount: 1,
      issueCount: 1,
      source: 'classpath:mcp-servers.json',
      servers: [
        {
          code: 'gemini',
          source: 'classpath:mcp-servers.json',
          command: 'node',
          args: [],
          enabled: true,
          clientEnabled: true,
          diagnosticStatus: 'ready',
          commandAvailable: true,
          authorized: true
        },
        {
          code: 'private-mcp',
          source: 'classpath:mcp-servers.json',
          command: 'node',
          args: [],
          enabled: true,
          clientEnabled: true,
          diagnosticStatus: 'issue',
          commandAvailable: true,
          authorized: false
        }
      ]
    } as never)

    const wrapper = mountView()
    await flushPromises()

    const select = wrapper.get('select')
    await select.setValue('multi')
    await select.trigger('change')
    await flushPromises()

    expect(getMcpServersByAgent).toHaveBeenCalledWith('multi')
    expect(wrapper.text()).toContain('private-mcp')
    expect(wrapper.text()).toContain('未授权')
  })
})
