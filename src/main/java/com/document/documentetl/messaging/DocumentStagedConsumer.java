package com.document.documentetl.messaging;

import com.document.documentetl.events.DocumentStagedEvent;
import com.document.documentetl.service.v2.DocumentV2ParseChunkService;
import com.document.documentetl.service.v2.PipelineEventAuditService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.etl.v2.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentStagedConsumer {

    private final DocumentV2ParseChunkService parseChunkService;
    private final PipelineEventAuditService auditService;

    public DocumentStagedConsumer(DocumentV2ParseChunkService parseChunkService,
                                  PipelineEventAuditService auditService) {
        this.parseChunkService = parseChunkService;
        this.auditService = auditService;
    }

    @KafkaListener(topics = "${app.kafka.topics.document-staged-v2}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(DocumentStagedEvent event, ConsumerRecord<String, DocumentStagedEvent> record) {
        auditService.record(
                event.eventId(),
                "DocumentStaged",
                record.topic(),
                record.key(),
                event.documentId(),
                null,
                null,
                event.contentHash(),
                "RECEIVED",
                null,
                event.occurredAt()
        );
        try {
            DocumentV2ParseChunkService.ParseChunkResult result = parseChunkService.parseAndChunk(event);
            auditService.record(
                    event.eventId(),
                    "DocumentStaged",
                    record.topic(),
                    record.key(),
                    event.documentId(),
                    result.contentId(),
                    null,
                    event.contentHash(),
                    result.staleEvent() ? "IGNORED" : "PROCESSED",
                    null,
                    event.occurredAt()
            );
        } catch (RuntimeException e) {
            auditService.record(
                    event.eventId(),
                    "DocumentStaged",
                    record.topic(),
                    record.key(),
                    event.documentId(),
                    null,
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
