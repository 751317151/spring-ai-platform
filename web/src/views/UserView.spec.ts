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
  default: {
    name: 'RoleModal',
    template: '<div class="role-modal-stub" />',
    props: ['role']
  }
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
    getRoleUsage.mockReset()
  })

  it('switches between role, user and permission workspaces', async () => {
    const store = useUserStore()
    store.loadAll = vi.fn().mockResolvedValue(undefined) as never
    store.roles = [
      { id: '1001', roleName: 'ROLE_ADMIN', description: '系统管理员' },
      { id: '1002', roleName: 'ROLE_SUPPORT', description: '支持角色' }
    ] as never
    store.users = [
      { userId: 'admin', username: '管理员', department: '系统管理', roles: 'ROLE_ADMIN', enabled: true }
    ] as never
    store.permissions = [
      {
        id: '2001',
        botType: 'multi',
        allowedRoles: 'ROLE_ADMIN',
        allowedDepartments: '系统管理',
        dataScope: 'ALL',
        allowedOperations: 'READ,WRITE',
        dailyTokenLimit: 1000,
        enabled: true
      }
    ] as never

    const wrapper = mount(UserView)

    expect(wrapper.text()).toContain('角色目录')
    expect(wrapper.text()).toContain('ROLE_SUPPORT')

    await wrapper.findAll('.section-tab')[1].trigger('click')
    expect(wrapper.text()).toContain('用户与角色')
    expect(wrapper.text()).toContain('管理员')

    await wrapper.findAll('.section-tab')[2].trigger('click')
    expect(wrapper.text()).toContain('角色与 AI 助手权限')
    expect(wrapper.text()).toContain('multi')
  })

  it('opens role usage drawer and blocks referenced role deletion', async () => {
    const store = useUserStore()
    store.loadAll = vi.fn().mockResolvedValue(undefined) as never
    store.roles = [{ id: '1001', roleName: 'ROLE_ADMIN', description: '系统管理员' }] as never
    store.users = [] as never
    store.permissions = [] as never
    store.deleteRole = vi.fn().mockResolvedValue(true) as never
    getRoleUsage.mockResolvedValue({
      roleId: '1001',
      roleName: 'ROLE_ADMIN',
      userCount: 1,
      permissionCount: 1,
      userReferences: ['admin(管理员)'],
      permissionReferences: ['multi (#2007)']
    })

    const wrapper = mount(UserView)
    await wrapper.findAll('button').find((item) => item.text() === '详情')?.trigger('click')

    expect(wrapper.text()).toContain('角色引用详情')
    expect(wrapper.text()).toContain('admin(管理员)')

    await wrapper.findAll('button').find((item) => item.text() === '删除')?.trigger('click')

    expect(confirm).not.toHaveBeenCalled()
    expect(store.deleteRole).not.toHaveBeenCalled()
    expect(showToast).toHaveBeenCalledWith('角色 ROLE_ADMIN 仍被引用，请先解除关联后再删除')
  })

  it('deletes unreferenced role after confirmation', async () => {
    const store = useUserStore()
    store.loadAll = vi.fn().mockResolvedValue(undefined) as never
    store.roles = [{ id: '1002', roleName: 'ROLE_SUPPORT', description: '支持角色' }] as never
    store.users = [] as never
    store.permissions = [] as never
    store.deleteRole = vi.fn().mockResolvedValue(true) as never
    getRoleUsage.mockResolvedValue({
      roleId: '1002',
      roleName: 'ROLE_SUPPORT',
      userCount: 0,
      permissionCount: 0,
      userReferences: [],
      permissionReferences: []
    })

    const wrapper = mount(UserView)
    await wrapper.findAll('button').find((item) => item.text() === '删除')?.trigger('click')

    expect(confirm).toHaveBeenCalled()
    expect(store.deleteRole).toHaveBeenCalledWith('1002')
    expect(showToast).toHaveBeenCalledWith('角色 ROLE_SUPPORT 已删除')
  })
})
