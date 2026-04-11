import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import UserView from './UserView.vue'
import { useUserStore } from '@/stores/user'

const confirm = vi.fn()
const showToast = vi.fn()
const { getRoleUsage } = vi.hoisted(() => ({
  getRoleUsage: vi.fn()
}))

vi.mock('@/api/auth', () => ({
  getRoleUsage
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

vi.mock('@/components/user/RoleModal.vue', () => ({
  default: { name: 'RoleModal', template: '<div class="role-modal-stub" />', props: ['role'] }
}))

vi.mock('@/components/user/UserModal.vue', () => ({
  default: { name: 'UserModal', template: '<div class="user-modal-stub" />', props: ['userId'] }
}))

vi.mock('@/components/user/AgentDefinitionModal.vue', () => ({
  default: {
    name: 'AgentDefinitionModal',
    template: '<div class="agent-definition-modal-stub" />',
    props: ['definition']
  }
}))

describe('UserView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    confirm.mockReset()
    confirm.mockResolvedValue(true)
    showToast.mockReset()
    getRoleUsage.mockReset()
  })

  it('switches between role, assistant and quota workspaces', async () => {
    const store = useUserStore()
    store.loadAll = vi.fn().mockResolvedValue(undefined) as never
    store.roles = [
      { id: '1001', roleName: 'ROLE_ADMIN', description: '管理员' },
      { id: '1002', roleName: 'ROLE_SUPPORT', description: '支持角色' }
    ] as never
    store.users = [{ userId: 'admin', username: '管理员', department: '平台', roles: 'ROLE_ADMIN', enabled: true }] as never
    store.agentDefinitions = [
      {
        id: 3101,
        agentCode: 'legal-assistant',
        agentName: '法务助手',
        assistantProfile: 'generic',
        allowedRoles: 'ROLE_ADMIN,ROLE_SUPPORT',
        description: '法务侧统一入口',
        enabled: true,
        systemDefined: false,
        defaultModel: 'auto',
        sortOrder: 10,
        dailyTokenLimit: 1000
      },
      {
        id: 3102,
        agentCode: 'mcp',
        agentName: 'MCP 助手',
        assistantProfile: 'mcp',
        allowedRoles: 'ROLE_ADMIN',
        enabled: true,
        systemDefined: true
      }
    ] as never
    store.roleTokenLimits = [] as never
    store.userTokenLimits = [] as never

    const wrapper = mount(UserView)

    expect(wrapper.text()).toContain('角色目录')
    expect(wrapper.text()).toContain('ROLE_SUPPORT')

    await wrapper.findAll('.section-tab')[2].trigger('click')
    expect(wrapper.text()).toContain('助手')
    expect(wrapper.text()).toContain('法务助手')
    expect(wrapper.text()).toContain('特殊助手')

    await wrapper.findAll('.section-tab')[3].trigger('click')
    expect(wrapper.text()).toContain('角色 Token 配额')
  })

  it('opens role usage drawer and blocks referenced role deletion', async () => {
    const store = useUserStore()
    store.loadAll = vi.fn().mockResolvedValue(undefined) as never
    store.roles = [{ id: '1001', roleName: 'ROLE_ADMIN', description: '管理员' }] as never
    store.users = [] as never
    store.agentDefinitions = [] as never
    store.roleTokenLimits = [] as never
    store.userTokenLimits = [] as never
    store.deleteRole = vi.fn().mockResolvedValue(true) as never
    getRoleUsage.mockResolvedValue({
      roleId: '1001',
      roleName: 'ROLE_ADMIN',
      userCount: 1,
      permissionCount: 1,
      userReferences: ['admin(管理员)'],
      permissionReferences: ['法务助手 (legal-assistant)']
    })

    const wrapper = mount(UserView)
    await wrapper.findAll('button').find((item) => item.text() === '详情')?.trigger('click')

    expect(wrapper.text()).toContain('admin(管理员)')

    await wrapper.findAll('button').find((item) => item.text() === '删除')?.trigger('click')

    expect(confirm).not.toHaveBeenCalled()
    expect(store.deleteRole).not.toHaveBeenCalled()
    expect(showToast).toHaveBeenCalledWith('角色 ROLE_ADMIN 仍被引用，请先解除关联后再删除')
  })
})
