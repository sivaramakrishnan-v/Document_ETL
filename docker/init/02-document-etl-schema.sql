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
