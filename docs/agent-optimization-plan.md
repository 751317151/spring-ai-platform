# Spring AI Platform Agent 专项优化计划

## 文档目的

这份文档用于单独沉淀 `agent-service` 及其前端 Agent 工作台的优化基线、当前完成状态和后续待办。

后续所有 Agent 相关迭代，统一按本文档更新，不再分散维护多份状态说明。

## 当前结论

当前 Agent 模块的第一阶段闭环已经基本完成：

1. Agent 基础运行、治理、观测、工作台、测试和文档主干已形成闭环
2. P0 与 P1 目标已完成主体落地
3. P2 中已补齐基础版对比视图、日志生命周期治理、结构化错误入口和归档 trace 回放入口
4. 当前剩余工作主要集中在治理细化、历史问题清理、冷热流转深化和进一步产品化

---

## 一、已完成

### 1. Agent 基础能力

1. 已支持多助手注册与路由
2. 已支持统一聊天入口与流式输出
3. 已支持会话记忆和会话级配置存储
4. 已支持聊天页 Agent 诊断面板
5. 已支持独立 Agent 工作台入口

### 2. Agent 元数据与配置体系

1. 已形成 Agent 级元数据注册中心基础版
2. 已支持 Agent 元数据查询与统一展示入口
3. 已减少前后端重复维护助手定义的分散问题

### 3. Agent 权限与治理能力

1. 已支持 Agent 访问权限校验
2. 已支持工具白名单
3. 已支持连接器白名单
4. 已支持 MCP 服务白名单
5. 已补充连接器路径前缀级资源边界
6. 已补充 MCP 工具范围展示
7. 已补充数据分析范围控制
8. 已补充运行时策略摘要、风险等级和受限资源统计
9. 已支持权限解释接口与工作台展示
10. 已补充 schema 级数据范围校验与跨 schema 查询拦截
11. 已补充 schema 与数据源绑定规则、agent 数据源白名单和跨 schema 组合白名单
12. 已补充数据范围拒绝时的细粒度审计解释，可返回命中的 schema、data source 和允许范围说明
13. 已补充运行时隔离基础版，包括 agent 级最大并发、请求超时、流式超时配置以及控制器接入
14. 已将运行时隔离状态、活跃请求数、并发上限、Token 配额和超时边界纳入运行时策略摘要
15. 已将多智能体恢复与归档 trace 回放入口接入运行时隔离边界
16. 已补充内部连接器工具的结构化拒绝解释，可返回 `errorCode`、`resource` 和 `detail`
17. 已补充 MCP 服务目录中的授权决策细节，可返回 `accessReasonCode`、`accessReasonMessage` 和 `accessDetail`
18. 已补充 agent 级运行时队列治理，可配置 `maxQueueDepth`、`queueWaitTimeoutMs` 并纳入运行时策略摘要

### 4. Agent 可观测性与排障能力

1. 已支持工具调用审计落库
2. 已支持工具调用按 `traceId` 查询
3. 已支持多智能体执行轨迹落库
4. 已支持聊天消息 `traceId` 展示与跳转
5. 已支持工作台运行概览、失败样本、工具审计、MCP 状态、执行轨迹展示
6. 已支持趋势统计、工具排行榜、错误类型聚合和健康摘要
7. 已支持 4 周长期趋势、周报摘要和最近变化提示
8. 已补充基础版日志生命周期治理视图与清理预览能力
9. 已补充日志生命周期自动治理调度基础版
10. 已补充归档 manifest 基础版，可生成最近归档候选样本和归档清单元信息
11. 已补充真实归档导出基础版，可按 agent 导出 `audit`、`tool-audit`、`trace` JSONL 文件并记录归档产物信息
12. 已补充最近归档详情回查基础版，可查询最近一次归档的产物清单、样本和导出统计
13. 已补充最近归档文件预览基础版，可按产物类型预览最近归档文件的前几行内容
14. 已补充 cold data 统计基础版，可在日志生命周期摘要中展示最近归档导出的冷数据规模
15. 已补充最近归档 trace 检索基础版，可按 `traceId` 查询最近归档中的多智能体 trace
16. 已补充归档详情中的操作提示信息，可直接提示 trace 检索、回放和 bundle 运维路径
17. 已补充归档任务配置透出，可在归档详情中直接查看 `sampleLimit` 与 `exportBatchSize`

### 5. 多智能体治理

1. 已完成执行轨迹持久化与详情查看
2. 已补充步骤级恢复、重试、跳过和回放接口
3. 已支持恢复后的轨迹治理基础能力
4. 已补充基于归档 trace 的重新执行入口，可将冷数据中的历史请求回放为新的多智能体 trace

### 6. Agent 工作台联动与产品能力

