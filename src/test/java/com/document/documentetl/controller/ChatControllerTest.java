package com.document.documentetl.controller;

import com.document.documentetl.dto.AgenticAskResponse;
import com.document.documentetl.service.AgenticRagService;
import com.document.documentetl.service.ChatReviewService;
import com.document.documentetl.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private ChatReviewService chatReviewService;

    @MockBean
    private AgenticRagService agenticRagService;

    @Test
    void existingAgentEndpointStillReturnsAnswer() throws Exception {
        AgenticAskResponse response = new AgenticAskResponse();
        response.setThreadId("thread-123");
        response.setCheckpointId("checkpoint-123");
        response.setAnswer("This is the answer.");
        response.setValidationOutcome("GROUNDED");
        response.setValidationStatus("GROUNDED");
        response.setWorkflowStatus("COMPLETED");
        response.setCitations(List.of("[doc=1]"));
        response.setVisited(List.of("normalize_query"));
        response.setFeedback(List.of("ok"));
        response.setGroundednessScore(0.92);
        response.setCitationCoverageScore(0.80);
        response.setUnsupportedClaimsCount(0);
        response.setGroundingStatus("GROUNDED");

        when(agenticRagService.ask(anyString(), eq("thread-123"), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/chat/agent/ask?threadId=thread-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Explain this document"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value("thread-123"))
                .andExpect(jsonPath("$.answer").value("This is the answer."))
                .andExpect(jsonPath("$.workflowStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.groundednessScore").value(0.92))
                .andExpect(jsonPath("$.citationCoverageScore").value(0.80))
                .andExpect(jsonPath("$.unsupportedClaimsCount").value(0))
                .andExpect(jsonPath("$.groundingStatus").value("GROUNDED"));
    }
}
