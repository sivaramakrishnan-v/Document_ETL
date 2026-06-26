CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS document_etl;

COMMENT ON SCHEMA document_etl IS
'Document ETL pipeline schema for event-driven staging, parsing, chunking, embedding, retrieval, and reconciliation.';

CREATE TABLE IF NOT EXISTS document_etl.source_documents (
    document_id BIGSERIAL PRIMARY KEY,
    source_uri TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_size_bytes BIGINT,
    content_hash VARCHAR(64) NOT NULL,
    version_number INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'STAGED',
    last_modified_at TIMESTAMPTZ,
    staged_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_source_documents_source_hash UNIQUE (source_uri, content_hash),
    CONSTRAINT chk_source_documents_status CHECK (
        status IN ('STAGED', 'PARSING', 'PARSED', 'CHUNKED', 'COMPLETED', 'FAILED', 'SKIPPED')
    )
);

COMMENT ON TABLE document_etl.source_documents IS
'Tracks source files discovered or staged for the Document ETL pipeline.';

CREATE TABLE IF NOT EXISTS document_etl.extracted_contents (
    content_id UUID PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES document_etl.source_documents(document_id) ON DELETE CASCADE,
    content_hash VARCHAR(64) NOT NULL,
    version_number INTEGER NOT NULL,
    raw_text TEXT NOT NULL,
    extraction_status VARCHAR(32) NOT NULL DEFAULT 'EXTRACTED',
    extracted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_extracted_contents_document_hash UNIQUE (document_id, content_hash),
    CONSTRAINT chk_extracted_contents_status CHECK (extraction_status IN ('EXTRACTED', 'FAILED', 'SKIPPED'))
);

COMMENT ON TABLE document_etl.extracted_contents IS
'Stores raw text extracted from source documents after parsing.';

CREATE TABLE IF NOT EXISTS document_etl.text_chunks (
    chunk_id UUID PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES document_etl.source_documents(document_id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES document_etl.extracted_contents(content_id) ON DELETE CASCADE,
    content_hash VARCHAR(64) NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    token_count INTEGER,
    char_start INTEGER,
    char_end INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_text_chunks_content_index UNIQUE (content_id, chunk_index),
    CONSTRAINT chk_text_chunks_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT chk_text_chunks_token_count CHECK (token_count IS NULL OR token_count >= 0),
    CONSTRAINT chk_text_chunks_char_range CHECK (
        char_start IS NULL OR char_end IS NULL OR char_end >= char_start
    )
);

COMMENT ON TABLE document_etl.text_chunks IS
'Stores text chunks derived from extracted content. Embeddings are stored separately.';

CREATE TABLE IF NOT EXISTS document_etl.chunk_embeddings (
    embedding_id UUID PRIMARY KEY,
    chunk_id UUID NOT NULL REFERENCES document_etl.text_chunks(chunk_id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES document_etl.source_documents(document_id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES document_etl.extracted_contents(content_id) ON DELETE CASCADE,
    content_hash VARCHAR(64) NOT NULL,
    model_provider VARCHAR(64) NOT NULL DEFAULT 'vertex-ai',
    embedding_model VARCHAR(128) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    embedding vector NOT NULL,
    embedding_status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chunk_embeddings_model UNIQUE (chunk_id, model_provider, embedding_model, embedding_dimension),
    CONSTRAINT chk_chunk_embeddings_dimension CHECK (embedding_dimension > 0),
    CONSTRAINT chk_chunk_embeddings_status CHECK (
        embedding_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'RETRY_EXHAUSTED')
    )
);

COMMENT ON TABLE document_etl.chunk_embeddings IS
'Stores vector embeddings for text chunks, separated from source text and keyed by embedding model.';

CREATE TABLE IF NOT EXISTS document_etl.embedding_jobs (
    job_id UUID PRIMARY KEY,
    chunk_id UUID NOT NULL REFERENCES document_etl.text_chunks(chunk_id) ON DELETE CASCADE,
    model_provider VARCHAR(64) NOT NULL DEFAULT 'vertex-ai',
    embedding_model VARCHAR(128) NOT NULL,
    embedding_dimension INTEGER NOT NULL DEFAULT 768,
    job_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_embedding_jobs_chunk_model UNIQUE (chunk_id, model_provider, embedding_model, embedding_dimension),
    CONSTRAINT chk_embedding_jobs_status CHECK (
        job_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'RETRY_EXHAUSTED')
    ),
    CONSTRAINT chk_embedding_jobs_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_embedding_jobs_dimension CHECK (embedding_dimension > 0)
);

COMMENT ON TABLE document_etl.embedding_jobs IS
'Tracks embedding work items and retry state for Kafka consumers and reconciliation.';

CREATE TABLE IF NOT EXISTS document_etl.pipeline_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    topic_name VARCHAR(255),
    message_key TEXT,
    document_id BIGINT,
    content_id UUID,
    chunk_id UUID,
    content_hash VARCHAR(64),
    processing_status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    error_message TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pipeline_events_status CHECK (
        processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED', 'DLT')
    )
);

