# Frontend UI Optimization Plan

## Purpose

- This document records the next-stage frontend design and interaction optimization directions for the `web` module.
- It focuses on visual hierarchy, interaction efficiency, state feedback, and product polish.
- It is intended to be used as the execution baseline for future frontend refinements.

## Execution Status

### Completed

- Chat workspace:
  - session search, recent grouping, archive/pin/batch actions
  - response copy, quote, continue, regenerate, branch session
  - per-session draft persistence and continuation cards
- RAG workflow:
  - clearer 3-step page rhythm
  - status-focused document operations and failure handling
  - chunk preview navigation and summary copy
- Dashboard, monitor, user pages:
  - summary cards, quick actions, recent-operation guidance
  - cross-page deep links between dashboard, chat, RAG, and monitor
- Global interaction:
  - command palette / quick jump
  - notification center
  - shortcut help panel
  - route-loading fallback and page transition guard
  - mobile quick-entry trays for recent views and common actions
- Table and card consistency:
  - user, monitor, RAG, and dashboard cards now share clearer action density and empty-state handling
- Frontend tests:
  - major view interaction specs are in place
  - store specs now cover `auth`, `chat`, `gateway`, `monitor`, `rag`, `runtime`, and `user`

### Remaining Low-Priority Items

- Fine-grained motion polish for more page-to-page transitions
- More advanced chart drill-down interactions in monitor
- End-to-end smoke coverage for key frontend journeys

## Current Assessment

- The current frontend already covers the main business flows:
  - dashboard
  - chat assistant
  - RAG knowledge base
  - monitor
  - user and permission management
  - MCP visibility
- The current baseline is functionally usable, but the overall experience still feels closer to an internal admin console than a polished AI product.
- Main current issues:
  - page information hierarchy is inconsistent
  - primary and secondary actions are not clearly separated
  - loading, empty, and error states are not consistently designed
  - some pages feel like stacked components rather than guided workflows
  - chat and RAG pages do not yet have a strong “AI workspace” feel

## Overall Objectives

1. Make the frontend feel like a coherent product rather than a collection of pages.
2. Improve operation efficiency for frequent tasks.
3. Reduce cognitive load in dense pages.
4. Strengthen state feedback and long-task visibility.
5. Build a reusable UI interaction baseline for later feature expansion.

## Priority Overview

### P1: High Value, Should Be Done First

1. Chat workspace redesign
2. RAG workflow restructuring
3. Global state feedback standardization
4. Page header, card, and table design unification

### P2: Medium Value, Strongly Recommended

1. Dashboard and monitor readability improvement
2. Sidebar and header interaction upgrade
3. Batch operations and action-density optimization
4. Keyboard shortcuts and efficiency enhancements

### P3: Nice to Have

1. Motion and micro-interaction polish
2. Onboarding and empty-state guidance
3. Theme refinement and stronger AI product visual identity

## Detailed Plan

### P1-1 Chat Workspace Redesign

#### Goal

- Turn the chat page into the main productivity workspace of the product.
- Make conversation, agent switching, and message operations smoother and clearer.

#### Current Signals

- [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue) already contains the core layout, but the page still feels like a dashboard section instead of a dedicated AI workspace.
- [SessionList.vue](/F:/Java/spring-ai-platform/web/src/components/chat/SessionList.vue) exposes too many inline actions at once.
- [ChatInput.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatInput.vue) is usable but still minimal.
- [MessageBubble.vue](/F:/Java/spring-ai-platform/web/src/components/chat/MessageBubble.vue) can carry more structure and action affordances.

#### Optimization Items

1. Rebuild the chat header into a clearer workspace header.
   - Show assistant name, role, capabilities, and current session state.
   - Move less-frequent actions into a compact “more” menu.
2. Simplify session list scanning.
   - Only show key metadata by default.
   - Reveal rename, archive, delete, and pin actions on hover or active state.
   - Add session search and recent grouping.
3. Upgrade message interaction.
   - Add copy, regenerate, quote, and timestamp display.
   - Improve code block presentation and one-click copy.
   - Visually distinguish answer text, source blocks, and tool-result blocks.
4. Upgrade the input bar into a true workbench.
   - Add stop-generation support.
   - Add follow-up suggestion chips after response.
   - Preserve unsent draft text per session if practical.
5. Improve conversation continuity.
   - Keep scroll position stable when switching sessions.
   - Better empty state for a new session.

