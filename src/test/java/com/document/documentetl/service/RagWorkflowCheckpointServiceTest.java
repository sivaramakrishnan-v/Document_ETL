package com.document.documentetl.service;

import com.document.documentetl.dto.RagCheckpointResponse;
import com.document.documentetl.model.RagWorkflowCheckpoint;
import com.document.documentetl.repository.RagWorkflowCheckpointRepository;
import com.document.documentetl.service.grounding.GroundingScoreResult;
import com.document.documentetl.service.grounding.GroundingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagWorkflowCheckpointServiceTest {

    @Mock
    private RagWorkflowCheckpointRepository repository;

    @InjectMocks
    private RagWorkflowCheckpointService service;

    @Test
    void fetchLatestCheckpointByThreadId() {
        RagWorkflowCheckpoint latest = checkpoint("thread-1", "COMPLETED", OffsetDateTime.now().plusSeconds(10));
        when(repository.findTopByThreadIdOrderByCreatedAtDesc("thread-1")).thenReturn(Optional.of(latest));

        Optional<RagCheckpointResponse> response = service.findLatestByThreadId("thread-1");

        assertTrue(response.isPresent());
        assertEquals(latest.getCheckpointId(), response.get().getCheckpointId());
        assertEquals("thread-1", response.get().getThreadId());
        assertEquals("COMPLETED", response.get().getWorkflowStatus());
        assertEquals(0.9d, response.get().getGroundednessScore());
    }

    @Test
    void fetchFullCheckpointHistoryByThreadId() {
        RagWorkflowCheckpoint newest = checkpoint("thread-2", "COMPLETED", OffsetDateTime.now().plusSeconds(10));
        RagWorkflowCheckpoint older = checkpoint("thread-2", "FAILED", OffsetDateTime.now());
        when(repository.findByThreadIdOrderByCreatedAtDesc("thread-2")).thenReturn(List.of(newest, older));

        List<RagCheckpointResponse> history = service.findHistoryByThreadId("thread-2");

        assertEquals(2, history.size());
        assertEquals(newest.getCheckpointId(), history.get(0).getCheckpointId());
        assertEquals(older.getCheckpointId(), history.get(1).getCheckpointId());
    }

    @Test
    void storesGroundingFieldsOnCheckpointUpdate() {
        UUID checkpointId = UUID.randomUUID();
        RagWorkflowCheckpoint checkpoint = checkpoint("thread-3", "ANSWER_GENERATED", OffsetDateTime.now());
        checkpoint.setCheckpointId(checkpointId);

        when(repository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(repository.save(any(RagWorkflowCheckpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroundingScoreResult grounding = new GroundingScoreResult(
                0.75d,
                0.5d,
                1,
                GroundingStatus.PARTIALLY_GROUNDED,
                3,
                4
        );

        service.markGroundingScored(checkpointId, grounding);

        verify(repository).findById(checkpointId);
        verify(repository).save(eq(checkpoint));
        assertEquals(0.75d, checkpoint.getGroundednessScore());
        assertEquals(0.5d, checkpoint.getCitationCoverageScore());
        assertEquals(1, checkpoint.getUnsupportedClaimsCount());
        assertEquals("PARTIALLY_GROUNDED", checkpoint.getGroundingStatus());
    }

    private static RagWorkflowCheckpoint checkpoint(String threadId, String workflowStatus, OffsetDateTime createdAt) {
        RagWorkflowCheckpoint checkpoint = new RagWorkflowCheckpoint();
        checkpoint.setCheckpointId(UUID.randomUUID());
        checkpoint.setThreadId(threadId);
        checkpoint.setWorkflowStatus(workflowStatus);
        checkpoint.setGroundednessScore(0.9d);
        checkpoint.setCitationCoverageScore(0.8d);
        checkpoint.setUnsupportedClaimsCount(0);
        checkpoint.setGroundingStatus("GROUNDED");
        checkpoint.setCreatedAt(createdAt);
        checkpoint.setUpdatedAt(createdAt);
        checkpoint.setCheckpointNamespace("default");
        return checkpoint;
    }
}
