# 后端分层开发规范

## Naming Rules

- `*Mapper` 后缀只允许用于 MyBatis 持久层接口。
- 持久化模型统一使用 `*Entity` 命名。
- 控制器入参对象优先使用 `*Request`，兼容 `*DTO`。
- 控制器出参对象统一使用 `*Response` 或 `*VO`。
- DTO、VO、Response 等对象转换组件统一使用 `*Assembler` 命名。
- 禁止新增 `*ViewMapper`、`*ResponseMapper` 这类非持久层 `Mapper` 命名。

## 目标

统一当前项目后端开发方式，明确 `controller`、`service`、`mapper`、`entity/dto/vo` 的职责边界，避免控制器膨胀、业务逻辑分散、数据访问越层和后期维护困难。

本文档适用于：
- `auth-service`
- `gateway-service`
- `rag-service`
- `agent-service`
- `monitor-service`

## 核心原则

后端代码默认遵守以下调用链：

`controller -> service -> mapper`

对于复杂业务编排场景，允许扩展为：

`controller -> orchestrator/application service -> domain service -> mapper`

无论采用哪种形式，都必须满足：
- `controller` 不承载业务逻辑
- `mapper` 不承载业务逻辑
- 业务规则统一落在 `service` 或编排层

## 分层职责

### 1. Controller 层

职责：
- 接收 HTTP 请求
- 解析参数、路径变量、请求体
- 做基础格式校验
- 做权限入口控制
- 调用 `service` 或编排服务
- 返回统一响应对象

禁止：
- 编写核心业务规则
- 编写流程编排逻辑
- 直接调用 `mapper`
- 直接拼接 SQL
- 直接返回数据库实体对象
- 在控制器里写大段 `try/catch` 做业务兜底

控制器中允许存在的代码：
- 参数合法性判断
- 请求上下文提取
- 安全校验入口
- 调用一个或少量服务方法
- 响应组装

示例：
- 正确：`controller` 接收用户请求后调用 `AgentConversationOrchestrator`
- 错误：`controller` 内直接判断业务分支、查询数据库、拼装多阶段结果

### 2. Service 层

职责：
- 承载业务规则
- 处理事务
- 编排多个数据源或多个 `mapper`
- 调用外部系统
- 做领域对象转换
- 处理业务异常

细分建议：
- 普通业务服务：`xxxService`
- 编排服务：`xxxOrchestrator` / `xxxApplicationService`
- 基础支撑服务：`xxxFactory` / `xxxStore` / `xxxSupport`

要求：
- 一个服务类只负责一类业务能力
- 复杂流程优先拆分，而不是继续堆一个“大而全”的 `service`
- 业务异常和系统异常要有明确边界

### 3. Mapper 层

职责：
- 只负责数据访问
- 执行 SQL
- 完成实体与表之间的映射

禁止：
- 写业务判断
- 写流程控制
- 拼装上层响应对象
- 在 `mapper` 中实现业务兜底逻辑

规范：
- SQL 统一放 XML
- Mapper 接口只保留数据访问方法定义
- 复杂查询仍然属于数据访问，但查询结果应明确映射对象

### 4. Entity / DTO / VO 分层

#### Entity

命名规则：
- `xxxEntity`

职责：
- 对应数据库表结构
- 仅用于持久化层

禁止：
- 直接作为接口出参暴露给前端

#### DTO

命名规则：
- `xxxRequest` / `xxxDTO`

职责：
- 接收请求参数
- 表示输入数据结构

#### VO / Response

命名规则：
- `xxxVO` / `xxxResponse`

职责：
- 返回给前端
- 表示接口输出结构

要求：
- `controller` 出参默认使用 `VO` / `Response`
- `service` 负责完成 `entity -> vo` 或领域对象 -> 响应对象转换

## 项目推荐调用方式

### 简单 CRUD 场景

推荐：

`controller -> service -> mapper`

适用：
- 用户管理
- 权限配置
- 知识库基础信息管理

### 复杂流程编排场景

推荐：

`controller -> orchestrator -> service -> mapper`

适用：
- 多智能体执行链路
- AI 对话编排
- RAG 文档入库链路
- 网关模型路由与健康探测

说明：
- `orchestrator` 负责流程组织
- `service` 负责单点业务能力
- `mapper` 负责持久化

## Controller 编码规范

要求：
- 方法保持短小
- 每个接口只做一件事
- 尽量只调用一个编排入口

建议：
- 控制器方法长度尽量控制在 30 行以内
- 如果一个接口需要处理多个阶段，请下沉到 `service` 或 `orchestrator`

返回规范：
- 使用统一 `Result<T>` 响应
- 失败场景尽量交给全局异常处理

## Service 编码规范

要求：
- 业务含义明确
- 方法名体现业务动作
- 事务边界清晰

建议：
- 单个 `service` 类尽量控制在 300 行以内
- 过长则拆分为多个服务或 support 类
- 能复用的逻辑抽成独立能力类，不要复制粘贴

## Mapper 编码规范

要求：
- 一个 Mapper 对应一类实体或聚合查询
- 参数命名清晰
- 结果映射明确

禁止：
- 在控制层拼接查询条件后传入原始 SQL
- 使用注解 SQL 替代 XML

## 异常处理规范

### Controller

建议：
- 不在 `controller` 中做大范围 `catch (Exception)`
- 参数错误、权限错误、资源不存在等，优先交给统一异常处理

### Service

建议：
- 对可预期业务异常做分类处理
- 对外部依赖失败进行必要兜底
- 不要无差别吞掉异常

### Mapper

建议：
- 不处理业务异常
- 数据访问错误向上抛出，由 `service` 处理

## 日志规范

核心链路日志至少包含：
- `traceId`
- `userId`
- `sessionId`
- `agentType`

级别建议：
- 可预期业务拒绝：`warn`
- 外部依赖失败但已降级：`warn`
- 未预期系统错误：`error`

## 反例

以下写法禁止继续新增：

### 反例 1

`controller` 中直接：
- 查数据库
- 调多个 mapper
- 拼装复杂业务结果
- 记录一堆业务日志

### 反例 2

`mapper` 中直接体现业务语义：
- “如果查询不到就自动补数据”
- “如果状态异常就改状态”

### 反例 3

接口直接返回 `entity`

风险：
- 暴露内部字段
- 后续数据库结构调整会影响接口

## 当前项目落地建议

### agent-service

建议分层：
- `controller`
- `orchestrator`
- `service`
- `mapper`

### gateway-service

建议分层：
- `controller`
- `service`
- `factory/store/support`
- `mapper`

### rag-service

建议分层：
- `controller`
- `application service`
- `document service/index service/audit service`
- `mapper`

### monitor-service

建议分层：
- `controller`
- `query facade service`
- `metrics/trace/feedback/export service`
- `mapper` 或 `JdbcTemplate` 查询层

## 代码评审检查项

提交代码时至少检查：
- `controller` 是否写了业务逻辑
- 是否存在 `controller` 直接调 `mapper`
- 是否直接返回 `entity`
- 是否新增了注解 SQL
- 是否新增了超大 `service`
- 是否把复杂流程收口到编排层

## 最终要求

本项目后端开发默认必须遵守：
- `controller` 不写业务代码
- `controller` 不直接操作 `mapper`
- 业务逻辑统一进入 `service` 或编排层
- `mapper` 只负责数据访问
- 接口不直接暴露 `entity`

如果确实需要例外，必须在代码评审中说明原因，否则按违规处理。
