# Spring AI Platform Agent 工具接入规范

## 文档目标

这份文档用于统一 Agent 工具接入、权限治理和审计要求，确保工具能力从“能用”提升到“可治理、可排障”。

## 工具分类

当前工具大致分为 4 类：

1. 基础工具
2. 内部接口工具
3. 数据分析工具
4. MCP 工具

## 接入要求

### 工具定义

新工具需要满足：

1. 有明确工具名
2. 有清晰输入输出
3. 有稳定错误提示
4. 能被 `ToolAuditAspect` 审计

### 安全治理

工具接入后必须确认以下规则：

1. `agentToolAllowlist`
2. `agentConnectorAllowlist`
3. `agentConnectorResourceAllowlist`
4. `agentMcpServerAllowlist`
5. `agentMcpToolAllowlist`
6. `agentDataScopeAllowlist`

## 资源级权限规则

### 连接器

连接器除了“连接器编码级”授权，还要确认：

1. 允许访问哪些路径前缀
2. 是否允许访问共享路径
3. 是否允许跨部门资源

### MCP

MCP 除了“服务级”授权，还要确认：

1. 哪些 Agent 可以访问该服务
2. 哪些 Agent 只能访问部分工具
3. 前端是否能展示授权工具范围

### 数据分析

数据分析类工具必须明确：

1. 允许访问哪些 schema
2. 允许访问哪些 table
3. 是否存在兜底通配规则

## 审计要求

每次工具调用至少要能记录：

1. `agentType`
2. `toolName`
3. `inputSummary`
4. `outputSummary`
5. `success`
6. `errorMessage`
7. `reasonCode`
8. `deniedResource`
9. `latencyMs`
10. `traceId`

## 拒绝结构

常见拒绝类型：

1. `TOOL_DENIED`
2. `CONNECTOR_DENIED`
3. `CONNECTOR_RESOURCE_DENIED`
4. `MCP_DENIED`
5. `DATA_SCOPE_DENIED`

## 接入自检清单

1. 工具能被正常调用
2. 被拒绝时能返回结构化原因
3. 审计日志可按 `traceId` 检索
4. Agent 诊断面板可看到该工具审计
5. Agent 工作台能统计到该工具调用
