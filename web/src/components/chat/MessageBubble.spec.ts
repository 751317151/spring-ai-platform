import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MessageBubble from './MessageBubble.vue'

const showToast = vi.fn()

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    showToast
  })
}))

describe('MessageBubble', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    showToast.mockReset()
    localStorage.clear()

    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined)
      }
    })

    global.URL.createObjectURL = vi.fn(() => 'blob:mock')
    global.URL.revokeObjectURL = vi.fn()
  })

  it('renders assistant actions and emits insert prompt', async () => {
    const wrapper = mount(MessageBubble, {
      props: {
        role: 'assistant',
        content: 'This is the answer content',
        responseId: 'resp-1'
      }
    })

    const buttons = wrapper.findAll('button')
    await buttons[1]?.trigger('click')

    expect(wrapper.emitted('insert-prompt')?.[0]?.[0]).toContain('> This is the answer content')
  })

  it('copies content and shows toast', async () => {
    const wrapper = mount(MessageBubble, {
      props: {
        role: 'user',
        content: 'copy this message'
      }
    })

    await wrapper.find('button').trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('copy this message')
    expect(showToast).toHaveBeenCalledWith('消息已复制')
  })

  it('renders runtime config snapshot chips', () => {
    const wrapper = mount(MessageBubble, {
      props: {
        role: 'assistant',
        content: 'answer',
        responseId: 'resp-config',
        sessionConfigSnapshot: {
          model: 'gpt-4.1',
          temperature: 0.2,
          maxContextMessages: 4,
          knowledgeEnabled: false
        }
      }
    })

    const text = wrapper.text()
    expect(text).toContain('模型 gpt-4.1')
    expect(text).toContain('温度 0.2')
    expect(text).toContain('上下文 4 条')
    expect(text).toContain('知识增强 关闭')
  })

  it('renders highlighted state when requested', () => {
    const wrapper = mount(MessageBubble, {
      props: {
        role: 'assistant',
        content: 'highlight me',
        messageIndex: 3,
        highlighted: true
      }
    })

    expect(wrapper.find('.msg.is-highlighted').exists()).toBe(true)
    expect(wrapper.find('.msg').attributes('data-message-index')).toBe('3')
  })

  it('renders derived-from chip for assistant messages', () => {
    const wrapper = mount(MessageBubble, {
      props: {
        role: 'assistant',
        content: 'follow-up answer',
        derivedFrom: {
          action: 'continue',
          messageIndex: 2
        }
      }
    })

    expect(wrapper.text()).toContain('继续生成自 #3')
  })

  it('deduplicates similar favorites and exports a single message', async () => {
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const first = mount(MessageBubble, {
      props: {
        role: 'assistant',
        content: 'save this answer',
        responseId: 'resp-favorite-1',
        agentType: 'rd',
        sessionId: 'session-1',
        sessionSummary: '排查记录',
        messageIndex: 1
      }
    })

    await first.findAll('.msg-text-btn')[2]!.trigger('click')
    expect(showToast).toHaveBeenCalledWith('消息已收藏')

    const second = mount(MessageBubble, {
      props: {
        role: 'assistant',
        content: 'save this answer',
        responseId: 'resp-favorite-2',
        agentType: 'rd',
        sessionId: 'session-2',
        sessionSummary: '重复排查',
        messageIndex: 2
      }
    })

    await second.findAll('.msg-text-btn')[2]!.trigger('click')

    const favorites = JSON.parse(localStorage.getItem('learning_center_favorites') || '[]')
    expect(favorites).toHaveLength(1)
    expect(favorites[0].duplicateCount).toBe(2)
    expect(favorites[0].sourceMessageIndex).toBe(1)
    expect(showToast).toHaveBeenCalledWith('检测到相似收藏，已合并到原记录')

    await second.findAll('.msg-text-btn')[3]!.trigger('click')
    expect(clickSpy).toHaveBeenCalled()
    expect(showToast).toHaveBeenCalledWith('单条消息已导出')
  })
})
