package com.documentetl.mcp.model;

import java.util.List;

public record AskDocumentsRequest(
        String question,
        String documentId
) {
}
