<template>
  <div v-if="visible" class="confirm-mask" @click.self="resolveConfirm(false)">
    <div class="confirm-dialog">
      <div class="confirm-eyebrow">确认操作</div>
      <div class="confirm-title">{{ title }}</div>
      <div v-if="description" class="confirm-desc">{{ description }}</div>
      <div class="confirm-actions">
        <button class="btn btn-ghost" @click="resolveConfirm(false)">{{ cancelText }}</button>
        <button class="btn" :class="intent === 'danger' ? 'btn-danger' : 'btn-primary'" @click="resolveConfirm(true)">
          {{ confirmText }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useConfirm } from '@/composables/useConfirm'

const {
  visible,
  title,
  description,
  confirmText,
  cancelText,
  intent,
  resolveConfirm
} = useConfirm()
</script>

<style scoped>
.confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(15, 23, 42, 0.58);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.confirm-dialog {
  width: min(440px, 100%);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r3);
  padding: 22px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.32);
}

.confirm-eyebrow {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text3);
}

.confirm-title {
  margin-top: 8px;
  font-size: 20px;
  font-weight: 600;
  color: var(--text);
}

.confirm-desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text2);
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
</style>
