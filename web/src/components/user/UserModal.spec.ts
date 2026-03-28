import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import UserModal from './UserModal.vue'
import { useUserStore } from '@/stores/user'

const { getUser } = vi.hoisted(() => ({
  getUser: vi.fn()
}))

vi.mock('@/api/auth', () => ({
  getUser
}))

describe('UserModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getUser.mockReset()
  })

  it('creates a user and emits saved', async () => {
    const store = useUserStore()
    store.createUser = vi.fn().mockResolvedValue(true) as never
    store.users = [{ userId: 'alice01', username: 'alice' }] as never

    const wrapper = mount(UserModal, {
      props: {
        userId: null
      }
    })

    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue('alice01')
    await inputs[1]!.setValue('alice')
    await inputs[2]!.setValue('123456')
    await inputs[3]!.setValue('E001')
    await inputs[4]!.setValue('研发部')
    await inputs[5]!.setValue('ROLE_ADMIN')

    await wrapper.findAll('button')[1]!.trigger('click')

    expect(store.createUser).toHaveBeenCalledWith({
      userId: 'alice01',
      username: 'alice',
      password: '123456',
      employeeId: 'E001',
      department: '研发部',
      roles: 'ROLE_ADMIN'
    })
    expect(wrapper.emitted('saved')?.[0]).toEqual(['alice01'])
  })

  it('loads and updates an existing user', async () => {
    getUser.mockResolvedValue({
      userId: 'u-1',
      username: 'alice',
      employeeId: 'E001',
      department: '研发部',
      roles: 'ROLE_RD'
    })

    const store = useUserStore()
    store.updateUser = vi.fn().mockResolvedValue(true) as never

    const wrapper = mount(UserModal, {
      props: {
        userId: 'u-1'
      }
    })

    await flushPromises()

    const inputs = wrapper.findAll('input')
    expect((inputs[0]!.element as HTMLInputElement).value).toBe('u-1')
    expect((inputs[1]!.element as HTMLInputElement).value).toBe('alice')

    await inputs[1]!.setValue('alice-updated')
    await inputs[2]!.setValue('new-pass')
    await inputs[4]!.setValue('平台部')
    await wrapper.findAll('button')[1]!.trigger('click')

    expect(store.updateUser).toHaveBeenCalledWith('u-1', {
      username: 'alice-updated',
      department: '平台部',
      employeeId: 'E001',
      roles: 'ROLE_RD',
      password: 'new-pass'
    })
  })
})
