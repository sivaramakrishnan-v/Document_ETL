package com.document.documentetl.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EmbeddingRequestedEvent(
        UUID eventId,
        UUID chunkId,
        Long documentId,
        UUID contentId,
        String contentHash,
        String modelProvider,
        String embeddingModel,
        int embeddingDimension,
        OffsetDateTime occurredAt
) {
}
