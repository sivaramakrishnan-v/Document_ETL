package com.document.documentetl.service.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final RagEvaluator relevancyEvaluator;
    private final RagEvaluator answerSimilarityEvaluator;
    private final EvaluationLogSink evaluationLogSink;
    private final String evaluationModelName;

    public EvaluationService(
            @Qualifier("relevancyEvaluatorStrategy") RagEvaluator relevancyEvaluator,
            @Qualifier("answerSimilarityStrategy") RagEvaluator answerSimilarityEvaluator,
            EvaluationLogSink evaluationLogSink,
            @Value("${app.rag.evaluation.model-name:gemini-1.5-pro}") String evaluationModelName) {
        this.relevancyEvaluator = relevancyEvaluator;
        this.answerSimilarityEvaluator = answerSimilarityEvaluator;
        this.evaluationLogSink = evaluationLogSink;
        this.evaluationModelName = evaluationModelName;
    }

    public List<EvaluationResult> evaluateAndLog(String question,
                                                 String retrievedContext,
                                                 String answer,
                                                 String goldenAnswer) {
        long startedAtNanos = System.nanoTime();
        List<EvaluationResult> results = new ArrayList<>();
        Instant now = Instant.now();

        log.info("Evaluation pipeline started: questionChars={}, contextChars={}, answerChars={}, goldenAnswerProvided={}",
                lengthOf(question),
                lengthOf(retrievedContext),
                lengthOf(answer),
                goldenAnswer != null && !goldenAnswer.isBlank());

        EvaluationResult relevancyResult = safelyEvaluate(
                () -> relevancyEvaluator.evaluate(question, retrievedContext, answer),
                "relevancy");
        results.add(relevancyResult);
        logResult(now, question, retrievedContext, answer, goldenAnswer, relevancyResult);

        EvaluationResult similarityResult;
        if (goldenAnswer == null || goldenAnswer.isBlank()) {
            similarityResult = new EvaluationResult(
                    "answer_similarity",
                    0.0,
                    "Golden answer not provided; answer similarity metric skipped.");
        } else {
            similarityResult = safelyEvaluate(
                    () -> answerSimilarityEvaluator.evaluate(question, goldenAnswer, answer),
                    "answer_similarity");
        }
        results.add(similarityResult);
        logResult(now, question, retrievedContext, answer, goldenAnswer, similarityResult);

        long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
        log.info("Evaluation pipeline completed: metricsCount={}, durationMs={}", results.size(), durationMs);
        return results;
    }

    private EvaluationResult safelyEvaluate(EvaluationCall call, String metricName) {
        try {
            return call.run();
        } catch (Exception e) {
            log.warn("Evaluation failed for {} metric: {}", metricName, e.getMessage());
            return new EvaluationResult(metricName, 0.0, "Evaluation failed: " + e.getMessage());
        }
    }

    private void logResult(Instant timestamp,
                           String question,
                           String retrievedContext,
                           String answer,
                           String goldenAnswer,
                           EvaluationResult result) {
        evaluationLogSink.log(new EvaluationLogEntry(
                timestamp,
                question,
                retrievedContext,
                answer,
                goldenAnswer,
                result.metric(),
                result.score(),
                result.reasoning(),
                evaluationModelName
        ));
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    @FunctionalInterface
    private interface EvaluationCall {
        EvaluationResult run();
    }
}
