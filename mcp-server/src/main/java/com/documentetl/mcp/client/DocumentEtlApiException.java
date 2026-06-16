package com.documentetl.mcp.client;

public class DocumentEtlApiException extends RuntimeException {

    private final int statusCode;

    public DocumentEtlApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
