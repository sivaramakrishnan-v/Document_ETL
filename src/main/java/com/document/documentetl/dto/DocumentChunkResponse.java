package com.document.documentetl.dto;

import com.document.documentetl.model.DocumentChunk;

import java.util.UUID;

public class DocumentChunkResponse {

    private UUID chunkId;
    private Long documentId;
    private String chunkText;
    private int chunkIndex;
    private boolean processed;

    public DocumentChunkResponse() {
    }

    public DocumentChunkResponse(UUID chunkId,
                                 Long documentId,
                                 String chunkText,
                                 int chunkIndex,
                                 float[] embedding) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.chunkText = chunkText;
        this.chunkIndex = chunkIndex;
        this.processed = embedding != null && embedding.length > 0;
    }

    public DocumentChunkResponse(DocumentChunk documentChunk) {
        this(documentChunk.getChunkId(),
             documentChunk.getDocumentId(),
             documentChunk.getChunkText(),
             documentChunk.getChunkIndex(),
             documentChunk.getEmbedding());
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

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }
}
