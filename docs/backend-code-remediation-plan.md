# 后端代码规范整改文档

## Naming Rules

- 持久化模型统一改为 `*Entity` 命名。
- 控制器入参对象统一收口为 `*Request`，兼容保留少量 `*DTO`。
- 控制器出参对象统一为 `*Response` / `*VO`。
- 非持久层对象转换组件统一改为 `*Assembler` 命名。
- `*Mapper` 后缀仅保留给 MyBatis 持久层接口。

## 当前结论

本轮整改的核心结构问题已经完成，项目已经达到可交付状态。

已完成的高优先级整改：
- 移除业务服务中的运行期 DDL，表结构统一回收到初始化脚本。
- 拆分超大类，降低控制器和服务类的职责耦合。
- 收紧重点链路的异常边界，避免主流程滥用 `catch (Exception)`。
- `CommonConfiguration` 已拆分，通配符导入已清理。
- MyBatis Mapper 注解 SQL 已清理为 XML 方案。
- 关键模块测试已补齐并通过。

已验证：
- `mvn -pl agent-service -am test -DskipITs`
- `mvn -pl monitor-service -am test -DskipITs`
- `mvn test -DskipITs`

## 已完成项

### 1. 超大类拆分

已完成：
- `agent-service/src/main/java/com/huah/ai/platform/agent/controller/AgentConversationController.java`
  - 已将编排逻辑下沉到 `AgentConversationOrchestrator`
- `agent-service/src/main/java/com/huah/ai/platform/agent/multi/MultiAgentTraceService.java`
  - 已拆出 `MultiAgentStageRunner`
  - 已拆出 `MultiAgentTraceSupport`
- `gateway-service/src/main/java/com/huah/ai/platform/gateway/service/ModelGatewayService.java`
  - 已拆出模型工厂和统计存储职责
- `monitor-service/src/main/java/com/huah/ai/platform/monitor/service/MonitorQueryService.java`
  - 已拆为门面类
  - 查询、Trace、反馈、CSV 导出已分别下沉到独立服务

### 2. 运行期 DDL 清理

已完成：
- `gateway-service/src/main/java/com/huah/ai/platform/gateway/service/ModelGatewayService.java`
- `scripts/init.sql`

结果：
- 服务启动不再依赖运行期改表。
- 数据库结构来源统一回到脚本。

### 3. 配置类拆分

已完成：
- 删除 `agent-service/src/main/java/com/huah/ai/platform/agent/config/CommonConfiguration.java`
- 拆分为：
  - `AgentMemoryConfiguration`
  - `AgentChatClientFactory`
  - `AssistantChatClientConfiguration`
  - `McpChatClientConfiguration`
  - `AgentSystemPrompts`

### 4. 异常边界收紧

已完成重点链路：
- `AgentConversationController`
- `MultiAgentTraceService`
- `SearchTools`
- `WeatherTools`
- `McpServerCatalogService`
- `ModelGatewayService`
- `MonitorQueryService`

说明：
- 仍有少量基础设施、初始化、审计降级类保留兜底异常处理，这是有意保留，不属于主业务链路失控。

### 5. MyBatis 访问规范

已完成：
- 项目中 Mapper 注解 SQL 已清理。
- Mapper 接口不再保留 `@Select` / `@Insert` / `@Update` / `@Delete` 的业务 SQL。
- 当前 SQL 已统一为 XML 管理。

### 6. 通配符导入清理

已完成：
- `agent-service/src/main/java/com/huah/ai/platform/agent/audit/AiAuditLog.java`
- `auth-service/src/main/java/com/huah/ai/platform/auth/model/AiRole.java`
- `auth-service/src/main/java/com/huah/ai/platform/auth/model/AiUser.java`
- `auth-service/src/main/java/com/huah/ai/platform/auth/model/BotPermission.java`

当前代码中已无业务源码通配符导入残留。

### 7. DTO / Entity / VO 边界

已完成主要接口层整改：
- `auth-service`、`rag-service` 持久化模型已统一收口为 `*Entity` 命名。
- `rag-service` 知识库管理接口入参已从持久化实体改为 `KnowledgeBaseRequest`。
- 控制器层已以响应对象为主，不再把核心持久化实体直接暴露为接口返回。
- 监控、网关、多智能体链路的接口出参已收口到 view/response 对象。
- 非持久层对象转换组件已统一为 `*Assembler` 命名。

## 当前保留项

这些不是阻塞项，不影响编译、测试和当前交付：

### 1. 静态规则进一步收紧

建议后续继续补：
- 禁止通配符导入的静态规则
- 类长度、方法长度阈值规则
- 更细粒度的 Checkstyle / Spotless / PMD 规则

### 2. 非核心链路异常进一步分类

当前仍有一些 `catch (Exception)` 位于以下类型代码中：
- 初始化器
- 审计降级逻辑
- 调度任务
- 工具类外部依赖兼容分支

这些位置可以继续细分，但不再属于本轮“必须整改”的阻塞问题。

### 3. 日志字段全局统一

建议继续推进：
- 所有核心错误日志统一补齐 `traceId`
- 对话链路统一补齐 `userId`、`agentType`、`sessionId`
- 外部依赖失败日志统一带上异常类型

## 模块结果

### agent-service

已完成：
- 控制器编排拆分
- 多智能体追踪拆分
- 配置拆分
- 重点异常收口
- 单测补齐并通过

### gateway-service

已完成：
- 运行期 DDL 清理
- 模型工厂职责拆分
- 统计落库职责拆分
- 相关测试通过

### monitor-service

已完成：
- 查询聚合类拆分
- Trace/反馈/导出职责拆分
- 门面化收口
- 模块测试通过

### auth-service

已完成：
- 用户登录模型与测试已同步到当前实现
- 通配符导入已清理
- 管理接口响应组装已改为 `Assembler` 命名

### rag-service

已完成：
- 与当前 ID 模型、构造器和审计接口相关测试已同步
- 模块测试通过
- 响应组装已改为 `Assembler` 命名

## 最终状态

如果按最初整改目标衡量：
- 结构性问题：已完成
- 主要规范问题：已完成
- 编译与测试验证：已完成
- 长期治理项：保留为后续增强

这份文档现在应视为“核心整改完成，进入持续治理阶段”。
