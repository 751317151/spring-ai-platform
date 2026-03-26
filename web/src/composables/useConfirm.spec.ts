import { describe, expect, it } from 'vitest'
import { useConfirm } from './useConfirm'

describe('useConfirm', () => {
  it('opens confirm dialog with provided options and resolves result', async () => {
    const confirmApi = useConfirm()

    const pending = confirmApi.confirm({
      title: '删除文档',
      description: '删除后不可恢复',
      confirmText: '删除',
      cancelText: '取消',
      intent: 'danger'
    })

    expect(confirmApi.visible.value).toBe(true)
    expect(confirmApi.title.value).toBe('删除文档')
    expect(confirmApi.description.value).toBe('删除后不可恢复')
    expect(confirmApi.confirmText.value).toBe('删除')
    expect(confirmApi.cancelText.value).toBe('取消')
    expect(confirmApi.intent.value).toBe('danger')

    confirmApi.resolveConfirm(true)
    await expect(pending).resolves.toBe(true)
    expect(confirmApi.visible.value).toBe(false)
  })
})
