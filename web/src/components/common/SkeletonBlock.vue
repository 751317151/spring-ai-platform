<template>
  <div class="skeleton-block" :class="[`skeleton-block-${variant}`]" :style="containerStyle">
    <div
      v-for="idx in count"
      :key="idx"
      class="skeleton skeleton-item"
      :style="{ height: `${height}px` }"
    ></div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  count?: number
  height?: number
  gap?: number
  minWidth?: number
  variant?: 'list' | 'grid'
}>(), {
  count: 3,
  height: 60,
  gap: 10,
  minWidth: 220,
  variant: 'list'
})

const containerStyle = computed(() => ({
  gap: `${props.gap}px`,
  gridTemplateColumns: props.variant === 'grid' ? `repeat(auto-fit, minmax(${props.minWidth}px, 1fr))` : '1fr'
}))
</script>

<style scoped>
.skeleton-block {
  display: grid;
}

.skeleton-item {
  width: 100%;
  border-radius: 14px;
}
</style>
