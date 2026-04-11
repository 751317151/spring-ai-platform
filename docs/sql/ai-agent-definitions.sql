CREATE TABLE IF NOT EXISTS ai_agent_definitions (
    id BIGINT PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL UNIQUE,
    agent_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    icon VARCHAR(16),
    color VARCHAR(32),
    system_prompt TEXT NOT NULL,
    default_model VARCHAR(128),
    tool_codes VARCHAR(512),
    mcp_server_codes VARCHAR(512),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    daily_token_limit INTEGER NOT NULL DEFAULT 100000,
    assistant_profile VARCHAR(64) NOT NULL,
    system_defined BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_agent_roles (
    id BIGSERIAL PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_agent_roles_agent
        FOREIGN KEY (agent_code) REFERENCES ai_agent_definitions(agent_code) ON DELETE CASCADE,
    CONSTRAINT fk_ai_agent_roles_role
        FOREIGN KEY (role_id) REFERENCES ai_roles(id) ON DELETE CASCADE,
    CONSTRAINT uk_ai_agent_roles_agent_role UNIQUE (agent_code, role_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_definitions_enabled_sort
    ON ai_agent_definitions (enabled, sort_order, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_roles_agent
    ON ai_agent_roles (agent_code);

CREATE INDEX IF NOT EXISTS idx_ai_agent_roles_role
    ON ai_agent_roles (role_id);

COMMENT ON TABLE ai_agent_definitions IS '统一助手定义表，保存助手元数据、系统提示词与默认 Token 配额';
COMMENT ON TABLE ai_agent_roles IS '助手与角色关联表，定义哪些角色可以访问哪些助手';
COMMENT ON COLUMN ai_agent_definitions.agent_code IS '助手编码，直接作为 agentType 使用';
COMMENT ON COLUMN ai_agent_definitions.daily_token_limit IS '助手默认每日 Token 配额';
COMMENT ON COLUMN ai_agent_definitions.assistant_profile IS '助手运行时能力档案，普通助手默认 generic';
COMMENT ON COLUMN ai_agent_definitions.system_defined IS '是否为受保护的特殊助手';
