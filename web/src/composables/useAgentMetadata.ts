import { computed, ref } from 'vue'
import { getAgentMetadata } from '@/api/agent'
import type { AgentMetadataItem } from '@/api/types'
import { AGENT_CONFIG } from '@/utils/constants'

export interface AgentViewConfig {
  agentType: string
  name: string
  icon: string
  color: string
  desc: string
  defaultModel?: string
  defaultTemperature?: number
  defaultMaxContextMessages?: number
  supportsKnowledge?: boolean
  supportsTools?: boolean
  supportsMultiAgentMode?: boolean
  supportsMultiStepRecovery?: boolean
  registered?: boolean
}

const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const metadataMap = ref<Record<string, AgentMetadataItem>>({})

function buildFallbackMap(): Record<string, AgentViewConfig> {
  return Object.entries(AGENT_CONFIG).reduce<Record<string, AgentViewConfig>>((acc, [agentType, config]) => {
    acc[agentType] = {
      agentType,
      name: config.name,
      icon: config.icon,
      color: config.color,
      desc: config.desc,
      defaultModel: 'auto',
      defaultTemperature: 0.7,
      defaultMaxContextMessages: 10,
      supportsKnowledge: agentType !== 'weather',
      supportsTools: true,
      supportsMultiAgentMode: agentType === 'multi',
      supportsMultiStepRecovery: false,
      registered: true
    }
    return acc
  }, {})
}

const fallbackMap = buildFallbackMap()

function mapMetadataItem(item: AgentMetadataItem): AgentViewConfig {
  const fallback = fallbackMap[item.agentType]
  return {
    agentType: item.agentType,
    name: item.name || fallback?.name || item.agentType,
    icon: item.icon || fallback?.icon || 'AI',
    color: item.color || fallback?.color || '#6b7280',
    desc: item.description || fallback?.desc || '',
    defaultModel: item.defaultModel ?? fallback?.defaultModel,
    defaultTemperature: item.defaultTemperature ?? fallback?.defaultTemperature,
    defaultMaxContextMessages: item.defaultMaxContextMessages ?? fallback?.defaultMaxContextMessages,
    supportsKnowledge: item.supportsKnowledge ?? fallback?.supportsKnowledge,
    supportsTools: item.supportsTools ?? fallback?.supportsTools,
    supportsMultiAgentMode: item.supportsMultiAgentMode ?? fallback?.supportsMultiAgentMode,
    supportsMultiStepRecovery: item.supportsMultiStepRecovery ?? fallback?.supportsMultiStepRecovery,
    registered: item.registered ?? fallback?.registered
  }
}

export function useAgentMetadata() {
  const agentMap = computed<Record<string, AgentViewConfig>>(() => {
    const merged: Record<string, AgentViewConfig> = { ...fallbackMap }
    Object.values(metadataMap.value).forEach((item) => {
      merged[item.agentType] = mapMetadataItem(item)
    })
    return merged
  })

  const agentList = computed<AgentViewConfig[]>(() =>
    Object.values(agentMap.value).filter((item) => item.registered !== false)
  )

  const agentLabelMap = computed<Record<string, string>>(() =>
    Object.values(agentMap.value).reduce<Record<string, string>>((acc, item) => {
      acc[item.agentType] = item.name
      return acc
    }, {})
  )

  async function loadAgentMetadata(force = false) {
    if (loading.value || (loaded.value && !force)) {
      return
    }
    loading.value = true
    error.value = ''
    try {
      const response = await getAgentMetadata()
      const nextMap: Record<string, AgentMetadataItem> = {}
      for (const item of response.agents || []) {
        nextMap[item.agentType] = item
      }
      metadataMap.value = nextMap
      loaded.value = true
    } catch (err) {
      error.value = err instanceof Error ? err.message : '助手元数据加载失败'
    } finally {
      loading.value = false
    }
  }

  function getAgentConfig(agentType: string): AgentViewConfig {
    return agentMap.value[agentType] || fallbackMap[agentType] || {
      agentType,
      name: agentType,
      icon: 'AI',
      color: '#6b7280',
      desc: ''
    }
  }

  return {
    loading,
    loaded,
    error,
    agentMap,
    agentList,
    agentLabelMap,
    loadAgentMetadata,
    getAgentConfig
  }
}
