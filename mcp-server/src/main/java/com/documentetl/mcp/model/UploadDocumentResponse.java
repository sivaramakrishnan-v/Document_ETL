package com.documentetl.mcp.model;

public record UploadDocumentResponse(
        String documentId,
        String fileName,
        DocumentStatus status
) {
}
