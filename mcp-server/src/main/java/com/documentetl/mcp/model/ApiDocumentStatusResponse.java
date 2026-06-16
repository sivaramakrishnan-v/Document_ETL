package com.documentetl.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiDocumentStatusResponse(
        Object documentId,
        String status,
        Integer chunks,
        Boolean embeddingsGenerated
) {
}
