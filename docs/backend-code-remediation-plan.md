# 后端代码规范整改文档

## 命名规范

- 持久化模型统一使用 `*Entity` 命名。
- 控制器入参统一使用 `*Request`，兼容保留少量 `*DTO`。
- 控制器出参统一使用 `*Response` 或 `*VO`。
- 非持久层对象转换组件统一使用 `*Assembler` 命名。
- `*Mapper` 后缀只保留给 MyBatis 持久层接口。

## 当前结论

本轮整改的核心结构性问题已经基本收敛，项目主干已达到可持续演进状态。

已完成的高优先级整改包括：

- 移除业务服务中的运行期 DDL，表结构统一回收到初始化脚本。
- 拆分超大类，降低控制器和服务类职责耦合。
- 收紧主链路异常边界，避免核心流程滥用 `catch (Exception)`。
- `CommonConfiguration` 已拆分，通配符导入已清理。
- MyBatis 注解 SQL 已统一迁移到 XML。
- 关键模块测试已补齐并通过。
- `auth-service` 已补齐 `ai_user_roles` 关系表，运行时角色改为由关系表驱动。

已验证命令：

- `mvn -pl agent-service -am test -DskipITs`
- `mvn -pl monitor-service -am test -DskipITs`
- `mvn test -DskipITs`

## 已完成项

### 1. 超大类拆分

- `agent-service/src/main/java/com/huah/ai/platform/agent/controller/AgentConversationController.java`
  - 编排逻辑已下沉到 `AgentConversationOrchestrator`
- `agent-service/src/main/java/com/huah/ai/platform/agent/multi/MultiAgentTraceService.java`
  - 已拆出 `MultiAgentStageRunner`
  - 已拆出 `MultiAgentTraceSupport`
- `gateway-service/src/main/java/com/huah/ai/platform/gateway/service/ModelGatewayService.java`
  - 已拆分模型工厂和统计落库职责
- `monitor-service/src/main/java/com/huah/ai/platform/monitor/service/MonitorQueryService.java`
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

- 仍保留少量初始化、审计降级、调度任务、外部依赖兼容场景下的兜底异常处理。
- 这些属于基础设施边界，不属于主业务链路失控。

### 5. MyBatis 访问规范

已完成：

- 项目中的注解 SQL 已清理。
- Mapper 接口不再承载业务 SQL。
- 新增 SQL 统一放到 XML 管理。

### 6. DTO / Entity / VO 边界

已完成：

- `auth-service`、`rag-service` 的持久化模型已统一为 `*Entity`。
- `rag-service` 知识库管理接口入参已从实体改为 `KnowledgeBaseRequest`。
- 控制器层已以响应对象为主，不再直接暴露持久化实体。
- 对象转换组件已统一改为 `*Assembler`。

### 7. RBAC 模型整改

已完成：

- 新增 `ai_user_roles` 用户角色关联表。
- `auth-service` 登录、用户查询、用户管理已从关联表聚合角色。
- 对外接口和前端仍兼容 `roles` 逗号分隔字符串，避免扩大联动修改范围。
- 用户创建和更新时会先校验角色是否合法，再写库，避免产生半成功数据。
- `ai_bot_permissions` 继续承担助手访问控制矩阵职责。

## 当前保留项

这些问题不影响当前编译、测试和交付，但建议后续继续推进：

### 1. 静态规则继续收紧

- 禁止通配符导入的静态规则
- 类长度、方法长度阈值规则
- 更细粒度的 Checkstyle / Spotless / PMD 规则

### 2. 非核心链路异常继续分类

当前仍有少量 `catch (Exception)` 位于以下类型代码：

- 初始化器
- 审计降级逻辑
- 调度任务
- 工具类外部依赖兼容分支

这些位置还可以继续细分，但已经不属于当前阻塞项。

### 3. 日志字段继续统一

- 核心错误日志统一补齐 `traceId`
- 对话链路统一补齐 `userId`、`agentType`、`sessionId`
- 外部依赖失败日志统一带上异常类型

## 模块结果

### agent-service

- 控制器编排拆分完成
- 多智能体追踪拆分完成
- 配置拆分完成
- 重点异常收口完成
- 单测补齐并通过

### gateway-service

- 运行期 DDL 清理完成
- 模型工厂职责拆分完成
- 统计落库职责拆分完成
- 相关测试通过

### monitor-service

- 查询聚合类拆分完成
- Trace / 反馈 / 导出职责拆分完成
- 门面化收口完成
- 模块测试通过

### auth-service

- 管理接口响应组装已改为 `Assembler`
- 登录与用户管理逻辑已切换到关系型 RBAC 聚合
- 用户角色改为通过 `ai_user_roles` 维护
- 初始化脚本与运行时逻辑已同步

### rag-service

- 与当前 ID 模型、构造器和审计接口相关测试已同步
- 模块测试通过
- 响应组装已改为 `Assembler`

## 最终状态

按本轮整改目标衡量：

- 结构性问题：主干已完成整改
- 主要规范问题：已完成
- 编译与测试验证：需要持续随代码增量验证
- 长期治理项：保留为后续增强

当前可视为“核心整改完成，进入持续治理阶段”。
