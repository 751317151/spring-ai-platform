<template>
  <section>
    <h2>RAG 知识库</h2>
    <div class="panel">
      <h3>文档入库</h3>
      <input v-model="ingest.documentId" placeholder="documentId" />
      <select v-model="ingest.documentType">
        <option>PDF</option><option>WORD</option><option>EXCEL</option>
        <option>CHEMICAL_STRUCTURE</option><option>LAB_REPORT</option><option>PLAIN_TEXT</option>
      </select>
      <input v-model="ingest.businessDomain" placeholder="businessDomain" />
      <textarea v-model="ingest.content" rows="5" placeholder="文档内容"></textarea>
      <button @click="doIngest">执行入库</button>
      <p>切片数：{{ chunkCount }}</p>
    </div>

    <div class="panel">
      <h3>知识问答</h3>
      <input v-model="query.question" placeholder="问题" />
      <input v-model="query.businessDomain" placeholder="businessDomain" />
      <input v-model.number="query.topK" type="number" min="1" />
      <button @click="doQuery">查询</button>
      <pre>{{ answer }}</pre>
    </div>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ingestRag, queryRag } from '../api/platform'

const chunkCount = ref(0)
const answer = ref('')

const ingest = reactive({
  documentId: 'lab-report-001',
  documentType: 'LAB_REPORT',
  businessDomain: 'manufacturing',
  content: '生产批次A出现温度偏高，建议检查反应釜冷却系统并复核传感器校准记录。'
})

const query = reactive({
  question: '质控异常处理流程是什么？',
  businessDomain: 'manufacturing',
  topK: 3
})

const doIngest = async () => {
  const res = await ingestRag({ ...ingest })
  chunkCount.value = res.data.length
}

const doQuery = async () => {
  const res = await queryRag({ ...query })
  answer.value = res.data.answer
}
</script>
