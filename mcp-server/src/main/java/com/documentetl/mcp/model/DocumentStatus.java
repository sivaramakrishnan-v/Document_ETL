package com.documentetl.mcp.model;

public enum DocumentStatus {
    UPLOADED,
    PARSING,
    CHUNKING,
    EMBEDDING,
    READY,
    FAILED;

    public static DocumentStatus fromApiStatus(String status) {
        if (status == null || status.isBlank()) {
            return UPLOADED;
        }
        return switch (status.trim().toUpperCase()) {
            case "UPLOADED", "STAGED" -> UPLOADED;
            case "PARSING", "PARSED" -> PARSING;
            case "CHUNKING", "CHUNKED" -> CHUNKING;
            case "EMBEDDING" -> EMBEDDING;
            case "READY", "COMPLETED", "COMPLETE" -> READY;
            case "FAILED", "ERROR" -> FAILED;
            default -> UPLOADED;
        };
    }
}
