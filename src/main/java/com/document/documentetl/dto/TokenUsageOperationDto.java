package com.document.documentetl.dto;

public class TokenUsageOperationDto {

    private String operationName;
    private long requestCount;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;

    public TokenUsageOperationDto() {
    }

    public TokenUsageOperationDto(String operationName,
                                  long requestCount,
                                  long promptTokens,
                                  long completionTokens,
                                  long totalTokens) {
        this.operationName = operationName;
        this.requestCount = requestCount;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
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
}
