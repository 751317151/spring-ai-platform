# Spring AI Enterprise Platform

基于 **Spring Boot + Spring AI** 的企业级 AI 平台参考实现，覆盖以下能力：

- 企业私有大模型推理平台（本地/混合云/私有化接入）
- 多模型服务网关（OpenAI 兼容、国产模型、自部署模型）
- RAG 知识库（多格式文档入库、切片、向量化、检索增强生成）
- AI Agent 应用框架（工具调用、Multi-Agent 协作、记忆管理）
- 企业级安全与监控（分层权限、指标采集、异常监控）
- **前后端分离 Vue3 管理台**（模型网关 / RAG / Agent / 监控可操作页面）

## 技术栈

### 后端
- Java 21
- Spring Boot 3.3
- Spring AI 1.0
- Spring Security + Actuator + Micrometer(Prometheus)
- 可选 PgVector 向量存储

### 前端
- Vue 3
- Vite
- Vue Router
- Axios

## 项目结构

```text
.
├── src/main/java/com/example/aiplatform      # Spring Boot 后端
├── src/main/resources/application.yml
└── frontend                                  # Vue3 管理台（前后端分离）
    ├── src/views
    │   ├── DashboardView.vue                 # 监控总览
    │   ├── ModelsView.vue                    # 模型网关
    │   ├── RagView.vue                       # RAG 知识库
    │   └── AgentView.vue                     # Agent 调度
    └── src/api/platform.js                   # 对接后端 API
```

## 启动方式

### 1) 启动后端

```bash
mvn spring-boot:run
```

后端默认端口：`8080`

默认账号：
- 普通业务账号：`ai-user / ai-user-pass`
- 管理员账号：`ai-admin / ai-admin-pass`

### 2) 启动前端管理台

```bash
cd frontend
npm install
npm run dev
```

前端默认端口：`5173`，已在 `vite.config.js` 中代理：
- `/api/** -> http://localhost:8080`
- `/actuator/** -> http://localhost:8080`

## 管理台功能

### 监控总览
- 实时查看总请求数、错误率、平均延迟、Token 消耗、异常计数。
- 对接接口：`GET /api/admin/monitoring`

### 模型网关
- 注册模型（支持 OPENAI_COMPATIBLE / DOMESTIC_VENDOR / SELF_HOSTED）
- 查询模型列表
- 发起推理调用
- 对接接口：
  - `POST /api/models`
  - `GET /api/models`
  - `POST /api/models/inference`

### RAG 知识库
- 文档入库（类型、领域、内容）
- 检索问答（问题、领域、TopK）
- 对接接口：
  - `POST /api/rag/ingest`
  - `POST /api/rag/query`

### Agent 调度
- 执行单 Agent 任务
- 生成 Multi-Agent 协作计划
- 对接接口：
  - `POST /api/agents/task`
  - `GET /api/agents/collaboration`

## 落地建议（对应需求）

1. **推理平台**：将 `ModelGatewayService` 对接真实网关（K8s Service Mesh + API Gateway + 熔断策略）。
2. **RAG 平台**：替换 `RagService` 中的模拟 embedding 为 Spring AI EmbeddingModel + PgVector/ES。
3. **Agent 平台**：将 `ToolOrchestrationService` 接入企业 API、审批流、数据库与消息系统。
4. **安全治理**：接入企业 SSO（OAuth2/OIDC）与细粒度 ABAC/RBAC 策略。
5. **监控告警**：Prometheus + Grafana + Alertmanager 监控 Token、耗时、错误率与异常请求。
