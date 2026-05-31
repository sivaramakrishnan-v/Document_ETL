package com.document.documentetl.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentStagedEvent(
        UUID eventId,
        Long documentId,
        String sourceUri,
        String contentHash,
        int versionNumber,
        OffsetDateTime occurredAt
) {
}
