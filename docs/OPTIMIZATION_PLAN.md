# AI Platform 高并发优化计划

> 状态：已实施完成  |  编译验证：通过

## 第一阶段：基础治理

### 1.1 线程池治理 [已完成]
- **问题**: `newCachedThreadPool` 无上限，高并发下线程数可能爆炸
- **方案**: 改为有界线程池（核心16/最大64） + `CallerRunsPolicy` 拒绝策略
- **配置化**: 通过 `application.yml` 配置 `agent.executor.*` / `gateway.executor.*`
- **修改文件**:
  - `agent-service/.../config/AgentAsyncConfiguration.java`
  - `gateway-service/.../config/GatewayAsyncConfiguration.java`

### 1.2 接口幂等性 [已完成]
- **问题**: 用户快速连点"发送"会产生重复请求，无防重机制
- **方案**: Redis `X-Request-Id` 去重（TTL 10s），`@Idempotent` 注解 + 拦截器
- **新增文件**:
  - `platform-common/.../idempotent/Idempotent.java` — 注解
  - `platform-common/.../idempotent/IdempotentInterceptor.java` — Redis 去重拦截器
  - `platform-common/.../idempotent/IdempotentWebMvcConfigurer.java` — 注册拦截器
- **修改文件**:
  - `agent-service/.../controller/AgentConversationController.java` — 添加 `@Idempotent`
  - `agent-service/.../dto/AgentChatRequest.java` — 添加 `requestId` 字段

### 1.3 API 文档 [已完成]
- **问题**: 无 API 文档
- **方案**: 接入 SpringDoc OpenAPI 3，Swagger UI 访问 `/swagger-ui.html`
- **新增文件**:
  - `agent-service/.../config/OpenApiConfig.java`
- **修改文件**:
  - `agent-service/pom.xml` — 添加 `springdoc-openapi-starter-webmvc-ui` 依赖
  - `agent-service/.../resources/application.yml` — 添加 springdoc 配置
  - `AgentConversationController` — 添加 `@Tag`, `@Operation`
  - `AgentSessionController` — 添加 `@Tag`, `@Operation`
  - `AgentOperationsController` — 添加 `@Tag`, `@Operation`

## 第二阶段：性能与扩展

### 2.1 分布式限流 [已完成]
- **问题**: `AgentRuntimeIsolationService` 基于单实例内存，多实例部署失效
- **方案**: Redis Lua 脚本实现分布式信号量，通过 `agent.isolation.distributed=true` 启用
- **向后兼容**: 默认 `false`（本地模式），Redis 故障自动降级到本地模式
- **新增文件**:
  - `agent-service/.../resources/lua/distributed_semaphore_acquire.lua`
  - `agent-service/.../resources/lua/distributed_semaphore_release.lua`
- **修改文件**:
  - `agent-service/.../service/AgentRuntimeIsolationService.java` — 双模式（本地/分布式）

### 2.2 会话分页加载 [已完成]
- **问题**: `getHistory()` 一次加载全部消息
- **方案**: 对话历史加分页（`offset` + `limit`），返回 `total` 总数
- **修改文件**:
  - `agent-service/.../memory/ConversationMemoryService.java` — 新增 `getHistory(sessionId, offset, limit)` + `getHistoryCount()`
  - `agent-service/.../controller/AgentSessionController.java` — 历史接口增加 `offset`/`limit` 参数

### 2.3 本地缓存 [已完成]
- **问题**: 每次请求都查 Redis，无本地缓存
- **方案**: Caffeine 缓存会话配置（500 条，TTL 30s），`@Cacheable` / `@CacheEvict`
- **新增文件**:
  - `agent-service/.../config/CacheConfiguration.java`
- **修改文件**:
  - `agent-service/pom.xml` — 添加 `caffeine` 依赖
  - `agent-service/.../memory/ConversationMemoryService.java` — 添加 `@Cacheable`/`@CacheEvict`

## 第三阶段：体验增强

### 3.1 工具调用中间态推送 [已完成]
- **问题**: Agent 调用工具时用户看不到中间状态
- **方案**: 自定义 Spring AI `StreamAdvisor` 拦截工具调用，SSE 推送 `tool_status` 事件
- **前端对接**: 监听 SSE `tool_status` 事件类型，展示 "正在搜索..."、"正在查询数据库..." 等
- **新增文件**:
  - `agent-service/.../advisor/ToolExecutionStreamAdvisor.java`
- **修改文件**:
  - `agent-service/.../config/AgentChatClientFactory.java` — 新增 `buildChatClientWithToolStatus()` 方法

## 配置参考

```yaml
# agent-service application.yml

# 线程池配置
agent:
  executor:
    core-pool-size: 16
    max-pool-size: 64
    queue-capacity: 128
    keep-alive-seconds: 60
  isolation:
    distributed: false  # 设为 true 启用 Redis 分布式限流

# gateway-service application.yml
gateway:
  executor:
    core-pool-size: 8
    max-pool-size: 32
    queue-capacity: 64
    keep-alive-seconds: 60
```