COMMENT ON TABLE document_etl.pipeline_events IS
'Audit table for Kafka pipeline events and processing outcomes.';

CREATE TABLE IF NOT EXISTS document_etl.knowledge_bases (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_knowledge_bases_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

COMMENT ON TABLE document_etl.knowledge_bases IS
'Logical knowledge bases used to group documents and retrieval scope.';

CREATE UNIQUE INDEX IF NOT EXISTS uq_knowledge_bases_name_ci
ON document_etl.knowledge_bases (LOWER(name));

CREATE TABLE IF NOT EXISTS document_etl.spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS spring_session_ix1
ON document_etl.spring_session (session_id);

CREATE INDEX IF NOT EXISTS spring_session_ix2
ON document_etl.spring_session (expiry_time);

CREATE INDEX IF NOT EXISTS spring_session_ix3
ON document_etl.spring_session (principal_name);

CREATE TABLE IF NOT EXISTS document_etl.spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES document_etl.spring_session(primary_id)
        ON DELETE CASCADE
);

INSERT INTO document_etl.knowledge_bases (id, name, description, status)
SELECT
    '00000000-0000-0000-0000-000000000001'::UUID,
    'General',
    'Default knowledge base',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM document_etl.knowledge_bases
    WHERE LOWER(name) = LOWER('General')
);

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
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_document_etl_token_usage_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

COMMENT ON TABLE document_etl.token_usage_events IS
'Tracks LLM token usage events for Document ETL operations.';

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
    agent_visited JSONB,
    agent_feedback JSONB,
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

COMMENT ON TABLE document_etl.rag_workflow_checkpoint IS
'Stores persistent checkpoints for RAG workflow history and status.';

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
    ingested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE document_etl.mlflow_trace_bridge_events IS
'Stores chat trace events prepared for MLflow ingestion.';

CREATE TABLE IF NOT EXISTS document_etl.mlflow_trace_bridge_documents (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    doc_id BIGINT,
    source TEXT,
    source_path TEXT,
    rank_order INTEGER NOT NULL,
    similarity DOUBLE PRECISION,
    page_content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_etl_mlflow_trace_bridge_docs_event
        FOREIGN KEY (event_id)
        REFERENCES document_etl.mlflow_trace_bridge_events (event_id)
        ON DELETE CASCADE
);

COMMENT ON TABLE document_etl.mlflow_trace_bridge_documents IS
'Stores retrieved document snapshots associated with MLflow trace bridge events.';

CREATE INDEX IF NOT EXISTS idx_source_documents_status
ON document_etl.source_documents (status);

CREATE INDEX IF NOT EXISTS idx_source_documents_content_hash
ON document_etl.source_documents (content_hash);

CREATE INDEX IF NOT EXISTS idx_extracted_contents_document_hash
ON document_etl.extracted_contents (document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_text_chunks_document_hash
ON document_etl.text_chunks (document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_text_chunks_content_id
ON document_etl.text_chunks (content_id);

CREATE INDEX IF NOT EXISTS idx_chunk_embeddings_chunk_model
ON document_etl.chunk_embeddings (chunk_id, model_provider, embedding_model);

CREATE INDEX IF NOT EXISTS idx_chunk_embeddings_document_model
ON document_etl.chunk_embeddings (document_id, model_provider, embedding_model);

CREATE INDEX IF NOT EXISTS idx_embedding_jobs_status_next_attempt
ON document_etl.embedding_jobs (job_status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_pipeline_events_document
ON document_etl.pipeline_events (document_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_pipeline_events_type_status
ON document_etl.pipeline_events (event_type, processing_status);

CREATE INDEX IF NOT EXISTS idx_document_etl_token_usage_created_at
ON document_etl.token_usage_events (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_document_etl_token_usage_operation
ON document_etl.token_usage_events (operation_name);

CREATE INDEX IF NOT EXISTS idx_document_etl_token_usage_status
ON document_etl.token_usage_events (status);

CREATE INDEX IF NOT EXISTS idx_document_etl_token_usage_run_id
ON document_etl.token_usage_events (run_id);

CREATE INDEX IF NOT EXISTS idx_document_etl_rag_checkpoint_thread_id
ON document_etl.rag_workflow_checkpoint(thread_id);

CREATE INDEX IF NOT EXISTS idx_document_etl_rag_checkpoint_thread_created
ON document_etl.rag_workflow_checkpoint(thread_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_document_etl_mlflow_trace_bridge_events_run
ON document_etl.mlflow_trace_bridge_events (run_id, event_time_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS idx_document_etl_mlflow_trace_bridge_events_ingested
ON document_etl.mlflow_trace_bridge_events (ingested, event_time_epoch_ms ASC);

CREATE INDEX IF NOT EXISTS idx_document_etl_mlflow_trace_bridge_docs_event
ON document_etl.mlflow_trace_bridge_documents (event_id, rank_order);