#### Candidate Files

- [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue)
- [SessionList.vue](/F:/Java/spring-ai-platform/web/src/components/chat/SessionList.vue)
- [ChatInput.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatInput.vue)
- [ChatMessages.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatMessages.vue)
- [MessageBubble.vue](/F:/Java/spring-ai-platform/web/src/components/chat/MessageBubble.vue)

#### Acceptance Criteria

- The chat page has a clear visual center and lower action noise.
- Session management feels lighter and faster.
- Messages provide stronger interaction affordances and better readability.

### P1-2 RAG Workflow Restructuring

#### Goal

- Make RAG feel like a guided workflow instead of a component stack.
- Reduce confusion for first-time or occasional users.

#### Current Signals

- [RagView.vue](/F:/Java/spring-ai-platform/web/src/views/RagView.vue) already contains all core capabilities, but the flow is not strongly guided.
- [DocumentTable.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentTable.vue) is feature-rich but visually dense.
- Upload, document management, and questioning are currently placed together without a strong step structure.

#### Optimization Items

1. Reorganize the page into a 3-step workflow.
   - Select knowledge base.
   - Upload and manage documents.
   - Ask questions against the current knowledge base.
2. Strengthen document operation clarity.
   - Highlight processing, indexed, and failed states more clearly.
   - Add stronger failed-state explanation display and retry guidance.
3. Improve chunk preview usability.
   - Show chunk count and sampling more prominently.
   - Support quick navigation between chunks.
4. Improve query result readability.
   - Better source snippet card styling.
   - Stronger answer/evidence separation.
   - Clearer feedback entry points for answer-level and evidence-level feedback.
5. Add better empty-state instructions.
   - No knowledge base selected
   - No documents uploaded
   - No retrieval result returned

#### Candidate Files

- [RagView.vue](/F:/Java/spring-ai-platform/web/src/views/RagView.vue)
- [KnowledgeBaseGrid.vue](/F:/Java/spring-ai-platform/web/src/components/rag/KnowledgeBaseGrid.vue)
- [DocumentUpload.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentUpload.vue)
- [DocumentTable.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentTable.vue)
- [RagQueryPanel.vue](/F:/Java/spring-ai-platform/web/src/components/rag/RagQueryPanel.vue)

#### Acceptance Criteria

- A new user can understand the RAG workflow without extra explanation.
- Document and evidence states are easier to scan.
- Query results feel more trustworthy and easier to inspect.

### P1-3 Global State Feedback Standardization

#### Goal

- Make all pages feel responsive and predictable under loading, failure, and long-running operations.

#### Current Signals

- Toasts already exist, but page-level and component-level feedback are not fully standardized.
- Some actions still rely too much on global toast only.

#### Optimization Items

1. Define a standard loading-state pattern.
   - page skeleton
   - card skeleton
   - table row skeleton
   - button loading
2. Define a standard empty-state pattern.
   - short explanation
   - recommended next action
   - optional quick action button
3. Define a standard error-state pattern.
   - clear reason
   - retry button
   - related troubleshooting hint where needed
4. Improve long-task progress feedback.
   - file upload
   - document reindex
   - streaming generation
5. Standardize destructive action confirmation.
   - delete
   - archive
   - clear memory
   - batch operations

#### Candidate Files

- [ToastNotification.vue](/F:/Java/spring-ai-platform/web/src/components/common/ToastNotification.vue)
- [BackendStatusBanner.vue](/F:/Java/spring-ai-platform/web/src/components/common/BackendStatusBanner.vue)
- [ChatInput.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatInput.vue)
- [DocumentUpload.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentUpload.vue)
- [DocumentTable.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentTable.vue)
- [UserTable.vue](/F:/Java/spring-ai-platform/web/src/components/user/UserTable.vue)

#### Acceptance Criteria

- Loading, empty, and error states feel consistent across pages.
- High-risk operations have clear confirmation and result feedback.
- Long-running tasks no longer feel opaque.

### P1-4 Page Header, Card, and Table Design Unification

#### Goal

- Build a reusable visual grammar across the whole frontend.

#### Current Signals

- Different pages currently use slightly different title areas and section rhythms.
- Tables and cards work, but their density and hierarchy vary from page to page.

#### Optimization Items

1. Standardize page headers.
   - title
   - one-line subtitle
   - right-side primary action
   - optional filter bar
