package com.documentetl.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiAskRequest(
        String question,
        String documentId
) {
}
