package com.document.documentetl.service.agent.retrieval;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.v2.KeywordV2SearchService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KeywordSearchTool implements RetrievalTool {

    private final KeywordV2SearchService keywordV2SearchService;

    public KeywordSearchTool(KeywordV2SearchService keywordV2SearchService) {
        this.keywordV2SearchService = keywordV2SearchService;
    }

    @Override
    public String name() {
        return "keyword_search";
    }

    @Override
    public List<SearchResult> execute(String query, int limit) {
        return keywordV2SearchService.retrieve(query, limit);
    }
}

