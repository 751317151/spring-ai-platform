import type { FavoriteMessageRecord, LearningNoteRecord } from '@/api/types'

const FAVORITES_KEY = 'learning_center_favorites'
const NOTES_KEY = 'learning_center_notes'

export interface SaveFavoriteMessageResult {
  status: 'created' | 'updated' | 'deduplicated'
  record: FavoriteMessageRecord
}

function safeParse<T>(raw: string | null, fallback: T): T {
  if (!raw) {
    return fallback
  }
  try {
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

function persist<T>(key: string, value: T) {
  localStorage.setItem(key, JSON.stringify(value))
}

async function runServerSync(task: (api: typeof import('@/api/learning')) => Promise<unknown>) {
  try {
    const api = await import('@/api/learning')
    if (!api.isLearningCenterServerEnabled()) {
      return
    }
    await task(api)
  } catch {
    // keep local cache as the fallback path when server sync is unavailable
  }
}

function compactText(value: string) {
  return (value || '').replace(/\s+/g, ' ').trim().toLowerCase()
}

function mergeById<T extends { id: string }>(localItems: T[], remoteItems: T[]) {
  const map = new Map<string, T>()
  remoteItems.forEach((item) => map.set(item.id, item))
  localItems.forEach((item) => map.set(item.id, item))
  return [...map.values()]
}

export function normalizeTags(values?: string[] | string | null) {
  const list = Array.isArray(values)
    ? values
    : String(values || '')
      .split(/[,，\n]/)
      .map((item) => item.trim())

  return list
    .map((item) => item.trim())
    .filter(Boolean)
    .filter((item, index, all) => all.findIndex((entry) => entry.toLowerCase() === item.toLowerCase()) === index)
    .slice(0, 12)
}

function mergeTags(...groups: Array<string[] | undefined | null>) {
  return normalizeTags(groups.flatMap((group) => group || []))
}

function deriveFavoriteTags(record: FavoriteMessageRecord) {
  const tags = [
    record.agentType || '',
    record.role === 'assistant' ? '助手回复' : '用户提问',
    record.sessionConfigSnapshot?.model ? `模型:${record.sessionConfigSnapshot.model}` : ''
  ]
  return normalizeTags(tags)
}

function normalizeFavoriteRecord(record: FavoriteMessageRecord): FavoriteMessageRecord {
  return {
    ...record,
    tags: mergeTags(deriveFavoriteTags(record), record.tags),
    duplicateCount: Math.max(1, record.duplicateCount || 1),
    lastCollectedAt: record.lastCollectedAt || record.createdAt
  }
}

function favoriteSignature(record: FavoriteMessageRecord) {
  return `${record.role}|${compactText(record.content)}`
}

export function listFavoriteMessages(): FavoriteMessageRecord[] {
  return safeParse<FavoriteMessageRecord[]>(localStorage.getItem(FAVORITES_KEY), [])
    .map(normalizeFavoriteRecord)
    .sort((left, right) => (right.lastCollectedAt || right.createdAt) - (left.lastCollectedAt || left.createdAt))
}

export function getFavoriteMessage(id: string): FavoriteMessageRecord | null {
  return listFavoriteMessages().find((item) => item.id === id) || null
}

export function saveFavoriteMessage(record: FavoriteMessageRecord): SaveFavoriteMessageResult {
  const normalized = normalizeFavoriteRecord({
    ...record,
    createdAt: record.createdAt || Date.now()
  })
  const favorites = listFavoriteMessages()
  const byId = favorites.find((item) => item.id === normalized.id)
  if (byId) {
    const updated = normalizeFavoriteRecord({
      ...byId,
      ...normalized,
      tags: mergeTags(byId.tags, normalized.tags),
      duplicateCount: Math.max(byId.duplicateCount || 1, normalized.duplicateCount || 1),
      lastCollectedAt: Date.now()
    })
    const next = [updated, ...favorites.filter((item) => item.id !== normalized.id)].slice(0, 300)
    persist(FAVORITES_KEY, next)
    void runServerSync((api) => api.saveLearningFavorite(updated))
    return { status: 'updated', record: updated }
  }

  const duplicate = favorites.find((item) => favoriteSignature(item) === favoriteSignature(normalized))
  if (duplicate) {
    const merged = normalizeFavoriteRecord({
      ...duplicate,
      sessionSummary: duplicate.sessionSummary || normalized.sessionSummary,
      sessionId: duplicate.sessionId || normalized.sessionId,
      responseId: duplicate.responseId || normalized.responseId,
      agentType: duplicate.agentType || normalized.agentType,
      sessionConfigSnapshot: duplicate.sessionConfigSnapshot || normalized.sessionConfigSnapshot || null,
      tags: mergeTags(duplicate.tags, normalized.tags),
      duplicateCount: (duplicate.duplicateCount || 1) + 1,
      lastCollectedAt: Date.now()
    })
    const next = [merged, ...favorites.filter((item) => item.id !== duplicate.id)].slice(0, 300)
    persist(FAVORITES_KEY, next)
    void runServerSync((api) => api.saveLearningFavorite(merged))
    return { status: 'deduplicated', record: merged }
  }

  const next = [normalized, ...favorites].slice(0, 300)
  persist(FAVORITES_KEY, next)
  void runServerSync((api) => api.saveLearningFavorite(normalized))
  return { status: 'created', record: normalized }
}

export function removeFavoriteMessage(id: string) {
  persist(FAVORITES_KEY, listFavoriteMessages().filter((item) => item.id !== id))
  void runServerSync((api) => api.deleteLearningFavorite(id))
}

export function isFavoriteMessage(id: string) {
  return listFavoriteMessages().some((item) => item.id === id)
}

export function updateFavoriteMessageTags(id: string, tags: string[]) {
  const favorites = listFavoriteMessages()
  const target = favorites.find((item) => item.id === id)
  if (!target) {
    return null
  }
  const updated = normalizeFavoriteRecord({
    ...target,
    tags: mergeTags(tags, deriveFavoriteTags(target))
  })
  persist(FAVORITES_KEY, [updated, ...favorites.filter((item) => item.id !== id)])
  void runServerSync((api) => api.saveLearningFavorite(updated))
  return updated
}

function normalizeLearningNote(note: LearningNoteRecord): LearningNoteRecord {
  return {
    ...note,
    tags: normalizeTags(note.tags)
  }
}

export function listLearningNotes(): LearningNoteRecord[] {
  return safeParse<LearningNoteRecord[]>(localStorage.getItem(NOTES_KEY), [])
    .map(normalizeLearningNote)
    .sort((left, right) => right.updatedAt - left.updatedAt)
}

export function saveLearningNote(note: LearningNoteRecord) {
  const normalized = normalizeLearningNote(note)
  const next = [normalized, ...listLearningNotes().filter((item) => item.id !== note.id)].slice(0, 300)
  persist(NOTES_KEY, next)
  void runServerSync((api) => api.saveLearningNoteApi(normalized))
}

export function removeLearningNote(id: string) {
  persist(NOTES_KEY, listLearningNotes().filter((item) => item.id !== id))
  void runServerSync((api) => api.deleteLearningNoteApi(id))
}

export async function hydrateLearningCenterCacheFromServer() {
  try {
    const api = await import('@/api/learning')
    if (!api.isLearningCenterServerEnabled()) {
      return false
    }
    const localFavorites = listFavoriteMessages()
    const localNotes = listLearningNotes()
    const [favorites, notes] = await Promise.all([
      api.listLearningFavorites(),
      api.listLearningNotesApi()
    ])
    const mergedFavorites = mergeById(localFavorites, favorites)
    const mergedNotes = mergeById(localNotes, notes)
    persist(FAVORITES_KEY, mergedFavorites)
    persist(NOTES_KEY, mergedNotes)
    const remoteFavoriteIds = new Set(favorites.map((item) => item.id))
    const remoteNoteIds = new Set(notes.map((item) => item.id))
    await Promise.all([
      ...localFavorites
        .filter((item) => !remoteFavoriteIds.has(item.id))
        .map((item) => api.saveLearningFavorite(item).catch(() => {})),
      ...localNotes
        .filter((item) => !remoteNoteIds.has(item.id))
        .map((item) => api.saveLearningNoteApi(item).catch(() => {}))
    ])
    return true
  } catch {
    return false
  }
}
