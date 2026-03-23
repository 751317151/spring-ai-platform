# Spring AI Platform Optimization Plan

## Current Baseline

- P0 completed:
  - Fixed `gateway-service` build compatibility with Spring AI `1.1.0`
  - Enabled JWT blacklist enforcement in all services
  - Removed hardcoded admin login fallback from `auth-service`
  - Moved sensitive defaults out of service config into environment variables
  - Replaced permissive CORS wildcard with configurable origin allowlist
- P1 completed:
  - Removed Flyway because the current runtime targets PostgreSQL `16.13`, and restored `scripts/init.sql` bootstrap
  - Consolidated schema and seed data back into [`init.sql`](/F:/Java/spring-ai-platform/scripts/init.sql)
  - Aligned runtime Postgres credentials with `DB_USER` and `DB_PASS`
  - Added first unit-test batch for `auth-service` and `agent-service`
  - Added `gateway-service` routing tests and made fallback routing deterministic
  - Normalized deprecated SQL script comments to avoid garbled text in script files
  - Fixed authorization-denied handling to return `403` instead of `500`
  - Added GitHub Actions CI for backend tests and frontend build
  - Added `rag-service` delete-flow unit tests
  - Added `rag-service` upload-flow unit tests
  - Restored direct Chinese text in key source files to avoid `\uXXXX` and garbled comments
  - Added request `traceId` propagation and response echo in `platform-common`
  - Added initial RAG dependency failure metrics and stage latency metrics
- Verification completed:
  - Backend: `mvn test` passes
  - Frontend: `npm run build` passes

## Environment Conventions

- Root env file: [`.env`](/F:/Java/spring-ai-platform/.env)
- Example env file: [`.env.example`](/F:/Java/spring-ai-platform/.env.example)
- Required runtime variables:
  - `DB_USER`
  - `DB_PASS`
  - `JWT_SECRET`
  - `S3_BUCKET`
  - `S3_ACCESS_KEY`
  - `S3_SECRET_KEY`
  - `ALI_QWEN_API_KEY`

## Optimization Status

### P1: Engineering Hardening

1. Stabilize database bootstrap.
   - Completed:
     - removed Flyway dependencies and service-level Flyway configuration
     - restored shared schema + seed bootstrap in [`init.sql`](/F:/Java/spring-ai-platform/scripts/init.sql)
     - documented that versioned migrations should only be reintroduced after confirming PostgreSQL 16 compatibility

2. Add minimum automated tests.
   - Completed:
     - `auth-service`
       - login success/failure
       - logout blacklist flow
       - token validation rejection for blacklisted token
       - admin authorization
     - `agent-service`
       - permission checks
       - token quota checks
     - `rag-service`
       - upload flow completed
       - delete flow completed
     - `gateway-service`
       - model routing completed

3. Add CI pipeline.
   - Completed:
     - [ci.yml](/F:/Java/spring-ai-platform/.github/workflows/ci.yml)
     - backend `mvn test`
     - frontend `npm run build`
     - Docker image build for backend services and frontend web image

4. Standardize configuration naming.
   - Completed:
     - keep uppercase env style only
     - removed legacy aliases like `aliQwen_api`

### P2: Security and Authorization Refinement

1. Tighten authorization defaults.
   - Completed in [`AgentAccessChecker.java`](/F:/Java/spring-ai-platform/agent-service/src/main/java/com/huah/ai/platform/agent/security/AgentAccessChecker.java)
   - default deny on missing permission records
   - default deny on DB failure
   - added regression tests for role mismatch, missing config, and DB failure

2. Add role restrictions for management endpoints.
   - Completed in:
     - [`RagController.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/controller/RagController.java)
     - [`MonitorController.java`](/F:/Java/spring-ai-platform/monitor-service/src/main/java/com/huah/ai/platform/monitor/controller/MonitorController.java)
   - enabled method security in `rag-service` and `monitor-service`
   - restricted RAG management write endpoints to `ROLE_ADMIN`
   - restricted monitor endpoints to `ROLE_ADMIN`

3. Improve token lifecycle.
   - Completed in:
     - [`JwtUtil.java`](/F:/Java/spring-ai-platform/platform-common/src/main/java/com/huah/ai/platform/common/util/JwtUtil.java)
     - [`JwtAuthFilter.java`](/F:/Java/spring-ai-platform/platform-common/src/main/java/com/huah/ai/platform/common/filter/JwtAuthFilter.java)
     - [`AuthController.java`](/F:/Java/spring-ai-platform/auth-service/src/main/java/com/huah/ai/platform/auth/controller/AuthController.java)
   - short-lived access token
   - rotating refresh token
   - blacklist by `jti`
   - frontend stores refresh token and retries once on `401`

### P3: RAG and Observability

1. Add document ingestion state machine.
   - Completed core state transitions in:
     - [`DocumentMeta.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/model/DocumentMeta.java)
     - [`DocumentIngestionService.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/service/DocumentIngestionService.java)
     - [`DocumentMetaService.java`](/F:/Java/spring-ai-platform/rag-service/src/main/java/com/huah/ai/platform/rag/service/DocumentMetaService.java)
   - `PROCESSING`
   - `INDEXED`
   - `FAILED`
   - compensation on failed upload/index path
   - Completed extension:
     - explicit retry endpoint for failed documents
     - scheduled retry worker for retryable failed documents

2. Improve monitoring.
   - Completed:
     - request trace id propagation with `X-Trace-Id`
     - RAG stage latency metrics
     - RAG S3 / vector-store / database failure counters
     - gateway external model call latency and success/failure counters
     - agent external model call latency metrics
     - agent Redis failure counters for token quota and user profile reads/writes
     - monitor-service alert evaluation extracted from controller into rule-based service
     - Prometheus alert rules and monitor alert UI text normalized to readable Chinese
     - monitor-service now prefers Alertmanager active alerts and falls back to local rule evaluation
     - alert payload enriched with labels, fingerprints, and Alertmanager silence links

3. Remove silent frontend mock fallback.
   - Completed:
     - added explicit `VITE_DEMO_MODE` switch for frontend demo behavior
     - chat and RAG pages now show visible backend-status banners
     - disabled silent mock fallback when demo mode is off
     - normalized related frontend Chinese copy to readable UTF-8

## Suggested Execution Order

1. Database bootstrap hardening
2. Minimum tests
3. CI
4. Authorization tightening
5. RAG ingestion hardening
6. Monitoring upgrade
7. Frontend demo mode cleanup

## Completion Summary

- All items in this optimization plan have been completed for the current codebase baseline.
- Future work should be tracked in a new follow-up plan rather than reopening completed items here.

## Useful Verification Commands

```powershell
$env:DB_USER='postgres'
$env:DB_PASS='blackstar'
$env:JWT_SECRET='enterprise-ai-platform-secret-key-minimum-256-bits!!'
$env:S3_BUCKET='blackstar'
$env:S3_ACCESS_KEY='CtqsQz02BLuHIt555PMjoM4d'
$env:S3_SECRET_KEY='mltBO0bOnAXxXKvPCtFh4obvSrqavJz'
$env:ALI_QWEN_API_KEY='test-key'
mvn test
```

```powershell
cd web
npm run build
```
