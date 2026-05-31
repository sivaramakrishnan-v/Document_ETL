package com.document.documentetl.controller;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.MlflowActionTrackingService;
import com.document.documentetl.service.SearchOrchestrator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final String DEFAULT_STRATEGY_TYPE = "vector";

    private final SearchOrchestrator searchOrchestrator;
    private final MlflowActionTrackingService mlflowActionTrackingService;

    public SearchController(SearchOrchestrator searchOrchestrator,
                            MlflowActionTrackingService mlflowActionTrackingService) {
        this.searchOrchestrator = searchOrchestrator;
        this.mlflowActionTrackingService = mlflowActionTrackingService;
    }

    @GetMapping
    public List<SearchResult> search(@RequestParam("q") String query,
                                     @RequestParam(name = "maxResults", defaultValue = "5") int maxResults,
                                     @RequestParam(name = "strategyType", defaultValue = DEFAULT_STRATEGY_TYPE)
                                     String strategyType) {
        long startedAtNanos = System.nanoTime();
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'q' must not be blank");
        }

        int resultLimit = maxResults > 0 ? maxResults : DEFAULT_MAX_RESULTS;
        try {
            List<SearchResult> results = searchOrchestrator.retrieve(query, resultLimit, strategyType);
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            mlflowActionTrackingService.logActionSuccess(
                    "search.retrieve",
                    durationMs,
                    Map.of(
                            "query_length_chars", (double) query.length(),
                            "requested_max_results", (double) resultLimit,
                            "results_returned", (double) results.size()),
                    Map.of(
                            "endpoint", "/api/search",
                            "strategy_type", strategyType == null ? DEFAULT_STRATEGY_TYPE : strategyType));
            return results;
        } catch (IllegalArgumentException ex) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            mlflowActionTrackingService.logActionFailure(
                    "search.retrieve",
                    durationMs,
                    ex,
                    Map.of(
                            "endpoint", "/api/search",
                            "strategy_type", strategyType == null ? DEFAULT_STRATEGY_TYPE : strategyType));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            mlflowActionTrackingService.logActionFailure(
                    "search.retrieve",
                    durationMs,
                    ex,
                    Map.of(
                            "endpoint", "/api/search",
                            "strategy_type", strategyType == null ? DEFAULT_STRATEGY_TYPE : strategyType));
            throw ex;
        }
    }
}
