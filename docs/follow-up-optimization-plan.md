# Spring AI Platform Follow-up Optimization Plan

## Purpose

- This document tracks the next stage of optimization after [`optimization-plan.md`](/F:/Java/spring-ai-platform/docs/optimization-plan.md).
- The previous plan is considered completed for the current baseline.
- Future work should be planned and executed against this document.

## Current Assessment

- The project already has a usable multi-module baseline:
  - auth
  - agent
  - RAG
  - gateway
  - monitor
  - web
- Core engineering hardening has been completed:
  - JWT blacklist and refresh flow
  - CORS allowlist
  - CI
  - test baseline
  - RAG retry flow
  - trace id propagation
  - monitoring baseline
- The next stage should focus on:
  - making agent capabilities more realistic and maintainable
  - improving RAG operational usability
  - improving observability and troubleshooting efficiency
  - reducing repeated code and clarifying module boundaries
  - improving project documentation and long-term maintainability

## Priority Overview

### P1: High Value, Should Be Done First

1. Agent capability abstraction and consolidation
2. Conversation and session governance
3. RAG usability and retrieval quality improvements
4. Monitoring detail and troubleshooting views

### P2: Medium Value, Strongly Recommended

1. DTO and response model standardization
2. Controller slimming and business boundary cleanup
3. Expanded regression test coverage
4. README and architecture documentation improvement

### P3: Nice to Have

1. More advanced tool integrations
2. Evaluation and ranking mechanisms
3. Admin productivity enhancements in frontend

## Detailed Plan

### P1-1 Agent capability abstraction and consolidation

#### Goal

- Reduce repeated code in `agent-service`.
- Make it easier to add new assistants and tools without copying large blocks of logic.
- Upgrade agent capabilities from demo-style behavior toward configurable real integrations.

#### Current Signals

- Multiple assistant implementations exist under [`agent-service/src/main/java/com/huah/ai/platform/agent/service`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/service).
- Multiple tool classes exist under [`agent-service/src/main/java/com/huah/ai/platform/agent/tools`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/tools).
- This is workable now, but the maintenance cost will grow quickly as more assistant types are added.

#### Optimization Items

1. Introduce a unified assistant strategy abstraction.
   - Define a common assistant contract for:
     - assistant code
     - system prompt construction
     - tool binding
     - model selection
     - permission check hook
     - audit hook
2. Extract shared chat execution logic.
   - Consolidate repeated request preparation, error handling, and result packaging.
3. Introduce configurable tool registration.
   - Move from hardcoded tool usage toward a registry-driven pattern.
   - Support enabling or disabling tool groups from configuration.
4. Split mock-style tools from real integrations.
   - Mark demo tools clearly.
   - Prepare integration interfaces for Jira, Confluence, search, database query, and internal APIs.

#### Candidate Files

- [`AgentController.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/controller/AgentController.java)
- [`MultiAgentOrchestrator.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/multi/MultiAgentOrchestrator.java)
- [`CodeAssistantAgent.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/service/CodeAssistantAgent.java)
- [`RdAssistantAgent.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/service/RdAssistantAgent.java)
- [`SearchAssistantAgent.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/service/SearchAssistantAgent.java)
- [`RdTools.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/tools/RdTools.java)

#### Acceptance Criteria

- Adding a new assistant no longer requires copying a large existing class.
- Tool enablement can be adjusted by configuration.
- Demo tools and real tools are clearly separated.
- Shared logic is centralized and covered by tests.

### P1-2 Conversation and session governance

#### Goal

- Improve the practical usability of the chat module.
- Prevent conversation history from growing without control.

#### Optimization Items

1. Add auto-generated conversation titles.
2. Add archive, delete, and pin actions for sessions.
3. Add conversation summary compression for long histories.
4. Add token usage tracking per session and per user.
5. Add clearer session ownership validation and cleanup flow.

#### Candidate Files

- [`ConversationMemoryService.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/memory/ConversationMemoryService.java)
- [`AgentController.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/controller/AgentController.java)
- [`chat.ts`](/F:/Java/spring-ai-platform/web/src/stores/chat.ts)
- [`ChatView.vue`](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue)
- [`SessionList.vue`](/F:/Java/spring-ai-platform/web/src/components/chat/SessionList.vue)

#### Acceptance Criteria

- Sessions support title management and lifecycle operations.
- Long conversations can be compressed without breaking continuity.
- Token usage is visible and available for monitoring.

