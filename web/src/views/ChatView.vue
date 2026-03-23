<template>
  <div class="chat-layout" style="height: calc(100vh - 56px - 48px)">
    <div class="chat-sidebar">
      <div style="padding: 12px 8px; font-size: 11px; font-weight: 500; text-transform: uppercase; letter-spacing: .08em; color: var(--text3)">AI 助手</div>
      <AgentList />
      <SessionList />
      <div style="padding: 8px; border-top: 1px solid var(--border)">
        <div class="form-group" style="margin: 0">
          <label class="form-label">模型</label>
          <select class="form-select" style="padding: 6px 10px">
            <option v-for="opt in MODEL_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
      </div>
    </div>
    <div class="chat-main">
      <div class="chat-header">
        <div class="chat-agent-icon" :style="{ background: 'var(--accent-dim)', fontSize: '18px' }">{{ agentConfig.icon }}</div>
        <div>
          <div style="font-size: 13px; font-weight: 500; color: var(--text)">{{ agentConfig.name }}</div>
          <div style="font-size: 11px; color: var(--text3)">{{ agentConfig.desc }}</div>
        </div>
        <div style="margin-left: auto; display: flex; gap: 8px">
          <button class="btn btn-ghost btn-sm" @click="chatStore.createNewSession()">新建对话</button>
          <button class="btn btn-ghost btn-sm" @click="chatStore.clearChat()">清空对话</button>
          <button class="btn btn-ghost btn-sm" @click="showToast('会话已导出')">导出</button>
        </div>
      </div>
      <BackendStatusBanner
        service="chat"
        demo-message="聊天页当前运行在演示模式，消息回答来自本地 mock 数据。"
        unavailable-message="聊天后端暂不可用，页面不会再自动回退到模拟回答。"
      />
      <ChatMessages />
      <ChatInput @send="handleSend" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AgentList from '@/components/chat/AgentList.vue'
import SessionList from '@/components/chat/SessionList.vue'
import ChatMessages from '@/components/chat/ChatMessages.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import BackendStatusBanner from '@/components/common/BackendStatusBanner.vue'
import { useChatStore } from '@/stores/chat'
import { useToast } from '@/composables/useToast'
import { MODEL_OPTIONS } from '@/utils/constants'

const chatStore = useChatStore()
const { showToast } = useToast()

const agentConfig = computed(() => chatStore.getAgentConfig())

function handleSend(message: string) {
  chatStore.sendMessage(message)
}
</script>
