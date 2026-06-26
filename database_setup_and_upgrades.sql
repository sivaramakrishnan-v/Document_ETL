DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'advisor_user') THEN
        CREATE USER advisor_user WITH ENCRYPTED PASSWORD 'admin123';
    END IF;
END
$$;

SELECT 'CREATE DATABASE ai_advisor_db'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'ai_advisor_db')\gexec

/* ==========================================================================
   DATABASE EVOLUTION SCRIPT: Document ETL & Vector Search (RAG)
   ==========================================================================
   Description: This script tracks the schema from initial creation to the 
   production-grade "Smart Update" layer.
*/

-- 1. INITIAL SCHEMA & EXTENSION SETUP
CREATE SCHEMA IF NOT EXISTS document_etl;

-- Enable the pgvector extension (required for AI embeddings)
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. CORE TABLE CREATION
-- Tracking local file uploads
CREATE TABLE IF NOT EXISTS document_etl.staged_documents (
    document_id VARCHAR(64) PRIMARY KEY,
    file_name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'STAGED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Storing raw text after Tika parsing
CREATE TABLE IF NOT EXISTS document_etl.parsed_content (
    content_id UUID PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    raw_text TEXT,
    parsing_status VARCHAR(20),
    extracted_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Storing text chunks and their mathematical vectors
CREATE TABLE IF NOT EXISTS document_etl.document_chunks (
    chunk_id UUID PRIMARY KEY,
    document_id VARCHAR(64),
    content_id UUID,
    chunk_text TEXT,
    chunk_index INTEGER,
    embedding vector(768), 
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. PERMISSIONS
GRANT ALL PRIVILEGES ON SCHEMA document_etl TO advisor_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA document_etl TO advisor_user;

-- 4. THE "SMART UPDATE" UPGRADE (IDEMPOTENCY & VERSIONING)
-- Upgrade Staged Documents
ALTER TABLE document_etl.staged_documents 
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS last_modified_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;

-- Upgrade Parsed Content
ALTER TABLE document_etl.parsed_content 
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;

-- Upgrade Document Chunks
ALTER TABLE document_etl.document_chunks 
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

-- 5. PERFORMANCE INDEXES
CREATE INDEX IF NOT EXISTS idx_staged_doc_version 
ON document_etl.staged_documents (document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_parsed_content_version 
ON document_etl.parsed_content (document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_chunks_version 
ON document_etl.document_chunks (document_id, content_hash);

-- 6. MLFLOW TRACE BRIDGE PERSISTENCE
CREATE TABLE IF NOT EXISTS document_etl.mlflow_trace_bridge_events (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    event_time_epoch_ms BIGINT NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    question TEXT,
    answer TEXT,
    root_span_name VARCHAR(128),
    retriever_span_name VARCHAR(128),
    retriever_span_type VARCHAR(32),
    ingested BOOLEAN NOT NULL DEFAULT FALSE,
    ingested_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_etl.mlflow_trace_bridge_documents (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    doc_id BIGINT,
    source TEXT,
    source_path TEXT,
    rank_order INTEGER NOT NULL,
    similarity DOUBLE PRECISION,
    page_content TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mlflow_trace_bridge_docs_event
        FOREIGN KEY (event_id)
        REFERENCES document_etl.mlflow_trace_bridge_events (event_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mlflow_trace_bridge_events_run
ON document_etl.mlflow_trace_bridge_events (run_id, event_time_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS idx_mlflow_trace_bridge_events_ingested
ON document_etl.mlflow_trace_bridge_events (ingested, event_time_epoch_ms ASC);

CREATE INDEX IF NOT EXISTS idx_mlflow_trace_bridge_docs_event
ON document_etl.mlflow_trace_bridge_documents (event_id, rank_order);

-- 6.1 PERMISSIONS FOR TRACE BRIDGE TABLES
GRANT ALL PRIVILEGES ON TABLE document_etl.mlflow_trace_bridge_events TO advisor_user;
GRANT ALL PRIVILEGES ON TABLE document_etl.mlflow_trace_bridge_documents TO advisor_user;
GRANT USAGE, SELECT ON SEQUENCE document_etl.mlflow_trace_bridge_documents_id_seq TO advisor_user;

-- 6.2 TOKEN USAGE TRACKING
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

CREATE INDEX IF NOT EXISTS idx_token_usage_created_at
ON document_etl.token_usage_events (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_token_usage_operation
ON document_etl.token_usage_events (operation_name);

CREATE INDEX IF NOT EXISTS idx_token_usage_status
ON document_etl.token_usage_events (status);

ALTER TABLE document_etl.token_usage_events
ADD COLUMN IF NOT EXISTS run_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_token_usage_run_id
ON document_etl.token_usage_events (run_id);

GRANT ALL PRIVILEGES ON TABLE document_etl.token_usage_events TO advisor_user;
GRANT USAGE, SELECT ON SEQUENCE document_etl.token_usage_events_id_seq TO advisor_user;

-- 6.3 RAG WORKFLOW CHECKPOINT PERSISTENCE
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

GRANT ALL PRIVILEGES ON TABLE document_etl.rag_workflow_checkpoint TO advisor_user;

-- 7. VERIFICATION QUERY
SELECT table_name, column_name, data_type 
FROM information_schema.columns 
WHERE table_schema = 'document_etl' 
ORDER BY table_name, ordinal_position;
