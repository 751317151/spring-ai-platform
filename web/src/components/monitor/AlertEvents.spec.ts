import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AlertEvents from './AlertEvents.vue'

describe('AlertEvents', () => {
  it('renders empty state when alerts are missing', () => {
    const wrapper = mount(AlertEvents, { props: { alerts: [] } })
    expect(wrapper.text()).toContain('当前没有告警事件')
  })

  it('renders alert details and emits workflow actions', async () => {
    const wrapper = mount(AlertEvents, {
      props: {
        alerts: [{
          level: 'ERROR',
          type: 'GatewayDown',
          time: '2026-03-25T09:30:00Z',
          source: 'gateway',
          status: 'firing',
          workflowStatus: 'processing',
          workflowNote: '正在排查',
          message: '网关不可用',
          fingerprint: 'fp-1',
          labels: { env: 'dev' }
        }]
      }
    })

    expect(wrapper.text()).toContain('GatewayDown')
    expect(wrapper.text()).toContain('来源：gateway')
    expect(wrapper.text()).toContain('状态：firing')
    expect(wrapper.text()).toContain('流转：处理中')
    expect(wrapper.text()).toContain('备注：正在排查')
    expect(wrapper.text()).toContain('指纹：fp-1')
    expect(wrapper.text()).toContain('env=dev')

    await wrapper.findAll('.alert-action-btn')[0]!.trigger('click')
    expect(wrapper.emitted('workflow')?.[0]?.[0]).toEqual({ fingerprint: 'fp-1', workflowStatus: 'acknowledged' })
  })
})
