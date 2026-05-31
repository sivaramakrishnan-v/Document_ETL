package com.document.documentetl.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class TokenUsageSummaryResponse {

    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long requestCount;
    private long successCount;
    private long failureCount;
    private double failureRatePct;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastSeenAt;
    private List<TokenUsageOperationDto> topOperations;

    public TokenUsageSummaryResponse() {
    }

    public TokenUsageSummaryResponse(long promptTokens,
                                     long completionTokens,
                                     long totalTokens,
                                     long requestCount,
                                     long successCount,
                                     long failureCount,
                                     double failureRatePct,
                                     OffsetDateTime firstSeenAt,
                                     OffsetDateTime lastSeenAt,
                                     List<TokenUsageOperationDto> topOperations) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.requestCount = requestCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.failureRatePct = failureRatePct;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.topOperations = topOperations;
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(long promptTokens) {
        this.promptTokens = promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(long completionTokens) {
        this.completionTokens = completionTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(long successCount) {
        this.successCount = successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(long failureCount) {
        this.failureCount = failureCount;
    }

    public double getFailureRatePct() {
        return failureRatePct;
    }

    public void setFailureRatePct(double failureRatePct) {
        this.failureRatePct = failureRatePct;
    }

    public OffsetDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(OffsetDateTime firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(OffsetDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public List<TokenUsageOperationDto> getTopOperations() {
        return topOperations;
    }

    public void setTopOperations(List<TokenUsageOperationDto> topOperations) {
        this.topOperations = topOperations;
    }
}
