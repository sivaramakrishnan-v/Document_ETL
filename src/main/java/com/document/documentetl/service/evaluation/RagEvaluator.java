package com.document.documentetl.service.evaluation;

public interface RagEvaluator {

    EvaluationResult evaluate(String question, String context, String answer);
}
