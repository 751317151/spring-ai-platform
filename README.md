# Spring AI Platform

Multi-module Spring Boot platform for auth, agents, RAG, model gateway, and monitoring.

## Modules

- `auth-service`: authentication, user management, permission management
- `agent-service`: agent orchestration and business access control
- `rag-service`: document upload, parsing, and knowledge retrieval
- `gateway-service`: model routing for different LLM providers
- `monitor-service`: metrics and monitoring endpoints
- `platform-common`: shared security, JWT, result wrapper, common config
- `web`: frontend application

## Environment Setup

Runtime configuration has been moved out of service `application.yml` files.

1. Copy [`.env.example`](/F:/Java/spring-ai-platform/.env.example) to [`.env`](/F:/Java/spring-ai-platform/.env).
2. Fill in the required values for your local environment.

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

## Continuing Optimization

The next hardening tasks are tracked in [docs/optimization-plan.md](/F:/Java/spring-ai-platform/docs/optimization-plan.md).