2. Standardize section cards.
   - title
   - subtitle
   - utility action area
3. Standardize table design.
   - row height
   - hover state
   - action column
   - empty state
   - status pill style
4. Normalize spacing rhythm.
   - section spacing
   - card padding
   - form spacing
5. Reduce visual noise.
   - fewer always-visible small actions
   - more explicit primary CTA

#### Candidate Files

- [MainLayout.vue](/F:/Java/spring-ai-platform/web/src/layouts/MainLayout.vue)
- [AppHeader.vue](/F:/Java/spring-ai-platform/web/src/components/common/AppHeader.vue)
- [MetricCard.vue](/F:/Java/spring-ai-platform/web/src/components/common/MetricCard.vue)
- [DashboardView.vue](/F:/Java/spring-ai-platform/web/src/views/DashboardView.vue)
- [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)
- [UserView.vue](/F:/Java/spring-ai-platform/web/src/views/UserView.vue)

#### Acceptance Criteria

- Core pages feel like they belong to the same product.
- Cards and tables become easier to scan.
- Action hierarchy is easier to understand.

### P2-1 Dashboard and Monitor Readability Improvement

#### Goal

- Make metrics pages more actionable, not just more data-dense.

#### Optimization Items

1. Reduce chart overload and surface conclusions first.
2. Add “today summary” cards.
   - slowest path
   - most error-prone module
   - negative feedback hotspot
3. Improve table prioritization and sorting affordances.
4. Add quicker trace-oriented operator shortcuts.
   - copy trace id
   - copy user id
   - filter by current row context

#### Candidate Files

- [DashboardView.vue](/F:/Java/spring-ai-platform/web/src/views/DashboardView.vue)
- [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)

### P2-2 Sidebar and Header Interaction Upgrade

#### Goal

- Improve navigation efficiency and reduce layout heaviness.

#### Optimization Items

1. Add collapsible sidebar mode.
2. Add clearer current-page highlighting.
3. Add recent-visit or quick-access section.
4. Upgrade top header.
   - global search placeholder
   - current user shortcut
   - environment or backend health indicator

#### Candidate Files

- [AppSidebar.vue](/F:/Java/spring-ai-platform/web/src/components/common/AppSidebar.vue)
- [AppHeader.vue](/F:/Java/spring-ai-platform/web/src/components/common/AppHeader.vue)
- [MainLayout.vue](/F:/Java/spring-ai-platform/web/src/layouts/MainLayout.vue)

### P2-3 Batch Operations and Action-Density Optimization

#### Goal

- Make admin-heavy pages more efficient to use.

#### Optimization Items

1. Add batch selection where meaningful.
   - user management
   - document management
2. Move low-frequency row actions into dropdown menus.
3. Keep dangerous actions separated from routine actions.
4. Add sticky toolbar for selected items.

#### Candidate Files

- [UserTable.vue](/F:/Java/spring-ai-platform/web/src/components/user/UserTable.vue)
- [PermissionTable.vue](/F:/Java/spring-ai-platform/web/src/components/user/PermissionTable.vue)
- [DocumentTable.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentTable.vue)

### P2-4 Keyboard Shortcuts and Efficiency Enhancements

#### Goal

- Make the app feel faster for frequent use.

#### Optimization Items

1. Add keyboard shortcuts.
   - new chat
   - focus input
   - open search
   - quick page switch
2. Improve focus management.
3. Improve form keyboard navigation.

#### Candidate Files

- [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue)
- [ChatInput.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatInput.vue)
- [MainLayout.vue](/F:/Java/spring-ai-platform/web/src/layouts/MainLayout.vue)

### P3-1 Motion and Micro-Interaction Polish

#### Goal

- Add product polish without creating distraction.

#### Optional Directions

1. Subtle page transition
2. Card reveal animation
3. Hover feedback refinement
4. Better streaming typing rhythm in chat

### P3-2 Onboarding and Empty-State Guidance

#### Goal

- Reduce first-use confusion.

#### Optional Directions

1. First-visit helper on chat page
2. First-use helper on RAG page
3. Better empty-state copy with recommended next steps

### P3-3 Visual Identity Refinement

#### Goal

- Make the frontend feel more distinctive as an AI-oriented product.

#### Optional Directions

