package com.document.documentetl.service;

import com.document.documentetl.dto.AgenticAskResponse;
import com.document.documentetl.dto.RagCheckpointRequestContext;
import com.document.documentetl.model.RagWorkflowCheckpoint;
import com.document.documentetl.service.agent.RagAgentState;
import com.document.documentetl.service.agent.RagStateGraphFactory;
import com.document.documentetl.service.grounding.GroundingScoreResult;
import com.document.documentetl.service.grounding.GroundingScoreService;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgenticRagService {

    private static final Logger log = LoggerFactory.getLogger(AgenticRagService.class);

    private final CompiledGraph<RagAgentState> graph;
    private final RagWorkflowCheckpointService ragWorkflowCheckpointService;
    private final GroundingScoreService groundingScoreService;

    public AgenticRagService(RagStateGraphFactory ragStateGraphFactory,
                             RagWorkflowCheckpointService ragWorkflowCheckpointService,
                             GroundingScoreService groundingScoreService) {
        this.ragWorkflowCheckpointService = ragWorkflowCheckpointService;
        this.groundingScoreService = groundingScoreService;
        try {
            this.graph = ragStateGraphFactory.build();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to compile agentic RAG graph.", ex);
        }
    }

    public AgenticAskResponse ask(String question, String threadId) {
        return ask(question, threadId, null);
    }

    public AgenticAskResponse ask(String question, String threadId, List<Long> documentIds) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }

        String resolvedThreadId = (threadId == null || threadId.isBlank())
                ? UUID.randomUUID().toString()
                : threadId;
        boolean providedThreadId = threadId != null && !threadId.isBlank();
        RagWorkflowCheckpoint checkpoint = ragWorkflowCheckpointService.startCheckpoint(
                new RagCheckpointRequestContext(resolvedThreadId, "default", question)
        );

        Map<String, Object> input = new LinkedHashMap<>();
        input.put(RagAgentState.USER_QUERY, question);
        input.put(RagAgentState.THREAD_ID, resolvedThreadId);
        input.put(RagAgentState.CHECKPOINT_ID, checkpoint.getCheckpointId().toString());
        if (documentIds != null && !documentIds.isEmpty()) {
            input.put(RagAgentState.DOCUMENT_IDS, documentIds);
            log.info("Agentic RAG document scope applied: documentIds={}", documentIds);
        }

        RunnableConfig config = providedThreadId
                ? RunnableConfig.builder().threadId(resolvedThreadId).build()
                : RunnableConfig.empty();

        RagAgentState state;
        try {
            Optional<RagAgentState> result = graph.invoke(input, config);
            state = result.orElseThrow(() -> new IllegalStateException("Graph execution returned no state."));
        } catch (RuntimeException ex) {
            ragWorkflowCheckpointService.markFailed(checkpoint.getCheckpointId(), ex.getMessage());
            throw ex;
        }
        GroundingScoreResult grounding = groundingScoreService.calculate(
                state.finalAnswer().orElse(""),
                state.citations(),
                state.selectedEvidence()
        );
        ragWorkflowCheckpointService.markGroundingScored(checkpoint.getCheckpointId(), grounding);

        return new AgenticAskResponse(
                state.threadId().orElse(""),
                checkpoint.getCheckpointId().toString(),
                state.finalAnswer().orElse(""),
                state.validationOutcome().orElse(""),
                RagWorkflowCheckpointService.WORKFLOW_COMPLETED,
                state.contextGrade().orElse(""),
                state.validationOutcome().orElse(""),
                state.retrievalAttempts(),
                state.rewriteAttempts(),
                state.answerRevisionAttempts(),
                state.citations(),
                state.visited(),
                state.feedback(),
                grounding.getGroundednessScore(),
                grounding.getCitationCoverageScore(),
                grounding.getUnsupportedClaimsCount(),
                grounding.getGroundingStatus().name()
        );
    }
}
