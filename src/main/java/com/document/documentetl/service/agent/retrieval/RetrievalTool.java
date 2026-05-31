package com.document.documentetl.service.agent.retrieval;

import com.document.documentetl.dto.SearchResult;

import java.util.List;

public interface RetrievalTool {
    String name();

    List<SearchResult> execute(String query, int limit);
}

