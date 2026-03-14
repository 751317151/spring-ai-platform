<template>
  <div class="app">
    <AppSidebar />
    <div class="main">
      <AppHeader />
      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
  <ToastNotification />
</template>

<script setup lang="ts">
import AppSidebar from '@/components/common/AppSidebar.vue'
import AppHeader from '@/components/common/AppHeader.vue'
import ToastNotification from '@/components/common/ToastNotification.vue'
</script>
