package com.document.documentetl.service.agent.retrieval;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.SearchOrchestrator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MmrSearchTool implements RetrievalTool {

    private final SearchOrchestrator searchOrchestrator;

    public MmrSearchTool(SearchOrchestrator searchOrchestrator) {
        this.searchOrchestrator = searchOrchestrator;
    }

    @Override
    public String name() {
        return "mmr_search";
    }

    @Override
    public List<SearchResult> execute(String query, int limit) {
        return searchOrchestrator.retrieve(query, limit, "mmr");
    }

    @Override
    public List<SearchResult> execute(String query, int limit, List<Long> documentIds) {
        return searchOrchestrator.retrieve(query, limit, "mmr", documentIds);
    }
}
