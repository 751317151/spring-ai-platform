<template>
  <section>
    <h2>Agent 调度</h2>
    <div class="panel">
      <h3>单 Agent 任务</h3>
      <select v-model="task.agentType">
        <option>RND_ASSISTANT</option>
        <option>QUALITY_ASSISTANT</option>
        <option>SALES_ASSISTANT</option>
        <option>COMPLIANCE_ASSISTANT</option>
        <option>SUPPLY_CHAIN_ASSISTANT</option>
        <option>HR_ADMIN_ASSISTANT</option>
        <option>FINANCE_ANALYSIS_ASSISTANT</option>
      </select>
      <input v-model="task.userId" placeholder="userId" />
      <textarea v-model="task.task" rows="3" placeholder="任务描述"></textarea>
      <button @click="execute">执行任务</button>
      <pre>{{ taskOutput }}</pre>
    </div>

    <div class="panel">
      <h3>Multi-Agent 协作</h3>
      <input v-model="scenario" placeholder="scenario" />
      <button @click="collaborate">生成协作计划</button>
      <pre>{{ plan }}</pre>
    </div>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { executeAgentTask, getCollaboration } from '../api/platform'

const taskOutput = ref('')
const plan = ref('')
const scenario = ref('跨部门异常闭环处理')

const task = reactive({
  agentType: 'QUALITY_ASSISTANT',
  userId: 'u1001',
  task: '请生成本月产线异常复盘与审批建议',
  context: {}
})

const execute = async () => {
  const res = await executeAgentTask({ ...task })
  taskOutput.value = res.data.result
}

const collaborate = async () => {
  const res = await getCollaboration(scenario.value)
  plan.value = res.data.plan
}
</script>
