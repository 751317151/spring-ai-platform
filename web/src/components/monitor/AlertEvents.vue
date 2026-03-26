<template>
  <div class="alert-list">
    <EmptyState
      v-if="!alerts.length"
      icon="OK"
      title="当前没有告警事件"
      description="系统目前运行稳定，新的异常会在这里集中展示。"
      variant="compact"
      align="left"
    />
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
        <span v-if="alert.workflowStatus" class="alert-meta">流转：{{ workflowLabel(alert.workflowStatus) }}</span>
        <span v-if="alert.silencedUntil" class="alert-meta">静默至：{{ formatHistoryTime(alert.silencedUntil) }}</span>
        <a v-if="alert.silenceUrl" :href="alert.silenceUrl" target="_blank" rel="noreferrer" class="alert-link">前往 Alertmanager</a>
      </div>
      <div class="alert-message">{{ alert.message }}</div>
      <div v-if="alert.workflowNote" class="alert-fingerprint">备注：{{ alert.workflowNote }}</div>
      <div v-if="alert.fingerprint" class="alert-fingerprint">指纹：{{ alert.fingerprint }}</div>
      <div v-if="labelEntries(alert).length" class="alert-labels">
        <span v-for="[key, value] in labelEntries(alert)" :key="`${key}-${value}`" class="alert-label-chip">{{ key }}={{ value }}</span>
      </div>
      <div v-if="alert.fingerprint" class="alert-actions">
        <button class="alert-action-btn" @click="$emit('workflow', { fingerprint: alert.fingerprint, workflowStatus: 'acknowledged' })">确认</button>
        <button class="alert-action-btn" @click="$emit('workflow', { fingerprint: alert.fingerprint, workflowStatus: 'processing' })">处理中</button>
        <button class="alert-action-btn" @click="$emit('workflow', { fingerprint: alert.fingerprint, workflowStatus: 'resolved' })">已恢复</button>
        <button class="alert-action-btn" @click="$emit('silence', { fingerprint: alert.fingerprint, hours: 2 })">静默 2 小时</button>
        <button class="alert-action-btn" @click="$emit('silence', { fingerprint: alert.fingerprint, hours: 24 })">静默 24 小时</button>
        <button class="alert-action-btn" @click="$emit('note', alert)">备注</button>
        <button class="alert-action-btn" @click="$emit('history', alert.fingerprint)">查看历史</button>
      </div>
      <div v-if="alert.fingerprint && histories[alert.fingerprint]?.length" class="alert-history">
        <div class="alert-history-title">最近处理记录</div>
        <div
          v-for="item in histories[alert.fingerprint]"
          :key="`${item.createdAt}-${item.workflowStatus}-${item.workflowNote || ''}`"
          class="alert-history-item"
        >
          <span class="alert-history-time">{{ formatHistoryTime(item.createdAt) }}</span>
          <span class="alert-history-status">{{ workflowLabel(item.workflowStatus) }}</span>
          <span v-if="item.silencedUntil" class="alert-history-note">静默到 {{ formatHistoryTime(item.silencedUntil) }}</span>
          <span v-if="item.workflowNote" class="alert-history-note">{{ item.workflowNote }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import EmptyState from '@/components/common/EmptyState.vue'
import type { AlertEvent, AlertWorkflowHistory } from '@/api/types'

withDefaults(defineProps<{
  alerts: AlertEvent[]
  histories?: Record<string, AlertWorkflowHistory[]>
}>(), {
  histories: () => ({})
})

defineEmits<{
  (e: 'workflow', payload: { fingerprint: string; workflowStatus: string }): void
  (e: 'silence', payload: { fingerprint: string; hours: number }): void
  (e: 'note', alert: AlertEvent): void
  (e: 'history', fingerprint: string): void
}>()

function levelIcon(level: string): string {
  if (level === 'ERROR') return '[错误]'
  if (level === 'WARNING') return '[警告]'
  return '[提示]'
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

function formatHistoryTime(time?: string): string {
  if (!time) return ''
  try {
    return new Date(time).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  } catch {
    return time
  }
}

function labelEntries(alert: AlertEvent): Array<[string, string]> {
  return Object.entries(alert.labels || {})
}

function workflowLabel(value: string) {
  if (value === 'acknowledged') return '已确认'
  if (value === 'processing') return '处理中'
  if (value === 'resolved') return '已恢复'
  if (value === 'silenced') return '已静默'
  return value
}
</script>

<style scoped>
.alert-list { display: flex; flex-direction: column; gap: 8px; }
.alert-card { padding: 12px; border-radius: var(--r2); border: 1px solid; font-size: 12px; transition: transform var(--transition), box-shadow var(--transition), border-color var(--transition); }
.alert-card:hover { transform: translateY(-2px); box-shadow: 0 12px 24px rgba(15, 23, 42, 0.1); }
.alert-top { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
.alert-meta { font-size: 10px; color: var(--text3); }
.alert-link { font-size: 10px; color: var(--accent); text-decoration: underline; }
.alert-message { color: var(--text2); }
.alert-fingerprint { margin-top: 6px; font-size: 10px; color: var(--text3); }
.alert-labels { margin-top: 6px; display: flex; flex-wrap: wrap; gap: 6px; }
.alert-label-chip { padding: 2px 6px; border-radius: 999px; background: rgba(255,255,255,0.06); color: var(--text3); font-size: 10px; }
.alert-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.alert-action-btn { border: 1px solid var(--border); background: rgba(255,255,255,0.4); color: var(--text2); border-radius: 999px; font-size: 11px; padding: 4px 10px; cursor: pointer; }
.alert-action-btn:hover { color: var(--text); border-color: var(--accent); }
.alert-history { margin-top: 10px; padding-top: 10px; border-top: 1px dashed rgba(148, 163, 184, 0.25); }
.alert-history-title { margin-bottom: 8px; color: var(--text); font-size: 12px; font-weight: 600; }
.alert-history-item { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 6px; color: var(--text3); }
.alert-history-time { color: var(--text2); }
.alert-history-status { color: var(--text); font-weight: 500; }
.alert-history-note { color: var(--text3); }
</style>
