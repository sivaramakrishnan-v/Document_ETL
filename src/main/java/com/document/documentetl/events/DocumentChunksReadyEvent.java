package com.document.documentetl.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentChunksReadyEvent(
        UUID eventId,
        Long documentId,
        UUID contentId,
        String contentHash,
        int chunkCount,
        OffsetDateTime occurredAt
) {
}
