import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PermissionTable from './PermissionTable.vue'

describe('PermissionTable', () => {
  it('renders empty state when permissions are missing', () => {
    const wrapper = mount(PermissionTable, {
      props: {
        permissions: []
      }
    })

    expect(wrapper.text()).toContain('当前筛选条件下没有匹配的权限规则')
  })

  it('renders grouped permission rows and emits edit', async () => {
    const wrapper = mount(PermissionTable, {
      props: {
        permissions: [
          {
            id: 'perm-1',
            botType: 'assistant',
            allowedRoles: 'ROLE_ADMIN,ROLE_RD',
            allowedDepartments: '研发部',
            dataScope: 'ALL',
            allowedOperations: 'chat,rag',
            dailyTokenLimit: 10000,
            enabled: true
          },
          {
            id: 'perm-2',
            botType: 'assistant',
            allowedRoles: 'ROLE_USER',
            allowedDepartments: '',
            dataScope: 'SELF',
            allowedOperations: 'chat',
            dailyTokenLimit: 2000,
            enabled: false
          }
        ],
        riskIds: ['perm-2']
      }
    })

    expect(wrapper.text()).toContain('2 条规则')
    expect(wrapper.text()).toContain('1 条高风险')
    expect(wrapper.text()).toContain('研发部')
    expect(wrapper.text()).toContain('chat,rag')
    expect(wrapper.text()).toContain('启用')
    expect(wrapper.text()).toContain('停用')

    await wrapper.get('.table-action-btn').trigger('click')
    expect(wrapper.emitted('edit')?.[0]).toEqual(['perm-1'])

    await wrapper.get('.group-toggle').trigger('click')
    expect(wrapper.text()).toContain('展开')
  })
})
