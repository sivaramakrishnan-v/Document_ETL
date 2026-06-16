package com.documentetl.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiSearchResult(
        Object chunkId,
        Object documentId,
        Double score,
        Double similarity,
        String content,
        String text
) {
}
