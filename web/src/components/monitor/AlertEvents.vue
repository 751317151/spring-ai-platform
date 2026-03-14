<template>
  <div style="display: flex; flex-direction: column; gap: 8px">
    <div v-if="!alerts.length" style="padding: 10px 12px; font-size: 12px; color: var(--text3); text-align: center">
      暂无告警
    </div>
    <div
      v-for="(alert, idx) in alerts"
      :key="idx"
      style="padding: 10px 12px; border-radius: var(--r); border: 1px solid; font-size: 12px"
      :style="alertStyle(alert.level)"
    >
      <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px">
        <span :style="{ color: levelColor(alert.level), fontWeight: 500 }">{{ levelIcon(alert.level) }} {{ alert.type }}</span>
        <span style="font-size: 10px; color: var(--text3)">{{ formatAlertTime(alert.time) }}</span>
      </div>
      <div style="color: var(--text2)">{{ alert.message }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AlertEvent } from '@/api/types'

defineProps<{ alerts: AlertEvent[] }>()

function levelIcon(level: string): string {
  if (level === 'ERROR') return '\u2717'
  if (level === 'WARNING') return '\u26A0'
  return '\u2713'
}

function levelColor(level: string): string {
  if (level === 'ERROR') return 'var(--red)'
  if (level === 'WARNING') return 'var(--amber)'
  return 'var(--green)'
}

function alertStyle(level: string): Record<string, string> {
  if (level === 'ERROR') return { background: 'var(--red-dim)', borderColor: 'rgba(240,96,96,0.2)' }
  if (level === 'WARNING') return { background: 'var(--amber-dim)', borderColor: 'rgba(245,166,35,0.2)' }
  return { background: 'var(--green-dim)', borderColor: 'rgba(61,214,140,0.2)' }
}

function formatAlertTime(time: string): string {
  if (!time) return ''
  try {
    return new Date(time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    return time
  }
}
</script>
