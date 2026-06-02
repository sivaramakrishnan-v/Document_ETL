package com.document.documentetl.service;

import com.document.documentetl.dto.AgenticAskResponse;
import com.document.documentetl.dto.RagCheckpointRequestContext;
import com.document.documentetl.model.RagWorkflowCheckpoint;
import com.document.documentetl.service.agent.RagAgentState;
import com.document.documentetl.service.agent.RagStateGraphFactory;
import com.document.documentetl.service.grounding.GroundingScoreResult;
import com.document.documentetl.service.grounding.GroundingScoreService;
import com.document.documentetl.service.grounding.GroundingStatus;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgenticRagServiceTest {

    @Mock
    private RagStateGraphFactory ragStateGraphFactory;
    @Mock
    private RagWorkflowCheckpointService checkpointService;
    @Mock
    private GroundingScoreService groundingScoreService;
    @Mock
    private CompiledGraph<RagAgentState> graph;

    private AgenticRagService service;

    @BeforeEach
    void setUp() throws GraphStateException {
        when(ragStateGraphFactory.build()).thenReturn(graph);
        service = new AgenticRagService(ragStateGraphFactory, checkpointService, groundingScoreService);
    }

    @Test
    void createsNewThreadIdWhenNotProvided() {
        when(groundingScoreService.calculate(any(), any(), any())).thenReturn(
                new GroundingScoreResult(1.0, 1.0, 0, GroundingStatus.GROUNDED, 1, 1)
        );

        UUID checkpointId = UUID.randomUUID();
        RagWorkflowCheckpoint checkpoint = checkpoint(checkpointId, "generated-thread-placeholder");
        when(checkpointService.startCheckpoint(any(RagCheckpointRequestContext.class))).thenReturn(checkpoint);
        when(graph.invoke(any(Map.class), any(RunnableConfig.class)))
                .thenReturn(Optional.of(successState("resolved-thread", "Answer")));

        AgenticAskResponse response = service.ask("Explain this document", null);

        ArgumentCaptor<RagCheckpointRequestContext> requestContextCaptor = ArgumentCaptor.forClass(RagCheckpointRequestContext.class);
        verify(checkpointService).startCheckpoint(requestContextCaptor.capture());
        String generatedThreadId = requestContextCaptor.getValue().getThreadId();

        assertNotNull(generatedThreadId);
        assertTrue(generatedThreadId.matches("^[0-9a-fA-F\\-]{36}$"));
        assertEquals("Explain this document", requestContextCaptor.getValue().getUserQuery());

        ArgumentCaptor<Map<String, Object>> graphInputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(graph).invoke(graphInputCaptor.capture(), any(RunnableConfig.class));
        assertEquals(generatedThreadId, graphInputCaptor.getValue().get(RagAgentState.THREAD_ID));
        assertEquals(checkpointId.toString(), graphInputCaptor.getValue().get(RagAgentState.CHECKPOINT_ID));

        assertEquals("resolved-thread", response.getThreadId());
        assertEquals(checkpointId.toString(), response.getCheckpointId());
        assertEquals(1.0, response.getGroundednessScore());
        verify(checkpointService).markGroundingScored(eq(checkpointId), any(GroundingScoreResult.class));
    }

    @Test
    void reusesProvidedThreadId() {
        when(groundingScoreService.calculate(any(), any(), any())).thenReturn(
                new GroundingScoreResult(1.0, 1.0, 0, GroundingStatus.GROUNDED, 1, 1)
        );

        String threadId = "existing-thread-123";
        UUID checkpointId = UUID.randomUUID();
        when(checkpointService.startCheckpoint(any(RagCheckpointRequestContext.class)))
                .thenReturn(checkpoint(checkpointId, threadId));
        when(graph.invoke(any(Map.class), any(RunnableConfig.class)))
                .thenReturn(Optional.of(successState(threadId, "Answer")));

        service.ask("Explain this document", threadId);

        ArgumentCaptor<RagCheckpointRequestContext> requestContextCaptor = ArgumentCaptor.forClass(RagCheckpointRequestContext.class);
        verify(checkpointService).startCheckpoint(requestContextCaptor.capture());
        assertEquals(threadId, requestContextCaptor.getValue().getThreadId());
    }

    @Test
    void marksCheckpointFailedWhenWorkflowThrows() {
        UUID checkpointId = UUID.randomUUID();
        when(checkpointService.startCheckpoint(any(RagCheckpointRequestContext.class)))
                .thenReturn(checkpoint(checkpointId, "thread-x"));
        when(graph.invoke(any(Map.class), any(RunnableConfig.class)))
                .thenThrow(new IllegalStateException("workflow crashed"));

        assertThrows(IllegalStateException.class, () -> service.ask("Explain this document", "thread-x"));

        verify(checkpointService).markFailed(eq(checkpointId), eq("workflow crashed"));
    }

    private static RagWorkflowCheckpoint checkpoint(UUID checkpointId, String threadId) {
        RagWorkflowCheckpoint checkpoint = new RagWorkflowCheckpoint();
        checkpoint.setCheckpointId(checkpointId);
        checkpoint.setThreadId(threadId);
        return checkpoint;
    }

    private static RagAgentState successState(String threadId, String answer) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(RagAgentState.THREAD_ID, threadId);
        state.put(RagAgentState.FINAL_ANSWER, answer);
        state.put(RagAgentState.CONTEXT_GRADE, "SUFFICIENT");
        state.put(RagAgentState.VALIDATION_OUTCOME, "GROUNDED");
        state.put(RagAgentState.RETRIEVAL_ATTEMPTS, 1);
        state.put(RagAgentState.REWRITE_ATTEMPTS, 0);
        state.put(RagAgentState.ANSWER_REVISION_ATTEMPTS, 0);
        state.put(RagAgentState.CITATIONS, List.of("[doc=100]"));
        state.put(RagAgentState.VISITED, List.of("normalize_query", "query_planner"));
        state.put(RagAgentState.FEEDBACK, List.of("ok"));
        return new RagAgentState(state);
    }
}
