<template>
  <div class="alert-list">
    <div v-if="!alerts.length" class="alert-empty">
      当前没有告警事件，系统状态看起来正常。
    </div>
    <div
      v-for="(alert, idx) in alerts"
      :key="alert.fingerprint || idx"
      class="alert-card"
      :style="alertStyle(alert.level)"
    >
      <div class="alert-top">
        <span :style="{ color: levelColor(alert.level), fontWeight: 600 }">{{ levelIcon(alert.level) }} {{ alert.type }}</span>
        <span class="alert-meta">{{ formatAlertTime(alert.time) }}</span>
        <span v-if="alert.source" class="alert-meta">来源：{{ alert.source }}</span>
        <span v-if="alert.status" class="alert-meta">状态：{{ alert.status }}</span>
        <a
          v-if="alert.silenceUrl"
          :href="alert.silenceUrl"
          target="_blank"
          rel="noreferrer"
          class="alert-link"
        >
          创建静默
        </a>
      </div>
      <div class="alert-message">{{ alert.message }}</div>
      <div v-if="alert.fingerprint" class="alert-fingerprint">
        指纹：{{ alert.fingerprint }}
      </div>
      <div v-if="labelEntries(alert).length" class="alert-labels">
        <span
          v-for="[key, value] in labelEntries(alert)"
          :key="`${key}-${value}`"
          class="alert-label-chip"
        >
          {{ key }}={{ value }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AlertEvent } from '@/api/types'

defineProps<{ alerts: AlertEvent[] }>()

function levelIcon(level: string): string {
  if (level === 'ERROR') return '[错误]'
  if (level === 'WARNING') return '[警告]'
  return '[正常]'
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

function labelEntries(alert: AlertEvent): Array<[string, string]> {
  return Object.entries(alert.labels || {})
}
</script>

<style scoped>
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.alert-empty {
  padding: 16px 12px;
  font-size: 12px;
  color: var(--text3);
  text-align: center;
  border: 1px dashed var(--border);
  border-radius: var(--r2);
}

.alert-card {
  padding: 12px;
  border-radius: var(--r2);
  border: 1px solid;
  font-size: 12px;
  transition: transform var(--transition), box-shadow var(--transition), border-color var(--transition);
}

.alert-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.1);
}

.alert-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.alert-meta {
  font-size: 10px;
  color: var(--text3);
}

.alert-link {
  font-size: 10px;
  color: var(--accent);
  text-decoration: underline;
}

.alert-message {
  color: var(--text2);
}

.alert-fingerprint {
  margin-top: 6px;
  font-size: 10px;
  color: var(--text3);
}

.alert-labels {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.alert-label-chip {
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(255,255,255,0.06);
  color: var(--text3);
  font-size: 10px;
}
</style>
