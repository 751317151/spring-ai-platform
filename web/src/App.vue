<template>
  <router-view />
  <ConfirmDialog />
  <ToastNotification />
</template>

<script setup lang="ts">
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import ToastNotification from '@/components/common/ToastNotification.vue'
</script>
