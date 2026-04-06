# 项目亮点验证清单

本文档用于验证本项目写入简历的亮点是否真实落地，避免只看文档描述或页面展示。

建议按以下顺序验证：

1. 启动基础依赖与服务
2. 验证后端接口
3. 验证前端页面联动
4. 验证数据库落库结果
5. 验证测试与构建

## 一、启动准备

### 1. 基础依赖

需要至少准备以下依赖：

- PostgreSQL
- Redis
- MinIO 或兼容 S3 的对象存储

如需本地一键验证，可直接使用：

```powershell
docker compose up -d
```

相关文件：

- [docker-compose.yml](/F:/Java/spring-ai-platform/docker-compose.yml)
- [init.sql](/F:/Java/spring-ai-platform/scripts/init.sql)

### 2. 启动服务

推荐启动顺序：

1. `auth-service`
2. `gateway-service`
3. `agent-service`
4. `rag-service`
5. `monitor-service`
6. `web`

后端统一验证命令：

```powershell
mvn test -DskipITs
```

前端验证命令：

```powershell
cd web
npm run build
```

## 二、亮点 1：认证鉴权与权限管理

### 可写亮点

- 支持登录、退出、刷新 Token、权限校验、用户管理、Bot 权限管理
- 前端支持 Token 自动续期，减少长时间操作后的重复登录

### 代码证据

- [AuthController.java](/F:/Java/spring-ai-platform/auth-service/src/main/java/com/huah/ai/platform/auth/controller/AuthController.java)
- [AuthTokenService.java](/F:/Java/spring-ai-platform/auth-service/src/main/java/com/huah/ai/platform/auth/service/AuthTokenService.java)
- [client.ts](/F:/Java/spring-ai-platform/web/src/api/client.ts)

### 接口验证

#### 1. 登录

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "userId": "admin",
  "password": "admin123"
}
```

预期结果：

- 返回 `token`
- 返回 `refreshToken`
- 返回 `userId`、`roles`、`department`

#### 2. 刷新 Token

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "..."
}
```

预期结果：

- 返回新的访问令牌
- 刷新后前端本地存储中的 token 发生变化

#### 3. 获取当前用户可访问助手

```http
GET /api/v1/auth/my-bots
Authorization: Bearer <token>
```

预期结果：

- 不同角色返回不同 Bot 列表

#### 4. 用户与权限管理

管理员验证：

- `GET /api/v1/auth/users`
- `GET /api/v1/auth/permissions`

非管理员验证：

- 访问应被拒绝

### 前端验证

验证步骤：

1. 登录进入系统
2. 停留一段时间，等待 access token 接近过期
3. 再点击任意需要后端请求的页面或操作

预期结果：

- 前端自动调用 refresh
- 不会立即跳转登录页
- refresh 失效后才跳转登录页

重点代码：

- [client.ts](/F:/Java/spring-ai-platform/web/src/api/client.ts)

### 测试验证

```powershell
mvn -pl auth-service -am test -DskipITs
```

## 三、亮点 2：RAG 知识库与文档入库链路

### 可写亮点

- 支持知识库管理、文档上传、切片、向量化、重试、重建索引、证据反馈、流式问答

### 代码证据

- [RagController.java](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/controller/RagController.java)
- [DocumentIngestionService.java](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/service/DocumentIngestionService.java)
- [DocumentMetaService.java](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/service/DocumentMetaService.java)
- [RagView.vue](/F:/Java/spring-ai-platform/web/src/views/RagView.vue)
- [RagDetailView.vue](/F:/Java/spring-ai-platform/web/src/views/RagDetailView.vue)

### 接口验证

#### 1. 知识库 CRUD

- `POST /api/v1/rag/knowledge-bases`
- `GET /api/v1/rag/knowledge-bases`
- `GET /api/v1/rag/knowledge-bases/{id}`
- `PUT /api/v1/rag/knowledge-bases/{id}`
- `DELETE /api/v1/rag/knowledge-bases/{id}`

预期结果：

- 可创建、编辑、删除知识库
- 删除有文档的知识库会失败

#### 2. 上传文档

```http
POST /api/v1/rag/documents/upload
Content-Type: multipart/form-data
```

表单参数：

- `file`
- `knowledgeBaseId`
- `replaceExisting`

预期结果：

- 返回文档元数据
- 同名文档在 `replaceExisting=true` 时可覆盖

#### 3. 查看文档分块

- `GET /api/v1/rag/documents/{id}/chunks`

预期结果：

- 返回 chunk 列表
- 含 chunk preview、chunk index 等字段

#### 4. 文档重试与重建索引

- `POST /api/v1/rag/documents/{id}/retry`
- `POST /api/v1/rag/documents/{id}/reindex`

