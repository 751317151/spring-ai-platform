# Spring AI Platform Agent 架构说明

## 目标

这份文档用于说明 `agent-service` 当前的核心职责、关键链路、治理边界和扩展点，作为后续 Agent 优化与排障的统一架构基线。

## 模块边界

`agent-service` 当前主要承接 4 类职责：

1. Agent 统一接入
   - 多助手注册与路由
   - 统一聊天入口
   - 流式输出与会话记忆
2. Agent 治理与权限
   - Agent 访问校验
   - 工具级授权
   - 连接器/MCP/数据范围授权
3. Agent 可观测性
   - 聊天审计日志
   - 工具调用审计
   - 多智能体执行轨迹
4. Agent 运维工作台
   - Agent 元数据
   - 权限解释
   - MCP 状态
   - 工具审计
   - 多智能体轨迹与恢复

## 核心组件

### 1. Agent 接入层

- `AgentController`
- `AssistantAgentRegistry`
- `AssistantAgent`

### 2. 会话与上下文层

- `ConversationMemoryService`
- `SessionRuntimeInstructionBuilder`

### 3. 权限与治理层

- `AgentAccessChecker`
- `ToolSecurityService`
- `AgentAccessOverviewService`

### 4. 可观测性层

- `ToolAuditAspect`
- `AiAuditLogMapper`
- `AiToolAuditLogMapper`
- `MultiAgentTraceService`
- `AgentWorkbenchService`

### 5. 元数据层

- `AgentMetadataService`

## 关键链路

### 单助手聊天链路

1. 前端调用 `POST /api/v1/agent/{agentType}/chat`
2. `AgentController` 解析请求上下文
3. `AgentAccessChecker` 校验访问权限和配额
4. `ConversationMemoryService` 读取/更新会话配置
5. `AssistantAgentRegistry` 路由到具体助手
6. `ToolAuditAspect` 在工具调用时自动落审计日志
7. `AiAuditLogMapper` 落聊天审计
8. 前端根据 `traceId`、`responseId` 做联动展示

### 多智能体链路

1. 前端或聊天入口发起 `multi` Agent 请求
2. `MultiAgentTraceService` 创建新的执行轨迹
3. `MultiAgentOrchestrator` 按 `planner -> executor -> critic` 执行
4. 每一步都生成 `MultiAgentExecutionStep`
5. 执行结束后落 `MultiAgentExecutionTrace`
6. 前端通过 `traceId` 查看步骤明细并进行恢复

### 恢复链路

1. 前端调用 `POST /api/v1/agent/multi/traces/{traceId}/recover`
2. `MultiAgentTraceService` 加载原始轨迹与步骤
3. 根据 `retry / replay / skip` 选择恢复策略
4. 生成新的恢复轨迹并保留原始轨迹关联
5. 前端展示新轨迹及其来源关系

## 已完成的治理闭环

1. Agent 元数据中心基础版已落地
2. 连接器路径前缀、MCP 工具范围、数据范围控制已落地
3. 工具拒绝原因已结构化
4. 多智能体步骤恢复已落地
5. Agent 工作台趋势、排行、错误分布、健康摘要已落地

## 仍需继续完善

1. 更细的运行时资源隔离
2. Agent 长周期趋势与周报
3. 更系统的错误码治理
4. 生命周期归档与清理策略
