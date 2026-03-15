import client from './client'
import type { LoginRequest, LoginResponse, AiUser, BotPermission } from './types'

const BASE = '/api/v1/auth'

export function login(data: LoginRequest): Promise<LoginResponse> {
  return client.post(`${BASE}/login`, data)
}

export function logout(): Promise<void> {
  return client.post(`${BASE}/logout`)
}

export function getUsers(): Promise<AiUser[]> {
  return client.get(`${BASE}/users`)
}

export function getUser(id: string): Promise<AiUser> {
  return client.get(`${BASE}/users/${id}`)
}

export function createUser(data: Record<string, string>): Promise<AiUser> {
  return client.post(`${BASE}/users`, data)
}

export function updateUser(id: string, data: Record<string, unknown>): Promise<AiUser> {
  return client.put(`${BASE}/users/${id}`, data)
}

export function deleteUser(id: string): Promise<void> {
  return client.delete(`${BASE}/users/${id}`)
}

export function getPermissions(): Promise<BotPermission[]> {
  return client.get(`${BASE}/permissions`)
}

export function getPermission(id: string): Promise<BotPermission> {
  return client.get(`${BASE}/permissions/${id}`)
}

export function createPermission(data: Partial<BotPermission>): Promise<BotPermission> {
  return client.post(`${BASE}/permissions`, data)
}

export function updatePermission(id: string, data: Partial<BotPermission>): Promise<BotPermission> {
  return client.put(`${BASE}/permissions/${id}`, data)
}

export function deletePermission(id: string): Promise<void> {
  return client.delete(`${BASE}/permissions/${id}`)
}

export function getMyBots(): Promise<BotPermission[]> {
  return client.get(`${BASE}/my-bots`)
}
