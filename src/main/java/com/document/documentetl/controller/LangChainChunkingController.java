package com.document.documentetl.controller;

import com.document.documentetl.service.LangChainChunkingOrchestratorService;
import com.document.documentetl.service.MlflowActionTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/etl/langchain")
public class LangChainChunkingController {

    private static final Logger log = LoggerFactory.getLogger(LangChainChunkingController.class);

    private final LangChainChunkingOrchestratorService orchestratorService;
    private final MlflowActionTrackingService mlflowActionTrackingService;

    public LangChainChunkingController(LangChainChunkingOrchestratorService orchestratorService,
                                       MlflowActionTrackingService mlflowActionTrackingService) {
        this.orchestratorService = orchestratorService;
        this.mlflowActionTrackingService = mlflowActionTrackingService;
    }

    @GetMapping("/chunk")
    public List<Long> chunkAllParsedContent() {
        long startedAtNanos = System.nanoTime();
        log.info("LangChain chunk request received: endpoint=/api/etl/langchain/chunk");

        try {
            List<Long> processedDocumentIds = orchestratorService.chunkAllParsedContent();
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.info("LangChain chunk request completed: endpoint=/api/etl/langchain/chunk, processed={}, durationMs={}",
                    processedDocumentIds.size(),
                    durationMs);
            mlflowActionTrackingService.logActionSuccess(
                    "etl.langchain.chunk_all",
                    durationMs,
                    Map.of("documents_processed", (double) processedDocumentIds.size()),
                    Map.of("endpoint", "/api/etl/langchain/chunk"));
            return processedDocumentIds;
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("LangChain chunk request failed: endpoint=/api/etl/langchain/chunk, durationMs={}, error={}",
                    durationMs,
                    e.getMessage(),
                    e);
            mlflowActionTrackingService.logActionFailure(
                    "etl.langchain.chunk_all",
                    durationMs,
                    e,
                    Map.of("endpoint", "/api/etl/langchain/chunk"));
            throw e;
        }
    }

    @GetMapping("/chunk/{documentId}")
    public String chunkSingleDocument(@PathVariable Long documentId) {
        long startedAtNanos = System.nanoTime();
        log.info("LangChain single-document request received: endpoint=/api/etl/langchain/chunk/{}, documentId={}", documentId, documentId);

        try {
            orchestratorService.chunkSingleDocument(documentId);
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.info("LangChain single-document request completed: documentId={}, durationMs={}", documentId, durationMs);
            mlflowActionTrackingService.logActionSuccess(
                    "etl.langchain.chunk_single",
                    durationMs,
                    Map.of("documents_processed", 1.0d),
                    Map.of(
                            "endpoint", "/api/etl/langchain/chunk/{documentId}",
                            "document_id", String.valueOf(documentId)));
            return "Processed documentId=" + documentId;
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("LangChain single-document request failed: documentId={}, durationMs={}, error={}",
                    documentId,
                    durationMs,
                    e.getMessage(),
                    e);
            mlflowActionTrackingService.logActionFailure(
                    "etl.langchain.chunk_single",
                    durationMs,
                    e,
                    Map.of(
                            "endpoint", "/api/etl/langchain/chunk/{documentId}",
                            "document_id", String.valueOf(documentId)));
            throw e;
        }
    }
}
