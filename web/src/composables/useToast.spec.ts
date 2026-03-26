import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useToast } from './useToast'

describe('useToast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    const { clearHistory } = useToast()
    clearHistory()
  })

  it('shows toast message and appends unread history', () => {
    const { showToast, toastMessage, isVisible, toastHistory, unreadCount } = useToast()

    showToast('保存成功', 1000)

    expect(toastMessage.value).toBe('保存成功')
    expect(isVisible.value).toBe(true)
    expect(toastHistory.value[0]?.message).toBe('保存成功')
    expect(toastHistory.value[0]?.readAt).toBeNull()
    expect(unreadCount.value).toBe(1)
  })

  it('marks notifications as read', () => {
    const { showToast, toastHistory, unreadCount, markRead, markAllRead } = useToast()

    showToast('第一条', 1000)
    showToast('第二条', 1000)

    markRead(toastHistory.value[0]!.id)
    expect(unreadCount.value).toBe(1)
    expect(toastHistory.value[0]!.readAt).not.toBeNull()

    markAllRead()
    expect(unreadCount.value).toBe(0)
  })

  it('hides toast after duration', () => {
    const { showToast, isVisible } = useToast()

    showToast('稍后隐藏', 1000)
    vi.advanceTimersByTime(1001)

    expect(isVisible.value).toBe(false)
  })
})
