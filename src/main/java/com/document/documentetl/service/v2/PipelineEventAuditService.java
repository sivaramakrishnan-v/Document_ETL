package com.document.documentetl.service.v2;

import com.document.documentetl.model.v2.PipelineEvent;
import com.document.documentetl.repository.v2.PipelineEventRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PipelineEventAuditService {

    private final PipelineEventRepository pipelineEventRepository;

    public PipelineEventAuditService(PipelineEventRepository pipelineEventRepository) {
        this.pipelineEventRepository = pipelineEventRepository;
    }

    public void record(UUID eventId,
                       String eventType,
                       String topicName,
                       String messageKey,
                       Long documentId,
                       UUID contentId,
                       UUID chunkId,
                       String contentHash,
                       String status,
                       String errorMessage,
                       OffsetDateTime occurredAt) {
        PipelineEvent event = pipelineEventRepository.findById(eventId).orElseGet(PipelineEvent::new);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setTopicName(topicName);
        event.setMessageKey(messageKey);
        event.setDocumentId(documentId);
        event.setContentId(contentId);
        event.setChunkId(chunkId);
        event.setContentHash(contentHash);
        event.setProcessingStatus(status);
        event.setErrorMessage(errorMessage);
        event.setOccurredAt(occurredAt == null ? OffsetDateTime.now() : occurredAt);
        event.setProcessedAt(OffsetDateTime.now());
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(OffsetDateTime.now());
        }
        pipelineEventRepository.save(event);
    }
}
