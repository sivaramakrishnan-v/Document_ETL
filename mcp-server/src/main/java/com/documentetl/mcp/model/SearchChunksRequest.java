package com.documentetl.mcp.model;

public record SearchChunksRequest(
        String query,
        Integer topK
) {
}
