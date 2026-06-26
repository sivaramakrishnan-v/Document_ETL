package com.document.documentetl.service.agent.retrieval;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.SearchOrchestrator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VectorSearchTool implements RetrievalTool {

    private final SearchOrchestrator searchOrchestrator;

    public VectorSearchTool(SearchOrchestrator searchOrchestrator) {
        this.searchOrchestrator = searchOrchestrator;
    }

    @Override
    public String name() {
        return "vector_search";
    }

    @Override
    public List<SearchResult> execute(String query, int limit) {
        return searchOrchestrator.retrieve(query, limit, "vector");
    }

    @Override
    public List<SearchResult> execute(String query, int limit, List<Long> documentIds) {
        return searchOrchestrator.retrieve(query, limit, "vector", documentIds);
    }
}
