package com.document.documentetl.controller;

import com.document.documentetl.dto.DocumentChunkResponse;
import com.document.documentetl.model.DocumentChunk;
import com.document.documentetl.service.ChunkingService;
import com.document.documentetl.service.LangChainChunkingOrchestratorService;
import com.document.documentetl.service.MlflowActionTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/etl")
public class ChunkingController {

    private static final Logger log = LoggerFactory.getLogger(ChunkingController.class);
    private final ChunkingService chunkingService;
    private final LangChainChunkingOrchestratorService langChainChunkingOrchestratorService;
    private final MlflowActionTrackingService mlflowActionTrackingService;

    public ChunkingController(ChunkingService chunkingService,
                              LangChainChunkingOrchestratorService langChainChunkingOrchestratorService,
                              MlflowActionTrackingService mlflowActionTrackingService) {
        this.chunkingService = chunkingService;
        this.langChainChunkingOrchestratorService = langChainChunkingOrchestratorService;
        this.mlflowActionTrackingService = mlflowActionTrackingService;
    }

    @GetMapping("/chunk")
    public List<DocumentChunkResponse> chunkAllParsedContent() {
        long startedAtNanos = System.nanoTime();
        log.info("Chunk request received: endpoint=/api/etl/chunk");

        try {
            List<DocumentChunk> chunks = chunkingService.chunkAllParsedContent();
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.info("Chunk request completed: endpoint=/api/etl/chunk, chunksPersisted={}, durationMs={}",
                    chunks.size(),
                    durationMs);
            mlflowActionTrackingService.logActionSuccess(
                    "etl.chunk",
                    durationMs,
                    Map.of("chunks_persisted", (double) chunks.size()),
                    Map.of("endpoint", "/api/etl/chunk"));
            return chunks.stream()
                    .map(DocumentChunkResponse::new)
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("Chunk request failed: endpoint=/api/etl/chunk, durationMs={}, error={}",
                    durationMs,
                    e.getMessage(),
                    e);
            mlflowActionTrackingService.logActionFailure(
                    "etl.chunk",
                    durationMs,
                    e,
                    Map.of("endpoint", "/api/etl/chunk"));
            throw e;
        }
    }

    @GetMapping("/embed")
    public Map<String, Object> createEmbeddings(@RequestParam(name = "engine", defaultValue = "native") String engine) {
        String normalizedEngine = engine == null ? "native" : engine.trim().toLowerCase(Locale.ROOT);
        long startedAtNanos = System.nanoTime();
        log.info("Embed request received: endpoint=/api/etl/embed, engine={}", normalizedEngine);

        try {
            if ("native".equals(normalizedEngine) || "vertex".equals(normalizedEngine)) {
                List<DocumentChunk> chunks = chunkingService.chunkAllParsedContent();
                long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
                mlflowActionTrackingService.logActionSuccess(
                        "etl.embed.native",
                        durationMs,
                        Map.of(
                                "chunks_persisted", (double) chunks.size(),
                                "documents_processed", (double) chunks.stream()
                                        .map(DocumentChunk::getDocumentId)
                                        .filter(documentId -> documentId != null)
                                        .distinct()
                                        .count()
                        ),
                        Map.of("endpoint", "/api/etl/embed", "engine", normalizedEngine)
                );
                return Map.of(
                        "engine", "native",
                        "chunksPersisted", chunks.size(),
                        "documentsProcessed", chunks.stream()
                                .map(DocumentChunk::getDocumentId)
                                .filter(documentId -> documentId != null)
                                .distinct()
                                .count()
                );
            }

            if ("langchain".equals(normalizedEngine) || "langchain4j".equals(normalizedEngine)) {
                List<Long> processedDocumentIds = langChainChunkingOrchestratorService.chunkAllParsedContent();
                long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
                mlflowActionTrackingService.logActionSuccess(
                        "etl.embed.langchain",
                        durationMs,
                        Map.of("documents_processed", (double) processedDocumentIds.size()),
                        Map.of("endpoint", "/api/etl/embed", "engine", normalizedEngine)
                );
                return Map.of(
                        "engine", "langchain",
                        "documentsProcessed", processedDocumentIds.size(),
                        "documentIds", processedDocumentIds
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported engine '" + engine + "'. Supported values: native, langchain"
            );
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            mlflowActionTrackingService.logActionFailure(
                    "etl.embed",
                    durationMs,
                    e,
                    Map.of("endpoint", "/api/etl/embed", "engine", normalizedEngine)
            );
            throw e;
        }
    }
}
