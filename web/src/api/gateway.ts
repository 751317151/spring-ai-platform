import client from './client'
import type { GatewayModelsResponse, GatewayRouteDecisionPreview } from './types'

const BASE = '/api/v1/chat'

export function getModels(): Promise<GatewayModelsResponse> {
  return client.get(`${BASE}/models`)
}

export function updateLoadBalance(strategy: string): Promise<string> {
  return client.put(`${BASE}/config/load-balance`, { strategy })
}

export function getRouteDecisionPreview(scene = 'default', requestedModelId?: string): Promise<GatewayRouteDecisionPreview> {
  const params: Record<string, string> = { scene }
  if (requestedModelId) {
    params.requestedModelId = requestedModelId
  }
  return client.get(`${BASE}/route-decision`, { params })
}
