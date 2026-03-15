<template>
  <div class="agent-list">
    <div
      v-for="agent in chatStore.agentList"
      :key="agent.type"
      class="agent-item"
      :class="{ active: chatStore.currentAgent === agent.type }"
      @click="chatStore.selectAgent(agent.type)"
    >
      <div class="agent-name">
        <span class="agent-dot" :style="{ background: agent.color }"></span>
        {{ agent.name }}
      </div>
      <div class="agent-desc">{{ agent.desc }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()

onMounted(() => {
  chatStore.loadAvailableBots()
})
</script>
