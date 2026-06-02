package com.document.documentetl.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class RagCheckpointResponse {

    private UUID checkpointId;
    private String threadId;
    private String checkpointNamespace;
    private String userQuery;
    private String normalizedQuery;
    private String rewrittenQuery;
    private String retrievalStrategy;
    private List<Long> retrievedDocumentIds;
    private List<String> retrievedChunkIds;
    private List<String> retrievedContextSnapshot;
    private String generatedAnswer;
    private List<String> citations;
    private String validationStatus;
    private Double groundednessScore;
    private Double citationCoverageScore;
    private Integer unsupportedClaimsCount;
    private String groundingStatus;
    private String workflowStatus;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public RagCheckpointResponse() {
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
