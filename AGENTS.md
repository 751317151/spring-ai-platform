# AGENTS.md

## Backend Development Rules

All backend work in this repository must follow layered design.

Default flow:

`controller -> service -> mapper`

Complex orchestration flow:

`controller -> orchestrator -> service -> mapper`

Mandatory rules:

- `controller` does not implement business logic.
- `controller` does not directly call `mapper`.
- `service` owns business rules, orchestration, and transaction boundaries.
- `mapper` only handles persistence access.
- Only MyBatis persistence interfaces may use the `*Mapper` suffix.
- Persistence models must use the `*Entity` suffix.
- Controller input models should use `*Request` or `*DTO`.
- Controller output models should use `*Response` or `*VO`.
- DTO/VO/response conversion components must use `*Assembler` naming instead of `*Mapper`.
- Controllers do not directly expose persistence entities as API responses.
- New MyBatis SQL must be placed in XML mappings, not annotation SQL.
- Avoid broad `catch (Exception)` in core business flows unless it is a deliberate boundary for downgrade or infrastructure safety.

## References

- [docs/backend-layered-development-guidelines.md](/F:/Java/spring-ai-platform/docs/backend-layered-development-guidelines.md)
- [docs/backend-code-remediation-plan.md](/F:/Java/spring-ai-platform/docs/backend-code-remediation-plan.md)
