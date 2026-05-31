package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.evaluation.EvaluationResult;
import com.document.documentetl.service.evaluation.EvaluationService;
import dev.langchain4j.model.input.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ChatService {

    private static final int CONTEXT_CHUNK_LIMIT = 5;
    private static final String EMPTY_CONTEXT = "No relevant context available.";

    private final MmrSearchService mmrSearchService;
    private final GenerationModelGateway generationModelGateway;
    private final EvaluationService evaluationService;
    private final MlflowActionTrackingService mlflowActionTrackingService;
    private final MlflowTraceBridgeService mlflowTraceBridgeService;

    public ChatService(MmrSearchService mmrSearchService,
                       GenerationModelGateway generationModelGateway,
                       EvaluationService evaluationService,
                       MlflowActionTrackingService mlflowActionTrackingService,
                       MlflowTraceBridgeService mlflowTraceBridgeService) {
        this.mmrSearchService = mmrSearchService;
        this.generationModelGateway = generationModelGateway;
        this.evaluationService = evaluationService;
        this.mlflowActionTrackingService = mlflowActionTrackingService;
        this.mlflowTraceBridgeService = mlflowTraceBridgeService;
    }

    public String ask(String question) {
        return askWithSources(question).getAnswer();
    }

    public ChatAnswer askWithSources(String question) {
        return askWithSources(question, null);
    }

    public ChatAnswer askWithSources(String question, String goldenAnswer) {
        validateQuestion(question);
        long startedAtNanos = System.nanoTime();

        try {
            List<SearchResult> topChunks = mmrSearchService.search(question, CONTEXT_CHUNK_LIMIT);
            String context = buildContext(topChunks);

            String prompt = PromptTemplate.from(ChatPrompts.ANSWER_FROM_CONTEXT_TEMPLATE)
                    .apply(Map.of(
                            "context", context,
                            "question", question
                    ))
                    .text();

            String answer = generationModelGateway.generate(prompt, "chat.answer");
            List<Long> sources = extractSources(topChunks);
            List<EvaluationResult> evaluations = evaluationService.evaluateAndLog(question, context, answer, goldenAnswer);
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            Map<String, String> mlflowParams = buildMlflowParams(question, answer, sources, goldenAnswer);
            String runId = mlflowActionTrackingService.logActionSuccessAndGetRunId(
                    "chat.ask",
                    durationMs,
                    buildMlflowMetrics(question, answer, topChunks, sources, evaluations),
                    mlflowParams);
            mlflowTraceBridgeService.emitChatTrace(runId, question, answer, topChunks);
            return new ChatAnswer(answer, sources, evaluations);
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            mlflowActionTrackingService.logActionFailure(
                    "chat.ask",
                    durationMs,
                    e,
                    Map.of("golden_answer_provided", Boolean.toString(goldenAnswer != null && !goldenAnswer.isBlank())));
            throw e;
        }
    }

    private static void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
    }

    private static String buildContext(List<SearchResult> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return EMPTY_CONTEXT;
        }

        return chunks.stream()
                .map(SearchResult::getText)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(EMPTY_CONTEXT);
    }

    private static List<Long> extractSources(List<SearchResult> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        return chunks.stream()
                .map(SearchResult::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static Map<String, Double> buildMlflowMetrics(String question,
                                                           String answer,
                                                           List<SearchResult> chunks,
                                                           List<Long> sources,
                                                           List<EvaluationResult> evaluations) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("question_length_chars", (double) question.length());
        metrics.put("answer_length_chars", (double) answer.length());
        metrics.put("context_chunk_count", (double) (chunks != null ? chunks.size() : 0));
        metrics.put("source_count", (double) (sources != null ? sources.size() : 0));

        if (evaluations != null && !evaluations.isEmpty()) {
            double averageScore = evaluations.stream()
                    .mapToDouble(EvaluationResult::score)
                    .average()
                    .orElse(0.0);
            metrics.put("eval_average_score", averageScore);

            for (EvaluationResult evaluation : evaluations) {
                if (evaluation == null || evaluation.metric() == null || evaluation.metric().isBlank()) {
                    continue;
                }
                String metricName = "eval_" + evaluation.metric().replaceAll("[^A-Za-z0-9_]", "_");
                metrics.put(metricName, evaluation.score());
            }
        }

        return metrics;
    }

    private static Map<String, String> buildMlflowParams(String question,
                                                         String answer,
                                                         List<Long> sources,
                                                         String goldenAnswer) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("golden_answer_provided", Boolean.toString(goldenAnswer != null && !goldenAnswer.isBlank()));
        params.put("question", question);
        params.put("answer", answer);
        if (sources != null && !sources.isEmpty()) {
            params.put("source_document_ids", sources.toString());
        }
        return params;
    }

    public static class ChatAnswer {
        private final String answer;
        private final List<Long> sources;
        private final List<EvaluationResult> evaluations;

        public ChatAnswer(String answer, List<Long> sources, List<EvaluationResult> evaluations) {
            this.answer = answer;
            this.sources = sources;
            this.evaluations = evaluations;
        }

        public String getAnswer() {
            return answer;
        }

        public List<Long> getSources() {
            return sources;
        }

        public List<EvaluationResult> getEvaluations() {
            return evaluations;
        }
    }
}