### P1-3 RAG usability and retrieval quality improvements

#### Goal

- Make RAG easier to operate and easier to debug.
- Improve retrieval transparency and quality.

#### Optimization Items

1. Add chunk preview and chunk count visibility.
2. Add manual re-chunk and re-index operations.
3. Add duplicate document detection.
4. Add document versioning or replacement strategy.
5. Add retrieval hit display with highlighted source snippets.
6. Add knowledge base level access control if different user groups will use the same deployment.
7. Add unsupported structured file graceful handling instead of hard failure.

#### Candidate Files

- [`DocumentIngestionService.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/service/DocumentIngestionService.java)
- [`DocumentMetaService.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/service/DocumentMetaService.java)
- [`RagService.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/service/RagService.java)
- [`StructuredDocumentParser.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/parser/StructuredDocumentParser.java)
- [`DocumentTable.vue`](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentTable.vue)
- [`RagQueryPanel.vue`](/F:/Java/spring-ai-platform/web/src/components/rag/RagQueryPanel.vue)

#### Acceptance Criteria

- Users can see why a document failed and can retry or reprocess it.
- Users can inspect retrieval evidence instead of only seeing the final answer.
- Re-index and replacement flows are controllable.

### P1-4 Monitoring detail and troubleshooting views

#### Goal

- Move from basic visibility to faster root-cause analysis.

#### Optimization Items

1. Add per-service, per-agent, and per-model breakdown metrics.
2. Add slow request Top N view.
3. Add recent failure samples with trace id.
4. Add user-level usage ranking and filtering.
5. Add alert history, acknowledgment, and silence-oriented UI hooks.

#### Candidate Files

- [`MonitorController.java`](/F:/Java/spring-ai-platform/monitor-service/src/main/java/com/huah/ai/platform/monitor/controller/MonitorController.java)
- [`AlertEvaluationService.java`](/F:/Java/spring-ai-platform/monitor-service/src/main/java/com/huah/ai/platform/monitor/alert/AlertEvaluationService.java)
- [`MonitorView.vue`](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)
- [`AlertEvents.vue`](/F:/Java/spring-ai-platform/web/src/components/monitor/AlertEvents.vue)
- [`TopUsersTable.vue`](/F:/Java/spring-ai-platform/web/src/components/monitor/TopUsersTable.vue)

#### Acceptance Criteria

- Operators can identify the failing service or model quickly.
- Slow and failing requests are visible with traceable context.
- Alert information is operationally actionable.

### P2-1 DTO and response model standardization

#### Goal

- Reduce `Map<String, Object>` style drift.
- Improve readability, API stability, and frontend integration safety.

#### Optimization Items

1. Replace loose response maps with typed DTOs where practical.
2. Standardize paged response structure.
3. Standardize error payload fields across services.
4. Standardize agent tool response models.

#### Acceptance Criteria

- Public API payloads are easier to understand and evolve.
- Frontend type definitions become simpler and more reliable.

### P2-2 Controller slimming and service boundary cleanup

#### Goal

- Keep controller classes focused on transport concerns only.

#### Optimization Items

1. Move user context parsing into shared helpers or dedicated components.
2. Move session id rules into service-level utilities.
3. Centralize permission and request pre-check flows.
4. Reduce direct orchestration inside controllers.

#### Candidate Files

- [`AuthController.java`](/F:/Java/spring-ai-platform/auth-service/src/main/java/com/huah/ai/platform/auth/controller/AuthController.java)
- [`AgentController.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/controller/AgentController.java)
- [`RagController.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/controller/RagController.java)
- [`MonitorController.java`](/F:/Java/spring-ai-platform/monitor-service/src/main/java/com/huah/ai/platform/monitor/controller/MonitorController.java)

#### Acceptance Criteria

- Controllers are thinner and easier to scan.
- Business rules live in reusable service or helper layers.

### P2-3 Expanded regression test coverage

#### Goal

- Protect the project against regressions in edge cases that have already caused issues.

#### Recommended Test Additions

1. Chinese `userId` and special character session handling.
2. Token refresh concurrency and retry boundaries.
3. SSE disconnect and reconnect behavior.
4. RAG retry idempotency.
5. Permission matrix tests by role and resource.
6. CORS allowlist boundary cases.

#### Acceptance Criteria

