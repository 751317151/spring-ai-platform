# RAG 检索链路优化设计

## 目标

当前 `rag-service` 已具备基础向量检索、证据回显和反馈闭环，但检索链路仍以单路向量召回为主。本文档定义一版兼容现有接口的升级方案，在不改变主交互路径的前提下补齐以下能力：

- Query 改写
- 多路召回
- 规则式 Rerank
- 检索解释

## 当前问题

现状链路：

1. 前端提交 `question + knowledgeBaseId + topK`
2. 后端直接使用 `VectorStoreDocumentRetriever`
3. 基于单路向量召回生成答案
4. 再用原始问题做一次向量检索返回 `sources`

主要问题：

- 只有单路向量召回，召回面偏窄
- 用户问题中包含口语、修饰语时，检索 query 质量不稳定
- 没有独立 rerank，TopK 很大时容易把弱相关片段送给模型
- 前端虽然展示证据，但缺少“本次检索是怎么做的”解释

## 设计原则

- 保持现有接口兼容：原 `answer/sources/latencyMs` 字段不变
- 新增能力以可回退为前提：任一路召回失败不阻断整体问答
- 遵守分层：`controller -> service/orchestrator -> mapper`
- 尽量复用现有 `vector_store` 和 `document_meta`，避免新增表结构

## 目标链路

```text
用户问题
  -> QueryRewriteService
  -> RetrievalOrchestrator
     -> 原问题向量召回
     -> 改写问题向量召回
     -> 关键词召回
  -> 候选合并去重
  -> RerankService
  -> TopN 证据
  -> RagService 基于证据生成答案
  -> 返回 answer + sources + retrievalDebug
```

## 模块拆分

### QueryRewriteService

职责：

- 标准化用户问题
- 去掉礼貌语、空泛指令和冗余问句尾巴
- 生成更适合检索的 `retrievalQuery`
- 抽取关键词供关键词召回和解释展示使用

当前版本采用规则改写，避免把检索效果绑定到额外模型调用。后续可升级为“LLM 改写 + 规则兜底”。

### RetrievalOrchestrator

职责：

- 协调多路召回
- 聚合候选、去重
- 记录每一路召回条数与查询文本
- 调用 `RerankService` 产出最终证据列表

### VectorStoreSearchMapper

职责：

- 基于 `vector_store` 做关键词召回
- 使用 `filename / chunk_preview / content` 的匹配结果形成词法分数

### RerankService

职责：

- 综合语义分、关键词覆盖率、多路命中情况、文件名命中情况
- 产出统一的最终相关度分数

当前版本为规则式 Rerank，后续可替换为 cross-encoder 或外部 rerank 模型。

## 响应扩展

`RagQueryResponse` 增加 `retrievalDebug`：

- `originalQuery`
- `retrievalQuery`
- `alternateQueries`
- `keywords`
- `recallSteps`
- `candidateCount`
- `rerankedCount`
- `selectedCount`

`SourceDocument` 增加：

- `semanticScore`
- `keywordScore`
- `recallSources`
- `matchedTerms`

这些字段均为兼容扩展，老页面不读也不受影响。

## 实施顺序

1. 新增 QueryRewrite、关键词召回、Rerank、检索解释模型
2. 改造 `RagQueryFacadeService`，先检索再生成
3. 改造 `RagService`，从“advisor 内部检索”切到“基于显式证据生成”
4. 前端补充检索解释卡片
5. 增加关键单测并做模块回归

## 风险与取舍

- 关键词召回基于 `ILIKE`，效果优先，性能不是最终形态；后续可以换 PostgreSQL 全文索引
- 规则式 query rewrite 和 rerank 不会像专用模型那样强，但实现成本低、结果稳定、便于调试
- 当前 `/search` 调试接口仍保留原始向量搜索语义，不强制改成多路召回，避免影响已有使用方式
