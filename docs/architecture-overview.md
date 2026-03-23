# Spring AI Platform Architecture Overview

## Purpose

This document records the current runtime architecture, startup dependencies, and key request flows so the project can be restarted and extended without re-reading the whole codebase.

## Services

- `auth-service`
  - login, refresh token, logout, token validation
  - user and bot-permission management
- `agent-service`
  - assistant routing
  - session memory and session lifecycle
  - multi-agent orchestration
- `rag-service`
  - knowledge base management
  - document upload, parsing, chunking, indexing
  - retrieval and evidence display
- `gateway-service`
  - model registry
  - provider routing and load balancing
- `monitor-service`
  - audit-log query and metrics aggregation
  - slow request / failure sample views
  - alert aggregation
- `platform-common`
  - JWT validation filter
  - CORS filter
  - result wrapper and exception handling
  - trace id propagation
- `web`
  - admin console for chat, RAG, user management, monitoring

## Runtime Dependencies

### External Infrastructure

- PostgreSQL
  - users, permissions, audit logs, knowledge bases, document metadata, vector store
- Redis
  - token blacklist
  - token quota counters
  - session metadata
  - user profile cache
- S3 or MinIO compatible storage
  - original RAG source files
- LLM providers
  - OpenAI / DeepSeek / Qwen / Anthropic / others configured in env

## Startup Order

1. PostgreSQL
2. Redis
3. S3 or MinIO
4. `auth-service`
5. `gateway-service`
6. `agent-service`
7. `rag-service`
8. `monitor-service`
9. `web`

## Main Request Flows

### Authentication

1. `web` calls `auth-service /api/v1/auth/login`
2. `auth-service` validates user and issues access token + refresh token
3. frontend stores both tokens
4. other services validate JWT through shared `platform-common` filter
5. on `401`, frontend attempts one refresh request before redirecting to login

### Agent Chat

1. frontend sends chat request with `Authorization` and `X-Session-Id`
2. `agent-service` resolves user context
3. permission and token quota are checked
4. assistant agent is selected from `AssistantAgentRegistry`
5. session memory is loaded from `ConversationMemoryService`
6. result is streamed back to frontend
7. audit log and metrics are recorded

### RAG Upload

1. admin uploads file from frontend
2. `rag-service` creates `document_meta` in `PROCESSING`
3. source file is stored in object storage
4. file is parsed and chunked
5. chunk metadata is written into vector store metadata
6. document transitions to `INDEXED` or `FAILED`
7. frontend can inspect failure reason, retry, reindex, or preview chunks

### RAG Query

1. frontend sends question and knowledge base id
2. `rag-service` performs vector retrieval
3. retrieved chunks are passed to LLM prompt
4. answer and source evidence are returned
5. frontend shows final answer plus retrieval evidence panel

### Monitoring

1. request audit logs are written into `ai_audit_logs`
2. `monitor-service` queries database and meter registry
3. monitoring UI renders overview, per-agent/per-model stats, alerts, slow requests, and failure samples

## Module Boundaries

### Auth

- owns user identity, refresh-token lifecycle, and bot-permission records
- should not contain chat, RAG, or monitor business rules

### Agent

- owns assistant routing, session lifecycle, and per-agent access checks
- should not own user CRUD or knowledge-base indexing logic

### RAG

- owns document ingestion and retrieval behavior
- should not own general assistant orchestration

### Monitor

- owns query-oriented operational views over audit logs and metrics
- should not own the original write path for business data

## Known Design Conventions

- `Result<T>` is the standard API envelope
- management endpoints are restricted to `ROLE_ADMIN`
- session ids use URL-encoded user id prefixes to avoid Chinese-character routing issues
- RAG structured files that are not yet supported are stored but marked failed with a clear reason

## Next Optimization Focus

- expand regression coverage around session routing, CORS boundaries, and refresh concurrency
- continue shrinking remaining large controllers
- keep public API DTOs typed and avoid reintroducing loose `Map<String, Object>` payloads