1. 已支持从工作台跳转聊天页和监控页
2. 已支持失败样本携带 `traceId`、`sessionId` 进入排障路径
3. 已补充基础版助手对比视图
4. 已补充运行时策略、长期趋势和最近变化展示
5. 已补充对比视图中的差异提示基础版
6. 已补充后端化的对比结果与差异原因提示基础版
7. 已补充后端化的对比指标差值基础版，可直接返回调用量、失败率、延迟、工具失败和治理风险差值
8. 已补充对比视图中的更细归因解释基础版，可展示左右证据、影响说明和建议动作
9. 已补充对比视图详情面板基础版
10. 已补充对比视图中的助手详情抽屉基础版，可展示健康摘要、策略摘要、运行时高亮和近期变化
11. 已补充变化对比视图基础版，可对左右 Agent 的 recent changes 做并排对比
12. 已补充日志生命周期区块中的最近归档 manifest 信息展示
13. 已补充工作台中的最近归档产物和归档样本展示
14. 已补充工作台中的最近归档文件预览入口
15. 已补充工作台中的 archived trace lookup 基础入口
16. 已补充工作台中的 archived trace replay 基础入口
17. 已补充对比视图中的助手画像增强字段，可展示调用量、失败率、风险等级和 Top 错误类型
18. 已补充变化对比视图基础筛选，可按全部 / 高风险 / 差异化变化聚焦查看

### 7. Agent 错误结构与返回格式

1. 已为 Agent 返回结构增加 `error` 扩展字段
2. 已补充 `AgentErrorDetail`
3. 已统一工具权限拒绝时的结构化错误返回
4. 已统一参数非法时的结构化错误返回入口
5. 已补充权限拒绝时的 `detail` 细节字段，用于返回更细的治理解释
6. 已清理 `AgentAccessChecker` 与 `InternalApiTools` 中一批历史乱码和不一致提示

### 8. Agent 专项测试

1. 已补充 `ToolSecurityServiceTest`
2. 已补充 `MultiAgentTraceServiceTest`
3. 已补充 `AgentWorkbenchServiceTest`
4. 已补充 `AgentLogLifecycleServiceTest`
5. 已补充 `AgentWorkbenchView.spec.ts`
6. 已补充 `ChatAgentDiagnosticsPanel.spec.ts`
7. 已补充 `AgentLogLifecycleSchedulerTest`
8. 已补充 `AgentLogArchiveServiceTest`
9. 已通过前端 `vue-tsc` 类型检查
10. 已补充数据范围与跨 schema 查询限制的定向测试
11. 已补充对比结果与差异原因提示的后端定向测试
12. 已补充数据源白名单、跨 schema 组合白名单和运行时策略摘要增强的定向测试
13. 已补充运行时隔离、compare 详情抽屉/变化对比和 archived trace lookup 的后端定向测试
14. 已补充 archived trace replay 与 archive operation hints 的定向测试
15. 已复跑后端 Agent 相关定向 Maven 测试并通过
16. 已复跑前端 `vue-tsc` 并通过
17. 已补充 MCP 服务授权决策细节与对比视图画像字段的定向测试
18. 已补充运行时队列治理与归档配置透出的定向测试

### 9. Agent 文档体系

1. 已新增 [`agent-architecture.md`](/F:/Java/spring-ai-platform/docs/agent-architecture.md)
2. 已新增 [`agent-metadata-and-integration-guide.md`](/F:/Java/spring-ai-platform/docs/agent-metadata-and-integration-guide.md)
3. 已新增 [`agent-tool-integration-guide.md`](/F:/Java/spring-ai-platform/docs/agent-tool-integration-guide.md)
4. 已新增 [`agent-multi-agent-trace-guide.md`](/F:/Java/spring-ai-platform/docs/agent-multi-agent-trace-guide.md)
5. 已新增 [`agent-troubleshooting-guide.md`](/F:/Java/spring-ai-platform/docs/agent-troubleshooting-guide.md)
6. 已新增 [`agent-code-style-guide.md`](/F:/Java/spring-ai-platform/docs/agent-code-style-guide.md)

---

## 二、剩余待办

### 1. 治理细化

1. 在当前并发 / timeout / queue 隔离基础上继续补更细的 agent 级资源配额
2. 将当前细粒度审计解释继续扩展到更多非数据分析类工具和存量接口，尤其是 MCP 执行链与更多内部服务接口
3. 将当前运行时隔离继续下沉到更多调用链环节，例如内部连接器与 MCP 工具执行阶段本身

### 2. 工作台进一步产品化

1. 在当前后端化对比结果、详情抽屉和变化对比基础上继续完善 Agent 对比视图产品化
2. 将当前基础版详情抽屉继续升级为更完整的助手画像与治理画像视图，补齐更多长期趋势、工具依赖和治理边界细项
3. 将当前基础版变化对比视图继续升级为更细的变化归因筛选与聚焦视图

### 3. 日志生命周期治理增强

1. 在当前 cold data 统计、archived trace lookup、archived trace replay 和 archive config 基础上继续补热数据、冷数据和可清理数据的实际流转
2. 在当前最近归档详情、文件预览、lookup 与 replay 基础上继续补归档后的运维操作闭环和冷热状态切换闭环

### 4. 错误码与提示统一收口

1. 继续清理历史零散错误文案
2. 继续清理少量乱码和不一致提示
3. 收敛更多存量接口的错误码枚举和值域

