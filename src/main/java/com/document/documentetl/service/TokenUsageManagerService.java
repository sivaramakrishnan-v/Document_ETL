package com.document.documentetl.service;

import com.document.documentetl.dto.TokenUsageEventResponse;
import com.document.documentetl.dto.TokenUsageOperationDto;
import com.document.documentetl.dto.TokenUsageSummaryResponse;
import com.document.documentetl.model.TokenUsageEvent;
import com.document.documentetl.repository.TokenUsageEventRepository;
import com.document.documentetl.repository.TokenUsageOperationProjection;
import com.document.documentetl.repository.TokenUsageTotalsProjection;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class TokenUsageManagerService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MIN_TOKENS_FOR_NON_EMPTY_TEXT = 1;

    private final TokenUsageEventRepository tokenUsageEventRepository;

    public TokenUsageManagerService(TokenUsageEventRepository tokenUsageEventRepository) {
        this.tokenUsageEventRepository = tokenUsageEventRepository;
    }

    @Transactional
    public void recordSuccess(String operationName, String modelName, String prompt, String completion) {
        int promptChars = safeLength(prompt);
        int completionChars = safeLength(completion);
        int promptTokens = estimateTokens(prompt);
        int completionTokens = estimateTokens(completion);

        TokenUsageEvent event = new TokenUsageEvent();
        event.setOperationName(normalizeOperationName(operationName));
        event.setModelName(normalizeModelName(modelName));
        event.setPromptChars(promptChars);
        event.setCompletionChars(completionChars);
        event.setPromptTokens(promptTokens);
        event.setCompletionTokens(completionTokens);
        event.setTotalTokens(promptTokens + completionTokens);
        event.setStatus(STATUS_SUCCESS);
        event.setErrorMessage(null);
        event.setCreatedAt(OffsetDateTime.now());
        tokenUsageEventRepository.save(event);
    }

    @Transactional
    public void recordFailure(String operationName, String modelName, String prompt, Throwable throwable) {
        int promptChars = safeLength(prompt);
        int promptTokens = estimateTokens(prompt);

        TokenUsageEvent event = new TokenUsageEvent();
        event.setOperationName(normalizeOperationName(operationName));
        event.setModelName(normalizeModelName(modelName));
        event.setPromptChars(promptChars);
        event.setCompletionChars(0);
        event.setPromptTokens(promptTokens);
        event.setCompletionTokens(0);
        event.setTotalTokens(promptTokens);
        event.setStatus(STATUS_FAILED);
        event.setErrorMessage(extractErrorMessage(throwable));
        event.setCreatedAt(OffsetDateTime.now());
        tokenUsageEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public TokenUsageSummaryResponse getSummary(int topOperationsLimit) {
        int normalizedLimit = Math.max(1, Math.min(topOperationsLimit, 50));
        TokenUsageTotalsProjection totals = tokenUsageEventRepository.fetchTotals();
        List<TokenUsageOperationDto> topOperations = tokenUsageEventRepository.fetchTopOperations(normalizedLimit).stream()
                .map(TokenUsageManagerService::toOperationDto)
                .toList();

        long requestCount = totals != null ? totals.getRequestCount() : 0L;
        long failureCount = totals != null ? totals.getFailureCount() : 0L;
        double failureRatePct = requestCount == 0 ? 0.0 : ((double) failureCount * 100.0) / requestCount;

        return new TokenUsageSummaryResponse(
                totals != null ? totals.getPromptTokens() : 0L,
                totals != null ? totals.getCompletionTokens() : 0L,
                totals != null ? totals.getTotalTokens() : 0L,
                requestCount,
                totals != null ? totals.getSuccessCount() : 0L,
                failureCount,
                failureRatePct,
                totals != null ? toOffsetDateTime(totals.getFirstSeenAt()) : null,
                totals != null ? toOffsetDateTime(totals.getLastSeenAt()) : null,
                topOperations
        );
    }

    @Transactional(readOnly = true)
    public List<TokenUsageEventResponse> getRecentEvents(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 500));
        return tokenUsageEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, normalizedLimit)).stream()
                .map(event -> new TokenUsageEventResponse(
                        event.getId(),
                        event.getOperationName(),
                        event.getModelName(),
                        event.getPromptChars(),
                        event.getCompletionChars(),
                        event.getPromptTokens(),
                        event.getCompletionTokens(),
                        event.getTotalTokens(),
                        event.getStatus(),
                        event.getErrorMessage(),
                        event.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public long clearAllEvents() {
        long deleted = tokenUsageEventRepository.count();
        tokenUsageEventRepository.deleteAllInBatch();
        return deleted;
    }

    @Transactional
    public long clearEventsBefore(OffsetDateTime cutoff) {
        if (cutoff == null) {
            return 0L;
        }
        return tokenUsageEventRepository.deleteByCreatedAtBefore(cutoff);
    }

    private static TokenUsageOperationDto toOperationDto(TokenUsageOperationProjection projection) {
        return new TokenUsageOperationDto(
                projection.getOperationName(),
                projection.getRequestCount(),
                projection.getPromptTokens(),
                projection.getCompletionTokens(),
                projection.getTotalTokens()
        );
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int estimated = (int) Math.ceil(text.length() / 4.0d);
        return Math.max(MIN_TOKENS_FOR_NON_EMPTY_TEXT, estimated);
    }

    private static int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private static String normalizeOperationName(String operationName) {
        if (operationName == null || operationName.isBlank()) {
            return "generation.unknown";
        }
        String normalized = operationName.trim();
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }

    private static String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "unknown-model";
        }
        String normalized = modelName.trim();
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }

    private static String extractErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atOffset(ZoneOffset.UTC);
    }
}
