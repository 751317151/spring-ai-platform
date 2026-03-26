import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TopUsersTable from './TopUsersTable.vue'

describe('TopUsersTable', () => {
  it('renders empty state when users are missing', () => {
    const wrapper = mount(TopUsersTable, {
      props: {
        users: []
      }
    })

    expect(wrapper.text()).toContain('暂无高频用户数据')
  })

  it('emits pick events', async () => {
    const wrapper = mount(TopUsersTable, {
      props: {
        users: [
          {
            user_id: 'alice',
            agent_type: 'assistant',
            calls: 12,
            avg_latency: 320
          }
        ]
      }
    })

    const buttons = wrapper.findAll('button')
    await buttons[0]!.trigger('click')
    await buttons[1]!.trigger('click')

    expect(wrapper.emitted('pick-user')?.[0]).toEqual(['alice'])
    expect(wrapper.emitted('pick-agent')?.[0]).toEqual(['assistant'])
  })
  it('offers quick actions for the top user banner', async () => {
    const wrapper = mount(TopUsersTable, {
      props: {
        users: [
          {
            user_id: 'alice',
            agent_type: 'assistant',
            calls: 12,
            avg_latency: 320
          }
        ]
      }
    })

    expect(wrapper.text()).toContain('当前最高调用用户')

    const bannerButtons = wrapper.findAll('.banner-action-btn')
    await bannerButtons[0]!.trigger('click')
    await bannerButtons[1]!.trigger('click')

    expect(wrapper.emitted('pick-user')?.[0]).toEqual(['alice'])
    expect(wrapper.emitted('pick-agent')?.[0]).toEqual(['assistant'])
  })
})