### 5. 测试与环境复跑

1. 在可执行环境复跑前端 `vitest`
2. 在无文件锁环境复跑后端 Maven 测试
3. 补齐最终回归记录

### 6. 当前轮次详细记录

1. 已落地运行时隔离基础版：
   `AgentController` 已接入 agent 级最大并发限制；`AgentRuntimePolicyService` 已纳入 `maxConcurrency`、`currentActiveRequests`、`dailyTokenLimit`、`requestTimeoutMs`、`streamTimeoutMs`
2. 已落地对比视图增强基础版：
   compare response 已增加 `leftDetail`、`rightDetail`、`changeComparison`；工作台已补充详情抽屉和变化对比展示基础版
3. 已落地归档闭环增强基础版：
   lifecycle summary 已增加 `totalColdDataCount`；archive detail 已增加 `coldDataCount`；后端已增加最近归档 `traceId` 检索接口
4. 已落地本轮新增链路：
   多智能体恢复接口已纳入运行时隔离；后端已新增 archived trace replay 接口；archive detail 已增加 `operationHints`；工作台已补充 replay 入口
5. 已落地本轮错误提示收口：
   `AgentAccessChecker` 已统一主要权限 / 配额提示；`InternalApiTools` 已统一 connector 参数校验与拒绝解释结构
6. 已落地本轮产品化与治理增强：
   `McpServerCatalogService` 已补充授权原因字段；compare detail 已补充调用量、失败率、风险等级和 Top 错误类型；工作台已增加 change comparison 筛选
7. 已落地本轮运行时与生命周期增强：
   `AgentRuntimeIsolationService` 已补充 `maxQueueDepth` 与 `queueWaitTimeoutMs`；`AgentLogArchiveDetailResponse` 已补充 `sampleLimit` 与 `exportBatchSize`
8. 已完成的本轮验证：
   `mvn --% -pl agent-service -am test -Dtest=MultiAgentTraceServiceTest,AgentLogArchiveServiceTest,AgentRuntimePolicyServiceTest,AgentWorkbenchServiceTest,AgentLogLifecycleServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
   `npm exec vue-tsc -- --noEmit`
   `mvn --% -pl agent-service -am test -Dtest=McpServerCatalogServiceTest,AgentWorkbenchServiceTest,MultiAgentTraceServiceTest,AgentLogArchiveServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
   `mvn --% -pl agent-service -am test -Dtest=AgentRuntimeIsolationServiceTest,AgentRuntimePolicyServiceTest,AgentLogArchiveServiceTest,AgentWorkbenchServiceTest,McpServerCatalogServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
9. 本机环境级复跑结果：
   `mvn test` 未全量完成，失败原因不是 Agent 代码回归，而是 `gateway-service` surefire report 文件拒绝访问、`rag-service` 本地 Maven 仓库 `E:\\JAVA\\maven_repository` 写入 `resolver-status.properties` 被拒绝
   `npm exec vitest -- run src/views/AgentWorkbenchView.spec.ts` 仍未能启动，失败原因为本机 `esbuild spawn EPERM`

---

## 三、不阻塞上线项

以下事项建议继续推进，但不阻塞当前 Agent 主体能力上线或继续联调：

1. 在当前后端化对比结果和指标差值基础上继续完善 Agent 对比视图产品化
2. 在现有真实归档导出、trace lookup 与 replay 基础上继续完善冷热数据流转闭环
3. 在现有 schema 级限制基础上继续完善更细的跨数据源策略治理
4. 历史错误文案与乱码清理
5. 在受限本机环境下未完成的全量测试复跑

说明：

1. 当前主干能力、治理骨架、工作台、恢复能力、结构化错误和专项文档均已具备
2. 剩余项主要影响的是治理深度、运维体验完善度和长期维护质量
3. 这些问题需要继续做，但不构成当前阶段“Agent 主体不可用”

---

## 四、后续更新规则

后续每次继续做 Agent 优化时，统一按下面规则同步更新本文档：

1. 新完成的事项优先移动到“已完成”
2. 仍需继续推进的事项保留在“剩余待办”
3. 明确不影响当前阶段交付的事项放在“不阻塞上线项”
4. 不再新增零散状态文档，统一维护这一份
5. 若进入下一阶段，再在本文档顶部补“当前结论”，不要重新拆散结构

---

## 五、当前阶段判断

### 已达到

1. Agent 第一阶段优化目标已基本达成
2. P0 与 P1 项目已完成主体落地
3. P2 已完成基础版落地，并补齐归档 trace lookup / replay 闭环基础能力

### 未完全结束

1. 治理细化还未完全收口
2. 产品化细节还可继续增强
3. 历史错误文案与乱码还需继续清理
4. 全量测试复跑仍受本机环境限制

---

## 备注

1. 本文档作为 Agent 专项优化唯一基线，后续所有 Agent 相关迭代优先以本文为准
2. 若后续继续推进，建议只在本文档中持续更新状态，不再重新拆出新的状态说明文档