1. Refine typography scale
2. Improve color role system
3. Different visual identity for assistant, RAG, monitor, and admin spaces
4. Stronger icon consistency

## Recommended Execution Order

1. P1-4 Page header, card, and table design unification
2. P1-3 Global state feedback standardization
3. P1-1 Chat workspace redesign
4. P1-2 RAG workflow restructuring
5. P2-2 Sidebar and header interaction upgrade
6. P2-1 Dashboard and monitor readability improvement
7. P2-3 Batch operations and action-density optimization
8. P2-4 Keyboard shortcuts and efficiency enhancements
9. P3 optional polish items

## Iteration Guidance

- Prefer one vertical slice at a time.
- Each slice should include:
  - layout update
  - interaction update
  - responsive behavior check
  - empty/loading/error state update if affected
- Avoid broad visual rewrites without improving user task flow.
- Keep the existing admin-console baseline, but selectively strengthen the AI product feel in chat and RAG.

## Suggested First Execution Slice

- Target: chat workspace redesign
- Scope:
  - simplify header actions
  - reduce session action noise
  - strengthen message interactions
  - upgrade input bar
- Reason:
  - chat is the most central user-facing workflow
  - interaction gains here will be felt most immediately

## Progress Tracking Template

Use this section when continuing later.

### In Progress

- P3-3 Visual identity refinement
  - Assistant, RAG, monitor, and admin pages are being split into clearer space-level visual themes
  - Shared styles are being extended so each workspace keeps a distinct tone without breaking the existing design system
  - Typography rhythm, action controls, and status surfaces are being aligned so hero, section title, and workspace-level accents feel more intentional

### Next Candidate Task

- P2-1 Dashboard and monitor readability improvement
- Next best candidate: add operator context filters and cross-table quick narrowing in monitor so user or agent context can be reused without retyping

### Completed in This Frontend Plan

- P1-1 Chat workspace redesign
  - Reworked [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue) into a clearer workspace layout with hero header and staged conversation area
  - Upgraded [SessionList.vue](/F:/Java/spring-ai-platform/web/src/components/chat/SessionList.vue) with search, quieter default presentation, and hover-revealed actions
  - Refactored [SessionList.vue](/F:/Java/spring-ai-platform/web/src/components/chat/SessionList.vue) and added [SessionListRow.vue](/F:/Java/spring-ai-platform/web/src/components/chat/SessionListRow.vue) so session rows are template-based and easier to extend
  - Extended session scanning with `Pinned / Today / Yesterday / Earlier / Archived` grouping, stronger current-session labeling, and search-hit highlighting for title or session id
  - Upgraded [ChatMessages.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatMessages.vue) with a stronger empty state and quick-start prompts
  - Added per-session scroll-position memory in [ChatMessages.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatMessages.vue) so switching sessions and returning preserves reading position instead of always snapping to the latest message
  - Upgraded [MessageBubble.vue](/F:/Java/spring-ai-platform/web/src/components/chat/MessageBubble.vue) with copy action and clearer metadata
  - Upgraded [ChatInput.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatInput.vue) with prompt chips, stop-generation control, and stronger interaction hints
  - Added per-session unsent draft persistence in [ChatInput.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatInput.vue) and [chat.ts](/F:/Java/spring-ai-platform/web/src/stores/chat.ts), backed by `sessionStorage` so switching sessions or refreshing the page does not immediately discard in-progress prompts
  - Reworked [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue) so the header export area now supports transcript download, transcript copy, and compact session-summary copy instead of a placeholder button
  - Extended [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue) with session-level quick sharing actions such as copying the current session id and copying a reusable context prompt scaffold derived from the latest conversation state
  - Added chat route-state alignment in [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue) so `agent` and `session` are reflected into the URL query and can be restored from a copied deep link
  - Added stream stop support and related state handling in [chat.ts](/F:/Java/spring-ai-platform/web/src/stores/chat.ts)
  - Refined shared visual rhythm and chat workspace styling in [base.css](/F:/Java/spring-ai-platform/web/src/styles/base.css)
