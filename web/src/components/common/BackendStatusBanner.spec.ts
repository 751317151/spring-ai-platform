import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import BackendStatusBanner from './BackendStatusBanner.vue'
import { useRuntimeStore } from '@/stores/runtime'

describe('BackendStatusBanner', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders unavailable message when service is down', () => {
    const runtimeStore = useRuntimeStore()
    runtimeStore.markServiceUnavailable('chat', '聊天服务异常')

    const wrapper = mount(BackendStatusBanner, {
      props: {
        service: 'chat'
      }
    })

    expect(wrapper.text()).toContain('聊天服务异常')
  })
})
