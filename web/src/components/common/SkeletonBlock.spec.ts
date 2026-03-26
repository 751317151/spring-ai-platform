import { mount } from '@vue/test-utils'
import SkeletonBlock from './SkeletonBlock.vue'

describe('SkeletonBlock', () => {
  it('renders expected number of skeleton items', () => {
    const wrapper = mount(SkeletonBlock, {
      props: {
        count: 4,
        height: 72
      }
    })

    const items = wrapper.findAll('.skeleton-item')
    expect(items).toHaveLength(4)
    expect(items[0].attributes('style')).toContain('height: 72px;')
  })

  it('uses grid layout when variant is grid', () => {
    const wrapper = mount(SkeletonBlock, {
      props: {
        variant: 'grid',
        minWidth: 180
      }
    })

    expect(wrapper.attributes('style')).toContain('repeat(auto-fit, minmax(180px, 1fr))')
  })
})
