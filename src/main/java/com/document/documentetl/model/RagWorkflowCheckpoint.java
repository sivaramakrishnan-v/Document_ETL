package com.document.documentetl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rag_workflow_checkpoint", schema = "document_etl")
public class RagWorkflowCheckpoint {

    @Id
    @Column(name = "checkpoint_id", nullable = false, updatable = false)
    private UUID checkpointId;

    @Column(name = "thread_id", nullable = false, length = 100)
    private String threadId;

    @Column(name = "checkpoint_namespace", nullable = false, length = 100)
    private String checkpointNamespace = "default";

    @Column(name = "user_query", columnDefinition = "text")
    private String userQuery;

    @Column(name = "normalized_query", columnDefinition = "text")
    private String normalizedQuery;

    @Column(name = "rewritten_query", columnDefinition = "text")
    private String rewrittenQuery;

    @Column(name = "retrieval_strategy", length = 100)
    private String retrievalStrategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retrieved_document_ids", columnDefinition = "jsonb")
    private List<Long> retrievedDocumentIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retrieved_chunk_ids", columnDefinition = "jsonb")
    private List<String> retrievedChunkIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retrieved_context_snapshot", columnDefinition = "jsonb")
    private List<String> retrievedContextSnapshot = new ArrayList<>();

    @Column(name = "generated_answer", columnDefinition = "text")
    private String generatedAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations", columnDefinition = "jsonb")
    private List<String> citations = new ArrayList<>();

    @Column(name = "validation_status", length = 50)
    private String validationStatus;

    @Column(name = "groundedness_score")
    private Double groundednessScore;

    @Column(name = "citation_coverage_score")
    private Double citationCoverageScore;

    @Column(name = "unsupported_claims_count")
    private Integer unsupportedClaimsCount;

    @Column(name = "grounding_status", length = 50)
    private String groundingStatus;

    @Column(name = "workflow_status", length = 50)
    private String workflowStatus;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public RagWorkflowCheckpoint() {
    }

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (checkpointId == null) {
            checkpointId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(UUID checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getCheckpointNamespace() {
        return checkpointNamespace;
    }

    public void setCheckpointNamespace(String checkpointNamespace) {
        this.checkpointNamespace = checkpointNamespace;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    public void setNormalizedQuery(String normalizedQuery) {
        this.normalizedQuery = normalizedQuery;
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery;
    }

    public String getRetrievalStrategy() {
        return retrievalStrategy;
    }

    public void setRetrievalStrategy(String retrievalStrategy) {
        this.retrievalStrategy = retrievalStrategy;
    }

    public List<Long> getRetrievedDocumentIds() {
        return retrievedDocumentIds;
    }

    public void setRetrievedDocumentIds(List<Long> retrievedDocumentIds) {
        this.retrievedDocumentIds = retrievedDocumentIds;
    }

    public List<String> getRetrievedChunkIds() {
        return retrievedChunkIds;
    }

    public void setRetrievedChunkIds(List<String> retrievedChunkIds) {
        this.retrievedChunkIds = retrievedChunkIds;
    }

    public List<String> getRetrievedContextSnapshot() {
        return retrievedContextSnapshot;
    }

    public void setRetrievedContextSnapshot(List<String> retrievedContextSnapshot) {
        this.retrievedContextSnapshot = retrievedContextSnapshot;
    }

    public String getGeneratedAnswer() {
        return generatedAnswer;
    }

    public void setGeneratedAnswer(String generatedAnswer) {
        this.generatedAnswer = generatedAnswer;
    }

    public List<String> getCitations() {
        return citations;
    }

    public void setCitations(List<String> citations) {
        this.citations = citations;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getWorkflowStatus() {
        return workflowStatus;
    }

    public Double getGroundednessScore() {
        return groundednessScore;
    }

    public void setGroundednessScore(Double groundednessScore) {
        this.groundednessScore = groundednessScore;
    }

    public Double getCitationCoverageScore() {
        return citationCoverageScore;
    }

    public void setCitationCoverageScore(Double citationCoverageScore) {
        this.citationCoverageScore = citationCoverageScore;
    }

    public Integer getUnsupportedClaimsCount() {
        return unsupportedClaimsCount;
    }

    public void setUnsupportedClaimsCount(Integer unsupportedClaimsCount) {
        this.unsupportedClaimsCount = unsupportedClaimsCount;
    }

    public String getGroundingStatus() {
        return groundingStatus;
    }

    public void setGroundingStatus(String groundingStatus) {
        this.groundingStatus = groundingStatus;
    }

    public void setWorkflowStatus(String workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
