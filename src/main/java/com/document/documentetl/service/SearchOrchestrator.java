package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

@Service
public class SearchOrchestrator {

    private static final String DEFAULT_STRATEGY_TYPE = StrategyType.VECTOR.beanName();

    private final Map<String, RetrievalStrategy> retrievalStrategies;

    public SearchOrchestrator(Map<String, RetrievalStrategy> retrievalStrategies) {
        this.retrievalStrategies = retrievalStrategies;
    }

    public List<SearchResult> retrieve(String query, int limit, String strategyType) {
        validateInputs(query, limit);
        String normalizedStrategyType = normalizeStrategyType(strategyType);
        RetrievalStrategy strategy = retrievalStrategies.get(normalizedStrategyType);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unsupported strategyType '" + strategyType + "'. Supported values: " + supportedStrategies()
            );
        }
        return strategy.retrieve(query, limit);
    }

    private String normalizeStrategyType(String strategyType) {
        if (strategyType == null || strategyType.isBlank()) {
            return DEFAULT_STRATEGY_TYPE;
        }

        String normalized = strategyType.trim().toLowerCase(Locale.ROOT);
        StrategyType mappedType = StrategyType.fromAlias(normalized);
        return mappedType != null ? mappedType.beanName() : normalized;
    }

    private String supportedStrategies() {
        return String.join(", ", new TreeSet<>(retrievalStrategies.keySet()));
    }

    private static void validateInputs(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
    }

    private enum StrategyType {
        VECTOR("vector"),
        MMR("mmr"),
        RERANKER("reranker"),
        RRF_HYBRID("rrf-hybrid"),
        COMPOSITE("composite");

        private final String beanName;

        StrategyType(String beanName) {
            this.beanName = beanName;
        }

        private String beanName() {
            return beanName;
        }

        private static StrategyType fromAlias(String normalizedValue) {
            return switch (normalizedValue) {
                case "vector", "semantic", "vector-search", "vectorsearch" -> VECTOR;
                case "mmr" -> MMR;
                case "reranker", "rerank", "re-ranker", "cross-encoder" -> RERANKER;
                case "rrf-hybrid", "rrf", "hybrid", "rrfhybrid", "rrf_hybrid" -> RRF_HYBRID;
                case "composite", "pipeline" -> COMPOSITE;
                default -> null;
            };
        }
    }
}
