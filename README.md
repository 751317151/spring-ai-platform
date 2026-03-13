# 企业 AI 平台 — Enterprise AI Platform

基于 **Spring Boot 3.3 + Spring AI 1.0** 的多服务企业 AI 平台，支持多模型路由、RAG 知识库、多领域 Agent、全链路监控。

---

## 架构概览

```
浏览器前端 (enterprise-ai-frontend.html)
     │
     ├── :8083  auth-service      JWT 认证 / RBAC 权限
     ├── :8082  agent-service     7 个垂直领域 AI Agent + Multi-Agent
     ├── :8081  rag-service       RAG 知识库 / 向量检索 / 文档入库
     ├── :8080  gateway-service   多模型路由 / 负载均衡 / 熔断
     └── :8084  monitor-service   Micrometer 指标 / 审计日志查询

基础设施
     ├── PostgreSQL :5432    用户/权限/审计日志/文档元数据
     ├── PGVector              1536 维向量索引（HNSW + Cosine）
     ├── Redis :6379           会话记忆 / Token 配额 / 黑名单
     ├── Prometheus :9090      指标采集
     └── Grafana :3000         可视化看板（admin/admin）
```

---

## 快速启动

### 1. 前置条件

```bash
# Java 21, Maven 3.9+, Docker & Docker Compose
java --version   # 21+
mvn --version    # 3.9+
docker --version # 24+
```

### 2. 启动基础设施

```bash
# 启动 PostgreSQL + Redis + Prometheus + Grafana
docker-compose up -d postgres redis prometheus grafana

# 等待 PostgreSQL 就绪（约 15 秒）
docker-compose logs -f postgres | grep "ready to accept"
```

### 3. 配置 API Key

复制 `.env.example` 为 `.env` 并填写 API Key：

```bash
cp .env.example .env
# 编辑 .env，至少填写一个 AI 提供商的 Key：
# OPENAI_API_KEY=sk-xxx          # OpenAI GPT-4o
# AI_BASE_URL=https://api.deepseek.com  # 国内推荐：DeepSeek（兼容 OpenAI 协议）
```

> **推荐**：使用 DeepSeek（`https://api.deepseek.com`），性价比最高，Spring AI OpenAI 兼容模式直接对接。

### 4. 构建并启动服务

```bash
# 构建所有模块
mvn clean package -DskipTests

# 方式一：Docker Compose 全量启动
docker-compose up -d

# 方式二：逐个启动（开发调试推荐）
cd auth-service    && java -jar target/auth-service-1.0.0.jar    &
cd agent-service   && java -jar target/agent-service-1.0.0.jar   &
cd rag-service     && java -jar target/rag-service-1.0.0.jar     &
cd gateway-service && java -jar target/gateway-service-1.0.0.jar &
cd monitor-service && java -jar target/monitor-service-1.0.0.jar &
```

### 5. 初始化演示用户

```bash
# 创建 5 个演示账号（密码均为 admin123）
curl -X POST http://localhost:8083/api/v1/auth/init-demo-users
```

| 用户名 | 角色 | 可访问 Agent |
|--------|------|-------------|
| admin | ADMIN | 全部 |
| rd_user | RD | 研发助手 |
| sales_user | SALES | 销售助手 |
| hr_user | HR | HR 助手 |
| finance_user | FINANCE | 财务助手 |

### 6. 打开前端

直接用浏览器打开 `enterprise-ai-frontend.html`，输入用户名/密码登录。

---

## 服务接口文档

### auth-service (:8083)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/login` | 登录，返回 JWT |
| POST | `/api/v1/auth/logout` | 登出，Token 加黑名单 |
| POST | `/api/v1/auth/refresh` | 刷新 Token |
| GET  | `/api/v1/auth/validate` | 验证 Token（内部调用）|
| POST | `/api/v1/auth/init-demo-users` | 初始化演示用户 |

### agent-service (:8082)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/agent/{type}/chat` | 普通对话，type: rd/sales/hr/finance/supply-chain/qc/multi |
| POST | `/api/v1/agent/{type}/chat/stream` | 流式 SSE 对话 |
| POST | `/api/v1/agent/multi/execute` | Multi-Agent 复杂任务 |

请求体：`{"message": "你的问题"}`
可选 Header：`X-User-Id`, `X-Session-Id`（不传使用默认值）

### rag-service (:8081)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/rag/query` | RAG 问答（返回 answer + sources）|
| POST | `/api/v1/rag/query/stream` | SSE 流式 RAG 问答 |
| POST | `/api/v1/rag/documents/upload` | 上传文档入库 |
| GET  | `/api/v1/rag/documents?knowledgeBaseId=kb-001` | 文档列表 |

问答请求体：`{"question": "...", "knowledgeBaseId": "kb-001", "topK": 5}`

### gateway-service (:8080)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/chat` | 通过网关路由到最优模型 |
| GET  | `/api/v1/models` | 查看已注册模型列表 |
| GET  | `/api/v1/models/health` | 模型健康状态 |

### monitor-service (:8084)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/v1/monitor/overview` | 整体指标概览 |
| GET  | `/api/v1/monitor/by-agent` | 按 Agent 分类统计 |
| GET  | `/api/v1/monitor/audit-logs` | 审计日志（?limit=20&userId=xxx）|
| GET  | `/api/v1/monitor/token-top-users` | Token 消耗排行 |
| GET  | `/api/v1/monitor/alerts` | 活跃告警 |
| GET  | `/api/v1/monitor/token-usage/{userId}` | 用户 Token 配额查询 |

---

## 监控

- **Prometheus**：http://localhost:9090
- **Grafana**：http://localhost:3000（admin / admin）
  - 看板已预置：*Enterprise AI Platform*，自动采集 5 个服务的 Micrometer 指标

---

## 知识库默认数据

| ID | 名称 | 说明 |
|----|------|------|
| kb-001 | 企业通用知识库 | 公司政策、HR 制度、行政规定 |
| kb-002 | 研发技术知识库 | API 规范、架构设计、代码标准 |
| kb-003 | 销售产品知识库 | 产品手册、报价策略、客户案例 |

上传文档：前端「知识库」页面拖拽上传，或调用 `/api/v1/rag/documents/upload`。

---

## 常见问题

**Q: 启动报 `Connection refused` 到 PostgreSQL？**
```bash
docker-compose up -d postgres && sleep 15 && docker-compose logs postgres
```

**Q: 前端聊天无响应？**
检查浏览器 Console，确认 `agent-service:8082` 已启动。前端会自动降级到演示模式。

**Q: 如何替换 AI 提供商？**
修改 `.env` 中的 `AI_BASE_URL` 和 `AI_MODEL`：
- DeepSeek: `https://api.deepseek.com` / `deepseek-chat`
- 通义千问: `https://dashscope.aliyuncs.com/compatible-mode/v1` / `qwen-plus`
- Moonshot: `https://api.moonshot.cn/v1` / `moonshot-v1-8k`

**Q: pgvector 扩展未安装？**
```sql
-- 在 PostgreSQL 中执行：
CREATE EXTENSION IF NOT EXISTS vector;
```
使用 `pgvector/pgvector:pg16` Docker 镜像则已内置。