- Recently fixed bugs are covered by automated regression tests.
- New changes are less likely to break login, chat, or RAG flows.

### P2-4 README and architecture documentation improvement

#### Goal

- Make the repository easier to restart and maintain after time passes.

#### Optimization Items

1. Update README to reflect current completed baseline.
2. Add startup order and dependency description.
3. Add service port and routing overview.
4. Add environment variable descriptions.
5. Add architecture notes for auth, gateway, agent, RAG, and monitor interactions.
6. Add a troubleshooting section for common local issues.

#### Candidate Files

- [`README.md`](/F:/Java/spring-ai-platform/README.md)
- [`optimization-plan.md`](/F:/Java/spring-ai-platform/docs/optimization-plan.md)
- [`follow-up-optimization-plan.md`](/F:/Java/spring-ai-platform/docs/follow-up-optimization-plan.md)

#### Acceptance Criteria

- A future restart does not require re-learning the whole project from code.
- Key setup and runtime assumptions are documented.

### P3-1 More advanced tool integrations

#### Goal

- Increase the practical usefulness of the assistant suite.

#### Optional Directions

1. Web search with source references.
2. SQL query assistant with safe readonly guardrails.
3. Internal API connector abstraction.
4. File system or codebase assistant for local project workflows.
5. External MCP tool management UI.

### P3-2 Evaluation and ranking mechanisms

#### Goal

- Add feedback loops for answer quality.

#### Optional Directions

1. Chat answer thumbs up and thumbs down.
2. RAG answer evidence scoring.
3. Gateway routing outcome statistics by model.
4. Prompt and response evaluation samples for offline review.

### P3-3 Admin productivity enhancements

#### Goal

- Improve the frontend management experience.

#### Optional Directions

1. Batch permission editing.
2. Batch knowledge base operations.
3. Better filtering and sorting on dashboard and user pages.
4. Export views for monitoring and usage data.

## Recommended Execution Order

1. P1-1 Agent capability abstraction and consolidation
2. P1-2 Conversation and session governance
3. P1-3 RAG usability and retrieval quality improvements
4. P1-4 Monitoring detail and troubleshooting views
5. P2-1 DTO and response model standardization
6. P2-2 Controller slimming and service boundary cleanup
7. P2-3 Expanded regression test coverage
8. P2-4 README and architecture documentation improvement
9. P3 optional items

## Iteration Guidance

- Prefer finishing one vertical slice at a time.
- Each optimization item should include:
  - code changes
  - tests
  - documentation update if behavior changes
- Do not reopen items already marked complete in [`optimization-plan.md`](/F:/Java/spring-ai-platform/docs/optimization-plan.md) unless a new regression is found.

## Progress Tracking Template

Use this section when continuing work later.

### In Progress

- None
- Current baseline has completed the highest-value optional P3 items for the current personal-project scope

### Next Candidate Task

- Future extension only
- Optional next direction: MCP online editing, runtime health check, and one-click reload/restart workflow

### Completed in This Follow-up Plan

- P1-1 Agent capability abstraction and consolidation
  - Introduced unified `AssistantAgent` contract and `BaseAssistantAgent`
  - Added `AssistantAgentRegistry` to centralize agent lookup by type
  - Refactored [`AgentController.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/controller/AgentController.java) to use registry-based dispatch
  - Consolidated repeated `ChatClient` bean construction in [`CommonConfiguration.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/config/CommonConfiguration.java)
  - Added initial registry regression test in [`AssistantAgentRegistryTest.java`](/F:/Java/spring-ai-platform/agent-service/src/test/java/com/huah/ai/platform/agent/service/AssistantAgentRegistryTest.java)
- P1-2 Conversation and session governance
  - Added Redis-backed session metadata persistence in [`ConversationMemoryService.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/memory/ConversationMemoryService.java)
  - Added session title update endpoint in [`AgentController.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/controller/AgentController.java)
  - Added frontend rename flow in [`chat.ts`](/F:/Java/spring-ai-platform/web/src/stores/chat.ts) and [`SessionList.vue`](/F:/Java/spring-ai-platform/web/src/components/chat/SessionList.vue)
  - Added session delete and pin management in backend session APIs and frontend session list
  - Added session archive management and archived-session visibility toggle
  - Added automatic long-conversation compression into summary messages with regression coverage
