import client from './client'

export const getModels = () => client.get('/api/models')
export const createModel = (payload) => client.post('/api/models', payload)
export const runInference = (payload) => client.post('/api/models/inference', payload)

export const ingestRag = (payload) => client.post('/api/rag/ingest', payload)
export const queryRag = (payload) => client.post('/api/rag/query', payload)

export const executeAgentTask = (payload) => client.post('/api/agents/task', payload)
export const getCollaboration = (scenario) => client.get('/api/agents/collaboration', { params: { scenario } })

export const getMonitoring = () => client.get('/api/admin/monitoring')
