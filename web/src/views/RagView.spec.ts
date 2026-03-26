import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick, reactive } from 'vue'
import RagView from './RagView.vue'
import { useRagStore } from '@/stores/rag'

const replace = vi.fn()
const route = reactive({
  query: {} as Record<string, string>
})

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({
      replace
    }),
    useRoute: () => route
  }
})

describe('RagView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    replace.mockReset()
    route.query = {}
    window.HTMLElement.prototype.scrollIntoView = vi.fn()
    Object.defineProperty(window.navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true
    })
  })

  it('renders onboarding and syncs route query after loading knowledge bases', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-001'
    ragStore.currentKbName = '企业通用知识库'
    ragStore.knowledgeBases = [{ id: 'kb-001', name: '企业通用知识库' }] as never
    ragStore.documents = [{ id: 'doc-1', filename: '规章.pdf' }] as never
    ragStore.loadKnowledgeBases = vi.fn().mockResolvedValue(undefined) as never
    ragStore.loadDocuments = vi.fn().mockResolvedValue(undefined) as never
    ragStore.selectKb = vi.fn() as never

    const wrapper = mount(RagView, {
      global: {
        stubs: {
          BackendStatusBanner: { template: '<div class="banner-stub" />' },
          KnowledgeBaseGrid: { template: '<div class="kb-grid-stub" />' },
          DocumentUpload: { template: '<div class="upload-stub" />' },
          RagQueryPanel: { template: '<div class="query-stub" />' },
          DocumentTable: { template: '<div class="table-stub" />' }
        }
      }
    })

    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('知识库管理')
    expect(wrapper.text()).toContain('快速开始')
    expect(wrapper.text()).toContain('企业通用知识库')
    expect(replace).toHaveBeenCalled()
  })

  it('renders failure summary and copies overview', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-001'
    ragStore.currentKbName = '企业通用知识库'
    ragStore.knowledgeBases = [{ id: 'kb-001', name: '企业通用知识库' }] as never
    ragStore.documents = [
      { id: 'doc-1', filename: '规章.pdf', status: 'FAILED', errorMessage: '向量化失败' },
      { id: 'doc-2', filename: '手册.pdf', status: 'FAILED', errorMessage: '向量化失败' },
      { id: 'doc-3', filename: '制度.pdf', status: 'PROCESSING' }
    ] as never
    ragStore.loadKnowledgeBases = vi.fn().mockResolvedValue(undefined) as never
    ragStore.loadDocuments = vi.fn().mockResolvedValue(undefined) as never
    ragStore.selectKb = vi.fn() as never

    const wrapper = mount(RagView, {
      global: {
        stubs: {
          BackendStatusBanner: { template: '<div class="banner-stub" />' },
          KnowledgeBaseGrid: { template: '<div class="kb-grid-stub" />' },
          DocumentUpload: { template: '<div class="upload-stub" />' },
          RagQueryPanel: { template: '<div class="query-stub" />' },
          DocumentTable: { template: '<div class="table-stub" />' }
        }
      }
    })

    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('失败原因聚合')
    expect(wrapper.text()).toContain('向量化失败')

    const copyButton = wrapper.findAll('button').find((item) => item.text() === '复制概览')
    await copyButton?.trigger('click')

    expect(window.navigator.clipboard.writeText).toHaveBeenCalled()
    expect(vi.mocked(window.navigator.clipboard.writeText).mock.calls[0]?.[0]).toContain('知识库管理概览')
  })

  it('focuses failed documents when next-step summary is clicked', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-001'
    ragStore.currentKbName = '企业通用知识库'
    ragStore.knowledgeBases = [{ id: 'kb-001', name: '企业通用知识库' }] as never
    ragStore.documents = [
      { id: 'doc-1', filename: '规章.pdf', status: 'FAILED', errorMessage: '向量化失败' }
    ] as never
    ragStore.loadKnowledgeBases = vi.fn().mockResolvedValue(undefined) as never
    ragStore.loadDocuments = vi.fn().mockResolvedValue(undefined) as never
    ragStore.selectKb = vi.fn() as never

    const wrapper = mount(RagView, {
      global: {
        stubs: {
          BackendStatusBanner: { template: '<div class="banner-stub" />' },
          KnowledgeBaseGrid: { template: '<div class="kb-grid-stub" />' },
          DocumentUpload: { template: '<div class="upload-stub" />' },
          RagQueryPanel: { template: '<div class="query-stub" />' },
          DocumentTable: {
            template: '<div class="table-stub">{{ externalStatus }}</div>',
            props: ['externalStatus']
          }
        }
      }
    })

    await nextTick()
    await nextTick()

    const summaryButtons = wrapper.findAll('.summary-button')
    await summaryButtons[3]?.trigger('click')

    expect(wrapper.find('.table-stub').text()).toContain('FAILED')
  })

  it('shows dashboard context banner and clears route context', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-001'
    ragStore.currentKbName = '企业通用知识库'
    ragStore.knowledgeBases = [{ id: 'kb-001', name: '企业通用知识库' }] as never
    ragStore.documents = [
      { id: 'doc-1', filename: '规章.pdf', status: 'FAILED', errorMessage: '向量化失败' }
    ] as never
    ragStore.loadKnowledgeBases = vi.fn().mockResolvedValue(undefined) as never
    ragStore.loadDocuments = vi.fn().mockResolvedValue(undefined) as never
    ragStore.selectKb = vi.fn() as never
    route.query = { status: 'FAILED', source: 'dashboard', kb: 'kb-001' }

    const wrapper = mount(RagView, {
      global: {
        stubs: {
          BackendStatusBanner: { template: '<div class="banner-stub" />' },
          KnowledgeBaseGrid: { template: '<div class="kb-grid-stub" />' },
          DocumentUpload: { template: '<div class="upload-stub" />' },
          RagQueryPanel: { template: '<div class="query-stub" />' },
          DocumentTable: { template: '<div class="table-stub">{{ externalStatus }}</div>', props: ['externalStatus'] }
        }
      }
    })

    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('当前沿用总览页的处理上下文')
    expect(wrapper.text()).toContain('已自动筛选为“失败”')

    const clearButton = wrapper.findAll('button').find((item) => item.text() === '清除上下文')
    await clearButton?.trigger('click')

    expect(replace).toHaveBeenCalledWith({
      query: {
        kb: 'kb-001',
        source: undefined,
        status: undefined
      }
    })
  })

  it('copies highlight summary when a document is focused', async () => {
    const ragStore = useRagStore()
    ragStore.currentKb = 'kb-001'
    ragStore.currentKbName = '企业通用知识库'
    ragStore.knowledgeBases = [{ id: 'kb-001', name: '企业通用知识库' }] as never
    ragStore.documents = [
      { id: 'doc-1', filename: '规章.pdf', status: 'INDEXED' }
    ] as never
    ragStore.loadKnowledgeBases = vi.fn().mockResolvedValue(undefined) as never
    ragStore.loadDocuments = vi.fn().mockResolvedValue(undefined) as never
    ragStore.selectKb = vi.fn() as never

    const wrapper = mount(RagView, {
      global: {
        stubs: {
          BackendStatusBanner: { template: '<div class="banner-stub" />' },
          KnowledgeBaseGrid: { template: '<div class="kb-grid-stub" />' },
          DocumentUpload: { template: '<div class="upload-stub" />' },
          RagQueryPanel: {
            template: '<button class="query-stub" @click="$emit(\'focus-document\', { documentId: \'doc-1\', filename: \'规章.pdf\', chunkIndex: 2, highlightTerms: [\'权限\', \'角色\'] })">query</button>'
          },
          DocumentTable: { template: '<div class="table-stub" />' }
        }
      }
    })

    await nextTick()
    await nextTick()

    await wrapper.get('.query-stub').trigger('click')

    const copyButton = wrapper.findAll('button').find((item) => item.text() === '复制定位摘要')
    await copyButton?.trigger('click')

    expect(window.navigator.clipboard.writeText).toHaveBeenCalled()
    expect(String(vi.mocked(window.navigator.clipboard.writeText).mock.calls[0]?.[0])).toContain('知识库定位摘要')
  })
})
