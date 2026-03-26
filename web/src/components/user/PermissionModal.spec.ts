import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import PermissionModal from './PermissionModal.vue'
import { useUserStore } from '@/stores/user'

const { getPermission } = vi.hoisted(() => ({
  getPermission: vi.fn()
}))

vi.mock('@/api/auth', () => ({
  getPermission
}))

describe('PermissionModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getPermission.mockReset()
  })

  it('creates a permission and emits saved', async () => {
    const store = useUserStore()
    store.createPermission = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(PermissionModal, {
      props: {
        permissionId: null
      }
    })

    await wrapper.get('input[placeholder="例如：rd / sales / hr"]').setValue('assistant')
    await wrapper.get('input[placeholder="例如：研发中心,销售团队"]').setValue('研发部')
    await wrapper.get('input[type="number"]').setValue('200000')
    await wrapper.get('.modal-actions .btn-primary').trigger('click')

    expect(store.createPermission).toHaveBeenCalledWith(expect.objectContaining({
      botType: 'assistant',
      allowedDepartments: '研发部',
      dailyTokenLimit: 200000
    }))
    expect(wrapper.emitted('saved')).toBeTruthy()
  })

  it('loads and updates an existing permission', async () => {
    getPermission.mockResolvedValue({
      id: 'perm-1',
      botType: 'assistant',
      allowedRoles: 'ROLE_ADMIN',
      allowedDepartments: '研发部',
      dataScope: 'ALL',
      allowedOperations: 'READ,WRITE',
      dailyTokenLimit: 100000,
      enabled: true
    })

    const store = useUserStore()
    store.updatePermission = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(PermissionModal, {
      props: {
        permissionId: 'perm-1'
      }
    })

    await flushPromises()

    await wrapper.get('input[placeholder="例如：研发中心,销售团队"]').setValue('平台部')
    await wrapper.get('.status-toggle').trigger('click')
    await wrapper.get('.modal-actions .btn-primary').trigger('click')

    expect(store.updatePermission).toHaveBeenCalledWith('perm-1', expect.objectContaining({
      botType: 'assistant',
      allowedDepartments: '平台部',
      enabled: false
    }))
  })
})
