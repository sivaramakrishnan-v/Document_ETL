package com.document.documentetl.dto;

import java.time.OffsetDateTime;

public class TokenUsageEventResponse {

    private Long id;
    private String operationName;
    private String modelName;
    private int promptChars;
    private int completionChars;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private String status;
    private String errorMessage;
    private OffsetDateTime createdAt;

    public TokenUsageEventResponse() {
    }

    public TokenUsageEventResponse(Long id,
                                   String operationName,
                                   String modelName,
                                   int promptChars,
                                   int completionChars,
                                   int promptTokens,
                                   int completionTokens,
                                   int totalTokens,
                                   String status,
                                   String errorMessage,
                                   OffsetDateTime createdAt) {
        this.id = id;
        this.operationName = operationName;
        this.modelName = modelName;
        this.promptChars = promptChars;
        this.completionChars = completionChars;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getPromptChars() {
        return promptChars;
    }

    public void setPromptChars(int promptChars) {
        this.promptChars = promptChars;
    }

    public int getCompletionChars() {
        return completionChars;
    }

    public void setCompletionChars(int completionChars) {
        this.completionChars = completionChars;
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
}
