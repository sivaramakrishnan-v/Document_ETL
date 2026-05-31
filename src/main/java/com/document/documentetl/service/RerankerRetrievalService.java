package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service("reranker")
public class RerankerRetrievalService implements RetrievalStrategy {

    private static final int CANDIDATE_MULTIPLIER = 2;

    private final VectorSearchService vectorSearchService;

    public RerankerRetrievalService(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @Override
    public List<SearchResult> retrieve(String query, int limit) {
        int candidatePoolSize = Math.max(limit * CANDIDATE_MULTIPLIER, limit);
        List<SearchResult> vectorCandidates = vectorSearchService.retrieve(query, candidatePoolSize);
        return rerank(query, vectorCandidates, limit);
    }

    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int limit) {
        return rerankWithDetails(query, candidates, limit).stream()
                .map(RerankerScoreDetail::getResult)
                .toList();
    }

    public List<RerankerScoreDetail> rerankWithDetails(String query, List<SearchResult> candidates, int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }

        return candidates.stream()
                .map(candidate -> {
                    ScoringBreakdown scoringBreakdown = scoreWithMockReranker(query, candidate);
                    return new RerankerScoreDetail(
                            new SearchResult(
                                    candidate.getText(),
                                    candidate.getDocumentId(),
                                    scoringBreakdown.finalScore(),
                                    candidate.getEmbedding()
                            ),
                            scoringBreakdown.semanticScore(),
                            scoringBreakdown.lexicalOverlapScore(),
                            scoringBreakdown.exactPhraseBoost(),
                            scoringBreakdown.finalScore()
                    );
                })
                .sorted(Comparator.comparingDouble(RerankerScoreDetail::getFinalScore).reversed())
                .limit(limit)
                .toList();
    }

    private static ScoringBreakdown scoreWithMockReranker(String query, SearchResult candidate) {
        // Placeholder for a true reranker call, e.g., Vertex AI scoring or cross-encoder.
        double semanticScore = candidate.getSimilarity();
        double lexicalOverlapScore = lexicalOverlap(query, candidate.getText());
        double exactPhraseBoost = containsQueryPhrase(query, candidate.getText()) ? 0.1 : 0.0;

        double rawScore = (semanticScore * 0.65) + (lexicalOverlapScore * 0.25) + exactPhraseBoost;
        double boundedScore = Math.max(0.0, Math.min(1.0, rawScore));
        return new ScoringBreakdown(semanticScore, lexicalOverlapScore, exactPhraseBoost, boundedScore);
    }

    private static double lexicalOverlap(String query, String text) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return 0.0;
        }

        Set<String> textTokens = tokenize(text);
        if (textTokens.isEmpty()) {
            return 0.0;
        }

        long overlapCount = queryTokens.stream().filter(textTokens::contains).count();
        return (double) overlapCount / queryTokens.size();
    }

    private static Set<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private static boolean containsQueryPhrase(String query, String text) {
        if (query == null || text == null) {
            return false;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
        String normalizedText = text.toLowerCase(Locale.ROOT);
        return !normalizedQuery.isEmpty() && normalizedText.contains(normalizedQuery);
    }

    private record ScoringBreakdown(double semanticScore,
                                    double lexicalOverlapScore,
                                    double exactPhraseBoost,
                                    double finalScore) {
    }

    public static class RerankerScoreDetail {
        private final SearchResult result;
        private final double semanticScore;
        private final double lexicalOverlapScore;
        private final double exactPhraseBoost;
        private final double finalScore;

        public RerankerScoreDetail(SearchResult result,
                                   double semanticScore,
                                   double lexicalOverlapScore,
                                   double exactPhraseBoost,
                                   double finalScore) {
            this.result = result;
            this.semanticScore = semanticScore;
            this.lexicalOverlapScore = lexicalOverlapScore;
            this.exactPhraseBoost = exactPhraseBoost;
            this.finalScore = finalScore;
        }

        public SearchResult getResult() {
            return result;
        }

        public double getSemanticScore() {
            return semanticScore;
        }

        public double getLexicalOverlapScore() {
            return lexicalOverlapScore;
        }

        public double getExactPhraseBoost() {
            return exactPhraseBoost;
        }

        public double getFinalScore() {
            return finalScore;
        }
    }
}
