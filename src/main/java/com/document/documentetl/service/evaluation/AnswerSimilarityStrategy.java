package com.document.documentetl.service.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service("answerSimilarityStrategy")
public class AnswerSimilarityStrategy implements RagEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AnswerSimilarityStrategy.class);
    private static final String METRIC = "answer_similarity";
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
    private static final Pattern SCORE_PATTERN = Pattern.compile("(0(?:\\.\\d+)?|1(?:\\.0+)?)");

    private final EvaluationModelGateway evaluationModelGateway;
    private final ObjectMapper objectMapper;

    public AnswerSimilarityStrategy(
            EvaluationModelGateway evaluationModelGateway,
            ObjectMapper objectMapper) {
        this.evaluationModelGateway = evaluationModelGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationResult evaluate(String question, String context, String answer) {
        String goldenAnswer = context;
        if (isBlank(question) || isBlank(answer)) {
            return new EvaluationResult(METRIC, 0.0, "Evaluation skipped because question or answer was blank.");
        }
        if (isBlank(goldenAnswer)) {
            return new EvaluationResult(METRIC, 0.0, "Evaluation skipped because golden answer was not provided.");
        }

        String prompt = """
                You are an answer similarity judge for RAG evaluation.
                Compare the generated answer with the golden answer.

                Scoring guidance:
                - 1.0: semantically equivalent and complete.
                - 0.7-0.9: mostly correct with minor omissions.
                - 0.4-0.6: partially correct.
                - 0.0-0.3: incorrect or unrelated.

                Return ONLY valid JSON:
                {"score": <number from 0.0 to 1.0>, "reasoning": "<short explanation>"}

                Question:
                %s

                Golden Answer:
                %s

                Generated Answer:
                %s
                """.formatted(question, goldenAnswer, answer);

        long startedAtNanos = System.nanoTime();
        log.info("action=EVALUATION_LLM state=STARTED metric={} promptChars={} questionChars={} goldenAnswerChars={} answerChars={}",
                METRIC,
                prompt.length(),
                question.length(),
                goldenAnswer.length(),
                answer.length());
        String rawResponse = evaluationModelGateway.generate(prompt);
        long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
        log.info("action=EVALUATION_LLM state=COMPLETED metric={} durationMs={} responseChars={}",
                METRIC,
                durationMs,
                rawResponse == null ? 0 : rawResponse.length());
        return parse(rawResponse);
    }

    private EvaluationResult parse(String rawResponse) {
        String response = rawResponse == null ? "" : rawResponse.trim();
        if (response.isEmpty()) {
            return new EvaluationResult(METRIC, 0.0, "Evaluator returned an empty response.");
        }

        try {
            String jsonPayload = extractJsonPayload(response);
            JsonNode root = objectMapper.readTree(jsonPayload);
            double score = root.path("score").asDouble(Double.NaN);
            String reasoning = root.path("reasoning").asText("").trim();
            if (Double.isFinite(score) && !reasoning.isEmpty()) {
                return new EvaluationResult(METRIC, score, reasoning);
            }
        } catch (Exception ignored) {
            // Fallback below handles non-JSON responses.
        }

        Matcher scoreMatcher = SCORE_PATTERN.matcher(response.toLowerCase(Locale.ROOT));
        if (scoreMatcher.find()) {
            double extractedScore = Double.parseDouble(scoreMatcher.group(1));
            return new EvaluationResult(
                    METRIC,
                    extractedScore,
                    "Evaluator returned non-JSON output; score extracted from raw response.");
        }

        String truncated = response.length() > 300 ? response.substring(0, 300) + "..." : response;
        return new EvaluationResult(METRIC, 0.0, "Could not parse evaluator response: " + truncated);
    }

    private static String extractJsonPayload(String response) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return response;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
