# Dynamic Agent Capability Addendum

## Scope

This addendum supplements `dynamic-agent-definition-optimization-design.md` with the true dynamic capability scheme for normal assistants.

## Data Model

- `ai_agent_definitions.tool_codes`: comma-separated tool group codes selected in the admin UI.
- `ai_agent_definitions.mcp_server_codes`: comma-separated MCP server codes selected in the admin UI.

## Runtime

- Normal assistants continue to use `ai_agent_definitions` as the single source of truth.
- `DynamicAssistantAgent` resolves selected Spring tool groups dynamically from `tool_codes`.
- `DynamicAssistantAgent` resolves selected MCP clients dynamically from `mcp_server_codes`.
- `mcp` and `multi` remain special runtime entries.

## Admin UI

- Assistant create/edit now configures roles, model, system prompt, tool groups, and MCP servers in one form.
- No extra permission rule table is required for normal assistant capability configuration.
