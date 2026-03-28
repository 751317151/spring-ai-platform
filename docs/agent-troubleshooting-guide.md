# Spring AI Platform Agent 排障手册

## 目标

这份文档用于给研发、测试和运维提供一套稳定的 Agent 排障路径，避免遇到问题时只能回到日志里盲找。

## 推荐排障顺序

1. 先看聊天页诊断面板
2. 再看 Agent 工作台
3. 再按 `traceId` 联动到监控页
4. 最后再回日志和数据库

## 场景一：Agent 无法访问

排查步骤：

1. 检查 `AgentAccessChecker`
2. 检查当前用户角色、部门
3. 检查对应 Agent 的访问规则
4. 检查是否触发令牌配额限制

## 场景二：工具调用失败

排查步骤：

1. 在诊断面板查看工具调用审计
2. 关注 `reasonCode`、`deniedResource`、`traceId`
3. 判断是工具未授权、资源越界还是外部依赖异常

## 场景三：MCP 看起来已配置，但实际不可用

排查步骤：

1. 查看 `diagnosticStatus`
2. 查看 `authorized`
3. 查看 `authorizedTools`
4. 查看 `runtimeHint` 和 `issueReason`

## 场景四：多智能体执行失败

排查步骤：

1. 打开轨迹详情
2. 定位失败步骤
3. 检查该步骤是否 `recoverable`
4. 根据场景选择 `retry`、`replay` 或 `skip`

## 查询入口

### 前端

1. `ChatAgentDiagnosticsPanel`
2. `AgentWorkbenchView`
3. 监控页 `traceId` 详情

### 后端接口

1. `GET /api/v1/agent/diagnostics/{agentType}`
2. `GET /api/v1/agent/access/{agentType}`
3. `GET /api/v1/agent/workbench/{agentType}`
4. `GET /api/v1/agent/tools/audit`
5. `GET /api/v1/agent/multi/traces`
6. `GET /api/v1/agent/multi/traces/{traceId}`
7. `POST /api/v1/agent/multi/traces/{traceId}/recover`

## 数据排查入口

1. `ai_audit_logs`
2. `ai_tool_audit_logs`
3. `ai_multi_agent_traces`
4. `ai_multi_agent_trace_steps`
