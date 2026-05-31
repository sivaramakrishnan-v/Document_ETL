package com.document.documentetl.controller;

import com.document.documentetl.model.v2.PipelineEvent;
import com.document.documentetl.model.v2.SourceDocument;
import com.document.documentetl.repository.v2.ChunkEmbeddingRepository;
import com.document.documentetl.repository.v2.EmbeddingJobRepository;
import com.document.documentetl.repository.v2.PipelineEventRepository;
import com.document.documentetl.repository.v2.SourceDocumentRepository;
import com.document.documentetl.repository.v2.TextChunkRepository;
import com.document.documentetl.service.v2.DocumentV2EmbeddingReconciliationService;
import com.document.documentetl.service.v2.DocumentV2StagingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/etl/v2")
public class DocumentEtlV2Controller {

    private final DocumentV2StagingService stagingService;
    private final DocumentV2EmbeddingReconciliationService reconciliationService;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final TextChunkRepository textChunkRepository;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;
    private final EmbeddingJobRepository embeddingJobRepository;
    private final PipelineEventRepository pipelineEventRepository;
    private final String modelProvider;
    private final String embeddingModel;
    private final int embeddingDimension;

    public DocumentEtlV2Controller(DocumentV2StagingService stagingService,
                                   DocumentV2EmbeddingReconciliationService reconciliationService,
                                   SourceDocumentRepository sourceDocumentRepository,
                                   TextChunkRepository textChunkRepository,
                                   ChunkEmbeddingRepository chunkEmbeddingRepository,
                                   EmbeddingJobRepository embeddingJobRepository,
                                   PipelineEventRepository pipelineEventRepository,
                                   @Value("${app.etl.v2.embedding.provider}") String modelProvider,
                                   @Value("${app.etl.v2.embedding.model}") String embeddingModel,
                                   @Value("${app.etl.v2.embedding.dimension}") int embeddingDimension) {
        this.stagingService = stagingService;
        this.reconciliationService = reconciliationService;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.textChunkRepository = textChunkRepository;
        this.chunkEmbeddingRepository = chunkEmbeddingRepository;
        this.embeddingJobRepository = embeddingJobRepository;
        this.pipelineEventRepository = pipelineEventRepository;
        this.modelProvider = modelProvider;
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
    }

    @GetMapping("/stage")
    public Map<String, Object> stageLocalFiles() {
        DocumentV2StagingService.StagingSummary summary = stagingService.stageLocalFiles();
        return Map.of(
                "scannedFiles", summary.scannedFiles(),
                "persistedDocuments", summary.persistedDocuments().size(),
                "emittedEvents", summary.emittedEvents(),
                "metadataOnlyUpdates", summary.metadataOnlyUpdates(),
                "unchangedFiles", summary.unchangedFiles(),
                "unreadableFiles", summary.unreadableFiles(),
                "documents", summary.persistedDocuments().stream()
                        .map(SourceDocument::getDocumentId)
                        .toList()
        );
    }

    @GetMapping("/reconcile-embeddings")
    public Map<String, Object> reconcileEmbeddings() {
        reconciliationService.reconcileMissingEmbeddings();
        return Map.of("status", "submitted");
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> documentCounts = new LinkedHashMap<>();
        documentCounts.put("total", sourceDocumentRepository.count());
        documentCounts.put("staged", sourceDocumentRepository.countByStatus("STAGED"));
        documentCounts.put("chunked", sourceDocumentRepository.countByStatus("CHUNKED"));
        documentCounts.put("completed", sourceDocumentRepository.countByStatus("COMPLETED"));
        documentCounts.put("failed", sourceDocumentRepository.countByStatus("FAILED"));

        Map<String, Object> embeddingCounts = new LinkedHashMap<>();
        embeddingCounts.put("chunks", textChunkRepository.count());
        embeddingCounts.put("embeddings", chunkEmbeddingRepository.count());
        embeddingCounts.put("missingEmbeddings", textChunkRepository.countChunksMissingEmbedding(
                modelProvider,
                embeddingModel,
                embeddingDimension
        ));

        Map<String, Object> jobCounts = new LinkedHashMap<>();
        jobCounts.put("pending", embeddingJobRepository.countByJobStatus("PENDING"));
        jobCounts.put("inProgress", embeddingJobRepository.countByJobStatus("IN_PROGRESS"));
        jobCounts.put("completed", embeddingJobRepository.countByJobStatus("COMPLETED"));
        jobCounts.put("failed", embeddingJobRepository.countByJobStatus("FAILED"));

        Map<String, Object> eventCounts = new LinkedHashMap<>();
        eventCounts.put("total", pipelineEventRepository.count());
        eventCounts.put("received", pipelineEventRepository.countByProcessingStatus("RECEIVED"));
        eventCounts.put("processed", pipelineEventRepository.countByProcessingStatus("PROCESSED"));
        eventCounts.put("ignored", pipelineEventRepository.countByProcessingStatus("IGNORED"));
        eventCounts.put("failed", pipelineEventRepository.countByProcessingStatus("FAILED"));

        return Map.of(
                "documents", documentCounts,
                "embeddings", embeddingCounts,
                "embeddingJobs", jobCounts,
                "events", eventCounts,
                "recentEvents", pipelineEventRepository.findTop10ByOrderByProcessedAtDesc().stream()
                        .map(DocumentEtlV2Controller::toEventSummary)
                        .toList()
        );
    }

    private static Map<String, Object> toEventSummary(PipelineEvent event) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("eventType", event.getEventType());
        summary.put("topicName", event.getTopicName());
        summary.put("documentId", event.getDocumentId());
        summary.put("chunkId", event.getChunkId());
        summary.put("processingStatus", event.getProcessingStatus());
        summary.put("errorMessage", event.getErrorMessage());
        summary.put("processedAt", event.getProcessedAt());
        return summary;
    }
}
