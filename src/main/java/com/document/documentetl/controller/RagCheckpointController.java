package com.document.documentetl.controller;

import com.document.documentetl.dto.RagCheckpointResponse;
import com.document.documentetl.service.RagWorkflowCheckpointService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/rag/checkpoints")
public class RagCheckpointController {

    private final RagWorkflowCheckpointService ragWorkflowCheckpointService;

    public RagCheckpointController(RagWorkflowCheckpointService ragWorkflowCheckpointService) {
        this.ragWorkflowCheckpointService = ragWorkflowCheckpointService;
    }

    @GetMapping("/{threadId}/latest")
    public RagCheckpointResponse latest(@PathVariable String threadId) {
        validateThreadId(threadId);
        return ragWorkflowCheckpointService.findLatestByThreadId(threadId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No checkpoints found for threadId: " + threadId
                ));
    }

    @GetMapping("/{threadId}/history")
    public List<RagCheckpointResponse> history(@PathVariable String threadId) {
        validateThreadId(threadId);
        return ragWorkflowCheckpointService.findHistoryByThreadId(threadId);
    }

    private static void validateThreadId(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "threadId must not be blank");
        }
    }
}
