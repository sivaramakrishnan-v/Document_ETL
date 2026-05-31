package com.document.documentetl.model.v2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "extracted_contents", schema = "document_etl")
public class ExtractedContent {

    @Id
    @Column(name = "content_id", nullable = false, updatable = false)
    private UUID contentId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    @Column(name = "extraction_status", nullable = false, length = 32)
    private String extractionStatus = "EXTRACTED";

    @Column(name = "extracted_at", nullable = false)
    private OffsetDateTime extractedAt;

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

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getExtractionStatus() {
        return extractionStatus;
    }

    public void setExtractionStatus(String extractionStatus) {
        this.extractionStatus = extractionStatus;
    }

    public OffsetDateTime getExtractedAt() {
        return extractedAt;
    }

    public void setExtractedAt(OffsetDateTime extractedAt) {
        this.extractedAt = extractedAt;
    }
}
