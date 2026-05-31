package com.document.documentetl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Metadata for a locally staged document.
 */
@Entity
@Table(name = "staged_documents", schema = "knowledge")
public class StagedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "file_name", columnDefinition = "text")
    private String fileName;

    @Column(name = "file_path", columnDefinition = "text")
    private String filePath;

    @Column(name = "file_size")
    private long fileSize;

    @Column(nullable = false, length = 20)
    private String status = "STAGED";

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "last_modified_at")
    private OffsetDateTime lastModifiedAt;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public StagedDocument() {
        // JPA requires a default constructor
    }

    public StagedDocument(Long documentId,
                          String fileName,
                          String filePath,
                          long fileSize,
                          String status,
                          String contentHash,
                          OffsetDateTime lastModifiedAt,
                          int versionNumber,
                          OffsetDateTime createdAt,
                          OffsetDateTime updatedAt) {
        this.documentId = documentId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        if (status != null) {
            this.status = status;
        }
        this.contentHash = contentHash;
        this.lastModifiedAt = lastModifiedAt;
        this.versionNumber = versionNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public OffsetDateTime getLastModifiedAt() {
        return lastModifiedAt;
    }

    public void setLastModifiedAt(OffsetDateTime lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
