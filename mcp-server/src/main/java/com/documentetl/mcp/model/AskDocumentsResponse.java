package com.documentetl.mcp.model;

import java.util.List;

public record AskDocumentsResponse(
        String answer,
        List<Citation> citations
) {
    public record Citation(String chunkId, String documentName) {
    }
}
