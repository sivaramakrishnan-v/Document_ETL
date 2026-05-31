package com.document.documentetl.messaging;

import com.document.documentetl.events.DocumentChunksReadyEvent;
import com.document.documentetl.events.DocumentStagedEvent;
import com.document.documentetl.events.EmbeddingRequestedEvent;
import com.document.documentetl.model.v2.SourceDocument;
import com.document.documentetl.model.v2.TextChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class DocumentEventProducer {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String documentStagedTopic;
    private final String documentChunksReadyTopic;
    private final String embeddingRequestedTopic;
    private final String modelProvider;
    private final String embeddingModel;
    private final int embeddingDimension;

    public DocumentEventProducer(KafkaTemplate<Object, Object> kafkaTemplate,
                                 @Value("${app.kafka.topics.document-staged-v2}") String documentStagedTopic,
                                 @Value("${app.kafka.topics.document-chunks-ready-v2}") String documentChunksReadyTopic,
                                 @Value("${app.kafka.topics.embedding-requested-v2}") String embeddingRequestedTopic,
                                 @Value("${app.etl.v2.embedding.provider}") String modelProvider,
                                 @Value("${app.etl.v2.embedding.model}") String embeddingModel,
                                 @Value("${app.etl.v2.embedding.dimension}") int embeddingDimension) {
        this.kafkaTemplate = kafkaTemplate;
        this.documentStagedTopic = documentStagedTopic;
        this.documentChunksReadyTopic = documentChunksReadyTopic;
        this.embeddingRequestedTopic = embeddingRequestedTopic;
        this.modelProvider = modelProvider;
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
    }

    public DocumentStagedEvent publishDocumentStaged(SourceDocument document) {
        DocumentStagedEvent event = new DocumentStagedEvent(
                UUID.randomUUID(),
                document.getDocumentId(),
                document.getSourceUri(),
                document.getContentHash(),
                document.getVersionNumber(),
                OffsetDateTime.now()
        );
        kafkaTemplate.send(documentStagedTopic, document.getDocumentId().toString(), event);
        return event;
    }

    public DocumentChunksReadyEvent publishDocumentChunksReady(Long documentId,
                                                              UUID contentId,
                                                              String contentHash,
                                                              int chunkCount) {
        DocumentChunksReadyEvent event = new DocumentChunksReadyEvent(
                UUID.randomUUID(),
                documentId,
                contentId,
                contentHash,
                chunkCount,
                OffsetDateTime.now()
        );
        kafkaTemplate.send(documentChunksReadyTopic, documentId.toString(), event);
        return event;
    }

    public EmbeddingRequestedEvent publishEmbeddingRequested(TextChunk chunk) {
        EmbeddingRequestedEvent event = new EmbeddingRequestedEvent(
                UUID.randomUUID(),
                chunk.getChunkId(),
                chunk.getDocumentId(),
                chunk.getContentId(),
                chunk.getContentHash(),
                modelProvider,
                embeddingModel,
                embeddingDimension,
                OffsetDateTime.now()
        );
        kafkaTemplate.send(embeddingRequestedTopic, chunk.getChunkId().toString(), event);
        return event;
    }
}