预期结果：

- 失败文档可重试
- 已索引文档可重新构建索引

#### 5. RAG 问答

- `POST /api/v1/rag/query`
- `POST /api/v1/rag/query/stream`

预期结果：

- 返回回答结果
- 流式接口能持续返回增量内容

#### 6. 证据反馈

- `POST /api/v1/rag/feedback`
- `POST /api/v1/rag/feedback/evidence`

预期结果：

- 反馈提交成功
- 数据写入反馈表

### 数据库验证

重点检查以下表：

- `knowledge_bases`
- `document_meta`
- `vector_store`
- `ai_response_feedback`
- `ai_evidence_feedback`

示例 SQL：

```sql
select * from knowledge_bases order by created_at desc;
select * from document_meta order by created_at desc;
select metadata->>'doc_id', metadata->>'kb_id' from vector_store limit 20;
select * from ai_response_feedback order by created_at desc;
select * from ai_evidence_feedback order by created_at desc;
```

### 前端验证

验证页面：

- [RagView.vue](/F:/Java/spring-ai-platform/web/src/views/RagView.vue)
- [RagDetailView.vue](/F:/Java/spring-ai-platform/web/src/views/RagDetailView.vue)

验证步骤：

1. 打开知识库列表页
2. 点击某个知识库进入详情页
3. 在详情页上传文档
4. 查看文档列表与分块
5. 在右侧问答区提问

### 测试验证

```powershell
mvn -pl rag-service -am test -DskipITs
```

## 四、亮点 3：多助手与多智能体编排

### 可写亮点

- 支持普通助手对话、流式对话、多智能体执行链路、trace 跟踪、审计记录、失败回滚

### 代码证据

- [AgentConversationController.java](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/controller/AgentConversationController.java)
- [AgentConversationOrchestrator.java](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/service/AgentConversationOrchestrator.java)
- [MultiAgentTraceService.java](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/multi/MultiAgentTraceService.java)
- [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue)

### 接口验证

#### 1. 普通助手对话

```http
POST /api/v1/agents/rd/chat
Content-Type: application/json

{
  "message": "帮我分析一下最近的研发风险"
}
```

预期结果：

- 返回文本回答
- 返回 `responseId`

#### 2. 流式对话

```http
POST /api/v1/agents/rd/chat/stream
```

预期结果：

- SSE 持续返回 `chunk`
- 结束时返回 `done=true`

#### 3. 多智能体执行

```http
POST /api/v1/agents/multi/execute
Content-Type: application/json

{
  "task": "从研发、销售、财务三个视角分析某项目风险"
}
```

预期结果：

- 返回多阶段综合结果
- 结果链路可关联 trace

### 数据库验证

重点检查：

- `ai_audit_logs`
- `ai_tool_audit_logs`
- `ai_multi_agent_traces`
- `ai_multi_agent_trace_steps`

示例 SQL：

```sql
select * from ai_audit_logs order by created_at desc limit 20;
select * from ai_tool_audit_logs order by created_at desc limit 20;
select * from ai_multi_agent_traces order by created_at desc limit 20;
select * from ai_multi_agent_trace_steps order by created_at desc limit 50;
```

### 业务行为验证

重点观察：

- 额度不足时是否拒绝执行
- 无权限访问某助手时是否返回拒绝
- 流式失败时是否回滚最后一条用户消息
- 多智能体结果是否带 `traceId`

### 测试验证

```powershell
mvn -pl agent-service -am test -DskipITs
```

## 五、亮点 4：模型网关治理

### 可写亮点

- 支持模型注册、路由决策、健康探测、失败降级、负载均衡、调用统计与成本估算

### 代码证据

- [ModelGatewayService.java](/F:/Java/spring-ai-platform/gateway-service/src/main/java/com/huah/ai/platform/gateway/service/ModelGatewayService.java)
- [GatewayView.vue](/F:/Java/spring-ai-platform/web/src/views/GatewayView.vue)

### 验证项

#### 1. 路由策略

验证内容：

- `round-robin`
- `weighted`
- `least-latency`

预期结果：

- 切换策略后，路由预览和实际决策随之变化

#### 2. 健康探测

验证内容：

- 主动探活单模型
- 探活全部模型

预期结果：

- 模型状态更新为 healthy 或 degraded

#### 3. 失败降级

验证内容：

- 人为让某模型连续失败 3 次以上

预期结果：

- 模型被临时降级
- 路由优先选择其他健康模型

#### 4. 统计持久化

数据库验证：

```sql
select * from gateway_model_stats;
```

预期结果：

- `total_calls`
- `success_calls`
- `total_latency_ms`

