import client from './client'
import type { GatewayModelsResponse } from './types'

const BASE = '/api/v1/chat'

export function getModels(): Promise<GatewayModelsResponse> {
  return client.get(`${BASE}/models`)
}

export function updateLoadBalance(strategy: string): Promise<string> {
  return client.put(`${BASE}/config/load-balance`, { strategy })
}
