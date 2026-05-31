package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MmrSearchService {

    private static final String MMR_STRATEGY_TYPE = "mmr";

    private final SearchOrchestrator searchOrchestrator;

    public MmrSearchService(SearchOrchestrator searchOrchestrator) {
        this.searchOrchestrator = searchOrchestrator;
    }

    public List<SearchResult> search(String query, int limit) {
        return searchOrchestrator.retrieve(query, limit, MMR_STRATEGY_TYPE);
    }
}
