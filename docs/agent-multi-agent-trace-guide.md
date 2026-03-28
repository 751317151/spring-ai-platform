# Spring AI Platform 多智能体轨迹与恢复说明

## 文档目标

这份文档用于说明多智能体轨迹的结构、恢复动作和前后端联动方式，方便后续排障与人工干预。

## 轨迹模型

### Trace

`MultiAgentExecutionTrace` 关键字段包括：

1. `traceId`
2. `sessionId`
3. `userId`
4. `status`
5. `requestSummary`
6. `finalSummary`
7. `totalPromptTokens`
8. `totalCompletionTokens`
9. `totalLatencyMs`
10. `parentTraceId`
11. `recoverySourceTraceId`
12. `recoverySourceStepOrder`
13. `recoveryAction`

### Step

`MultiAgentExecutionStep` 关键字段包括：

1. `stepOrder`
2. `stage`
3. `agentName`
4. `inputSummary`
5. `outputSummary`
6. `success`
7. `recoverable`
8. `skipped`
9. `recoveryAction`
10. `sourceTraceId`
11. `sourceStepOrder`

## 标准阶段

当前默认阶段为：

1. `planner`
2. `executor`
3. `critic`

## 查询接口

1. `GET /api/v1/agent/multi/traces`
2. `GET /api/v1/agent/multi/traces/{traceId}`

## 恢复接口

`POST /api/v1/agent/multi/traces/{traceId}/recover`

请求体：

```json
{
  "stepOrder": 2,
  "action": "replay"
}
```

## 恢复动作说明

### `retry`

- 从指定步骤重新执行

### `replay`

- 从指定步骤开始重新跑后续链路

### `skip`

- 跳过指定步骤，继续执行后续汇总

## 前端联动

1. 聊天诊断面板支持查看轨迹、步骤与恢复动作
2. Agent 工作台支持带 `traceId` 跳转到聊天页和监控页

## 排障建议

1. 有轨迹但没步骤时，优先检查 `ai_multi_agent_trace_steps`
2. 步骤不可恢复时，优先检查 `recoverable`
3. 恢复后未生成新轨迹时，优先检查 `traceMapper.insert` 和 `stepMapper.insert`
