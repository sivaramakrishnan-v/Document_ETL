package com.document.documentetl.service;

import com.document.documentetl.config.AgentRagProperties;
import com.document.documentetl.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("mmr")
public class MmrRetrievalService implements RetrievalStrategy {

    private static final Logger log = LoggerFactory.getLogger(MmrRetrievalService.class);

    private final VectorSearchService vectorSearchService;
    private final MmrDiversityFilter mmrDiversityFilter;
    private final AgentRagProperties ragProperties;

    public MmrRetrievalService(VectorSearchService vectorSearchService,
                               MmrDiversityFilter mmrDiversityFilter,
                               AgentRagProperties ragProperties) {
        this.vectorSearchService = vectorSearchService;
        this.mmrDiversityFilter = mmrDiversityFilter;
        this.ragProperties = ragProperties;
    }

    @Override
    public List<SearchResult> retrieve(String query, int limit) {
        return retrieve(query, limit, null);
    }

    @Override
    public List<SearchResult> retrieve(String query, int limit, List<Long> documentIds) {
        int candidatePoolSize = Math.max(ragProperties.getMmrCandidateK(), limit);
        int finalLimit = Math.min(limit, ragProperties.getMmrFinalK());
        List<SearchResult> candidates = vectorSearchService.retrieve(query, candidatePoolSize, documentIds);
        List<SearchResult> selected = mmrDiversityFilter.filter(candidates, finalLimit, ragProperties.getMmrLambda());
        log.info("MMR retrieval completed: documentScope={} candidateCount={} resultCount={} lambda={}",
                normalizeDocumentIds(documentIds), candidates.size(), selected.size(), ragProperties.getMmrLambda());
        return selected;
    }

    public List<SearchResult> selectDiverse(List<SearchResult> candidates, int limit) {
        int finalLimit = Math.min(limit, ragProperties.getMmrFinalK());
        List<SearchResult> selected = mmrDiversityFilter.filter(candidates, finalLimit, ragProperties.getMmrLambda());
        log.info("MMR diversification completed: candidateCount={} resultCount={} lambda={}",
                candidates == null ? 0 : candidates.size(), selected.size(), ragProperties.getMmrLambda());
        return selected;
    }

    private static List<Long> normalizeDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        return documentIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }
}
