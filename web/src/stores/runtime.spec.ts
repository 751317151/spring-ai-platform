import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRuntimeStore } from './runtime'

describe('runtime store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('marks service unavailable with default message', () => {
    const store = useRuntimeStore()
    store.markServiceUnavailable('chat')

    const status = store.getServiceStatus('chat')
    expect(status.available).toBe(false)
    expect(status.message).toContain('智能助手服务')
    expect(status.updatedAt).not.toBeNull()
  })

  it('marks service available and clears message', () => {
    const store = useRuntimeStore()
    store.markServiceUnavailable('rag', '临时不可用')
    store.markServiceAvailable('rag')

    const status = store.getServiceStatus('rag')
    expect(status.available).toBe(true)
    expect(status.message).toBe('')
  })
})
