package com.document.documentetl.messaging;

import com.document.documentetl.events.DocumentChunksReadyEvent;
import com.document.documentetl.service.v2.DocumentV2EmbeddingService;
import com.document.documentetl.service.v2.PipelineEventAuditService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.etl.v2.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentChunksReadyConsumer {

    private final DocumentV2EmbeddingService embeddingService;
    private final PipelineEventAuditService auditService;

    public DocumentChunksReadyConsumer(DocumentV2EmbeddingService embeddingService,
                                       PipelineEventAuditService auditService) {
        this.embeddingService = embeddingService;
        this.auditService = auditService;
    }

    @KafkaListener(topics = "${app.kafka.topics.document-chunks-ready-v2}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(DocumentChunksReadyEvent event, ConsumerRecord<String, DocumentChunksReadyEvent> record) {
        auditService.record(
                event.eventId(),
                "DocumentChunksReady",
                record.topic(),
                record.key(),
                event.documentId(),
                event.contentId(),
                null,
                event.contentHash(),
                "RECEIVED",
                null,
                event.occurredAt()
        );
        try {
            embeddingService.embedChunks(event);
            auditService.record(
                    event.eventId(),
                    "DocumentChunksReady",
                    record.topic(),
                    record.key(),
                    event.documentId(),
                    event.contentId(),
                    null,
                    event.contentHash(),
                    "PROCESSED",
                    null,
                    event.occurredAt()
            );
        } catch (RuntimeException e) {
            auditService.record(
                    event.eventId(),
                    "DocumentChunksReady",
                    record.topic(),
                    record.key(),
                    event.documentId(),
                    event.contentId(),
                    null,
                    event.contentHash(),
                    "FAILED",
                    e.getMessage(),
                    event.occurredAt()
            );
            throw e;
        }
    }
}
