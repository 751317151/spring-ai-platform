-- ============================================================
-- Enterprise AI Platform — Database Initialization Script
-- PostgreSQL 14+   |   pgvector extension required
-- ============================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- Auth Service Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_users (
    id              VARCHAR(36) PRIMARY KEY,
    username        VARCHAR(64) UNIQUE NOT NULL,
    password_hash   VARCHAR(255),
    department      VARCHAR(128),
    employee_id     VARCHAR(32),
    roles           VARCHAR(255),          -- comma-separated: ROLE_ADMIN,ROLE_RD,...
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    last_login_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_roles (
    id          VARCHAR(36) PRIMARY KEY,
    role_name   VARCHAR(64) UNIQUE NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS ai_bot_permissions (
    id                  VARCHAR(36) PRIMARY KEY,
    bot_type            VARCHAR(32) UNIQUE NOT NULL,
    allowed_roles       VARCHAR(255),
    allowed_departments VARCHAR(255),
    data_scope          VARCHAR(16) DEFAULT 'DEPARTMENT',
    allowed_operations  VARCHAR(128),
    daily_token_limit   INT DEFAULT 100000,
    enabled             BOOLEAN DEFAULT TRUE
);

-- ============================================================
-- RAG Service Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    department      VARCHAR(128),
    chunk_size      INT DEFAULT 1000,
    chunk_overlap   INT DEFAULT 200,
    created_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS document_meta (
    id                  VARCHAR(36) PRIMARY KEY,
    filename            VARCHAR(255),
    knowledge_base_id   VARCHAR(36) REFERENCES knowledge_bases(id),
    file_size           BIGINT,
    storage_path        VARCHAR(500),
    chunk_count         INT DEFAULT 0,
    uploaded_by         VARCHAR(64),
    status              VARCHAR(16) DEFAULT 'PROCESSING',  -- PROCESSING | INDEXED | FAILED
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

-- PGVector table for embeddings (1536-dim for OpenAI/DeepSeek compatible)
CREATE TABLE IF NOT EXISTS vector_store (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content     TEXT,
    metadata    JSONB,
    embedding   vector(1536)
);

-- HNSW index for fast cosine similarity search
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Index for metadata-based filter (kb_id)
CREATE INDEX IF NOT EXISTS idx_vector_store_kb_id
    ON vector_store ((metadata->>'kb_id'));

-- ============================================================
-- Agent Service Tables (Audit Logs)
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_audit_logs (
    id                  VARCHAR(36) PRIMARY KEY,
    user_id             VARCHAR(64),
    agent_type          VARCHAR(64),
    model_id            VARCHAR(64),
    user_message        TEXT,
    ai_response         TEXT,
    prompt_tokens       INT,
    completion_tokens   INT,
    latency_ms          BIGINT,
    success             BOOLEAN,
    error_message       TEXT,
    client_ip           VARCHAR(64),
    session_id          VARCHAR(128),
    created_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_user_id   ON ai_audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_created   ON ai_audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_agent     ON ai_audit_logs (agent_type);

-- ============================================================
-- Gateway Service Tables (Model Stats)
-- ============================================================

CREATE TABLE IF NOT EXISTS gateway_model_stats (
    model_id        VARCHAR(64) PRIMARY KEY,
    total_calls     INT DEFAULT 0,
    success_calls   INT DEFAULT 0,
    total_latency_ms BIGINT DEFAULT 0,
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- Seed Data
-- ============================================================

-- Default roles
INSERT INTO ai_roles (id, role_name, description) VALUES
    (uuid_generate_v4()::text, 'ROLE_ADMIN',   '系统管理员'),
    (uuid_generate_v4()::text, 'ROLE_RD',      '研发工程师'),
    (uuid_generate_v4()::text, 'ROLE_SALES',   '销售人员'),
    (uuid_generate_v4()::text, 'ROLE_HR',      'HR 人员'),
    (uuid_generate_v4()::text, 'ROLE_FINANCE', '财务人员'),
    (uuid_generate_v4()::text, 'ROLE_USER',    '普通用户')
ON CONFLICT DO NOTHING;

-- Bot permission configs
INSERT INTO ai_bot_permissions (id, bot_type, allowed_roles, allowed_departments, data_scope, daily_token_limit, enabled) VALUES
    (uuid_generate_v4()::text, 'rd',           'ROLE_RD,ROLE_ADMIN',      '研发中心,系统管理',  'DEPARTMENT', 200000, TRUE),
    (uuid_generate_v4()::text, 'sales',        'ROLE_SALES,ROLE_ADMIN',   '销售部,系统管理',    'DEPARTMENT', 150000, TRUE),
    (uuid_generate_v4()::text, 'hr',           'ROLE_HR,ROLE_ADMIN',      '人力资源部,系统管理', 'DEPARTMENT', 100000, TRUE),
    (uuid_generate_v4()::text, 'finance',      'ROLE_FINANCE,ROLE_ADMIN', '财务部,系统管理',    'DEPARTMENT', 100000, TRUE),
    (uuid_generate_v4()::text, 'supply-chain', 'ROLE_USER,ROLE_ADMIN',    NULL,                 'DEPARTMENT', 100000, TRUE),
    (uuid_generate_v4()::text, 'qc',           'ROLE_USER,ROLE_ADMIN',    NULL,                 'DEPARTMENT', 100000, TRUE),
    (uuid_generate_v4()::text, 'multi',        'ROLE_ADMIN',              '系统管理',           'DEPARTMENT', 500000, TRUE)
ON CONFLICT DO NOTHING;

-- Default knowledge bases
INSERT INTO knowledge_bases (id, name, description, department, created_by, chunk_size, chunk_overlap, status, document_count, total_chunks, created_at) VALUES
    ('kb-001', '企业通用知识库', '公司政策、HR规定、行政制度等通用知识', '全公司',  'system', 1000, 200, 'ACTIVE', 0, 0, NOW()),
    ('kb-002', '研发技术知识库', '技术规范、API文档、架构设计、代码规范', '研发中心', 'system', 1000, 200, 'ACTIVE', 0, 0, NOW()),
    ('kb-003', '销售产品知识库', '产品手册、报价策略、客户案例、竞品分析', '销售部',  'system', 1000, 200, 'ACTIVE', 0, 0, NOW())
ON CONFLICT DO NOTHING;

-- Note: Demo users are created via POST /api/v1/auth/init-demo-users (password: admin123)
-- Password hashes are generated by BCryptPasswordEncoder at runtime.
