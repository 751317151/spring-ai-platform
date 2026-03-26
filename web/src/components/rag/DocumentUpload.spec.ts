import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import DocumentUpload from './DocumentUpload.vue'
import { useRagStore } from '@/stores/rag'

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast: vi.fn()
  })
}))

function createFileList(files: File[]): FileList {
  return {
    ...files,
    length: files.length,
    item: (index: number) => files[index] ?? null
  } as unknown as FileList
}

describe('DocumentUpload', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
    vi.restoreAllMocks()
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true
    })
  })

  it('calls uploadFile for selected files and keeps replaceExisting enabled by default', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-1'

    const uploadSpy = vi.spyOn(ragStore, 'uploadFile').mockResolvedValue({
      id: 'doc-1',
      status: 'INDEXED'
    } as never)

    const wrapper = mount(DocumentUpload)
    const input = wrapper.get('input[type="file"]')
    const file = new File(['hello'], 'guide.txt', { type: 'text/plain' })

    Object.defineProperty(input.element, 'files', {
      value: createFileList([file]),
      configurable: true
    })

    await input.trigger('change')
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(uploadSpy).toHaveBeenCalledTimes(1)
    expect(uploadSpy).toHaveBeenCalledWith(file, true)
    expect(wrapper.text()).toContain('guide.txt')
  })

  it('keeps a processing upload task visible after the file is accepted', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-1'

    const uploadSpy = vi.spyOn(ragStore, 'uploadFile').mockResolvedValue({
      id: 'doc-2',
      status: 'PROCESSING'
    } as never)

    const wrapper = mount(DocumentUpload)
    const input = wrapper.get('input[type="file"]')
    const file = new File(['hello'], 'policy.txt', { type: 'text/plain' })

    Object.defineProperty(input.element, 'files', {
      value: createFileList([file]),
      configurable: true
    })

    await input.trigger('change')
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(uploadSpy).toHaveBeenCalledWith(file, true)
    expect(wrapper.text()).toContain('policy.txt')
    expect(wrapper.findAll('.task-card')).toHaveLength(1)
  })

  it('offers upload summary actions for active tasks', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-1'
    ragStore.currentKbName = '测试知识库'

    const uploadSpy = vi.spyOn(ragStore, 'uploadFile').mockResolvedValue({
      id: 'doc-3',
      status: 'PROCESSING'
    } as never)

    const wrapper = mount(DocumentUpload)
    const input = wrapper.get('input[type="file"]')
    const file = new File(['bad'], 'broken.txt', { type: 'text/plain' })

    Object.defineProperty(input.element, 'files', {
      value: createFileList([file]),
      configurable: true
    })

    await input.trigger('change')
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(uploadSpy).toHaveBeenCalledWith(file, true)
    expect(wrapper.text()).toContain('查看处理中')

    await wrapper.findAll('.upload-actions-buttons .task-action-btn')[0]!.trigger('click')
    expect(wrapper.emitted('jumpStatus')?.[0]).toEqual(['PROCESSING'])

    const copyButton = wrapper.findAll('.upload-actions-buttons .task-action-btn').find((item) => item.text() === '复制上传摘要')
    await copyButton?.trigger('click')
    expect(navigator.clipboard.writeText).toHaveBeenCalled()
  })
})
