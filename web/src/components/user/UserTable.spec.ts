import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UserTable from './UserTable.vue'

describe('UserTable', () => {
  it('renders empty state when users are missing', () => {
    const wrapper = mount(UserTable, {
      props: {
        users: [],
        selectedIds: []
      }
    })

    expect(wrapper.text()).toContain('当前筛选条件下没有匹配的用户')
  })

  it('emits selection and row actions', async () => {
    const wrapper = mount(UserTable, {
      props: {
        users: [
          {
            id: 'u-1',
            username: 'alice',
            employeeId: 'E001',
            department: '研发部',
            roles: 'ROLE_ADMIN',
            enabled: true,
            lastLoginAt: '2026-03-25T08:00:00Z'
          }
        ],
        selectedIds: []
      }
    })

    const rowCheckbox = wrapper.findAll('input[type="checkbox"]')[1]
    await rowCheckbox!.setValue(true)
    expect(wrapper.emitted('update:selectedIds')?.[0]?.[0]).toEqual(['u-1'])

    const buttons = wrapper.findAll('.table-action-btn')
    await buttons[0]!.trigger('click')
    await buttons[1]!.trigger('click')
    await buttons[2]!.trigger('click')

    expect(wrapper.emitted('edit')?.[0]).toEqual(['u-1'])
    expect(wrapper.emitted('inspect')?.[0]).toEqual(['u-1'])
    expect(wrapper.emitted('delete')?.[0]).toEqual(['u-1', 'alice'])
  })
  it('emits reset-filters from empty state action', async () => {
    const wrapper = mount(UserTable, {
      props: {
        users: [],
        selectedIds: []
      }
    })

    await wrapper.findComponent({ name: 'EmptyState' }).vm.$emit('action')
    expect(wrapper.emitted('reset-filters')?.length).toBe(1)
  })
})
