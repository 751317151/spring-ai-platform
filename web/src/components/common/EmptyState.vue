<template>
  <div class="empty-state" :class="[`empty-state-${variant}`, `empty-state-${align}`]">
    <div v-if="badge || $slots.badge" class="empty-state-badge">
      <slot name="badge">{{ badge }}</slot>
    </div>
    <div class="empty-state-icon">{{ icon }}</div>
    <div class="empty-state-body">
      <div class="empty-state-title">{{ title }}</div>
      <div v-if="description" class="empty-state-desc">{{ description }}</div>
      <slot />
    </div>
    <div v-if="hasActions" class="empty-state-actions">
      <button v-if="actionText" class="btn btn-ghost btn-sm" @click="$emit('action')">{{ actionText }}</button>
      <button v-if="secondaryActionText" class="btn btn-primary btn-sm" @click="$emit('secondary-action')">{{ secondaryActionText }}</button>
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, useSlots } from 'vue'

const props = withDefaults(defineProps<{
  icon?: string
  title: string
  description?: string
  badge?: string
  actionText?: string
  secondaryActionText?: string
  variant?: 'compact' | 'section' | 'page'
  align?: 'center' | 'left'
}>(), {
  icon: '·',
  badge: '',
  description: '',
  actionText: '',
  secondaryActionText: '',
  variant: 'section',
  align: 'center'
})

defineEmits<{
  (e: 'action'): void
  (e: 'secondary-action'): void
}>()

const slots = useSlots()
const hasActions = computed(() => Boolean(props.actionText || props.secondaryActionText || slots.actions))
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  border: 1px dashed var(--border);
  border-radius: 18px;
  background:
    radial-gradient(circle at top, rgba(79, 142, 247, 0.12), transparent 48%),
    rgba(255, 255, 255, 0.02);
}

.empty-state-section,
.empty-state-page {
  padding: 28px 18px;
}

.empty-state-compact {
  padding: 18px 14px;
  gap: 10px;
  border-radius: 14px;
}

.empty-state-center {
  align-items: center;
  text-align: center;
}

.empty-state-left {
  align-items: flex-start;
  text-align: left;
}

.empty-state-page {
  min-height: 240px;
}

.empty-state-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(79, 142, 247, 0.1);
  color: var(--accent2);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.empty-state-icon {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(79, 142, 247, 0.2), rgba(61, 214, 140, 0.12));
  color: var(--accent2);
  font-size: 20px;
  font-weight: 700;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.empty-state-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 560px;
}

.empty-state-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.empty-state-desc {
  font-size: 12px;
  line-height: 1.7;
  color: var(--text3);
}

.empty-state-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
