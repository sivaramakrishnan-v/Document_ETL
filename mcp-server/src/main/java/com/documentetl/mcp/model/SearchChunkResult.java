package com.documentetl.mcp.model;

public record SearchChunkResult(
        String chunkId,
        double score,
        String content
) {
}
