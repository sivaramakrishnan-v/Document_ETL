package com.document.documentetl.service.v2;

import com.document.documentetl.messaging.DocumentEventProducer;
import com.document.documentetl.model.v2.TextChunk;
import com.document.documentetl.repository.v2.TextChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentV2EmbeddingReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentV2EmbeddingReconciliationService.class);

    private final TextChunkRepository textChunkRepository;
    private final DocumentV2EmbeddingService embeddingService;
    private final DocumentEventProducer documentEventProducer;
    private final boolean enabled;
    private final String modelProvider;
    private final String embeddingModel;
    private final int embeddingDimension;
    private final int batchSize;

    public DocumentV2EmbeddingReconciliationService(TextChunkRepository textChunkRepository,
                                                    DocumentV2EmbeddingService embeddingService,
                                                    DocumentEventProducer documentEventProducer,
                                                    @Value("${app.etl.v2.reconciliation.enabled:true}") boolean enabled,
                                                    @Value("${app.etl.v2.embedding.provider}") String modelProvider,
                                                    @Value("${app.etl.v2.embedding.model}") String embeddingModel,
                                                    @Value("${app.etl.v2.embedding.dimension}") int embeddingDimension,
                                                    @Value("${app.etl.v2.reconciliation.batch-size:100}") int batchSize) {
        this.textChunkRepository = textChunkRepository;
        this.embeddingService = embeddingService;
        this.documentEventProducer = documentEventProducer;
        this.enabled = enabled;
        this.modelProvider = modelProvider;
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.etl.v2.reconciliation.fixed-delay-ms:1800000}")
    public void reconcileMissingEmbeddings() {
        if (!enabled) {
            return;
        }
        List<TextChunk> missingChunks = textChunkRepository.findChunksMissingEmbedding(
                modelProvider,
                embeddingModel,
                embeddingDimension,
                PageRequest.of(0, Math.max(1, batchSize))
        );
        if (missingChunks.isEmpty()) {
            return;
        }

        for (TextChunk chunk : missingChunks) {
            embeddingService.ensureJob(chunk);
            documentEventProducer.publishEmbeddingRequested(chunk);
        }
        log.info("V2 embedding reconciliation published requests: count={}", missingChunks.size());
    }
}
