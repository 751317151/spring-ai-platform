# Spring AI Platform

Multi-module Spring Boot project for authentication, agent orchestration, RAG, model routing, monitoring, and a Vue frontend.

## Modules

- `auth-service`: login, refresh token, user and permission management
- `agent-service`: assistant routing, session memory, multi-agent orchestration
- `rag-service`: knowledge base CRUD, document ingestion, chunk preview, retrieval evidence
- `gateway-service`: model registry, routing, and provider abstraction
- `monitor-service`: metrics, audit logs, alerts, slow-request and failure views
- `platform-common`: JWT, shared DTOs, security and common config
- `web`: frontend management console

## Environment Setup

Runtime configuration is externalized from service `application.yml`.

1. Copy [`.env.example`](/F:/Java/spring-ai-platform/.env.example) to [`.env`](/F:/Java/spring-ai-platform/.env).
2. Fill the variables according to your local environment.

Common variables:

- `DB_USER`
- `DB_PASS`
- `JWT_SECRET`
- `ALI_QWEN_API_KEY`
- `OPENAI_API_KEY`
- `DEEPSEEK_API_KEY`
- `QWEN_API_KEY`
- `ANTHROPIC_API_KEY`
- `S3_ENDPOINT`
- `S3_REGION`
- `S3_BUCKET`
- `S3_ACCESS_KEY`
- `S3_SECRET_KEY`
- `APP_CORS_ALLOWED_ORIGINS`

## Local Startup Order

Recommended order:

1. PostgreSQL, Redis, MinIO or S3-compatible storage
2. `auth-service`
3. `gateway-service`
4. `agent-service`
5. `rag-service`
6. `monitor-service`
7. `web`

If you use a single command runner or IDE compound configuration, keep the same dependency order so auth and gateway are ready before agent, rag, and monitor features initialize.

## Database Initialization

The repository currently uses [scripts/init.sql](/F:/Java/spring-ai-platform/scripts/init.sql) for baseline schema and seed data.

Typical local flow:

1. Create the target database.
2. Execute `scripts/init.sql`.
3. Confirm `vector` and `uuid-ossp` extensions are enabled.

## Current RAG Capabilities

- Document upload, retry, and reindex
- Chunk count and chunk preview inspection
- Retrieval evidence display in query results
- Same-name document replacement during upload
- Graceful failure for structured files that are stored but not yet parsed, such as `csv`, `json`, `xml`, `mol`, `sdf`, and `cdx`

## Current Monitoring Capabilities

- Overview latency and token metrics
- Hourly latency and error trends
- Agent and model usage breakdown
- Top token-consuming users
- Alert event list
- Slow request Top N
- Recent failure samples

## Verification

Backend:

```powershell
mvn test
```

Frontend:

```powershell
cd web
npm run build
```

## Troubleshooting

- Login redirects repeatedly:
  Check that `auth-service` and refresh token config are both available, and verify browser local storage still contains `auth_token` and `auth_refreshToken`.

- Frontend reports backend unavailable:
  Confirm the corresponding service is running and that the gateway or proxy route points to the correct local port.

- `password authentication failed for user "${DB_USER}"`:
  This usually means environment variables were not injected into the process that launched the service. Re-open the terminal or IDE run configuration and confirm `.env` variables are loaded before startup.

- RAG upload succeeds but document stays failed:
  Open the document list and inspect the failure reason. Structured files like `csv` or `json` are now stored intentionally but remain unindexed until a dedicated parser is added.

## Planning Docs

- Baseline hardening: [docs/optimization-plan.md](/F:/Java/spring-ai-platform/docs/optimization-plan.md)
- Follow-up optimization: [docs/follow-up-optimization-plan.md](/F:/Java/spring-ai-platform/docs/follow-up-optimization-plan.md)
- Architecture overview: [docs/architecture-overview.md](/F:/Java/spring-ai-platform/docs/architecture-overview.md)
