import client from './client'
import type {
  LoginRequest,
  LoginResponse,
  AiUser,
  BotPermission,
  BotPermissionUpsertRequest,
  RoleOption,
  RoleTokenLimit,
  RoleTokenLimitUpsertRequest,
  RoleUpsertRequest,
  RoleUsage,
  UserTokenLimit,
  UserTokenLimitUpsertRequest,
  UserUpsertRequest
} from './types'

const BASE = '/api/v1/auth'

export function login(data: LoginRequest): Promise<LoginResponse> {
  return client.post(`${BASE}/login`, data)
}

export function refresh(refreshToken: string): Promise<LoginResponse> {
  return client.post(`${BASE}/refresh`, { refreshToken })
}

export function logout(refreshToken?: string): Promise<void> {
  return client.post(`${BASE}/logout`, refreshToken ? { refreshToken } : {})
}

export function getUsers(): Promise<AiUser[]> {
  return client.get(`${BASE}/users`)
}

export function getRoles(): Promise<RoleOption[]> {
  return client.get(`${BASE}/roles`)
}

export function createRole(data: RoleUpsertRequest): Promise<RoleOption> {
  return client.post(`${BASE}/roles`, data)
}

export function getRoleUsage(id: string): Promise<RoleUsage> {
  return client.get(`${BASE}/roles/${id}/usage`)
}

export function updateRole(id: string, data: RoleUpsertRequest): Promise<RoleOption> {
  return client.put(`${BASE}/roles/${id}`, data)
}

export function deleteRole(id: string): Promise<void> {
  return client.delete(`${BASE}/roles/${id}`)
}

export function getUser(id: string): Promise<AiUser> {
  return client.get(`${BASE}/users/${id}`)
}

export function createUser(data: UserUpsertRequest): Promise<AiUser> {
  return client.post(`${BASE}/users`, data)
}

export function updateUser(id: string, data: UserUpsertRequest): Promise<AiUser> {
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

export function createPermission(data: BotPermissionUpsertRequest): Promise<BotPermission> {
  return client.post(`${BASE}/permissions`, data)
}

export function updatePermission(id: string, data: BotPermissionUpsertRequest): Promise<BotPermission> {
  return client.put(`${BASE}/permissions/${id}`, data)
}

export function deletePermission(id: string): Promise<void> {
  return client.delete(`${BASE}/permissions/${id}`)
}

export function getRoleTokenLimits(): Promise<RoleTokenLimit[]> {
  return client.get(`${BASE}/role-token-limits`)
}

export function createRoleTokenLimit(data: RoleTokenLimitUpsertRequest): Promise<RoleTokenLimit> {
  return client.post(`${BASE}/role-token-limits`, data)
}

export function updateRoleTokenLimit(id: string, data: RoleTokenLimitUpsertRequest): Promise<RoleTokenLimit> {
  return client.put(`${BASE}/role-token-limits/${id}`, data)
}

export function deleteRoleTokenLimit(id: string): Promise<void> {
  return client.delete(`${BASE}/role-token-limits/${id}`)
}

export function getUserTokenLimits(): Promise<UserTokenLimit[]> {
  return client.get(`${BASE}/user-token-limits`)
}

export function createUserTokenLimit(data: UserTokenLimitUpsertRequest): Promise<UserTokenLimit> {
  return client.post(`${BASE}/user-token-limits`, data)
}

export function updateUserTokenLimit(id: string, data: UserTokenLimitUpsertRequest): Promise<UserTokenLimit> {
  return client.put(`${BASE}/user-token-limits/${id}`, data)
}

export function deleteUserTokenLimit(id: string): Promise<void> {
  return client.delete(`${BASE}/user-token-limits/${id}`)
}

export function getMyBots(): Promise<BotPermission[]> {
  return client.get(`${BASE}/my-bots`)
}