- P1-4 Page header, card, and table design unification
  - Added unified hero-style page headers and clearer action areas to [DashboardView.vue](/F:/Java/spring-ai-platform/web/src/views/DashboardView.vue), [RagView.vue](/F:/Java/spring-ai-platform/web/src/views/RagView.vue), [UserView.vue](/F:/Java/spring-ai-platform/web/src/views/UserView.vue), [GatewayView.vue](/F:/Java/spring-ai-platform/web/src/views/GatewayView.vue), and [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)
  - Standardized card header rhythm, page title hierarchy, and chat-adjacent workspace styling in [base.css](/F:/Java/spring-ai-platform/web/src/styles/base.css)
  - Reorganized RAG and admin page first-screen information to make the main task and primary actions clearer
  - Refined [DocumentTable.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentTable.vue), [UserTable.vue](/F:/Java/spring-ai-platform/web/src/components/user/UserTable.vue), and [PermissionTable.vue](/F:/Java/spring-ai-platform/web/src/components/user/PermissionTable.vue) to improve table readability, empty states, and action hierarchy
  - Added shared subtle-text support in [base.css](/F:/Java/spring-ai-platform/web/src/styles/base.css) to reduce repeated local styling and improve consistency
  - Reworked [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue), [AlertEvents.vue](/F:/Java/spring-ai-platform/web/src/components/monitor/AlertEvents.vue), and [TopUsersTable.vue](/F:/Java/spring-ai-platform/web/src/components/monitor/TopUsersTable.vue) to unify monitor card headers, table copy, empty states, and alert presentation
- P1-3 Global state feedback standardization
  - Added global confirmation flow via [useConfirm.ts](/F:/Java/spring-ai-platform/web/src/composables/useConfirm.ts) and [ConfirmDialog.vue](/F:/Java/spring-ai-platform/web/src/components/common/ConfirmDialog.vue), mounted from [App.vue](/F:/Java/spring-ai-platform/web/src/App.vue)
  - Added reusable empty-state component in [EmptyState.vue](/F:/Java/spring-ai-platform/web/src/components/common/EmptyState.vue)
  - Replaced browser-native delete confirmations in [UserView.vue](/F:/Java/spring-ai-platform/web/src/views/UserView.vue) and [DocumentTable.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentTable.vue) with the unified confirmation dialog
  - Applied reusable empty-state presentation in [McpView.vue](/F:/Java/spring-ai-platform/web/src/views/McpView.vue)
  - Added loading and retry state handling to [user.ts](/F:/Java/spring-ai-platform/web/src/stores/user.ts) and [rag.ts](/F:/Java/spring-ai-platform/web/src/stores/rag.ts)
  - Added loading placeholders and retry-oriented empty/error views to [UserView.vue](/F:/Java/spring-ai-platform/web/src/views/UserView.vue), [KnowledgeBaseGrid.vue](/F:/Java/spring-ai-platform/web/src/components/rag/KnowledgeBaseGrid.vue), [DocumentTable.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentTable.vue), and [McpView.vue](/F:/Java/spring-ai-platform/web/src/views/McpView.vue)
  - Added staged upload progress and recent task summaries to [DocumentUpload.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentUpload.vue)
  - Added query-stage messaging, retry affordance, and clearer query failure handling to [RagQueryPanel.vue](/F:/Java/spring-ai-platform/web/src/components/rag/RagQueryPanel.vue)
  - Added exporting state tracking in [monitor.ts](/F:/Java/spring-ai-platform/web/src/stores/monitor.ts) and button-level loading/error feedback in [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue)
- P2-1 Dashboard and monitor readability improvement
  - Reworked [DashboardView.vue](/F:/Java/spring-ai-platform/web/src/views/DashboardView.vue) to surface today-summary cards before charts and detail tables
  - Reworked [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue) to surface risk, slow-path, and negative-feedback summaries before dense data blocks
  - Added operator quick actions such as copying user ids from dashboard and monitor tables to reduce context switching during issue triage
  - Added monitor-page context filtering in [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue) so user or agent selections from tables can narrow feedback, evidence, slow-request, and failure panels without manual re-entry
  - Reworked [TopUsersTable.vue](/F:/Java/spring-ai-platform/web/src/components/monitor/TopUsersTable.vue) so top-user rows can directly drive monitor context instead of being read-only
  - Reworked [DashboardView.vue](/F:/Java/spring-ai-platform/web/src/views/DashboardView.vue) so audit-log rows and risk-summary cards can jump into [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue) with `userId` or `agent` route context pre-applied
