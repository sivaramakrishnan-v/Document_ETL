package com.documentetl.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiAskResponse(
        String answer,
        List<Object> sources,
        List<ApiCitation> citations
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiCitation(Object chunkId, String documentName) {
    }
}
