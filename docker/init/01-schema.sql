CREATE SCHEMA IF NOT EXISTS knowledge;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge.staged_documents (
    document_id VARCHAR(64) PRIMARY KEY,
    file_name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'STAGED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge.parsed_content (
    content_id UUID PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    raw_text TEXT,
    parsing_status VARCHAR(20),
    extracted_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge.document_chunks (
    chunk_id UUID PRIMARY KEY,
    document_id VARCHAR(64),
    content_id UUID,
    chunk_text TEXT,
    chunk_index INTEGER,
    embedding vector(768),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE knowledge.staged_documents
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS last_modified_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;

ALTER TABLE knowledge.parsed_content
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;

ALTER TABLE knowledge.document_chunks
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_staged_doc_version
ON knowledge.staged_documents (document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_parsed_content_version
ON knowledge.parsed_content (document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_chunks_version
ON knowledge.document_chunks (document_id, content_hash);
