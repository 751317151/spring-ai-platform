<template>
  <div class="agent-list">
    <div
      v-for="(config, type) in AGENT_CONFIG"
      :key="type"
      class="agent-item"
      :class="{ active: chatStore.currentAgent === type }"
      @click="chatStore.selectAgent(type as AgentType)"
    >
      <div class="agent-name">
        <span class="agent-dot" :style="{ background: config.color }"></span>
        {{ config.name }}
      </div>
      <div class="agent-desc">{{ config.desc }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useChatStore } from '@/stores/chat'
import { AGENT_CONFIG } from '@/utils/constants'
import type { AgentType } from '@/api/types'

const chatStore = useChatStore()
</script>
