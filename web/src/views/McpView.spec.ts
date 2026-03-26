import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import McpView from './McpView.vue'
import { getMcpServers } from '@/api/agent'

vi.mock('@/api/agent', () => ({
  getMcpServers: vi.fn()
}))

describe('McpView', () => {
  beforeEach(() => {
    vi.mocked(getMcpServers).mockReset()
    Object.defineProperty(window.navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true
    })
  })

  it('loads and renders MCP service summary', async () => {
    vi.mocked(getMcpServers).mockResolvedValue({
      clientEnabled: true,
      count: 1,
      source: 'classpath:mcp-servers.json',
      servers: [
        {
          code: 'fast-context',
          source: 'mcp-servers.json',
          command: 'node',
          args: ['server.js'],
          enabled: true,
          clientEnabled: true
        }
      ]
    })

    const wrapper = mount(McpView, {
      global: {
        stubs: {
          EmptyState: { template: '<div class="empty-state-stub"><slot /></div>' },
          SkeletonBlock: { template: '<div class="skeleton-stub" />' }
        }
      }
    })

    await Promise.resolve()
    await Promise.resolve()

    expect(wrapper.text()).toContain('MCP 管理')
    expect(wrapper.text()).toContain('已启用')
    expect(wrapper.text()).toContain('fast-context')
    expect(wrapper.text()).toContain('node')
  })

  it('copies issue summary for problematic services', async () => {
    vi.mocked(getMcpServers).mockResolvedValue({
      clientEnabled: true,
      count: 1,
      source: 'classpath:mcp-servers.json',
      servers: [
        {
          code: 'fast-context',
          source: 'mcp-servers.json',
          command: 'node',
          args: ['server.js'],
          enabled: false,
          clientEnabled: true
        }
      ]
    })

    const wrapper = mount(McpView, {
      global: {
        stubs: {
          EmptyState: { template: '<div class="empty-state-stub"><slot /></div>' },
          SkeletonBlock: { template: '<div class="skeleton-stub" />' }
        }
      }
    })

    await Promise.resolve()
    await Promise.resolve()

    const copyButton = wrapper.findAll('button').find((item) => item.text() === '复制异常清单')
    await copyButton?.trigger('click')

    expect(window.navigator.clipboard.writeText).toHaveBeenCalled()
    expect(vi.mocked(window.navigator.clipboard.writeText).mock.calls[0]?.[0]).toContain('fast-context')
    expect(wrapper.text()).toContain('服务端未启用')
  })

  it('sorts issue servers ahead of healthy servers', async () => {
    vi.mocked(getMcpServers).mockResolvedValue({
      clientEnabled: true,
      count: 2,
      source: 'classpath:mcp-servers.json',
      servers: [
        {
          code: 'z-healthy',
          source: 'mcp-servers.json',
          command: 'node',
          args: ['healthy.js'],
          enabled: true,
          clientEnabled: true
        },
        {
          code: 'a-issue',
          source: 'mcp-servers.json',
          command: 'node',
          args: ['issue.js'],
          enabled: false,
          clientEnabled: true
        }
      ]
    })

    const wrapper = mount(McpView, {
      global: {
        stubs: {
          EmptyState: { template: '<div class="empty-state-stub"><slot /></div>' },
          SkeletonBlock: { template: '<div class="skeleton-stub" />' }
        }
      }
    })

    await Promise.resolve()
    await Promise.resolve()

    const rows = wrapper.findAll('tbody tr')
    expect(rows[0]?.text()).toContain('a-issue')
    expect(rows[1]?.text()).toContain('z-healthy')
  })
})
