<template>
  <div class="empty-state">
    <div class="empty-state-icon">{{ icon }}</div>
    <div class="empty-state-title">{{ title }}</div>
    <div v-if="description" class="empty-state-desc">{{ description }}</div>
    <button v-if="actionText" class="btn btn-ghost btn-sm" @click="$emit('action')">{{ actionText }}</button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  icon?: string
  title: string
  description?: string
  actionText?: string
}>()

defineEmits<{
  (e: 'action'): void
}>()
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 8px;
  padding: 28px 16px;
  border: 1px dashed var(--border);
  border-radius: var(--r2);
  background: rgba(255,255,255,0.02);
}

.empty-state-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface2);
  color: var(--accent2);
  font-size: 20px;
}

.empty-state-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.empty-state-desc {
  max-width: 520px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text3);
}
</style>
