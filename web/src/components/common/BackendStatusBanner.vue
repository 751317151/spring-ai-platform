<template>
  <div v-if="visible" class="status-banner" :class="variant">
    <strong>{{ title }}</strong>
    <span>{{ message }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRuntimeStore } from '@/stores/runtime'
import type { BackendServiceKey } from '@/stores/runtime'

const props = defineProps<{
  service: BackendServiceKey
  demoMessage?: string
  unavailableMessage?: string
}>()

const runtimeStore = useRuntimeStore()

const status = computed(() => runtimeStore.getServiceStatus(props.service))
const visible = computed(() => runtimeStore.demoMode || !status.value.available)
const variant = computed(() => (runtimeStore.demoMode ? 'demo' : 'warning'))
const title = computed(() => (runtimeStore.demoMode ? '演示模式' : '后端不可用'))
const message = computed(() => {
  if (runtimeStore.demoMode) {
    return props.demoMessage || '当前页面展示的是本地演示数据，不代表真实后端返回结果。'
  }
  return status.value.message || props.unavailableMessage || '后端服务当前不可用，请稍后重试。'
})
</script>
