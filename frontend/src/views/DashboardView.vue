<template>
  <section>
    <h2>监控总览</h2>
    <button @click="load">刷新</button>
    <div class="grid">
      <article class="card"><h3>总请求数</h3><p>{{ data.totalRequests }}</p></article>
      <article class="card"><h3>错误率</h3><p>{{ (data.errorRate * 100).toFixed(2) }}%</p></article>
      <article class="card"><h3>平均延迟</h3><p>{{ data.averageLatencyMs.toFixed(2) }} ms</p></article>
      <article class="card"><h3>Token 消耗</h3><p>{{ data.totalTokensConsumed }}</p></article>
      <article class="card"><h3>异常计数</h3><p>{{ data.anomalyCount }}</p></article>
    </div>
  </section>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { getMonitoring } from '../api/platform'

const data = reactive({
  totalRequests: 0,
  errorRate: 0,
  averageLatencyMs: 0,
  totalTokensConsumed: 0,
  anomalyCount: 0
})

const load = async () => {
  const res = await getMonitoring()
  Object.assign(data, res.data)
}

onMounted(load)
</script>
