<template>
  <div class="metric-card" :class="color">
    <div class="metric-label">{{ label }}</div>
    <div class="metric-value">
      <slot>{{ value }}</slot>
    </div>
    <div class="metric-sub">{{ sub }}</div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value?: string | number
  sub?: string
  color?: 'blue' | 'green' | 'amber' | 'purple'
}>()
</script>
