package com.document.documentetl.service.agent.retrieval;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.SearchOrchestrator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HybridSearchTool implements RetrievalTool {

    private final SearchOrchestrator searchOrchestrator;

    public HybridSearchTool(SearchOrchestrator searchOrchestrator) {
        this.searchOrchestrator = searchOrchestrator;
    }

    @Override
    public String name() {
        return "hybrid_search";
    }

    @Override
    public List<SearchResult> execute(String query, int limit) {
        return searchOrchestrator.retrieve(query, limit, "rrf-hybrid");
    }
}

