CREATE USER advisor_user WITH ENCRYPTED PASSWORD 'admin123';

CREATE DATABASE ai_advisor_db;

/* ==========================================================================
   DATABASE EVOLUTION SCRIPT: Document ETL & Vector Search (RAG)
   ==========================================================================
   Description: This script tracks the schema from initial creation to the 
   production-grade "Smart Update" layer.
*/

-- 1. INITIAL SCHEMA & EXTENSION SETUP
CREATE SCHEMA IF NOT EXISTS knowledge;

-- Enable the pgvector extension (required for AI embeddings)
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. CORE TABLE CREATION
-- Tracking local file uploads
CREATE TABLE IF NOT EXISTS knowledge.staged_documents (
    document_id VARCHAR(64) PRIMARY KEY,
    file_name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'STAGED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Storing raw text after Tika parsing
CREATE TABLE IF NOT EXISTS knowledge.parsed_content (
    content_id UUID PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    raw_text TEXT,
    parsing_status VARCHAR(20),
    extracted_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Storing text chunks and their mathematical vectors
CREATE TABLE IF NOT EXISTS knowledge.document_chunks (
    chunk_id UUID PRIMARY KEY,
    document_id VARCHAR(64),
    content_id UUID,
    chunk_text TEXT,
    chunk_index INTEGER,
    embedding vector(768), 
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. PERMISSIONS
GRANT ALL PRIVILEGES ON SCHEMA knowledge TO advisor_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA knowledge TO advisor_user;

-- 4. THE "SMART UPDATE" UPGRADE (IDEMPOTENCY & VERSIONING)
-- Upgrade Staged Documents
ALTER TABLE knowledge.staged_documents 
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS last_modified_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;

-- Upgrade Parsed Content
ALTER TABLE knowledge.parsed_content 
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;

-- Upgrade Document Chunks
ALTER TABLE knowledge.document_chunks 
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

-- 5. PERFORMANCE INDEXES
CREATE INDEX IF NOT EXISTS idx_staged_doc_version 
ON knowledge.staged_documents (document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_parsed_content_version 
ON knowledge.parsed_content (document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_chunks_version 
ON knowledge.document_chunks (document_id, content_hash);

-- 6. MLFLOW TRACE BRIDGE PERSISTENCE
CREATE TABLE IF NOT EXISTS knowledge.mlflow_trace_bridge_events (
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

CREATE TABLE IF NOT EXISTS knowledge.mlflow_trace_bridge_documents (
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
        REFERENCES knowledge.mlflow_trace_bridge_events (event_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mlflow_trace_bridge_events_run
ON knowledge.mlflow_trace_bridge_events (run_id, event_time_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS idx_mlflow_trace_bridge_events_ingested
ON knowledge.mlflow_trace_bridge_events (ingested, event_time_epoch_ms ASC);

CREATE INDEX IF NOT EXISTS idx_mlflow_trace_bridge_docs_event
ON knowledge.mlflow_trace_bridge_documents (event_id, rank_order);

-- 6.1 PERMISSIONS FOR TRACE BRIDGE TABLES
GRANT ALL PRIVILEGES ON TABLE knowledge.mlflow_trace_bridge_events TO advisor_user;
GRANT ALL PRIVILEGES ON TABLE knowledge.mlflow_trace_bridge_documents TO advisor_user;
GRANT USAGE, SELECT ON SEQUENCE knowledge.mlflow_trace_bridge_documents_id_seq TO advisor_user;

-- 6.2 TOKEN USAGE TRACKING
CREATE TABLE IF NOT EXISTS knowledge.token_usage_events (
    id BIGSERIAL PRIMARY KEY,
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
ON knowledge.token_usage_events (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_token_usage_operation
ON knowledge.token_usage_events (operation_name);

CREATE INDEX IF NOT EXISTS idx_token_usage_status
ON knowledge.token_usage_events (status);

GRANT ALL PRIVILEGES ON TABLE knowledge.token_usage_events TO advisor_user;
GRANT USAGE, SELECT ON SEQUENCE knowledge.token_usage_events_id_seq TO advisor_user;

-- 7. VERIFICATION QUERY
SELECT table_name, column_name, data_type 
FROM information_schema.columns 
WHERE table_schema = 'knowledge' 
ORDER BY table_name, ordinal_position;