- P3-2 Evaluation and ranking mechanisms
  - Added unified `ai_response_feedback` storage and startup schema initialization in agent, rag, and monitor services
  - Added chat answer thumbs up and thumbs down feedback submission in [`AgentController.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/controller/AgentController.java) and the chat frontend
  - Added RAG answer feedback submission and response audit ids in [`RagController.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/controller/RagController.java) and [`RagQueryPanel.vue`](/F:/Java/spring-ai-platform/web/src/components/rag/RagQueryPanel.vue)
  - Added RAG evidence-level feedback with per-chunk up/down scoring in [`RagAuditService.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/service/RagAuditService.java), [`RagController.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/controller/RagController.java), and [`RagQueryPanel.vue`](/F:/Java/spring-ai-platform/web/src/components/rag/RagQueryPanel.vue)
  - Added feedback overview and recent feedback views in [`MonitorController.java`](/F:/Java/spring-ai-platform/monitor-service/src/main/java/com/huah/ai/platform/monitor/controller/MonitorController.java) and [`MonitorView.vue`](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)
  - Added recent evidence feedback visibility and CSV export in [`MonitorController.java`](/F:/Java/spring-ai-platform/monitor-service/src/main/java/com/huah/ai/platform/monitor/controller/MonitorController.java) and [`MonitorView.vue`](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)
  - Added feedback regression coverage in [`ResponseFeedbackServiceTest.java`](/F:/Java/spring-ai-platform/agent-service/src/test/java/com/huah/ai/platform/agent/audit/ResponseFeedbackServiceTest.java) and [`RagAuditServiceTest.java`](/F:/Java/spring-ai-platform/rag-service/src/test/java/com/huah/ai/platform/rag/service/RagAuditServiceTest.java)
  - Added gateway model routing outcome visibility in [`MonitorView.vue`](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue) by integrating [`gateway.ts`](/F:/Java/spring-ai-platform/web/src/api/gateway.ts) model stats into the monitor dashboard
- P3-3 Admin productivity enhancements
  - Added user list keyword, role, and status filters in the frontend admin page
  - Added bot-permission keyword and status filters in the frontend admin page
  - Added knowledge-base search and sorting controls in the RAG frontend
  - Added monitoring CSV export actions for top users, recent feedback, slow requests, and failure samples in [`MonitorController.java`](/F:/Java/spring-ai-platform/monitor-service/src/main/java/com/huah/ai/platform/monitor/controller/MonitorController.java) and [`MonitorView.vue`](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)
- P3-1 More advanced tool integrations
  - Upgraded the `data-analysis` assistant into a practical readonly SQL helper with accessible table discovery, table schema inspection, query plan preview, and table whitelist enforcement in [`DataAnalysisTools.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/tools/DataAnalysisTools.java)
  - Added readonly SQL safety regression coverage in [`DataAnalysisToolsTest.java`](/F:/Java/spring-ai-platform/agent-service/src/test/java/com/huah/ai/platform/agent/tools/DataAnalysisToolsTest.java)
  - Updated the data-analysis agent system prompt in [`CommonConfiguration.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/config/CommonConfiguration.java) to prefer schema discovery and explain-first query flow
  - Added a configuration-driven internal API connector abstraction with readonly GET invocation, path-prefix allowlist, and connector discovery in [`InternalApiTools.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/tools/InternalApiTools.java)
  - Extended tool configuration for reusable connector definitions in [`ToolsProperties.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/config/ToolsProperties.java) and [`application.yml`](/F:/Java/spring-ai-platform/agent-service/src/main/resources/application.yml)
  - Wired internal connector capability into the RD assistant chat client in [`CommonConfiguration.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/config/CommonConfiguration.java)
  - Added connector regression coverage in [`InternalApiToolsTest.java`](/F:/Java/spring-ai-platform/agent-service/src/test/java/com/huah/ai/platform/agent/tools/InternalApiToolsTest.java)
  - Added readonly MCP configuration visibility with [`McpServerCatalogService.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/service/McpServerCatalogService.java), `GET /api/v1/agent/mcp/servers`, and regression coverage in [`McpServerCatalogServiceTest.java`](/F:/Java/spring-ai-platform/agent-service/src/test/java/com/huah/ai/platform/agent/service/McpServerCatalogServiceTest.java)
  - Added admin-side MCP management view in [`McpView.vue`](/F:/Java/spring-ai-platform/web/src/views/McpView.vue) with route and sidebar entry for quick inspection of loaded MCP servers
