<template>
  <div class="drawer-mask">
    <aside class="drawer-shell">
      <div class="drawer-header">
        <div>
          <div class="drawer-title">角色引用详情</div>
          <div class="drawer-subtitle">{{ usage.roleName }}</div>
        </div>
        <button class="icon-btn" @click="emit('close')">关闭</button>
      </div>

      <div class="stats-grid">
        <div class="stat-card">
          <span class="stat-label">用户引用</span>
          <strong class="stat-value">{{ usage.userCount }}</strong>
        </div>
        <div class="stat-card">
          <span class="stat-label">助手引用</span>
          <strong class="stat-value">{{ usage.permissionCount }}</strong>
        </div>
      </div>

      <section class="detail-section">
        <div class="section-title">用户引用</div>
        <div v-if="usage.userReferences.length" class="reference-list">
          <div v-for="item in usage.userReferences" :key="item" class="reference-item">{{ item }}</div>
        </div>
        <EmptyState
          v-else
          icon="U"
          title="暂无用户引用"
          description="当前没有用户绑定这个角色。"
          variant="compact"
          align="left"
        />
      </section>

      <section class="detail-section">
        <div class="section-title">助手引用</div>
        <div v-if="usage.permissionReferences.length" class="reference-list">
          <div v-for="item in usage.permissionReferences" :key="item" class="reference-item">{{ item }}</div>
        </div>
        <EmptyState
          v-else
          icon="AI"
          title="暂无助手引用"
          description="当前没有助手使用这个角色。"
          variant="compact"
          align="left"
        />
      </section>
    </aside>
  </div>
</template>

<script setup lang="ts">
import type { RoleUsage } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'

defineProps<{ usage: RoleUsage }>()
const emit = defineEmits<{ close: [] }>()
</script>

<style scoped>
.drawer-mask { position: fixed; inset: 0; z-index: 1600; display: flex; justify-content: flex-end; background: rgba(15, 23, 42, 0.48); backdrop-filter: blur(6px); }
.drawer-shell { width: min(480px, 100%); height: 100%; padding: 24px; border-left: 1px solid rgba(148, 163, 184, 0.18); background: linear-gradient(180deg, rgba(15, 23, 42, 0.98), rgba(15, 23, 42, 0.94)); overflow-y: auto; }
.drawer-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.drawer-title { color: var(--text); font-size: 20px; font-weight: 700; }
.drawer-subtitle { margin-top: 6px; color: var(--text3); font-size: 13px; }
.icon-btn { border: 1px solid rgba(148, 163, 184, 0.18); border-radius: 10px; background: rgba(15, 23, 42, 0.46); color: var(--text2); padding: 8px 12px; font-size: 12px; cursor: pointer; }
.stats-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }
.stat-card { display: grid; gap: 6px; padding: 16px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 18px; background: rgba(15, 23, 42, 0.48); }
.stat-label { color: var(--text3); font-size: 12px; }
.stat-value { color: var(--text); font-size: 24px; }
.detail-section + .detail-section { margin-top: 18px; }
.section-title { margin-bottom: 10px; color: var(--text); font-size: 15px; font-weight: 600; }
.reference-list { display: grid; gap: 10px; }
.reference-item { padding: 12px 14px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 14px; background: rgba(15, 23, 42, 0.4); color: var(--text2); font-size: 13px; }
@media (max-width: 640px) { .stats-grid { grid-template-columns: 1fr; } }
</style>
