package com.documentetl.mcp.model;

public record DocumentStatusResponse(
        String documentId,
        DocumentStatus status,
        int chunks,
        boolean embeddingsGenerated
) {
}
