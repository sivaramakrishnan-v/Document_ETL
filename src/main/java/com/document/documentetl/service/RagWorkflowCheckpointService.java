package com.document.documentetl.service;

import com.document.documentetl.dto.RagCheckpointRequestContext;
import com.document.documentetl.dto.RagCheckpointResponse;
import com.document.documentetl.model.RagWorkflowCheckpoint;
import com.document.documentetl.repository.RagWorkflowCheckpointRepository;
import com.document.documentetl.service.grounding.GroundingScoreResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class RagWorkflowCheckpointService {

    public static final String WORKFLOW_STARTED = "STARTED";
    public static final String WORKFLOW_PLANNING_COMPLETED = "PLANNING_COMPLETED";
    public static final String WORKFLOW_RETRIEVAL_COMPLETED = "RETRIEVAL_COMPLETED";
    public static final String WORKFLOW_ANSWER_GENERATED = "ANSWER_GENERATED";
    public static final String WORKFLOW_VALIDATION_COMPLETED = "VALIDATION_COMPLETED";
    public static final String WORKFLOW_COMPLETED = "COMPLETED";
    public static final String WORKFLOW_FAILED = "FAILED";

    private static final Logger log = LoggerFactory.getLogger(RagWorkflowCheckpointService.class);
    private static final String DEFAULT_NAMESPACE = "default";

    private final RagWorkflowCheckpointRepository checkpointRepository;

    public RagWorkflowCheckpointService(RagWorkflowCheckpointRepository checkpointRepository) {
        this.checkpointRepository = checkpointRepository;
    }

    @Transactional
    public RagWorkflowCheckpoint startCheckpoint(RagCheckpointRequestContext requestContext) {
        RagWorkflowCheckpoint checkpoint = new RagWorkflowCheckpoint();
        checkpoint.setCheckpointId(UUID.randomUUID());
        checkpoint.setThreadId(requestContext.getThreadId());
        checkpoint.setCheckpointNamespace(normalizeNamespace(requestContext.getCheckpointNamespace()));
        checkpoint.setUserQuery(requestContext.getUserQuery());
        checkpoint.setWorkflowStatus(WORKFLOW_STARTED);
        RagWorkflowCheckpoint saved = checkpointRepository.save(checkpoint);
        log.debug("Started checkpoint {} for thread {}", saved.getCheckpointId(), saved.getThreadId());
        return saved;
    }

    @Transactional
    public void markPlanningCompleted(UUID checkpointId, String normalizedQuery, String rewrittenQuery) {
        updateCheckpoint(checkpointId, checkpoint -> {
            checkpoint.setNormalizedQuery(normalizedQuery);
            checkpoint.setRewrittenQuery(rewrittenQuery);
            checkpoint.setWorkflowStatus(WORKFLOW_PLANNING_COMPLETED);
        });
    }

    @Transactional
    public void markRetrievalCompleted(UUID checkpointId,
                                       String retrievalStrategy,
                                       List<Long> retrievedDocumentIds,
                                       List<String> retrievedChunkIds,
                                       List<String> retrievedContextSnapshot) {
        updateCheckpoint(checkpointId, checkpoint -> {
            checkpoint.setRetrievalStrategy(retrievalStrategy);
            checkpoint.setRetrievedDocumentIds(copyList(retrievedDocumentIds));
            checkpoint.setRetrievedChunkIds(copyList(retrievedChunkIds));
            checkpoint.setRetrievedContextSnapshot(copyList(retrievedContextSnapshot));
            checkpoint.setWorkflowStatus(WORKFLOW_RETRIEVAL_COMPLETED);
        });
    }

    @Transactional
    public void markAnswerGenerated(UUID checkpointId, String generatedAnswer, List<String> citations) {
        updateCheckpoint(checkpointId, checkpoint -> {
            checkpoint.setGeneratedAnswer(generatedAnswer);
            checkpoint.setCitations(copyList(citations));
            checkpoint.setWorkflowStatus(WORKFLOW_ANSWER_GENERATED);
        });
    }

    @Transactional
    public void markValidationCompleted(UUID checkpointId, String validationStatus) {
        updateCheckpoint(checkpointId, checkpoint -> {
            checkpoint.setValidationStatus(validationStatus);
            checkpoint.setWorkflowStatus(WORKFLOW_VALIDATION_COMPLETED);
        });
    }

    @Transactional
    public void markGroundingScored(UUID checkpointId, GroundingScoreResult groundingScoreResult) {
        if (groundingScoreResult == null) {
            return;
        }
        updateCheckpoint(checkpointId, checkpoint -> {
            checkpoint.setGroundednessScore(groundingScoreResult.getGroundednessScore());
            checkpoint.setCitationCoverageScore(groundingScoreResult.getCitationCoverageScore());
            checkpoint.setUnsupportedClaimsCount(groundingScoreResult.getUnsupportedClaimsCount());
            checkpoint.setGroundingStatus(groundingScoreResult.getGroundingStatus().name());
        });
    }

    @Transactional
    public void markCompleted(UUID checkpointId) {
        updateCheckpoint(checkpointId, checkpoint -> checkpoint.setWorkflowStatus(WORKFLOW_COMPLETED));
    }

    @Transactional
    public void markFailed(UUID checkpointId, String errorMessage) {
        updateCheckpoint(checkpointId, checkpoint -> {
            checkpoint.setWorkflowStatus(WORKFLOW_FAILED);
            checkpoint.setErrorMessage(errorMessage);
        });
    }

    @Transactional(readOnly = true)
    public Optional<RagCheckpointResponse> findLatestByThreadId(String threadId) {
        return checkpointRepository.findTopByThreadIdOrderByCreatedAtDesc(threadId)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<RagCheckpointResponse> findHistoryByThreadId(String threadId) {
        return checkpointRepository.findByThreadIdOrderByCreatedAtDesc(threadId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void updateCheckpoint(UUID checkpointId, Consumer<RagWorkflowCheckpoint> updater) {
        RagWorkflowCheckpoint checkpoint = checkpointRepository.findById(checkpointId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));
        updater.accept(checkpoint);
        checkpointRepository.save(checkpoint);
    }

    private RagCheckpointResponse toResponse(RagWorkflowCheckpoint checkpoint) {
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

    private static String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return DEFAULT_NAMESPACE;
        }
        return namespace;
    }

    private static <T> List<T> copyList(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
