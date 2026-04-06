# Development Guidelines

## Backend Layering

This project follows a strict layered backend structure.

Default call chain:

`controller -> service -> mapper`

For complex orchestration flows, use:

`controller -> orchestrator -> service -> mapper`

## Required Rules

- `controller` only handles request parsing, basic validation, auth entry, and response mapping.
- `controller` must not contain business logic.
- `controller` must not directly call `mapper`.
- `service` owns business rules, transactions, orchestration, and domain conversion.
- `mapper` only handles persistence and SQL execution.
- Only MyBatis persistence interfaces may use the `*Mapper` suffix.
- DTO/VO/response conversion components must use `*Assembler` naming instead of `*Mapper`.
- API responses must use response/view objects instead of exposing persistence entities directly.
- New SQL in MyBatis must be implemented in XML, not annotation SQL.

## Controller Rules

- Keep controller methods short and focused.
- Do not place multi-step business branching in controllers.
- Do not build SQL or persistence conditions in controllers.
- Prefer delegating complex flows to `orchestrator` or application services.

## Service Rules

- Business logic belongs in `service` or `orchestrator`.
- Split oversized services instead of continuing to expand them.
- Distinguish expected business failures from unexpected system failures.

## Mapper Rules

- Mapper interfaces only define data access methods.
- Do not place business decisions in mapper implementations or SQL mappings.
- Do not add annotation-based SQL for business queries.
- Reserve the `*Mapper` suffix for MyBatis persistence interfaces only.
- Use `*Assembler` or `*ResponseAssembler` for DTO/VO/response conversion components.

## DTO / Entity / VO Rules

- `DTO`: request input
- `Entity`: persistence model
- `VO/Response`: API output

Controllers must not directly return entities.

## Logging And Exceptions

- Core error logs should include `traceId`, and when available `userId`, `sessionId`, `agentType`.
- Expected business rejection should prefer `warn`.
- Unexpected failures should prefer `error`.
- Avoid broad `catch (Exception)` in core request flows unless it is an intentional boundary.

## Review Checklist

Before merging backend code, check:

- Is there business logic in `controller`?
- Does any `controller` directly call a `mapper`?
- Is any API directly returning an `entity`?
- Was any annotation SQL introduced?
- Did any service grow into a large mixed-responsibility class?

## Reference

Detailed project-specific guidance:

- [docs/backend-layered-development-guidelines.md](/F:/Java/spring-ai-platform/docs/backend-layered-development-guidelines.md)
- [docs/backend-code-remediation-plan.md](/F:/Java/spring-ai-platform/docs/backend-code-remediation-plan.md)
