package com.document.documentetl.service.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service("relevancyEvaluatorStrategy")
public class RelevancyEvaluatorStrategy implements RagEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RelevancyEvaluatorStrategy.class);
    private static final String METRIC = "relevancy";
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
    private static final Pattern SCORE_PATTERN = Pattern.compile("(0(?:\\.\\d+)?|1(?:\\.0+)?)");

    private final EvaluationModelGateway evaluationModelGateway;
    private final ObjectMapper objectMapper;

    public RelevancyEvaluatorStrategy(
            EvaluationModelGateway evaluationModelGateway,
            ObjectMapper objectMapper) {
        this.evaluationModelGateway = evaluationModelGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationResult evaluate(String question, String context, String answer) {
        if (isBlank(question) || isBlank(answer)) {
            return new EvaluationResult(METRIC, 0.0, "Evaluation skipped because question or answer was blank.");
        }
        if (isBlank(context)) {
            return new EvaluationResult(METRIC, 0.0, "No retrieved context was provided for faithfulness evaluation.");
        }

        String prompt = """
                You are a strict RAG faithfulness judge.
                Score how well the answer is supported by the provided context.

                Rules:
                - 1.0 means every material claim in the answer is grounded in context.
                - 0.0 means the answer is unsupported or contradicts context.
                - Penalize hallucinations heavily.
                - Do not use outside knowledge.

                Return ONLY valid JSON:
                {"score": <number from 0.0 to 1.0>, "reasoning": "<short explanation>"}

                Question:
                %s

                Retrieved Context:
                %s

                Generated Answer:
                %s
                """.formatted(question, context, answer);

        long startedAtNanos = System.nanoTime();
        log.info("action=EVALUATION_LLM state=STARTED metric={} promptChars={} questionChars={} contextChars={} answerChars={}",
                METRIC,
                prompt.length(),
                question.length(),
                context.length(),
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
