import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LearningCenterView from './LearningCenterView.vue'

const push = vi.fn()
const showToast = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push
  })
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

vi.mock('@/api/auth', () => ({
  getMyBots: vi.fn()
}))

vi.mock('@/api/agent', () => ({
  getSessions: vi.fn(),
  getHistory: vi.fn()
}))

describe('LearningCenterView', () => {
  beforeEach(() => {
    localStorage.clear()
    push.mockReset()
    showToast.mockReset()
    global.URL.createObjectURL = vi.fn(() => 'blob:mock')
    global.URL.revokeObjectURL = vi.fn()
  })

  it('renders favorite list, filters by tag and exports selected favorites', async () => {
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    localStorage.setItem('learning_center_favorites', JSON.stringify([
      {
        id: 'fav-1',
        role: 'assistant',
        content: '这是一条值得收藏的回答',
        agentType: 'rd',
        sessionId: 'session-1',
        sessionSummary: '接口排查',
        sourceMessageIndex: 2,
        createdAt: Date.now(),
        tags: ['登录', '排查']
      },
      {
        id: 'fav-2',
        role: 'assistant',
        content: '另一条消息',
        agentType: 'rag',
        sessionId: 'session-2',
        sessionSummary: '知识库整理',
        createdAt: Date.now(),
        tags: ['知识库']
      }
    ]))

    const wrapper = mount(LearningCenterView)
    await flushPromises()

    expect(wrapper.text()).toContain('接口排查')
    expect(wrapper.text()).toContain('知识库整理')

    await wrapper.find('input[placeholder="输入标签或关键词过滤收藏"]').setValue('登录')
    await flushPromises()

    expect(wrapper.text()).toContain('接口排查')
    expect(wrapper.text()).not.toContain('知识库整理')

    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    await checkboxes[1]!.setValue(true)
    await wrapper.findAll('button').find((button) => button.text() === '导出选中收藏')?.trigger('click')

    expect(clickSpy).toHaveBeenCalled()
    expect(showToast).toHaveBeenCalledWith('选中收藏已导出')
  })

  it('saves tagged note to local storage and reopens source session', async () => {
    localStorage.setItem('learning_center_notes', JSON.stringify([
      {
        id: 'note-1',
        title: '会话复盘',
        content: '记录登录链路和排查结论',
        tags: ['登录', '复盘'],
        relatedSessionId: 'session-88',
        relatedAgentType: 'rd',
        relatedSessionSummary: '登录排查',
        relatedMessageIndex: 4,
        createdAt: Date.now(),
        updatedAt: Date.now()
      }
    ]))

    const wrapper = mount(LearningCenterView)
    await flushPromises()

    await wrapper.find('input[placeholder="搜索收藏、笔记、标签或会话标题，例如：复盘、登录、CORS"]').setValue('复盘')
    await flushPromises()

    expect(wrapper.text()).toContain('会话复盘')
    await wrapper.findAll('button').find((button) => button.text() === '回到来源会话')?.trigger('click')

    expect(push).toHaveBeenCalledWith({
      name: 'chat',
      query: {
        agent: 'rd',
        session: 'session-88',
        message: '4'
      }
    })
  })

  it('creates tagged note to local storage', async () => {
    const wrapper = mount(LearningCenterView)

    await wrapper.find('input[placeholder="笔记标题"]').setValue('排查笔记')
    await wrapper.find('input[placeholder="标签，使用逗号分隔，例如：登录、排查、RAG"]').setValue('登录, 排查')
    await wrapper.find('textarea').setValue('记录登录链路和排查结论')
    await wrapper.findAll('button').find((button) => button.text() === '保存笔记')?.trigger('click')

    const notes = JSON.parse(localStorage.getItem('learning_center_notes') || '[]')
    expect(notes).toHaveLength(1)
    expect(notes[0].title).toBe('排查笔记')
    expect(notes[0].content).toBe('记录登录链路和排查结论')
    expect(notes[0].tags).toEqual(['登录', '排查'])
    expect(showToast).toHaveBeenCalledWith('学习笔记已保存')
  })

  it('searches sessions and navigates to chat result', async () => {
    const authApi = await import('@/api/auth')
    const agentApi = await import('@/api/agent')

    vi.mocked(authApi.getMyBots).mockResolvedValue([
      {
        id: 'bot-1',
        botType: 'rd',
        allowedRoles: 'ROLE_ADMIN',
        dataScope: 'ALL',
        allowedOperations: 'chat',
        dailyTokenLimit: 0,
        enabled: true
      }
    ])
    vi.mocked(agentApi.getSessions).mockResolvedValue([
      {
        sessionId: 'session-9',
        summary: 'CORS 问题排查',
        updatedAt: '2026-03-26 12:00:00'
      }
    ])
    vi.mocked(agentApi.getHistory).mockResolvedValue([
      {
        role: 'assistant',
        content: '最终原因是 CORS 配置不一致'
      }
    ] as never)

    const wrapper = mount(LearningCenterView)
    await wrapper.find('input[placeholder="输入关键词，例如：CORS、登录跳转、知识库、提示词"]').setValue('cors')
    await wrapper.findAll('button').find((button) => button.text() === '开始搜索')?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('CORS 问题排查')
    expect(wrapper.text()).toContain('命中会话标题：CORS 问题排查')
    expect(showToast).toHaveBeenCalledWith('已找到 1 条匹配结果')

    await wrapper.findAll('button').find((button) => button.text() === '打开会话')?.trigger('click')
    expect(push).toHaveBeenCalledWith({
      name: 'chat',
      query: {
        agent: 'rd',
        session: 'session-9',
        message: undefined
      }
    })
  })

  it('shows search hit excerpt and expandable context for message results', async () => {
    const authApi = await import('@/api/auth')
    const agentApi = await import('@/api/agent')

    vi.mocked(authApi.getMyBots).mockResolvedValue([
      {
        id: 'bot-1',
        botType: 'rd',
        allowedRoles: 'ROLE_ADMIN',
        dataScope: 'ALL',
        allowedOperations: 'chat',
        dailyTokenLimit: 0,
        enabled: true
      }
    ])
    vi.mocked(agentApi.getSessions).mockResolvedValue([
      {
        sessionId: 'session-context-1',
        summary: '登录链路排查',
        updatedAt: '2026-03-26 16:00:00'
      }
    ])
    vi.mocked(agentApi.getHistory).mockResolvedValue([
      {
        role: 'user',
        content: '先看一下登录流程和鉴权中间件'
      },
      {
        role: 'assistant',
        content: '最终确认是中文用户名场景下 CORS 预检失败，原因是网关和服务返回头不一致。'
      },
      {
        role: 'assistant',
        content: '修复后再检查 cookie、origin 和 allow-credentials。'
      }
    ] as never)

    const wrapper = mount(LearningCenterView)
    const searchInput = wrapper.findAll('input[type="text"]').at(-1)
    await searchInput?.setValue('cors')
    await searchInput?.trigger('keydown.enter')
    await flushPromises()

    expect(wrapper.text()).toContain('消息命中')
    expect(wrapper.text()).toContain('展开上下文')
    expect(wrapper.text()).toContain('CORS 预检失败')

    await wrapper.findAll('button').find((button) => button.text() === '展开上下文')?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('前文')
    expect(wrapper.text()).toContain('先看一下登录流程和鉴权中间件')
    expect(wrapper.text()).toContain('后文')
    expect(wrapper.text()).toContain('修复后再检查 cookie、origin 和 allow-credentials')
  })

  it('shows source summary and opens source session from local search', async () => {
    localStorage.setItem('learning_center_favorites', JSON.stringify([
      {
        id: 'fav-local-1',
        role: 'assistant',
        content: '本地收藏内容',
        agentType: 'rd',
        sessionId: 'session-local-1',
        sessionSummary: '本地来源会话',
        sourceMessageIndex: 1,
        createdAt: Date.now(),
        tags: ['来源']
      }
    ]))

    const wrapper = mount(LearningCenterView)
    await flushPromises()

    const localSearchInput = wrapper.findAll('input').find((input) => input.attributes('placeholder')?.includes('CORS'))
    await localSearchInput?.setValue('本地')
    await flushPromises()

    expect(wrapper.text()).toContain('消息 #2')
    await wrapper.get('.local-source-btn').trigger('click')

    expect(push).toHaveBeenCalledWith({
      name: 'chat',
      query: {
        agent: 'rd',
        session: 'session-local-1',
        message: '1'
      }
    })
  })

  it('filters and sorts local learning search results', async () => {
    const now = Date.now()
    localStorage.setItem('learning_center_favorites', JSON.stringify([
      {
        id: 'fav-sort-1',
        role: 'assistant',
        content: 'CORS ??????',
        agentType: 'rd',
        sessionId: 'session-sort-1',
        sessionSummary: '????',
        sourceMessageIndex: 0,
        createdAt: now - 10_000,
        lastCollectedAt: now - 10_000,
        tags: ['CORS']
      }
    ]))
    localStorage.setItem('learning_center_notes', JSON.stringify([
      {
        id: 'note-sort-1',
        title: 'CORS ??',
        content: '????????',
        relatedSessionId: 'session-sort-2',
        relatedAgentType: 'rd',
        tags: ['CORS'],
        createdAt: now,
        updatedAt: now
      }
    ]))

    const wrapper = mount(LearningCenterView)
    await flushPromises()

    await wrapper.findAll('input[type="text"]')[0]?.setValue('cors')
    await flushPromises()

    let localSearchSection = wrapper.get('.local-search-section')
    expect(localSearchSection.text()).toContain('CORS ??')
    expect(localSearchSection.text()).toContain('CORS ??????')

    const selects = wrapper.findAll('select')
    await selects[0]?.setValue('note')
    await selects[1]?.setValue('latest')
    await flushPromises()

    localSearchSection = wrapper.get('.local-search-section')
    expect(localSearchSection.text()).toContain('CORS ??')
    expect(localSearchSection.text()).not.toContain('CORS ??????')
  })

  it('continues from learning material into chat with prompt query', async () => {
    localStorage.setItem('learning_center_favorites', JSON.stringify([
      {
        id: 'fav-local-2',
        role: 'assistant',
        content: '这是要继续追问的来源内容',
        agentType: 'rd',
        sessionId: 'session-local-2',
        sessionSummary: '继续追问来源',
        sourceMessageIndex: 0,
        createdAt: Date.now(),
        tags: ['继续']
      }
    ]))

    const wrapper = mount(LearningCenterView)
    await flushPromises()

    const localSearchInput = wrapper.findAll('input').find((input) => input.attributes('placeholder')?.includes('CORS'))
    await localSearchInput?.setValue('继续')
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '继续追问')?.trigger('click')

    expect(push).toHaveBeenCalledWith({
      name: 'chat',
      query: expect.objectContaining({
        agent: 'rd',
        session: 'session-local-2',
        message: '0',
        source: 'learning',
        prompt: expect.stringContaining('这是要继续追问的来源内容')
      })
    })
  })

  it('builds a multi-source follow-up draft and sends it to chat', async () => {
    const now = Date.now()
    localStorage.setItem('learning_center_favorites', JSON.stringify([
      {
        id: 'fav-draft-1',
        role: 'assistant',
        content: '第一条来源内容',
        agentType: 'rd',
        sessionId: 'session-draft-1',
        sessionSummary: '来源一',
        sourceMessageIndex: 0,
        createdAt: now,
        tags: ['草稿']
      }
    ]))
    localStorage.setItem('learning_center_notes', JSON.stringify([
      {
        id: 'note-draft-1',
        title: '笔记来源',
        content: '第二条来源内容',
        relatedSessionId: 'session-draft-2',
        relatedAgentType: 'rd',
        relatedSessionSummary: '来源二',
        relatedMessageIndex: 2,
        tags: ['草稿'],
        createdAt: now,
        updatedAt: now
      }
    ]))

    const wrapper = mount(LearningCenterView)
    await flushPromises()

    const appendButtons = wrapper.findAll('button').filter((button) => button.text() === '加入追问')
    await appendButtons[0]?.trigger('click')
    await appendButtons[1]?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('追问草稿台')
    expect(wrapper.text()).toContain('第一条来源内容')
    expect(wrapper.text()).toContain('第二条来源内容')

    await wrapper.findAll('button').find((button) => button.text() === '发送到聊天页')?.trigger('click')

    expect(push).toHaveBeenCalledWith({
      name: 'chat',
      query: expect.objectContaining({
        agent: 'rd',
        session: 'session-draft-2',
        message: '2',
        source: 'learning',
        prompt: expect.stringContaining('第一条来源内容')
      })
    })
  })
  it('supports editing, reordering and saving follow-up draft as note', async () => {
    const now = Date.now()
    localStorage.setItem('learning_center_favorites', JSON.stringify([
      {
        id: 'fav-edit-1',
        role: 'assistant',
        content: '第一条来源内容',
        agentType: 'rd',
        sessionId: 'session-edit-1',
        sessionSummary: '来源一',
        sourceMessageIndex: 1,
        createdAt: now,
        tags: ['草稿']
      },
      {
        id: 'fav-edit-2',
        role: 'assistant',
        content: '第二条来源内容',
        agentType: 'rd',
        sessionId: 'session-edit-2',
        sessionSummary: '来源二',
        sourceMessageIndex: 3,
        createdAt: now + 1,
        tags: ['草稿']
      }
    ]))

    const wrapper = mount(LearningCenterView)
    await flushPromises()

    const appendButtons = wrapper.findAll('button').filter((button) => button.text() === '加入追问')
    await appendButtons[0]?.trigger('click')
    await appendButtons[1]?.trigger('click')
    await flushPromises()

    const moveUpButton = wrapper.findAll('button').find((button) => button.text() === '上移')
    await moveUpButton?.trigger('click')
    await flushPromises()

    await wrapper.get('textarea.followup-draft-preview').setValue('请基于第二条来源优先分析，并补充下一步行动。')
    await wrapper.findAll('button').find((button) => button.text() === '保存为笔记')?.trigger('click')

    const notes = JSON.parse(localStorage.getItem('learning_center_notes') || '[]')
    expect(notes).toHaveLength(1)
    expect(notes[0].title).toContain('追问草稿')
    expect(notes[0].content).toBe('请基于第二条来源优先分析，并补充下一步行动。')
    expect(notes[0].relatedSessionId).toBe('session-edit-1')
    expect(showToast).toHaveBeenCalledWith('追问草稿已保存为学习笔记')

    await wrapper.findAll('button').find((button) => button.text() === '发送到聊天页')?.trigger('click')

    expect(push).toHaveBeenLastCalledWith({
      name: 'chat',
      query: expect.objectContaining({
        agent: 'rd',
        session: 'session-edit-1',
        message: '1',
        source: 'learning',
        prompt: '请基于第二条来源优先分析，并补充下一步行动。'
      })
    })
  })

  it('saves, applies and removes follow-up templates', async () => {
    const now = Date.now()
    localStorage.setItem('learning_center_favorites', JSON.stringify([
      {
        id: 'fav-template-1',
        role: 'assistant',
        content: 'template source content',
        agentType: 'rd',
        sessionId: 'session-template-1',
        sessionSummary: 'template source',
        sourceMessageIndex: 0,
        createdAt: now,
        tags: ['template']
      }
    ]))

    const wrapper = mount(LearningCenterView)
    await flushPromises()

    const favoriteActions = wrapper.findAll('.learning-grid .learning-section .learning-card .learning-actions')[0]
    await favoriteActions?.findAll('button')[2]?.trigger('click')
    await flushPromises()

    await wrapper.get('textarea.followup-draft-preview').setValue('template body content')
    await wrapper.get('.followup-template-toolbar input').setValue('template-name')
    await wrapper.get('.followup-template-toolbar button').trigger('click')
    await flushPromises()

    const savedTemplates = JSON.parse(localStorage.getItem('learning_center_followup_templates') || '[]')
    expect(savedTemplates).toHaveLength(1)
    expect(savedTemplates[0].name).toBe('template-name')
    expect(savedTemplates[0].content).toBe('template body content')
    expect(wrapper.text()).toContain('template-name')

    await wrapper.get('textarea.followup-draft-preview').setValue('temporary draft')
    const templateCard = wrapper.get('.followup-template-card')
    const templateButtons = templateCard.findAll('button')
    await templateButtons[0]!.trigger('click')
    await flushPromises()
    expect((wrapper.get('textarea.followup-draft-preview').element as HTMLTextAreaElement).value).toBe('template body content')

    await templateButtons[1]!.trigger('click')
    await flushPromises()
    const remainingTemplates = JSON.parse(localStorage.getItem('learning_center_followup_templates') || '[]')
    expect(remainingTemplates).toHaveLength(0)
    expect(wrapper.find('.followup-template-card').exists()).toBe(false)
  })
})
