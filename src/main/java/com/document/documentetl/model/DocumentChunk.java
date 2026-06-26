package com.document.documentetl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_chunks", schema = "document_etl")
public class DocumentChunk {

    @Id
    @Column(name = "chunk_id", nullable = false, updatable = false)
    private UUID chunkId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", columnDefinition = "vector(768)")
    private float[] embedding;

    public DocumentChunk() {
        // JPA default constructor
    }

    public DocumentChunk(UUID chunkId,
                         Long documentId,
                         UUID contentId,
                         String chunkText,
                         int chunkIndex,
                         String contentHash,
                         LocalDateTime createdAt) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.contentId = contentId;
        this.chunkText = chunkText;
        this.chunkIndex = chunkIndex;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
        this.embedding = null;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
}
