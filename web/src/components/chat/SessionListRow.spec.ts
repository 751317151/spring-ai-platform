import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import SessionListRow from './SessionListRow.vue'

const baseSession = {
  sessionId: 'session-001',
  summary: 'Release Planning Review',
  updatedAt: String(Date.now()),
  pinned: true,
  archived: false
}

describe('SessionListRow', () => {
  it('renders highlighted title segments and matching hint for keyword hits', () => {
    const wrapper = mount(SessionListRow, {
      props: {
        session: baseSession,
        active: true,
        isEditing: false,
        draftTitle: '',
        keyword: 'plan',
        subtitle: '今天 10:00',
        selected: false,
        hasDraft: true
      }
    })

    expect(wrapper.find('.session-pin').exists()).toBe(true)
    expect(wrapper.find('.session-current').exists()).toBe(true)
    expect(wrapper.find('.session-draft-chip').exists()).toBe(true)
    expect(wrapper.find('.session-highlight').text().toLowerCase()).toBe('plan')
    expect(wrapper.find('.session-match-hint').exists()).toBe(true)
  })

  it('emits rename update, confirm and cancel events in editing mode', async () => {
    const captureInput = vi.fn()
    const wrapper = mount(SessionListRow, {
      props: {
        session: baseSession,
        active: false,
        isEditing: true,
        draftTitle: 'New Title',
        keyword: '',
        subtitle: '今天 10:00',
        captureInput
      }
    })

    const input = wrapper.get('.session-title-input')
    expect(captureInput).toHaveBeenCalled()

    await input.setValue('Updated Title')
    expect(wrapper.emitted('update:draft-title')?.[0]?.[0]).toBe('Updated Title')

    await input.trigger('keydown.enter')
    expect(wrapper.emitted('confirm-rename')?.[0]?.[0]).toBe('session-001')

    await input.trigger('keydown.esc')
    expect(wrapper.emitted('cancel-rename')).toHaveLength(1)

    await input.trigger('blur')
    expect(wrapper.emitted('confirm-rename')?.[1]?.[0]).toBe('session-001')
  })

  it('emits row actions without triggering select when action buttons are clicked', async () => {
    const wrapper = mount(SessionListRow, {
      props: {
        session: baseSession,
        active: false,
        isEditing: false,
        draftTitle: '',
        keyword: '',
        subtitle: '今天 10:00',
        selected: false
      }
    })

    await wrapper.get('.session-select-input').setValue(true)
    expect(wrapper.emitted('toggle-select')?.[0]?.[0]).toBe('session-001')
    expect(wrapper.emitted('select')).toBeUndefined()

    const buttons = wrapper.findAll('.session-action-btn')
    await buttons[0]!.trigger('click')
    await buttons[1]!.trigger('click')
    await buttons[2]!.trigger('click')
    await buttons[3]!.trigger('click')

    expect(wrapper.emitted('toggle-pin')).toHaveLength(1)
    expect(wrapper.emitted('start-rename')?.[0]?.[0]).toEqual(baseSession)
    expect(wrapper.emitted('toggle-archive')).toHaveLength(1)
    expect(wrapper.emitted('delete')).toHaveLength(1)
    expect(wrapper.emitted('select')).toBeUndefined()

    await wrapper.get('.session-item').trigger('click')
    expect(wrapper.emitted('select')?.[0]?.[0]).toBe('session-001')
  })
})
