package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("mmr")
public class MmrRetrievalService implements RetrievalStrategy {

    private static final int CANDIDATE_MULTIPLIER = 4;
    private static final double DEFAULT_LAMBDA = 0.7;

    private final VectorSearchService vectorSearchService;
    private final MmrDiversityFilter mmrDiversityFilter;

    public MmrRetrievalService(VectorSearchService vectorSearchService,
                               MmrDiversityFilter mmrDiversityFilter) {
        this.vectorSearchService = vectorSearchService;
        this.mmrDiversityFilter = mmrDiversityFilter;
    }

    @Override
    public List<SearchResult> retrieve(String query, int limit) {
        int candidatePoolSize = Math.max(limit * CANDIDATE_MULTIPLIER, limit);
        List<SearchResult> candidates = vectorSearchService.retrieve(query, candidatePoolSize);
        return mmrDiversityFilter.filter(candidates, limit, DEFAULT_LAMBDA);
    }

    public List<SearchResult> selectDiverse(List<SearchResult> candidates, int limit) {
        return mmrDiversityFilter.filter(candidates, limit, DEFAULT_LAMBDA);
    }
}
