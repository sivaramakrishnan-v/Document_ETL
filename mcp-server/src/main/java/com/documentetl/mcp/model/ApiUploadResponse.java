package com.documentetl.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiUploadResponse(
        Object documentId,
        String fileName,
        String status
) {
}
