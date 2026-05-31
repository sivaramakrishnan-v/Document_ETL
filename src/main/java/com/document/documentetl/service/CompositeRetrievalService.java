package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("composite")
public class CompositeRetrievalService implements RetrievalStrategy {

    private static final Logger log = LoggerFactory.getLogger(CompositeRetrievalService.class);

    private static final int RRF_CANDIDATE_LIMIT = 50;
    private static final int MMR_DIVERSITY_LIMIT = 20;

    private final RrfHybridRetrievalService rrfHybridRetrievalService;
    private final MmrRetrievalService mmrRetrievalService;
    private final RerankerRetrievalService rerankerRetrievalService;

    public CompositeRetrievalService(RrfHybridRetrievalService rrfHybridRetrievalService,
                                     MmrRetrievalService mmrRetrievalService,
                                     RerankerRetrievalService rerankerRetrievalService) {
        this.rrfHybridRetrievalService = rrfHybridRetrievalService;
        this.mmrRetrievalService = mmrRetrievalService;
        this.rerankerRetrievalService = rerankerRetrievalService;
    }

    @Override
    public List<SearchResult> retrieve(String query, int limit) {
        log.info("action=COMPOSITE_RETRIEVAL state=STARTED queryLength={} requestedLimit={}",
                queryLength(query), limit);

        if (limit <= 0) {
            log.warn("action=COMPOSITE_RETRIEVAL state=SKIPPED reason=INVALID_LIMIT requestedLimit={}", limit);
            return List.of();
        }

        try {
            log.info("action=RRF_RETRIEVAL state=STARTED candidateLimit={}", RRF_CANDIDATE_LIMIT);
            List<SearchResult> rrfCandidates = rrfHybridRetrievalService.retrieve(query, RRF_CANDIDATE_LIMIT);
            log.info("action=RRF_RETRIEVAL state={} resultCount={}",
                    rrfCandidates.isEmpty() ? "EMPTY" : "COMPLETED", rrfCandidates.size());
            if (rrfCandidates.isEmpty()) {
                log.info("action=COMPOSITE_RETRIEVAL state=COMPLETED resultCount=0 reason=NO_RRF_CANDIDATES");
                return List.of();
            }

            int diversityLimit = Math.min(MMR_DIVERSITY_LIMIT, rrfCandidates.size());
            log.info("action=MMR_DIVERSIFICATION state=STARTED inputCount={} diversityLimit={}",
                    rrfCandidates.size(), diversityLimit);
            List<SearchResult> diverseCandidates = mmrRetrievalService.selectDiverse(rrfCandidates, diversityLimit);
            log.info("action=MMR_DIVERSIFICATION state={} resultCount={}",
                    diverseCandidates.isEmpty() ? "EMPTY" : "COMPLETED", diverseCandidates.size());
            if (diverseCandidates.isEmpty()) {
                log.info("action=COMPOSITE_RETRIEVAL state=COMPLETED resultCount=0 reason=NO_DIVERSE_CANDIDATES");
                return List.of();
            }

            log.info("action=RERANK state=STARTED inputCount={} outputLimit={}", diverseCandidates.size(), limit);
            List<SearchResult> rerankedResults = rerankerRetrievalService.rerank(query, diverseCandidates, limit);
            log.info("action=RERANK state={} resultCount={}",
                    rerankedResults.isEmpty() ? "EMPTY" : "COMPLETED", rerankedResults.size());

            log.info("action=COMPOSITE_RETRIEVAL state=COMPLETED resultCount={}", rerankedResults.size());
            return rerankedResults;
        } catch (RuntimeException ex) {
            log.error("action=COMPOSITE_RETRIEVAL state=FAILED queryLength={} requestedLimit={}",
                    queryLength(query), limit, ex);
            throw ex;
        }
    }

    private static int queryLength(String query) {
        return query == null ? 0 : query.length();
    }
}
