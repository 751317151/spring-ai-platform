import { mount } from '@vue/test-utils'
import EmptyState from './EmptyState.vue'

describe('EmptyState', () => {
  it('renders title, description and actions', async () => {
    const wrapper = mount(EmptyState, {
      props: {
        icon: 'AI',
        title: '暂无数据',
        description: '请先创建内容',
        actionText: '重试',
        secondaryActionText: '新建'
      }
    })

    expect(wrapper.text()).toContain('暂无数据')
    expect(wrapper.text()).toContain('请先创建内容')
    expect(wrapper.text()).toContain('重试')
    expect(wrapper.text()).toContain('新建')

    await wrapper.find('button.btn.btn-ghost').trigger('click')
    expect(wrapper.emitted('action')).toHaveLength(1)
  })

  it('renders badge slot content', () => {
    const wrapper = mount(EmptyState, {
      props: {
        title: '空状态'
      },
      slots: {
        badge: '<span>推荐</span>'
      }
    })

    expect(wrapper.text()).toContain('推荐')
  })
})
