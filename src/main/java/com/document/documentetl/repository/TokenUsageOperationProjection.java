package com.document.documentetl.repository;

public interface TokenUsageOperationProjection {
    String getOperationName();

    long getRequestCount();

    long getPromptTokens();

    long getCompletionTokens();

    long getTotalTokens();
}
