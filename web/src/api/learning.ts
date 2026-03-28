import client, { getAuthToken } from './client'
import type { FavoriteMessageRecord, FollowUpTemplateRecord, LearningNoteRecord } from './types'

const BASE = '/api/v1/agent/learning'

export function isLearningCenterServerEnabled() {
  return Boolean(getAuthToken())
}

export function listLearningFavorites(): Promise<FavoriteMessageRecord[]> {
  return client.get(`${BASE}/favorites`)
}

export function saveLearningFavorite(record: FavoriteMessageRecord): Promise<void> {
  return client.post(`${BASE}/favorites`, record)
}

export function deleteLearningFavorite(id: string): Promise<void> {
  return client.delete(`${BASE}/favorites/${id}`)
}

export function listLearningNotesApi(): Promise<LearningNoteRecord[]> {
  return client.get(`${BASE}/notes`)
}

export function saveLearningNoteApi(record: LearningNoteRecord): Promise<void> {
  return client.post(`${BASE}/notes`, record)
}

export function deleteLearningNoteApi(id: string): Promise<void> {
  return client.delete(`${BASE}/notes/${id}`)
}

export function listFollowUpTemplates(): Promise<FollowUpTemplateRecord[]> {
  return client.get(`${BASE}/templates`)
}

export function saveFollowUpTemplateApi(record: FollowUpTemplateRecord): Promise<void> {
  return client.post(`${BASE}/templates`, record)
}

export function deleteFollowUpTemplateApi(id: string): Promise<void> {
  return client.delete(`${BASE}/templates/${id}`)
}
