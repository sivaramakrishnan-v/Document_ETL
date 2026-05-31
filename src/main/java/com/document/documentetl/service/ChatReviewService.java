package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.RerankerRetrievalService.RerankerScoreDetail;
import com.document.documentetl.service.evaluation.EvaluationResult;
import com.document.documentetl.service.evaluation.EvaluationService;
import dev.langchain4j.model.input.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ChatReviewService {

    private static final String EMPTY_CONTEXT = "No relevant context available.";

    private final VectorSearchService vectorSearchService;
    private final RerankerRetrievalService rerankerRetrievalService;
    private final GenerationModelGateway generationModelGateway;
    private final EvaluationService evaluationService;
    private final MlflowActionTrackingService mlflowActionTrackingService;
    private final MlflowTraceBridgeService mlflowTraceBridgeService;

    public ChatReviewService(VectorSearchService vectorSearchService,
                             RerankerRetrievalService rerankerRetrievalService,
                             GenerationModelGateway generationModelGateway,
                             EvaluationService evaluationService,
                             MlflowActionTrackingService mlflowActionTrackingService,
                             MlflowTraceBridgeService mlflowTraceBridgeService) {
        this.vectorSearchService = vectorSearchService;
        this.rerankerRetrievalService = rerankerRetrievalService;
        this.generationModelGateway = generationModelGateway;
        this.evaluationService = evaluationService;
        this.mlflowActionTrackingService = mlflowActionTrackingService;
        this.mlflowTraceBridgeService = mlflowTraceBridgeService;
    }

    public ChatReviewAnswer askWithTrace(String question,
                                         String goldenAnswer,
                                         int topK,
                                         int candidateK,
                                         double lambda) {
        validateQuestion(question);
        int normalizedTopK = Math.max(1, topK);
        int normalizedCandidateK = Math.max(normalizedTopK, candidateK);
        double boundedLambda = Math.max(0.0, Math.min(1.0, lambda));
        int mmrOutputLimit = Math.max(normalizedTopK, Math.min(normalizedCandidateK, normalizedTopK * 2));

        long startedAtNanos = System.nanoTime();
        try {
            List<SearchResult> vectorCandidates = vectorSearchService.retrieve(question, normalizedCandidateK);
            List<MmrStep> mmrTrace = buildMmrTrace(vectorCandidates, mmrOutputLimit, boundedLambda);
            List<SearchResult> mmrSelected = mmrTrace.stream()
                    .map(MmrStep::getResult)
                    .toList();
            List<RerankerScoreDetail> rerankerTrace =
                    rerankerRetrievalService.rerankWithDetails(question, mmrSelected, normalizedTopK);

            List<SearchResult> finalChunks = rerankerTrace.stream()
                    .map(RerankerScoreDetail::getResult)
                    .toList();
            String context = buildContext(finalChunks);
            String prompt = PromptTemplate.from(ChatPrompts.ANSWER_FROM_CONTEXT_TEMPLATE)
                    .apply(Map.of(
                            "context", context,
                            "question", question
                    ))
                    .text();
            String answer = generationModelGateway.generate(prompt, "chat.review.answer");
            List<Long> sources = extractSources(finalChunks);
            List<EvaluationResult> evaluations = evaluationService.evaluateAndLog(question, context, answer, goldenAnswer);

            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            String runId = mlflowActionTrackingService.logActionSuccessAndGetRunId(
                    "chat.ask.review",
                    durationMs,
                    buildMlflowMetrics(question, answer, vectorCandidates, mmrSelected, finalChunks, evaluations,
                            normalizedTopK, normalizedCandidateK, boundedLambda),
                    Map.of(
                            "top_k", Integer.toString(normalizedTopK),
                            "candidate_k", Integer.toString(normalizedCandidateK),
                            "mmr_lambda", Double.toString(boundedLambda),
                            "golden_answer_provided", Boolean.toString(goldenAnswer != null && !goldenAnswer.isBlank())
                    )
            );
            mlflowTraceBridgeService.emitChatTrace(runId, question, answer, finalChunks);

            return new ChatReviewAnswer(
                    answer,
                    sources,
                    evaluations,
                    normalizedTopK,
                    normalizedCandidateK,
                    boundedLambda,
                    vectorCandidates,
                    mmrTrace,
                    rerankerTrace
            );
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            mlflowActionTrackingService.logActionFailure(
                    "chat.ask.review",
                    durationMs,
                    e,
                    Map.of(
                            "top_k", Integer.toString(normalizedTopK),
                            "candidate_k", Integer.toString(normalizedCandidateK),
                            "mmr_lambda", Double.toString(boundedLambda),
                            "golden_answer_provided", Boolean.toString(goldenAnswer != null && !goldenAnswer.isBlank())
                    )
            );
            throw e;
        }
    }

    private static List<MmrStep> buildMmrTrace(List<SearchResult> candidates, int outputLimit, double lambda) {
        if (candidates == null || candidates.isEmpty() || outputLimit <= 0) {
            return List.of();
        }

        int boundedOutputLimit = Math.min(outputLimit, candidates.size());
        List<SearchResult> remaining = new ArrayList<>(candidates);
        List<SearchResult> selected = new ArrayList<>(boundedOutputLimit);
        List<MmrStep> trace = new ArrayList<>(boundedOutputLimit);

        SearchResult first = remaining.stream()
                .max(Comparator.comparingDouble(SearchResult::getSimilarity))
                .orElse(null);
        if (first == null) {
            return List.of();
        }

        selected.add(first);
        remaining.remove(first);
        trace.add(new MmrStep(first, lambda * first.getSimilarity(), 0.0));

        while (selected.size() < boundedOutputLimit && !remaining.isEmpty()) {
            SearchResult bestCandidate = null;
            double bestMmrScore = Double.NEGATIVE_INFINITY;
            double bestMaxSimilarityToSelected = 0.0;

            for (SearchResult candidate : remaining) {
                double maxSimilarityToSelected = maxSimilarityToSelected(candidate, selected);
                double mmrScore = (lambda * candidate.getSimilarity())
                        - ((1.0 - lambda) * maxSimilarityToSelected);
                if (mmrScore > bestMmrScore) {
                    bestMmrScore = mmrScore;
                    bestCandidate = candidate;
                    bestMaxSimilarityToSelected = maxSimilarityToSelected;
                }
            }

            if (bestCandidate == null) {
                break;
            }
            selected.add(bestCandidate);
            remaining.remove(bestCandidate);
            trace.add(new MmrStep(bestCandidate, bestMmrScore, bestMaxSimilarityToSelected));
        }

        return trace;
    }

    private static double maxSimilarityToSelected(SearchResult candidate, List<SearchResult> selected) {
        float[] candidateEmbedding = candidate.getEmbedding();
        if (candidateEmbedding == null || candidateEmbedding.length == 0 || selected == null || selected.isEmpty()) {
            return 0.0;
        }

        double maxSimilarity = 0.0;
        for (SearchResult chosen : selected) {
            float[] chosenEmbedding = chosen.getEmbedding();
            if (chosenEmbedding == null || chosenEmbedding.length == 0) {
                continue;
            }
            maxSimilarity = Math.max(maxSimilarity, cosineSimilarity(candidateEmbedding, chosenEmbedding));
        }
        return maxSimilarity;
    }

    private static double cosineSimilarity(float[] left, float[] right) {
        int len = Math.min(left.length, right.length);
        if (len == 0) {
            return 0.0;
        }

        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < len; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
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

    private static void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
    }

    private static Map<String, Double> buildMlflowMetrics(String question,
                                                           String answer,
                                                           List<SearchResult> vectorCandidates,
                                                           List<SearchResult> mmrSelected,
                                                           List<SearchResult> rerankedResults,
                                                           List<EvaluationResult> evaluations,
                                                           int topK,
                                                           int candidateK,
                                                           double lambda) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("question_length_chars", (double) question.length());
        metrics.put("answer_length_chars", (double) answer.length());
        metrics.put("vector_candidates_count", (double) (vectorCandidates != null ? vectorCandidates.size() : 0));
        metrics.put("mmr_selected_count", (double) (mmrSelected != null ? mmrSelected.size() : 0));
        metrics.put("reranked_count", (double) (rerankedResults != null ? rerankedResults.size() : 0));
        metrics.put("top_k", (double) topK);
        metrics.put("candidate_k", (double) candidateK);
        metrics.put("mmr_lambda", lambda);

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

    public static class ChatReviewAnswer {
        private final String answer;
        private final List<Long> sources;
        private final List<EvaluationResult> evaluations;
        private final int topK;
        private final int candidateK;
        private final double mmrLambda;
        private final List<SearchResult> vectorTopK;
        private final List<MmrStep> mmrOutput;
        private final List<RerankerScoreDetail> rerankerOutput;

        public ChatReviewAnswer(String answer,
                                List<Long> sources,
                                List<EvaluationResult> evaluations,
                                int topK,
                                int candidateK,
                                double mmrLambda,
                                List<SearchResult> vectorTopK,
                                List<MmrStep> mmrOutput,
                                List<RerankerScoreDetail> rerankerOutput) {
            this.answer = answer;
            this.sources = sources;
            this.evaluations = evaluations;
            this.topK = topK;
            this.candidateK = candidateK;
            this.mmrLambda = mmrLambda;
            this.vectorTopK = vectorTopK;
            this.mmrOutput = mmrOutput;
            this.rerankerOutput = rerankerOutput;
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

        public int getTopK() {
            return topK;
        }

        public int getCandidateK() {
            return candidateK;
        }

        public double getMmrLambda() {
            return mmrLambda;
        }

        public List<SearchResult> getVectorTopK() {
            return vectorTopK;
        }

        public List<MmrStep> getMmrOutput() {
            return mmrOutput;
        }

        public List<RerankerScoreDetail> getRerankerOutput() {
            return rerankerOutput;
        }
    }

    public static class MmrStep {
        private final SearchResult result;
        private final double mmrScore;
        private final double maxSimilarityToSelected;

        public MmrStep(SearchResult result, double mmrScore, double maxSimilarityToSelected) {
            this.result = result;
            this.mmrScore = mmrScore;
            this.maxSimilarityToSelected = maxSimilarityToSelected;
        }

        public SearchResult getResult() {
            return result;
        }

        public double getMmrScore() {
            return mmrScore;
        }

        public double getMaxSimilarityToSelected() {
            return maxSimilarityToSelected;
        }
    }
}