- P2-2 Sidebar and header interaction upgrade
  - Reworked [MainLayout.vue](/F:/Java/spring-ai-platform/web/src/layouts/MainLayout.vue) to persist sidebar collapse state and recent visits
  - Reworked [AppSidebar.vue](/F:/Java/spring-ai-platform/web/src/components/common/AppSidebar.vue) with collapsible navigation and recent-view shortcuts
  - Reworked [AppHeader.vue](/F:/Java/spring-ai-platform/web/src/components/common/AppHeader.vue) with quick navigation, stronger current-page framing, and user shortcut area
  - Added lightweight quick-jump search in [AppHeader.vue](/F:/Java/spring-ai-platform/web/src/components/common/AppHeader.vue) and global `Ctrl/Cmd + K` focus handling in [MainLayout.vue](/F:/Java/spring-ai-platform/web/src/layouts/MainLayout.vue) so page switching no longer depends only on sidebar scanning
  - Normalized navigation titles in [index.ts](/F:/Java/spring-ai-platform/web/src/router/index.ts) so header and breadcrumb labels stay consistent
- P2-3 Batch operations and action-density optimization
  - Added batch selection and bulk delete flow to [UserView.vue](/F:/Java/spring-ai-platform/web/src/views/UserView.vue) and [UserTable.vue](/F:/Java/spring-ai-platform/web/src/components/user/UserTable.vue)
  - Added batch selection, bulk delete, and bulk reindex flow to [DocumentTable.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentTable.vue)
  - Reworked [DocumentTable.vue](/F:/Java/spring-ai-platform/web/src/components/rag/DocumentTable.vue) with status filters, clearer batch disable states, filtered empty states, and recent batch-result feedback so large document sets are easier to manage
- P2-4 Keyboard shortcuts and efficiency enhancements
  - Added global page-switch shortcuts and chat shortcut dispatching in [MainLayout.vue](/F:/Java/spring-ai-platform/web/src/layouts/MainLayout.vue)
  - Added focus exposure and keyboard hint copy in [ChatInput.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatInput.vue)
  - Added chat shortcut handling and discoverability hints in [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue)
- P3-2 Onboarding and empty-state guidance
  - Added first-use onboarding steps and clearer starter guidance to [ChatMessages.vue](/F:/Java/spring-ai-platform/web/src/components/chat/ChatMessages.vue)
  - Added first-use 3-step onboarding guidance to [RagView.vue](/F:/Java/spring-ai-platform/web/src/views/RagView.vue)
- P3-1 Motion and micro-interaction polish
  - Added route-level page transition in [App.vue](/F:/Java/spring-ai-platform/web/src/App.vue)
  - Extended shared motion definitions in [transitions.css](/F:/Java/spring-ai-platform/web/src/styles/transitions.css)
  - Added restrained page, hero, card, and prompt motion polish in [base.css](/F:/Java/spring-ai-platform/web/src/styles/base.css)
- P3-3 Visual identity refinement
  - Added workspace-level semantic theme hooks in [ChatView.vue](/F:/Java/spring-ai-platform/web/src/views/ChatView.vue), [RagView.vue](/F:/Java/spring-ai-platform/web/src/views/RagView.vue), [MonitorView.vue](/F:/Java/spring-ai-platform/web/src/views/MonitorView.vue), and [UserView.vue](/F:/Java/spring-ai-platform/web/src/views/UserView.vue)
  - Added differentiated chat, RAG, monitor, and admin visual surfaces, hero gradients, and accent treatments in [base.css](/F:/Java/spring-ai-platform/web/src/styles/base.css)
  - Extended the same workspace identity approach to [DashboardView.vue](/F:/Java/spring-ai-platform/web/src/views/DashboardView.vue), [GatewayView.vue](/F:/Java/spring-ai-platform/web/src/views/GatewayView.vue), and [McpView.vue](/F:/Java/spring-ai-platform/web/src/views/McpView.vue)
  - Refined typography rhythm and icon presentation for section headers and knowledge-base cards in [base.css](/F:/Java/spring-ai-platform/web/src/styles/base.css) and [KnowledgeBaseGrid.vue](/F:/Java/spring-ai-platform/web/src/components/rag/KnowledgeBaseGrid.vue)
  - Refined component-level action buttons, status pills, alert cards, and model-card surfaces in [base.css](/F:/Java/spring-ai-platform/web/src/styles/base.css) and [AlertEvents.vue](/F:/Java/spring-ai-platform/web/src/components/monitor/AlertEvents.vue)
