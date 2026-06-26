CREATE SCHEMA IF NOT EXISTS document_etl;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_etl.staged_documents (
    document_id VARCHAR(64) PRIMARY KEY,
    file_name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'STAGED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_etl.parsed_content (
    content_id UUID PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    raw_text TEXT,
    parsing_status VARCHAR(20),
    extracted_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_etl.document_chunks (
    chunk_id UUID PRIMARY KEY,
    document_id VARCHAR(64),
    content_id UUID,
    chunk_text TEXT,
    chunk_index INTEGER,
    embedding vector(768),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE document_etl.staged_documents
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS last_modified_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;

ALTER TABLE document_etl.parsed_content
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;

ALTER TABLE document_etl.document_chunks
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_staged_doc_version
ON document_etl.staged_documents (document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_parsed_content_version
ON document_etl.parsed_content (document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_chunks_version
ON document_etl.document_chunks (document_id, content_hash);

CREATE TABLE IF NOT EXISTS document_etl.token_usage_events (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(100),
    operation_name VARCHAR(128) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    prompt_chars INTEGER NOT NULL,
    completion_chars INTEGER NOT NULL DEFAULT 0,
    prompt_tokens INTEGER NOT NULL,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_token_usage_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

ALTER TABLE document_etl.token_usage_events
ADD COLUMN IF NOT EXISTS run_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_token_usage_created_at
ON document_etl.token_usage_events (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_token_usage_operation
ON document_etl.token_usage_events (operation_name);

CREATE INDEX IF NOT EXISTS idx_token_usage_status
ON document_etl.token_usage_events (status);

CREATE INDEX IF NOT EXISTS idx_token_usage_run_id
ON document_etl.token_usage_events (run_id);

CREATE TABLE IF NOT EXISTS document_etl.rag_workflow_checkpoint (
    checkpoint_id UUID PRIMARY KEY,
    thread_id VARCHAR(100) NOT NULL,
    checkpoint_namespace VARCHAR(100) NOT NULL DEFAULT 'default',
    user_query TEXT,
    normalized_query TEXT,
    rewritten_query TEXT,
    retrieval_strategy VARCHAR(100),
    retrieved_document_ids JSONB,
    retrieved_chunk_ids JSONB,
    retrieved_context_snapshot JSONB,
    generated_answer TEXT,
    citations JSONB,
    validation_status VARCHAR(50),
    groundedness_score NUMERIC(5,4),
    citation_coverage_score NUMERIC(5,4),
    unsupported_claims_count INTEGER,
    grounding_status VARCHAR(50),
    workflow_status VARCHAR(50),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE document_etl.rag_workflow_checkpoint
ADD COLUMN IF NOT EXISTS groundedness_score NUMERIC(5,4),
ADD COLUMN IF NOT EXISTS citation_coverage_score NUMERIC(5,4),
ADD COLUMN IF NOT EXISTS unsupported_claims_count INTEGER,
ADD COLUMN IF NOT EXISTS grounding_status VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_rag_checkpoint_thread_id
ON document_etl.rag_workflow_checkpoint(thread_id);

CREATE INDEX IF NOT EXISTS idx_rag_checkpoint_thread_created
ON document_etl.rag_workflow_checkpoint(thread_id, created_at DESC);
