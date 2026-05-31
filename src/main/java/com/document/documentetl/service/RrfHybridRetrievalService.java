package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service("rrf-hybrid")
public class RrfHybridRetrievalService implements RetrievalStrategy {

    private static final int CANDIDATE_MULTIPLIER = 3;
    private static final int RRF_K = 60;

    private static final String KEYWORD_SEARCH_SQL = """
            SELECT chunk_text,
                   document_id,
                   ts_rank_cd(
                       to_tsvector('english', COALESCE(chunk_text, '')),
                       plainto_tsquery('english', ?)
                   ) AS keyword_score
            FROM knowledge.document_chunks
            WHERE to_tsvector('english', COALESCE(chunk_text, ''))
                  @@ plainto_tsquery('english', ?)
            ORDER BY keyword_score DESC
            LIMIT ?
            """;

    private final VectorSearchService vectorSearchService;
    private final JdbcTemplate jdbcTemplate;

    public RrfHybridRetrievalService(VectorSearchService vectorSearchService, JdbcTemplate jdbcTemplate) {
        this.vectorSearchService = vectorSearchService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SearchResult> retrieve(String query, int limit) {
        int candidatePoolSize = Math.max(limit * CANDIDATE_MULTIPLIER, limit);

        CompletableFuture<List<SearchResult>> vectorFuture =
                CompletableFuture.supplyAsync(() -> vectorSearchService.retrieve(query, candidatePoolSize));
        CompletableFuture<List<SearchResult>> keywordFuture =
                CompletableFuture.supplyAsync(() -> keywordSearch(query, candidatePoolSize));

        List<SearchResult> vectorRanked = vectorFuture.join();
        List<SearchResult> keywordRanked = keywordFuture.join();

        Map<String, HybridScore> merged = new HashMap<>();
        applyRrf(vectorRanked, merged);
        applyRrf(keywordRanked, merged);

        return merged.values().stream()
                .sorted((left, right) -> Double.compare(right.score, left.score))
                .limit(limit)
                .map(score -> new SearchResult(score.chunkText, score.documentId, score.score, score.embedding))
                .toList();
    }

    private List<SearchResult> keywordSearch(String query, int limit) {
        return jdbcTemplate.query(
                KEYWORD_SEARCH_SQL,
                (rs, rowNum) -> new SearchResult(
                        rs.getString("chunk_text"),
                        toDocumentId(rs.getObject("document_id")),
                        rs.getDouble("keyword_score")
                ),
                query,
                query,
                limit
        );
    }

    private static void applyRrf(List<SearchResult> rankedResults, Map<String, HybridScore> merged) {
        for (int i = 0; i < rankedResults.size(); i++) {
            SearchResult result = rankedResults.get(i);
            float[] resultEmbedding = result.getEmbedding();
            int rank = i + 1;
            double rrfScore = 1.0 / (rank + RRF_K);

            String key = buildChunkKey(result.getDocumentId(), result.getText());
            HybridScore existing = merged.get(key);
            if (existing == null) {
                merged.put(key, new HybridScore(
                        result.getText(),
                        result.getDocumentId(),
                        resultEmbedding,
                        rrfScore
                ));
            } else {
                existing.score += rrfScore;
                if (existing.embedding == null && resultEmbedding != null) {
                    existing.embedding = resultEmbedding;
                }
            }
        }
    }

    private static String buildChunkKey(Long documentId, String chunkText) {
        return documentId + "::" + chunkText;
    }

    private static Long toDocumentId(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(rawValue.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("document_id is not numeric: " + rawValue, ex);
        }
    }

    private static final class HybridScore {
        private final String chunkText;
        private final Long documentId;
        private float[] embedding;
        private double score;

        private HybridScore(String chunkText, Long documentId, float[] embedding, double score) {
            this.chunkText = chunkText;
            this.documentId = documentId;
            this.embedding = embedding;
            this.score = score;
        }
    }
}
