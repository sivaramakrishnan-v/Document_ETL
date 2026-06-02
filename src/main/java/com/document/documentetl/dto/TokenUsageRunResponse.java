package com.document.documentetl.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class TokenUsageRunResponse {

    private String runId;
    private boolean legacyUncorrelated;
    private int actionCount;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private List<TokenUsageEventResponse> actions = new ArrayList<>();

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public boolean isLegacyUncorrelated() {
        return legacyUncorrelated;
    }

    public void setLegacyUncorrelated(boolean legacyUncorrelated) {
        this.legacyUncorrelated = legacyUncorrelated;
    }

    public int getActionCount() {
        return actionCount;
    }

    public void setActionCount(int actionCount) {
        this.actionCount = actionCount;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<TokenUsageEventResponse> getActions() {
        return actions;
    }

    public void setActions(List<TokenUsageEventResponse> actions) {
        this.actions = actions;
    }
}
