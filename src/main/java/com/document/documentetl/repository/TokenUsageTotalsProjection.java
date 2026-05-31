package com.document.documentetl.repository;

import java.time.Instant;

public interface TokenUsageTotalsProjection {
    long getPromptTokens();

    long getCompletionTokens();

    long getTotalTokens();

    long getRequestCount();

    long getSuccessCount();

    long getFailureCount();

    Instant getFirstSeenAt();

    Instant getLastSeenAt();
}
