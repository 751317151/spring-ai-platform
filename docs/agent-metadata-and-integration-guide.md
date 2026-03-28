# Spring AI Platform Agent 元数据与接入规范

## 文档目标

这份文档用于统一 Agent 元数据定义、接入约束和前后端消费方式，避免再次出现同一类助手在多个地方重复维护的问题。

## 元数据来源

后端统一通过：

- `AgentMetadataService`
- `GET /api/v1/agent/metadata`

前端统一通过：

- `useAgentMetadata`

## 元数据字段

每个 Agent 至少应提供以下字段：

1. `agentType`
2. `name`
3. `description`
4. `icon`
5. `color`
6. `defaultModel`
7. `defaultTemperature`
8. `defaultMaxContextMessages`
9. `supportsKnowledge`
10. `supportsTools`
11. `supportsMultiAgentMode`
12. `supportsMultiStepRecovery`
13. `registered`

## 接入新 Agent 的最小步骤

1. 实现新的 `AssistantAgent`
2. 在 `AssistantAgentRegistry` 中注册
3. 在 `AgentMetadataService` 中补充该 Agent 的元数据
4. 确认权限规则
5. 验证前端聊天页、工作台、诊断面板都能正确展示

## 前后端约束

### 后端约束

1. `agentType` 必须全局唯一
2. 元数据必须准确反映能力边界
3. 不允许只在前端新增配置而不补后端元数据

### 前端约束

1. 优先读取后端元数据
2. 本地 `AGENT_CONFIG` 只作为兜底
3. 页面不要再硬编码 Agent 名称、颜色、图标和能力说明

## 推荐校验清单

1. `GET /api/v1/agent/metadata` 能返回新 Agent
2. 新 Agent 可以进入聊天页
3. 新 Agent 能进入 Agent 工作台
4. 若支持工具调用，工具审计可以落库
5. 若支持恢复能力，前端能显示恢复入口
