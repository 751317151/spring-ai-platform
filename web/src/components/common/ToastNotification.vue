<template>
  <Teleport to="body">
    <div class="toast" :class="{ show: isVisible }">{{ toastMessage }}</div>
  </Teleport>
</template>

<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toastMessage, isVisible } = useToast()
</script>
