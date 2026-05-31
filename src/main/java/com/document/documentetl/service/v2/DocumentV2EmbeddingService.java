package com.document.documentetl.service.v2;

import com.document.documentetl.events.DocumentChunksReadyEvent;
import com.document.documentetl.events.EmbeddingRequestedEvent;
import com.document.documentetl.model.v2.ChunkEmbedding;
import com.document.documentetl.model.v2.EmbeddingJob;
import com.document.documentetl.model.v2.SourceDocument;
import com.document.documentetl.model.v2.TextChunk;
import com.document.documentetl.repository.v2.ChunkEmbeddingRepository;
import com.document.documentetl.repository.v2.EmbeddingJobRepository;
import com.document.documentetl.repository.v2.SourceDocumentRepository;
import com.document.documentetl.repository.v2.TextChunkRepository;
import com.document.documentetl.service.VertexAiEmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentV2EmbeddingService {

    private final TextChunkRepository textChunkRepository;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;
    private final EmbeddingJobRepository embeddingJobRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final VertexAiEmbeddingService vertexAiEmbeddingService;
    private final String modelProvider;
    private final String embeddingModel;
    private final int embeddingDimension;

    public DocumentV2EmbeddingService(TextChunkRepository textChunkRepository,
                                      ChunkEmbeddingRepository chunkEmbeddingRepository,
                                      EmbeddingJobRepository embeddingJobRepository,
                                      SourceDocumentRepository sourceDocumentRepository,
                                      VertexAiEmbeddingService vertexAiEmbeddingService,
                                      @Value("${app.etl.v2.embedding.provider}") String modelProvider,
                                      @Value("${app.etl.v2.embedding.model}") String embeddingModel,
                                      @Value("${app.etl.v2.embedding.dimension}") int embeddingDimension) {
        this.textChunkRepository = textChunkRepository;
        this.chunkEmbeddingRepository = chunkEmbeddingRepository;
        this.embeddingJobRepository = embeddingJobRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.vertexAiEmbeddingService = vertexAiEmbeddingService;
        this.modelProvider = modelProvider;
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
    }

    public EmbeddingSummary embedChunks(DocumentChunksReadyEvent event) {
        List<TextChunk> chunks = textChunkRepository.findByContentIdOrderByChunkIndex(event.contentId());
        int completed = 0;
        int skipped = 0;
        for (TextChunk chunk : chunks) {
            if (!event.contentHash().equals(chunk.getContentHash())) {
                skipped++;
                continue;
            }
            if (embedChunk(chunk, modelProvider, embeddingModel, embeddingDimension)) {
                completed++;
            } else {
                skipped++;
            }
        }

        sourceDocumentRepository.findById(event.documentId()).ifPresent(document -> {
            document.setStatus("COMPLETED");
            document.setUpdatedAt(OffsetDateTime.now());
            sourceDocumentRepository.save(document);
        });
        return new EmbeddingSummary(completed, skipped);
    }

    public boolean embedRequestedChunk(EmbeddingRequestedEvent event) {
        TextChunk chunk = textChunkRepository.findById(event.chunkId())
                .orElseThrow(() -> new IllegalStateException("V2 text chunk not found: " + event.chunkId()));
        if (!event.contentHash().equals(chunk.getContentHash())) {
            return false;
        }
        return embedChunk(chunk, event.modelProvider(), event.embeddingModel(), event.embeddingDimension());
    }

    public EmbeddingJob ensureJob(TextChunk chunk) {
        return ensureJob(chunk, modelProvider, embeddingModel, embeddingDimension);
    }

    private boolean embedChunk(TextChunk chunk, String provider, String model, int dimension) {
        EmbeddingJob job = ensureJob(chunk, provider, model, dimension);
        if (chunkEmbeddingRepository.existsByChunkIdAndModelProviderAndEmbeddingModelAndEmbeddingDimension(
                chunk.getChunkId(), provider, model, dimension)) {
            markJobCompleted(job);
            return false;
        }

        markJobInProgress(job);
        try {
            float[] embedding = vertexAiEmbeddingService.embed(chunk.getChunkText());
            if (embedding == null || embedding.length != dimension) {
                throw new IllegalStateException("Expected embedding dimension " + dimension + " but got " +
                        (embedding == null ? 0 : embedding.length));
            }

            OffsetDateTime now = OffsetDateTime.now();
            ChunkEmbedding chunkEmbedding = new ChunkEmbedding();
            chunkEmbedding.setEmbeddingId(UUID.randomUUID());
            chunkEmbedding.setChunkId(chunk.getChunkId());
            chunkEmbedding.setDocumentId(chunk.getDocumentId());
            chunkEmbedding.setContentId(chunk.getContentId());
            chunkEmbedding.setContentHash(chunk.getContentHash());
            chunkEmbedding.setModelProvider(provider);
            chunkEmbedding.setEmbeddingModel(model);
            chunkEmbedding.setEmbeddingDimension(dimension);
            chunkEmbedding.setEmbedding(embedding);
            chunkEmbedding.setEmbeddingStatus("COMPLETED");
            chunkEmbedding.setCreatedAt(now);
            chunkEmbedding.setUpdatedAt(now);
            chunkEmbeddingRepository.save(chunkEmbedding);

            markJobCompleted(job);
            return true;
        } catch (RuntimeException e) {
            markJobFailed(job, e);
            sourceDocumentRepository.findById(chunk.getDocumentId()).ifPresent(this::markDocumentFailed);
            throw e;
        }
    }

    private EmbeddingJob ensureJob(TextChunk chunk, String provider, String model, int dimension) {
        return embeddingJobRepository
                .findByChunkIdAndModelProviderAndEmbeddingModelAndEmbeddingDimension(
                        chunk.getChunkId(), provider, model, dimension)
                .orElseGet(() -> {
                    OffsetDateTime now = OffsetDateTime.now();
                    EmbeddingJob job = new EmbeddingJob();
                    job.setJobId(UUID.randomUUID());
                    job.setChunkId(chunk.getChunkId());
                    job.setModelProvider(provider);
                    job.setEmbeddingModel(model);
                    job.setEmbeddingDimension(dimension);
                    job.setJobStatus("PENDING");
                    job.setAttemptCount(0);
                    job.setCreatedAt(now);
                    job.setUpdatedAt(now);
                    return embeddingJobRepository.save(job);
                });
    }

    private void markJobInProgress(EmbeddingJob job) {
        job.setJobStatus("IN_PROGRESS");
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setUpdatedAt(OffsetDateTime.now());
        embeddingJobRepository.save(job);
    }

    private void markJobCompleted(EmbeddingJob job) {
        job.setJobStatus("COMPLETED");
        job.setLastError(null);
        job.setNextAttemptAt(null);
        job.setUpdatedAt(OffsetDateTime.now());
        embeddingJobRepository.save(job);
    }

    private void markJobFailed(EmbeddingJob job, RuntimeException e) {
        job.setJobStatus("FAILED");
        job.setLastError(e.getMessage());
        job.setNextAttemptAt(OffsetDateTime.now().plusMinutes(5));
        job.setUpdatedAt(OffsetDateTime.now());
        embeddingJobRepository.save(job);
    }

    private void markDocumentFailed(SourceDocument document) {
        document.setStatus("FAILED");
        document.setUpdatedAt(OffsetDateTime.now());
        sourceDocumentRepository.save(document);
    }

    public record EmbeddingSummary(int completed, int skipped) {
    }
}
