import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import UserView from './UserView.vue'
import { useUserStore } from '@/stores/user'

const confirm = vi.fn()
const showToast = vi.fn()
const { getTokenUsage, getAuditLogs } = vi.hoisted(() => ({
  getTokenUsage: vi.fn(),
  getAuditLogs: vi.fn()
}))

vi.mock('@/api/monitor', () => ({
  getTokenUsage,
  getAuditLogs
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm
  })
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

vi.mock('@/components/user/UserModal.vue', () => ({
  default: {
    name: 'UserModal',
    template: '<div class="user-modal-stub" />',
    props: ['userId']
  }
}))

vi.mock('@/components/user/PermissionModal.vue', () => ({
  default: {
    name: 'PermissionModal',
    template: '<div class="permission-modal-stub" />',
    props: ['permissionId']
  }
}))

describe('UserView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    confirm.mockReset()
    confirm.mockResolvedValue(true)
    showToast.mockReset()
    getTokenUsage.mockReset()
    getAuditLogs.mockReset()
    Object.defineProperty(window.navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true
    })
  })

  it('filters users and handles batch delete', async () => {
    const store = useUserStore()
    store.users = [
      { userId: 'u-1', username: 'alice', employeeId: 'E001', department: '研发部', roles: 'ROLE_ADMIN', enabled: true, lastLoginAt: '2026-03-25T08:00:00Z' },
      { userId: 'u-2', username: 'bob', employeeId: 'E002', department: '销售部', roles: 'ROLE_USER', enabled: false, lastLoginAt: '2026-03-25T07:00:00Z' }
    ] as never
    store.permissions = [
      { id: 'p-1', botType: 'assistant', allowedRoles: 'ROLE_ADMIN', allowedDepartments: '研发部', dataScope: 'ALL', allowedOperations: 'chat', dailyTokenLimit: 1000, enabled: true },
      { id: 'p-2', botType: 'sales', allowedRoles: 'ROLE_USER', allowedDepartments: '销售部', dataScope: 'SELF', allowedOperations: 'query', dailyTokenLimit: 500, enabled: true }
    ] as never
    store.loadAll = vi.fn().mockResolvedValue(undefined) as never
    store.deleteUser = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(UserView)

    expect(wrapper.text()).toContain('用户与权限')
    expect(wrapper.text()).toContain('助手权限规则')

    await wrapper.get('input[placeholder="搜索用户ID、用户名、工号、部门或角色"]').setValue('alice')
    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).not.toContain('bob')

    await wrapper.get('input[placeholder="搜索用户ID、用户名、工号、部门或角色"]').setValue('')
    const rowCheckbox = wrapper.get('tbody .check-col input')
    await rowCheckbox.setValue(true)

    expect(wrapper.text()).toContain('已选择 1 个用户')

    const bulkDelete = wrapper.findAll('.bulk-actions .btn').find((item) => item.text().includes('批量删除'))
    await bulkDelete!.trigger('click')

    expect(confirm).toHaveBeenCalled()
    expect(store.deleteUser).toHaveBeenCalledWith('u-1')
    expect(showToast).toHaveBeenCalledWith('已删除 1 个用户')

    const riskyFilter = wrapper.findAll('button').find((item) => item.text() === '仅看高风险')
    await riskyFilter?.trigger('click')
    expect(wrapper.text()).toContain('当前仅展示高风险规则')

    const riskShortcut = wrapper.findAll('.risk-item').find((item) => item.text().includes('ROLE_ADMIN'))
    await riskShortcut?.trigger('click')
    expect((wrapper.get('input[placeholder="搜索助手、角色、部门或操作"]').element as HTMLInputElement).value).toBe('ROLE_ADMIN')

    const copyButton = wrapper.findAll('button').find((item) => item.text() === '复制概览')
    await copyButton?.trigger('click')
    expect(window.navigator.clipboard.writeText).toHaveBeenCalled()
    expect(String(vi.mocked(window.navigator.clipboard.writeText).mock.calls[0]?.[0])).toContain('用户与权限概览')
  })

  it('supports inspected user summary and same-department filter', async () => {
    const store = useUserStore()
    store.users = [
      { userId: 'u-1', username: 'alice', employeeId: 'E001', department: '研发部', roles: 'ROLE_ADMIN', enabled: true, lastLoginAt: '2026-03-25T08:00:00Z' }
    ] as never
    store.permissions = [] as never
    store.loadAll = vi.fn().mockResolvedValue(undefined) as never
    getTokenUsage.mockResolvedValue({ userId: 'u-1', date: '2026-03-25', tokensUsed: 1234 })
    getAuditLogs.mockResolvedValue([
      { id: 'log-1', user_id: 'u-1', agent_type: 'assistant', model_id: 'gpt', success: true, latency_ms: 200, created_at: '2026-03-25T08:30:00Z' }
    ])

    const wrapper = mount(UserView)
    const inspectButton = wrapper.findAll('button').find((item) => item.text() === '概览')
    await inspectButton?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('用户使用概览')

    const copySummaryButton = wrapper.findAll('button').find((item) => item.text() === '复制用户摘要')
    await copySummaryButton?.trigger('click')
    expect(window.navigator.clipboard.writeText).toHaveBeenCalled()
    expect(String(vi.mocked(window.navigator.clipboard.writeText).mock.calls[0]?.[0])).toContain('用户使用摘要')

    const departmentButton = wrapper.findAll('button').find((item) => item.text() === '筛选同部门用户')
    await departmentButton?.trigger('click')
    expect((wrapper.get('input[placeholder="搜索用户ID、用户名、工号、部门或角色"]').element as HTMLInputElement).value).toBe('研发部')
  })

  it('shows recent action banner after user follow-up actions', async () => {
    const store = useUserStore()
    store.users = [
      { userId: 'u-1', username: 'alice', employeeId: 'E001', department: '研发部', roles: 'ROLE_ADMIN', enabled: true, lastLoginAt: '2026-03-25T08:00:00Z' }
    ] as never
    store.permissions = [] as never
    store.loadAll = vi.fn().mockResolvedValue(undefined) as never
    getTokenUsage.mockResolvedValue({ userId: 'u-1', date: '2026-03-25', tokensUsed: 1234 })
    getAuditLogs.mockResolvedValue([])

    const wrapper = mount(UserView)
    const inspectButton = wrapper.findAll('button').find((item) => item.text() === '概览')
    await inspectButton?.trigger('click')
    await flushPromises()

    const departmentButton = wrapper.findAll('button').find((item) => item.text() === '筛选同部门用户')
    await departmentButton?.trigger('click')

    expect(wrapper.text()).toContain('最近操作')
  })
})
