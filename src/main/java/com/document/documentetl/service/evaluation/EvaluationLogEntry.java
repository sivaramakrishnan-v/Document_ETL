package com.document.documentetl.service.evaluation;

import java.time.Instant;

public record EvaluationLogEntry(
        Instant createdAt,
        String question,
        String retrievedContext,
        String answer,
        String goldenAnswer,
        String metric,
        double score,
        String reasoning,
        String evaluationModel) {
}
