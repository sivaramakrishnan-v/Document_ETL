package com.document.documentetl.controller;

import com.document.documentetl.dto.RagCheckpointResponse;
import com.document.documentetl.service.RagWorkflowCheckpointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagCheckpointController.class)
class RagCheckpointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagWorkflowCheckpointService checkpointService;

    @Test
    void getLatestCheckpointByThreadId() throws Exception {
        RagCheckpointResponse response = new RagCheckpointResponse();
        response.setCheckpointId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        response.setThreadId("thread-a");
        response.setWorkflowStatus("COMPLETED");
        response.setGroundednessScore(0.85);
        response.setCitationCoverageScore(0.75);
        response.setUnsupportedClaimsCount(1);
        response.setGroundingStatus("GROUNDED");
        when(checkpointService.findLatestByThreadId("thread-a")).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/rag/checkpoints/thread-a/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value("thread-a"))
                .andExpect(jsonPath("$.workflowStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.groundednessScore").value(0.85))
                .andExpect(jsonPath("$.citationCoverageScore").value(0.75))
                .andExpect(jsonPath("$.unsupportedClaimsCount").value(1))
                .andExpect(jsonPath("$.groundingStatus").value("GROUNDED"));
    }

    @Test
    void getCheckpointHistoryByThreadId() throws Exception {
        RagCheckpointResponse latest = new RagCheckpointResponse();
        latest.setCheckpointId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        latest.setThreadId("thread-b");
        latest.setWorkflowStatus("COMPLETED");
        RagCheckpointResponse older = new RagCheckpointResponse();
        older.setCheckpointId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        older.setThreadId("thread-b");
        older.setWorkflowStatus("FAILED");
        when(checkpointService.findHistoryByThreadId("thread-b")).thenReturn(List.of(latest, older));

        mockMvc.perform(get("/api/rag/checkpoints/thread-b/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].checkpointId").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$[1].workflowStatus").value("FAILED"));
    }
}
