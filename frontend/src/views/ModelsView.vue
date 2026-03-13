<template>
  <section>
    <h2>模型网关</h2>
    <div class="panel">
      <h3>注册模型</h3>
      <div class="form-grid">
        <input v-model="form.modelId" placeholder="modelId" />
        <input v-model="form.displayName" placeholder="displayName" />
        <select v-model="form.providerType">
          <option>OPENAI_COMPATIBLE</option>
          <option>DOMESTIC_VENDOR</option>
          <option>SELF_HOSTED</option>
        </select>
        <input v-model="form.endpoint" placeholder="endpoint" />
      </div>
      <button @click="register">提交注册</button>
    </div>

    <div class="panel">
      <h3>推理测试</h3>
      <textarea v-model="prompt" rows="3" placeholder="请输入推理问题"></textarea>
      <button @click="infer">调用推理</button>
      <pre>{{ inferenceResult }}</pre>
    </div>

    <div class="panel">
      <h3>模型列表</h3>
      <button @click="loadModels">刷新列表</button>
      <table>
        <thead><tr><th>ID</th><th>名称</th><th>供应商</th><th>Endpoint</th></tr></thead>
        <tbody>
          <tr v-for="item in models" :key="item.modelId">
            <td>{{ item.modelId }}</td>
            <td>{{ item.displayName }}</td>
            <td>{{ item.providerType }}</td>
            <td>{{ item.endpoint }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { createModel, getModels, runInference } from '../api/platform'

const models = ref([])
const prompt = ref('请总结本周质控风险')
const inferenceResult = ref('')

const form = reactive({
  modelId: 'qwen2.5-72b',
  displayName: 'Qwen 2.5 72B',
  providerType: 'DOMESTIC_VENDOR',
  endpoint: 'https://llm.company.local/v1'
})

const loadModels = async () => {
  const res = await getModels()
  models.value = res.data
}

const register = async () => {
  await createModel({ ...form })
  await loadModels()
}

const infer = async () => {
  const preferredModel = models.value[0]?.modelId
  const res = await runInference({ prompt: prompt.value, preferredModel, fallbackEnabled: true })
  inferenceResult.value = JSON.stringify(res.data, null, 2)
}

onMounted(loadModels)
</script>
