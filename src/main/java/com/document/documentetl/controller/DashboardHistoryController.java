package com.document.documentetl.controller;

import com.document.documentetl.dto.RagCheckpointResponse;
import com.document.documentetl.dto.TokenUsageEventResponse;
import com.document.documentetl.dto.TokenUsageRunResponse;
import com.document.documentetl.model.RagWorkflowCheckpoint;
import com.document.documentetl.model.TokenUsageEvent;
import com.document.documentetl.repository.RagWorkflowCheckpointRepository;
import com.document.documentetl.repository.TokenUsageEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardHistoryController {

    private static final int MAX_LIMIT = 200;

    private final RagWorkflowCheckpointRepository checkpointRepository;
    private final TokenUsageEventRepository tokenUsageEventRepository;

    public DashboardHistoryController(RagWorkflowCheckpointRepository checkpointRepository,
                                      TokenUsageEventRepository tokenUsageEventRepository) {
        this.checkpointRepository = checkpointRepository;
        this.tokenUsageEventRepository = tokenUsageEventRepository;
    }

    @GetMapping("/token-runs")
    public List<TokenUsageRunResponse> tokenRuns(
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<TokenUsageEvent> events = tokenUsageEventRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, MAX_LIMIT)
        );
        Map<String, List<TokenUsageEvent>> groupedEvents = new LinkedHashMap<>();
        for (TokenUsageEvent event : events) {
            String runId = event.getRunId();
            String groupKey = runId == null || runId.isBlank() ? "legacy-event-" + event.getId() : runId;
            groupedEvents.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(event);
        }
        return groupedEvents.entrySet().stream()
                .limit(boundedLimit)
                .map(entry -> toTokenRunResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @GetMapping("/checkpoints")
    public List<RagCheckpointResponse> checkpoints(
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return checkpointRepository.findAll(PageRequest.of(
                        0,
                        boundedLimit,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )).stream()
                .map(DashboardHistoryController::toResponse)
                .toList();
    }

    private static RagCheckpointResponse toResponse(RagWorkflowCheckpoint checkpoint) {
        RagCheckpointResponse response = new RagCheckpointResponse();
        response.setCheckpointId(checkpoint.getCheckpointId());
        response.setThreadId(checkpoint.getThreadId());
        response.setCheckpointNamespace(checkpoint.getCheckpointNamespace());
        response.setUserQuery(checkpoint.getUserQuery());
        response.setNormalizedQuery(checkpoint.getNormalizedQuery());
        response.setRewrittenQuery(checkpoint.getRewrittenQuery());
        response.setRetrievalStrategy(checkpoint.getRetrievalStrategy());
        response.setRetrievedDocumentIds(copyList(checkpoint.getRetrievedDocumentIds()));
        response.setRetrievedChunkIds(copyList(checkpoint.getRetrievedChunkIds()));
        response.setRetrievedContextSnapshot(copyList(checkpoint.getRetrievedContextSnapshot()));
        response.setGeneratedAnswer(checkpoint.getGeneratedAnswer());
        response.setCitations(copyList(checkpoint.getCitations()));
        response.setAgentVisited(copyList(checkpoint.getAgentVisited()));
        response.setAgentFeedback(copyList(checkpoint.getAgentFeedback()));
        response.setValidationStatus(checkpoint.getValidationStatus());
        response.setGroundednessScore(checkpoint.getGroundednessScore());
        response.setCitationCoverageScore(checkpoint.getCitationCoverageScore());
        response.setUnsupportedClaimsCount(checkpoint.getUnsupportedClaimsCount());
        response.setGroundingStatus(checkpoint.getGroundingStatus());
        response.setWorkflowStatus(checkpoint.getWorkflowStatus());
        response.setErrorMessage(checkpoint.getErrorMessage());
        response.setCreatedAt(checkpoint.getCreatedAt());
        response.setUpdatedAt(checkpoint.getUpdatedAt());
        return response;
    }

    private static TokenUsageRunResponse toTokenRunResponse(String runId, List<TokenUsageEvent> events) {
        TokenUsageRunResponse response = new TokenUsageRunResponse();
        response.setRunId(runId);
        response.setLegacyUncorrelated(runId.startsWith("legacy-event-"));
        response.setActionCount(events.size());
        response.setPromptTokens(events.stream().mapToInt(TokenUsageEvent::getPromptTokens).sum());
        response.setCompletionTokens(events.stream().mapToInt(TokenUsageEvent::getCompletionTokens).sum());
        response.setTotalTokens(events.stream().mapToInt(TokenUsageEvent::getTotalTokens).sum());
        response.setStatus(events.stream().anyMatch(event -> "FAILED".equals(event.getStatus())) ? "FAILED" : "SUCCESS");
        response.setStartedAt(events.stream()
                .map(TokenUsageEvent::getCreatedAt)
                .min(OffsetDateTime::compareTo)
                .orElse(null));
        response.setCompletedAt(events.stream()
                .map(TokenUsageEvent::getCreatedAt)
                .max(OffsetDateTime::compareTo)
                .orElse(null));
        response.setActions(events.stream().map(DashboardHistoryController::toTokenEventResponse).toList());
        return response;
    }

    private static TokenUsageEventResponse toTokenEventResponse(TokenUsageEvent event) {
        return new TokenUsageEventResponse(
                event.getId(),
                event.getRunId(),
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
        );
    }

    private static <T> List<T> copyList(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
