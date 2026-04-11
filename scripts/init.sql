CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS ai_users (
    userid VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    department VARCHAR(128),
    province VARCHAR(64),
    city VARCHAR(64),
    employee_id VARCHAR(32),
    roles VARCHAR(255),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_login_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_roles (
    id BIGINT PRIMARY KEY,
    role_name VARCHAR(64) UNIQUE NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_user_roles (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES ai_users(userid) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES ai_roles(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uk_ai_user_roles UNIQUE (user_id, role_id)
);

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

ALTER TABLE ai_agent_definitions ADD COLUMN IF NOT EXISTS tool_codes VARCHAR(512);
ALTER TABLE ai_agent_definitions ADD COLUMN IF NOT EXISTS mcp_server_codes VARCHAR(512);

CREATE TABLE IF NOT EXISTS ai_role_token_limits (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES ai_roles(id),
    bot_type VARCHAR(64),
    daily_token_limit INT NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_user_token_limits (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES ai_users(userid),
    bot_type VARCHAR(64),
    daily_token_limit INT NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_agent_roles (
    id BIGINT PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL REFERENCES ai_agent_definitions(agent_code) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES ai_roles(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uk_ai_agent_roles UNIQUE (agent_code, role_id)
);

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    department VARCHAR(128),
    visibility_scope VARCHAR(16) DEFAULT 'DEPARTMENT',
    chunk_size INT DEFAULT 1000,
    chunk_overlap INT DEFAULT 200,
    chunk_strategy VARCHAR(16) DEFAULT 'TOKEN',
    structured_batch_size INT DEFAULT 20,
    created_by VARCHAR(64),
    status VARCHAR(16) DEFAULT 'ACTIVE',
    document_count INT DEFAULT 0,
    total_chunks INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS document_meta (
    id BIGINT PRIMARY KEY,
    filename VARCHAR(255),
    knowledge_base_id BIGINT REFERENCES knowledge_bases(id),
    file_size BIGINT,
    storage_path VARCHAR(500),
    content_type VARCHAR(128),
    chunk_count INT DEFAULT 0,
    uploaded_by VARCHAR(64),
    status VARCHAR(16) DEFAULT 'PROCESSING',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    indexed_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content TEXT,
    metadata JSONB,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_vector_store_kb_id
    ON vector_store ((metadata->>'kb_id'));

CREATE TABLE IF NOT EXISTS ai_audit_logs (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(64),
    agent_type VARCHAR(64),
    model_id VARCHAR(64),
    user_message TEXT,
    ai_response TEXT,
    prompt_tokens INT,
    completion_tokens INT,
    latency_ms BIGINT,
    success BOOLEAN,
    error_message TEXT,
    client_ip VARCHAR(64),
    country VARCHAR(64),
    province VARCHAR(64),
    city VARCHAR(64),
    session_id VARCHAR(128),
    trace_id VARCHAR(64),
    phase_breakdown_json TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_response_feedback (
    id BIGINT PRIMARY KEY,
    response_id BIGINT NOT NULL UNIQUE,
    source_type VARCHAR(32) NOT NULL,
    knowledge_base_id BIGINT,
    feedback VARCHAR(16) NOT NULL,
    comment VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_evidence_feedback (
    id BIGINT PRIMARY KEY,
    response_id BIGINT NOT NULL,
    chunk_id VARCHAR(128) NOT NULL,
    knowledge_base_id BIGINT,
    feedback VARCHAR(16) NOT NULL,
    comment VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uk_evidence_feedback UNIQUE (response_id, chunk_id)
);

CREATE TABLE IF NOT EXISTS ai_tool_audit_logs (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(64),
    session_id VARCHAR(128),
    agent_type VARCHAR(64),
    tool_name VARCHAR(128) NOT NULL,
    tool_class VARCHAR(128),
    input_summary TEXT,
    output_summary TEXT,
    success BOOLEAN NOT NULL,
    error_message VARCHAR(500),
    reason_code VARCHAR(128),
    denied_resource VARCHAR(255),
    latency_ms BIGINT,
    trace_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_alert_workflow (
    fingerprint VARCHAR(128) PRIMARY KEY,
    workflow_status VARCHAR(32) NOT NULL,
    workflow_note VARCHAR(500),
    silenced_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_alert_workflow_history (
    id BIGINT PRIMARY KEY,
    fingerprint VARCHAR(128) NOT NULL,
    workflow_status VARCHAR(32) NOT NULL,
    workflow_note VARCHAR(500),
    silenced_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_multi_agent_traces (
    id BIGINT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    agent_type VARCHAR(64) NOT NULL,
    request_summary TEXT,
    final_summary TEXT,
    status VARCHAR(32) NOT NULL,
    total_prompt_tokens INTEGER DEFAULT 0,
    total_completion_tokens INTEGER DEFAULT 0,
    total_latency_ms BIGINT DEFAULT 0,
    step_count INTEGER DEFAULT 0,
    error_message VARCHAR(500),
    parent_trace_id VARCHAR(64),
    recovery_source_trace_id VARCHAR(64),
    recovery_source_step_order INTEGER,
    recovery_action VARCHAR(32),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_multi_agent_trace_steps (
    id BIGINT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    step_order INTEGER NOT NULL,
    stage VARCHAR(32) NOT NULL,
    agent_name VARCHAR(64) NOT NULL,
    input_summary TEXT,
    output_summary TEXT,
    prompt_tokens INTEGER DEFAULT 0,
    completion_tokens INTEGER DEFAULT 0,
    latency_ms BIGINT DEFAULT 0,
    success BOOLEAN NOT NULL,
    error_message VARCHAR(500),
    recoverable BOOLEAN DEFAULT TRUE,
    skipped BOOLEAN DEFAULT FALSE,
    recovery_action VARCHAR(32),
    source_trace_id VARCHAR(64),
    source_step_order INTEGER,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS learning_favorites (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    response_id BIGINT,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    agent_type VARCHAR(64),
    session_id VARCHAR(128),
    session_summary VARCHAR(255),
    source_message_index INTEGER,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_collected_at TIMESTAMP,
    duplicate_count INTEGER DEFAULT 1,
    tags_json TEXT,
    session_config_snapshot_json TEXT
);

CREATE TABLE IF NOT EXISTS learning_notes (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    source_type VARCHAR(32),
    related_favorite_id BIGINT,
    related_session_id VARCHAR(128),
    related_agent_type VARCHAR(64),
    related_session_summary VARCHAR(255),
    related_message_index INTEGER,
    tags_json TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS learning_followup_templates (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    source_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_user_id ON ai_audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_created ON ai_audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_agent ON ai_audit_logs (agent_type);
CREATE INDEX IF NOT EXISTS idx_audit_trace_id ON ai_audit_logs (trace_id);
CREATE INDEX IF NOT EXISTS idx_feedback_created ON ai_response_feedback (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feedback_source ON ai_response_feedback (source_type, feedback);
CREATE INDEX IF NOT EXISTS idx_evidence_feedback_created ON ai_evidence_feedback (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_evidence_feedback_chunk ON ai_evidence_feedback (chunk_id);
CREATE INDEX IF NOT EXISTS idx_tool_audit_created ON ai_tool_audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tool_audit_lookup ON ai_tool_audit_logs (user_id, agent_type, tool_name);
CREATE INDEX IF NOT EXISTS idx_alert_workflow_status ON ai_alert_workflow (workflow_status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_alert_workflow_history_fp ON ai_alert_workflow_history (fingerprint, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_multi_trace_user_session ON ai_multi_agent_traces (user_id, session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_multi_trace_status ON ai_multi_agent_traces (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_multi_trace_steps_lookup ON ai_multi_agent_trace_steps (trace_id, step_order);
CREATE INDEX IF NOT EXISTS idx_learning_favorites_user_time ON learning_favorites (user_id, last_collected_at DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_learning_notes_user_time ON learning_notes (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_learning_templates_user_time ON learning_followup_templates (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_role_token_limits_role ON ai_role_token_limits (role_id);
CREATE INDEX IF NOT EXISTS idx_role_token_limits_bot ON ai_role_token_limits (bot_type);
CREATE INDEX IF NOT EXISTS idx_user_token_limits_user ON ai_user_token_limits (user_id);
CREATE INDEX IF NOT EXISTS idx_user_token_limits_bot ON ai_user_token_limits (bot_type);

CREATE UNIQUE INDEX IF NOT EXISTS uk_role_token_limits_global
    ON ai_role_token_limits (role_id)
    WHERE bot_type IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_role_token_limits_bot
    ON ai_role_token_limits (role_id, bot_type)
    WHERE bot_type IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_token_limits_global
    ON ai_user_token_limits (user_id)
    WHERE bot_type IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_token_limits_bot
    ON ai_user_token_limits (user_id, bot_type)
    WHERE bot_type IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_agent_definitions_enabled_sort
    ON ai_agent_definitions (enabled, sort_order, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_roles_agent
    ON ai_agent_roles (agent_code);

CREATE INDEX IF NOT EXISTS idx_ai_agent_roles_role
    ON ai_agent_roles (role_id);

CREATE TABLE IF NOT EXISTS gateway_model_stats (
    model_id VARCHAR(64) PRIMARY KEY,
    total_calls INT DEFAULT 0,
    success_calls INT DEFAULT 0,
    total_latency_ms BIGINT DEFAULT 0,
    total_prompt_tokens BIGINT DEFAULT 0,
    total_completion_tokens BIGINT DEFAULT 0,
    total_estimated_cost DOUBLE PRECISION DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO ai_roles (id, role_name, description) VALUES
    (1001, 'ROLE_ADMIN', '系统管理员'),
    (1002, 'ROLE_RD', '研发工程师'),
    (1003, 'ROLE_SALES', '销售人员'),
    (1004, 'ROLE_HR', '人力资源'),
    (1005, 'ROLE_FINANCE', '财务人员'),
    (1006, 'ROLE_USER', '普通用户')
ON CONFLICT DO NOTHING;

INSERT INTO ai_users (
    userid,
    username,
    password_hash,
    department,
    province,
    city,
    employee_id,
    roles,
    enabled,
    created_at,
    updated_at
) VALUES (
    'admin',
    '管理员',
    '$2a$10$Jpg5nrec5XoRPj8bq4T74ehjfMuzd/DgttGurKPBoVgTaYrutI312',
    '系统管理',
    '湖北省',
    '武汉市',
    'EMP0001',
    'ROLE_ADMIN,ROLE_RD,ROLE_SALES,ROLE_HR,ROLE_FINANCE,ROLE_USER',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT DO NOTHING;

INSERT INTO ai_user_roles (id, user_id, role_id) VALUES
    (11001, 'admin', 1001),
    (11002, 'admin', 1002),
    (11003, 'admin', 1003),
    (11004, 'admin', 1004),
    (11005, 'admin', 1005),
    (11006, 'admin', 1006)
ON CONFLICT DO NOTHING;

INSERT INTO ai_agent_definitions (
    id,
    agent_code,
    agent_name,
    description,
    icon,
    color,
    system_prompt,
    default_model,
    tool_codes,
    mcp_server_codes,
    enabled,
    sort_order,
    daily_token_limit,
    assistant_profile,
    system_defined,
    created_by,
    updated_by,
    created_at,
    updated_at
) VALUES
    (2001, 'rd', '研发助手', '研发问答与方案分析', 'RD', '#4f8ef7',
     '你是企业研发助手，负责代码审查、缺陷分析、技术方案设计与研发知识问答。当前用户: {userId}',
     'auto', 'rd-tools,internal-api', '', TRUE, 10, 100000, 'generic', FALSE, 'system', 'system', NOW(), NOW()),
    (2002, 'sales', '销售助手', '销售支持与客户沟通', 'SA', '#3dd68c',
     '你是企业销售助手，负责报价查询、客户需求分析、方案推荐和订单相关问答。当前用户: {userId}',
     'auto', 'sales-tools', '', TRUE, 20, 100000, 'generic', FALSE, 'system', 'system', NOW(), NOW()),
    (2003, 'hr', 'HR 助手', '人事制度与流程答疑', 'HR', '#9d7cf4',
     '你是 HR 助手，负责员工信息查询、审批流程、政策问答和人事流程说明。当前用户: {userId}',
     'auto', 'hr-tools', '', TRUE, 30, 100000, 'generic', FALSE, 'system', 'system', NOW(), NOW()),
    (2004, 'finance', '财务助手', '财务分析与报表解读', 'FN', '#f5a623',
     '你是财务助手，负责报表解读、预算对比、费用分析和审批相关问答。当前用户: {userId}',
     'auto', 'finance-tools', '', TRUE, 40, 100000, 'generic', FALSE, 'system', 'system', NOW(), NOW()),
    (2005, 'supply-chain', '供应链助手', '供应链协同与状态跟踪', 'SC', '#2dd4bf',
     '你是供应链助手，负责库存查询、采购订单跟踪、补货建议和交付风险分析。当前用户: {userId}',
     'auto', 'supply-chain-tools', '', TRUE, 50, 100000, 'generic', FALSE, 'system', 'system', NOW(), NOW()),
    (2006, 'qc', '质控助手', '质量事件分析与预警', 'QC', '#f06060',
     '你是质控助手，负责质量事件分析、质检结果解读和风险预警。当前用户: {userId}',
     'auto', 'qc-tools', '', TRUE, 60, 100000, 'generic', FALSE, 'system', 'system', NOW(), NOW()),
    (2007, 'weather', '天气助手', '天气查询与出行建议', 'WX', '#38bdf8',
     '你是天气助手，负责天气查询、预报解读和出行建议。当前用户: {userId}',
     'auto', 'weather-tools', '', TRUE, 70, 100000, 'generic', FALSE, 'system', 'system', NOW(), NOW()),
    (2008, 'search', '搜索助手', '通用搜索与信息归纳', 'SE', '#6366f1',
     '你是搜索助手，负责互联网检索、网页摘要、信息比对与事实归纳。当前用户: {userId}',
     'auto', 'search-tools', '', TRUE, 80, 100000, 'generic', FALSE, 'system', 'system', NOW(), NOW()),
    (2009, 'data-analysis', '数据分析助手', '数据查询与分析说明', 'DA', '#f59e0b',
     '你是数据分析助手，负责数据库查询、结果解读、统计分析和图表建议。当前用户: {userId}',
     'auto', 'data-analysis-tools', '', TRUE, 90, 100000, 'generic', FALSE, 'system', 'system', NOW(), NOW()),
    (2010, 'code', '代码助手', '代码分析与实现建议', 'CO', '#10b981',
     '你是代码助手，负责代码分析、质量审查、实现建议和仓库检索。当前用户: {userId}',
     'auto', 'code-tools', '', TRUE, 100, 100000, 'generic', FALSE, 'system', 'system', NOW(), NOW()),
    (2011, 'mcp', 'MCP 助手', 'MCP 工具接入、服务诊断和能力扩展', 'MC', '#8b5cf6',
     '你是 MCP 助手，负责使用 MCP 工具完成外部服务调用与能力扩展。当前用户: {userId}',
     'auto', '', '', TRUE, 110, 100000, 'mcp', TRUE, 'system', 'system', NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO ai_agent_roles (id, agent_code, role_id) VALUES
    (21001, 'rd', 1001),
    (21002, 'rd', 1002),
    (21003, 'sales', 1001),
    (21004, 'sales', 1003),
    (21005, 'hr', 1001),
    (21006, 'hr', 1004),
    (21007, 'finance', 1001),
    (21008, 'finance', 1005),
    (21009, 'supply-chain', 1001),
    (21010, 'supply-chain', 1006),
    (21011, 'qc', 1001),
    (21012, 'qc', 1006),
    (21013, 'weather', 1001),
    (21014, 'weather', 1006),
    (21015, 'search', 1001),
    (21016, 'search', 1006),
    (21017, 'data-analysis', 1001),
    (21018, 'data-analysis', 1002),
    (21019, 'data-analysis', 1005),
    (21020, 'code', 1001),
    (21021, 'code', 1002),
    (21022, 'mcp', 1001)
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_bases (
    id,
    name,
    description,
    department,
    visibility_scope,
    created_by,
    chunk_size,
    chunk_overlap,
    status,
    document_count,
    total_chunks,
    created_at,
    updated_at
) VALUES
    (3001, '企业通用知识库', '公司政策、HR 规定、行政制度等通用知识', '全公司', 'PUBLIC', 'system', 1000, 200, 'ACTIVE', 0, 0, NOW(), NOW()),
    (3002, '研发技术知识库', '技术规范、API 文档、架构设计、代码规范', '研发中心', 'DEPARTMENT', 'system', 1000, 200, 'ACTIVE', 0, 0, NOW(), NOW()),
    (3003, '销售产品知识库', '产品手册、报价策略、客户案例、竞品分析', '销售部', 'DEPARTMENT', 'system', 1000, 200, 'ACTIVE', 0, 0, NOW(), NOW())
ON CONFLICT DO NOTHING;
