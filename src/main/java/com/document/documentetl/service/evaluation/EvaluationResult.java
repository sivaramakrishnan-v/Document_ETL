package com.document.documentetl.service.evaluation;

public record EvaluationResult(String metric, double score, String reasoning) {

    public EvaluationResult {
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("metric must not be blank");
        }
        if (reasoning == null || reasoning.isBlank()) {
            throw new IllegalArgumentException("reasoning must not be blank");
        }
        score = Math.max(0.0, Math.min(1.0, score));
    }
}