这些字段会持续更新

### 前端验证

打开：

- [GatewayView.vue](/F:/Java/spring-ai-platform/web/src/views/GatewayView.vue)

重点观察：

- 路由预览
- 模型健康状态
- 降级状态
- 场景路由

### 测试验证

```powershell
mvn -pl gateway-service -am test -DskipITs
```

## 六、亮点 5：监控审计与运维可观测性

### 可写亮点

- 提供监控概览、Trace 详情、工具审计、告警流转、CSV 导出
- 提供 Prometheus 和 Grafana 基础设施

### 代码证据

- [MonitorController.java](/F:/Java/spring-ai-platform/monitor-service/src/main/java/com/huah/ai/platform/monitor/controller/MonitorController.java)
- [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)
- [prometheus.yml](/F:/Java/spring-ai-platform/monitoring/prometheus.yml)
- [ai-platform.json](/F:/Java/spring-ai-platform/monitoring/grafana/dashboards/ai-platform.json)

### 接口验证

- `GET /api/v1/monitor/overview`
- `GET /api/v1/monitor/audit-logs`
- `GET /api/v1/monitor/tool-audits`
- `GET /api/v1/monitor/trace/{traceId}`
- `GET /api/v1/monitor/alerts`
- `POST /api/v1/monitor/alerts/{fingerprint}/workflow`
- `GET /api/v1/monitor/export/slow-requests`

预期结果：

- 能查到真实数据
- Trace 可定位单次请求
- 告警支持状态流转
- 可导出 CSV

### 前端验证

打开：

- [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)

重点观察：

- 指标卡片
- 筛选上下文
- Trace 详情
- 工具执行记录
- 告警事件

### 基础设施验证

使用：

```powershell
docker compose up -d
```

验证：

- Prometheus 能启动
- Grafana 能启动
- 仪表盘可导入或自动加载

## 七、亮点 6：前端管理台不是静态壳

### 可写亮点

- 提供聊天、知识库、知识库详情、模型网关、监控、用户管理等前端工作台

### 代码证据

- [index.ts](/F:/Java/spring-ai-platform/web/src/router/index.ts)
- [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue)
- [RagView.vue](/F:/Java/spring-ai-platform/web/src/views/RagView.vue)
- [RagDetailView.vue](/F:/Java/spring-ai-platform/web/src/views/RagDetailView.vue)
- [GatewayView.vue](/F:/Java/spring-ai-platform/web/src/views/GatewayView.vue)
- [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)
- [UserView.vue](/F:/Java/spring-ai-platform/web/src/views/UserView.vue)

### 页面验证

逐页验证：

- `/chat`
- `/rag`
- `/rag/:kbId`
- `/gateway`
- `/monitor`
- `/users`

预期结果：

- 不是静态展示
- 都有真实接口调用
- 页面操作能影响后端数据

## 八、亮点 7：工程化能力

### 可写亮点

- 具备 CI、前后端构建和 Docker 镜像构建能力

### 代码证据

- [ci.yml](/F:/Java/spring-ai-platform/.github/workflows/ci.yml)

### 验证项

CI 文件中已包含：

- Maven 测试
- 前端构建
- Docker 镜像构建

本地验证：

```powershell
mvn test -DskipITs
cd web
npm run build
docker compose build
```

## 九、当前不建议直接写进简历的点

这些点目前仓库里证据不足，建议不要写得过满：

- 已在 K8s / Helm 上生产发布
- 经过系统化压测并有明确 QPS / P95 指标
- 已大规模线上稳定运行
- 已形成完整多租户 SaaS 能力

原因：

- 未发现 K8s / Helm 部署目录
- 未发现 k6 / JMeter / 压测报告
- 未发现线上规模数据或运行指标结论

## 十、面试前建议准备的证据材料

建议至少准备以下内容：

- 登录成功截图
- 聊天页面截图
- 知识库详情页截图
- 网关路由页截图
- 监控页截图
- 数据库审计表截图
- `mvn test -DskipITs` 成功截图
- `npm run build` 成功截图
- `docker compose up -d` 运行截图

## 十一、推荐自查结论模板

你可以按下面格式给自己做最终确认：

- 认证鉴权：已实现 / 部分实现 / 未实现
- RAG 知识库：已实现 / 部分实现 / 未实现
- 多智能体编排：已实现 / 部分实现 / 未实现
- 模型网关治理：已实现 / 部分实现 / 未实现
- 监控审计：已实现 / 部分实现 / 未实现
- 前端工作台：已实现 / 部分实现 / 未实现
- CI 与部署基础：已实现 / 部分实现 / 未实现
- 压测与生产化：证据不足，暂不写入简历

