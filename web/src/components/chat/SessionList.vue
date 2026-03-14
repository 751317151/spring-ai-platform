<template>
  <div class="session-list" v-if="chatStore.sessionList.length > 0">
    <div class="session-list-label">
      <span>对话列表</span>
      <button class="btn-new-chat" @click="chatStore.createNewSession()">+ 新对话</button>
    </div>
    <div
      v-for="session in chatStore.sessionList"
      :key="session.sessionId"
      class="session-item"
      :class="{ active: chatStore.currentSessionId === session.sessionId }"
      @click="chatStore.switchSession(session.sessionId)"
    >
      {{ session.summary || '新对话' }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
</script>
