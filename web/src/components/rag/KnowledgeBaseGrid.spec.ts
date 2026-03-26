import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import KnowledgeBaseGrid from './KnowledgeBaseGrid.vue'
import { useRagStore } from '@/stores/rag'

describe('KnowledgeBaseGrid', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('支持关键词筛选、范围筛选、排序并显示可见范围标签', async () => {
    const store = useRagStore()
    store.knowledgeBases = [
      {
        id: 'kb-1',
        name: '研发规范',
        documentCount: 8,
        totalChunks: 20,
        status: 'ACTIVE',
        visibilityScope: 'DEPARTMENT'
      },
      {
        id: 'kb-2',
        name: '销售手册',
        documentCount: 2,
        totalChunks: 30,
        status: 'DISABLED',
        visibilityScope: 'PRIVATE'
      },
      {
        id: 'kb-3',
        name: '公共 FAQ',
        documentCount: 5,
        totalChunks: 15,
        status: 'ACTIVE',
        visibilityScope: 'PUBLIC'
      }
    ] as never
    store.currentKb = 'kb-1'
    store.currentKbName = '研发规范'

    const wrapper = mount(KnowledgeBaseGrid)

    expect(wrapper.text()).toContain('研发规范')
    expect(wrapper.text()).toContain('销售手册')
    expect(wrapper.text()).toContain('公共 FAQ')
    expect(wrapper.text()).toContain('部门内可见')
    expect(wrapper.text()).toContain('仅创建人可见')
    expect(wrapper.text()).toContain('公共可见')
    expect(wrapper.text()).toContain('范围：全部')
    expect(wrapper.findAll('button').find((item) => item.text() === '删除当前')?.attributes('disabled')).toBeDefined()

    await wrapper.find('input').setValue('销售')
    const filteredNames = wrapper.findAll('.kb-name').map((item) => item.text())
    expect(filteredNames).toEqual(['销售手册'])

    const selects = wrapper.findAll('select')
    await selects[0]!.setValue('PRIVATE')
    expect(wrapper.text()).toContain('范围：仅创建人可见')
    expect(wrapper.findAll('.kb-name').map((item) => item.text())).toEqual(['销售手册'])

    const resetButton = wrapper.findAll('button').find((item) => item.text() === '重置筛选')
    expect(resetButton).toBeTruthy()
    await resetButton!.trigger('click')
    expect(wrapper.text()).toContain('范围：全部')

    await selects[1]!.setValue('chunks')
    expect((wrapper.findAll('.kb-name')[0]?.text()) || '').toContain('销售手册')
  })
})
