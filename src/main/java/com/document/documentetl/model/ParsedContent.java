package com.document.documentetl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "parsed_content", schema = "knowledge")
public class ParsedContent {

    @Id
    @Column(name = "content_id", nullable = false, updatable = false)
    private UUID contentId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "parsing_status", nullable = false, length = 20)
    private String parsingStatus;

    @Column(name = "extracted_at", nullable = false)
    private LocalDateTime extractedAt;

    public ParsedContent() {
        // JPA default constructor
    }

    public ParsedContent(UUID contentId,
                         Long documentId,
                         String rawText,
                         String contentHash,
                         String parsingStatus,
                         LocalDateTime extractedAt) {
        this.contentId = contentId;
        this.documentId = documentId;
        this.rawText = rawText;
        this.contentHash = contentHash;
        this.parsingStatus = parsingStatus;
        this.extractedAt = extractedAt;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getParsingStatus() {
        return parsingStatus;
    }

    public void setParsingStatus(String parsingStatus) {
        this.parsingStatus = parsingStatus;
    }

    public LocalDateTime getExtractedAt() {
        return extractedAt;
    }

    public void setExtractedAt(LocalDateTime extractedAt) {
        this.extractedAt = extractedAt;
    }
}
