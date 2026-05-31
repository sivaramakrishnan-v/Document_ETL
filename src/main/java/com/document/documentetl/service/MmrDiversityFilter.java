package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class MmrDiversityFilter {

    public List<SearchResult> filter(List<SearchResult> candidates, int topK, double lambda) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }

        double boundedLambda = Math.max(0.0, Math.min(1.0, lambda));
        int resultSize = Math.min(topK, candidates.size());
        List<SearchResult> selected = new ArrayList<>(resultSize);
        List<SearchResult> remaining = new ArrayList<>(candidates);

        SearchResult first = remaining.stream()
                .max(Comparator.comparingDouble(SearchResult::getSimilarity))
                .orElse(null);
        if (first == null) {
            return List.of();
        }

        selected.add(first);
        remaining.remove(first);

        while (selected.size() < resultSize && !remaining.isEmpty()) {
            SearchResult bestCandidate = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (SearchResult candidate : remaining) {
                double maxSimilarityToSelected = maxSimilarityToSelected(candidate, selected);
                double mmrScore = (boundedLambda * candidate.getSimilarity())
                        - ((1.0 - boundedLambda) * maxSimilarityToSelected);

                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    bestCandidate = candidate;
                }
            }

            if (bestCandidate == null) {
                break;
            }

            selected.add(bestCandidate);
            remaining.remove(bestCandidate);
        }

        return selected;
    }

    private double maxSimilarityToSelected(SearchResult candidate, List<SearchResult> selected) {
        float[] candidateEmbedding = candidate.getEmbedding();
        if (candidateEmbedding == null || candidateEmbedding.length == 0) {
            return 0.0;
        }

        double maxSimilarity = 0.0;
        for (SearchResult chosen : selected) {
            float[] chosenEmbedding = chosen.getEmbedding();
            if (chosenEmbedding == null || chosenEmbedding.length == 0) {
                continue;
            }
            maxSimilarity = Math.max(maxSimilarity, calculateCosineSimilarity(candidateEmbedding, chosenEmbedding));
        }
        return maxSimilarity;
    }

    private double calculateCosineSimilarity(float[] v1, float[] v2) {
        int len = Math.min(v1.length, v2.length);
        if (len == 0) {
            return 0.0;
        }

        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < len; i++) {
            dot += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
