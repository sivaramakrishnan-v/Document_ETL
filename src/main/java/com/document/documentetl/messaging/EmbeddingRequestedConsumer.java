package com.document.documentetl.messaging;

import com.document.documentetl.events.EmbeddingRequestedEvent;
import com.document.documentetl.service.v2.DocumentV2EmbeddingService;
import com.document.documentetl.service.v2.PipelineEventAuditService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.etl.v2.enabled", havingValue = "true", matchIfMissing = true)
public class EmbeddingRequestedConsumer {

    private final DocumentV2EmbeddingService embeddingService;
    private final PipelineEventAuditService auditService;

    public EmbeddingRequestedConsumer(DocumentV2EmbeddingService embeddingService,
                                      PipelineEventAuditService auditService) {
        this.embeddingService = embeddingService;
        this.auditService = auditService;
    }

    @KafkaListener(topics = "${app.kafka.topics.embedding-requested-v2}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(EmbeddingRequestedEvent event, ConsumerRecord<String, EmbeddingRequestedEvent> record) {
        auditService.record(
                event.eventId(),
                "EmbeddingRequested",
                record.topic(),
                record.key(),
                event.documentId(),
                event.contentId(),
                event.chunkId(),
                event.contentHash(),
                "RECEIVED",
                null,
                event.occurredAt()
        );
        try {
            boolean processed = embeddingService.embedRequestedChunk(event);
            auditService.record(
                    event.eventId(),
                    "EmbeddingRequested",
                    record.topic(),
                    record.key(),
                    event.documentId(),
                    event.contentId(),
                    event.chunkId(),
                    event.contentHash(),
                    processed ? "PROCESSED" : "IGNORED",
                    null,
                    event.occurredAt()
            );
        } catch (RuntimeException e) {
            auditService.record(
                    event.eventId(),
                    "EmbeddingRequested",
                    record.topic(),
                    record.key(),
                    event.documentId(),
                    event.contentId(),
                    event.chunkId(),
                    event.contentHash(),
                    "FAILED",
                    e.getMessage(),
                    event.occurredAt()
            );
            throw e;
        }
    }
}
