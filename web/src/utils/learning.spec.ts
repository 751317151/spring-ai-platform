import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FavoriteMessageRecord, LearningNoteRecord } from '@/api/types'
import {
  hydrateLearningCenterCacheFromServer,
  listFavoriteMessages,
  listLearningNotes,
  saveFavoriteMessage,
  saveLearningNote
} from './learning'

const learningApi = vi.hoisted(() => ({
  isLearningCenterServerEnabled: vi.fn(),
  listLearningFavorites: vi.fn(),
  listLearningNotesApi: vi.fn(),
  saveLearningFavorite: vi.fn(),
  saveLearningNoteApi: vi.fn(),
  deleteLearningFavorite: vi.fn(),
  deleteLearningNoteApi: vi.fn()
}))

vi.mock('@/api/learning', () => learningApi)

describe('learning utils', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('hydrates local cache from server and backfills missing local records', async () => {
    const localFavorite: FavoriteMessageRecord = {
      id: 'local-fav',
      role: 'assistant',
      content: 'local answer',
      createdAt: 1
    }
    const localNote: LearningNoteRecord = {
      id: 'local-note',
      title: '本地笔记',
      content: 'draft',
      tags: [],
      createdAt: 1,
      updatedAt: 1
    }

    saveFavoriteMessage(localFavorite)
    saveLearningNote(localNote)

    learningApi.isLearningCenterServerEnabled.mockReturnValue(true)
    learningApi.listLearningFavorites.mockResolvedValue([
      { id: 'remote-fav', role: 'assistant', content: 'remote answer', createdAt: 2 }
    ])
    learningApi.listLearningNotesApi.mockResolvedValue([
      { id: 'remote-note', title: '远端笔记', content: 'remote', tags: ['归档'], createdAt: 2, updatedAt: 2 }
    ])
    learningApi.saveLearningFavorite.mockResolvedValue(undefined)
    learningApi.saveLearningNoteApi.mockResolvedValue(undefined)

    const hydrated = await hydrateLearningCenterCacheFromServer()

    expect(hydrated).toBe(true)
    expect(listFavoriteMessages().map((item) => item.id)).toEqual(['remote-fav', 'local-fav'])
    expect(listLearningNotes().map((item) => item.id)).toEqual(['remote-note', 'local-note'])
    expect(learningApi.saveLearningFavorite).toHaveBeenCalledWith(expect.objectContaining({ id: 'local-fav' }))
    expect(learningApi.saveLearningNoteApi).toHaveBeenCalledWith(expect.objectContaining({ id: 'local-note' }))
  })

  it('keeps local-only mode when server is disabled', async () => {
    learningApi.isLearningCenterServerEnabled.mockReturnValue(false)

    saveFavoriteMessage({
      id: 'fav-1',
      role: 'assistant',
      content: 'answer',
      createdAt: 1
    })

    const hydrated = await hydrateLearningCenterCacheFromServer()

    expect(hydrated).toBe(false)
    expect(listFavoriteMessages()).toHaveLength(1)
    expect(learningApi.listLearningFavorites).not.toHaveBeenCalled()
  })
})
