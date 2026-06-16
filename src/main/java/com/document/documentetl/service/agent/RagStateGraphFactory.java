package com.document.documentetl.service.agent;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.GenerationModelGateway;
import com.document.documentetl.service.MlflowActionTrackingService;
import com.document.documentetl.service.RagWorkflowCheckpointService;
import com.document.documentetl.service.agent.retrieval.FinishRetrievalTool;
import com.document.documentetl.service.agent.retrieval.RetrievalTool;
import com.document.documentetl.service.agent.retrieval.RetrievalToolRegistry;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class RagStateGraphFactory {
    private static final Logger log = LoggerFactory.getLogger(RagStateGraphFactory.class);
    private static final Pattern DOC_ID_PATTERN = Pattern.compile("doc=(\\d+)");
    private static final Pattern CHUNK_ID_PATTERN = Pattern.compile("chunk=([^\\s]+)");

    private static final String NODE_NORMALIZE_QUERY = "normalize_query";
    private static final String NODE_QUERY_PLANNER = "query_planner";
    private static final String NODE_RETRIEVAL_AGENT = "retrieval_agent";
    private static final String NODE_CONTEXT_GRADER = "context_grader";
    private static final String NODE_REWRITE_QUERY = "rewrite_query";
    private static final String NODE_ANSWER_GENERATOR = "answer_generator";
    private static final String NODE_ANSWER_VALIDATOR = "answer_validator";
    private static final String NODE_INSUFFICIENT_EVIDENCE = "insufficient_evidence_answer";
    private static final String NODE_PERSIST_TRACE = "persist_trace";

    private static final String ROUTE_SUFFICIENT = "SUFFICIENT";
    private static final String ROUTE_RETRY = "RETRY";
    private static final String ROUTE_NO_EVIDENCE = "NO_EVIDENCE";
    private static final String ROUTE_GROUNDED = "GROUNDED";
    private static final String ROUTE_REVISE = "REVISE";

    private static final int MAX_TOOL_CALLS = 3;
    private static final int MAX_EVIDENCE = 8;
    private static final int MAX_RETRIEVAL_ATTEMPTS = 2;

    private final RetrievalToolRegistry retrievalToolRegistry;
    private final FinishRetrievalTool finishRetrievalTool;
    private final GenerationModelGateway generationModelGateway;
    private final MlflowActionTrackingService mlflowActionTrackingService;
    private final RagWorkflowCheckpointService checkpointService;

    public RagStateGraphFactory(RetrievalToolRegistry retrievalToolRegistry,
                                FinishRetrievalTool finishRetrievalTool,
                                GenerationModelGateway generationModelGateway,
                                MlflowActionTrackingService mlflowActionTrackingService,
                                RagWorkflowCheckpointService checkpointService) {
        this.retrievalToolRegistry = retrievalToolRegistry;
        this.finishRetrievalTool = finishRetrievalTool;
        this.generationModelGateway = generationModelGateway;
        this.mlflowActionTrackingService = mlflowActionTrackingService;
        this.checkpointService = checkpointService;
    }

    public CompiledGraph<RagAgentState> build() throws GraphStateException {
        return new StateGraph<>(RagAgentState.SCHEMA, RagAgentState::new)
                .addNode(NODE_NORMALIZE_QUERY, node_async(new NormalizeQueryNode()))
                .addNode(NODE_QUERY_PLANNER, node_async(new QueryPlannerNode()))
                .addNode(NODE_RETRIEVAL_AGENT, node_async(new RetrievalAgentNode()))
                .addNode(NODE_CONTEXT_GRADER, node_async(new ContextGraderNode()))
                .addNode(NODE_REWRITE_QUERY, node_async(new RewriteQueryNode()))
                .addNode(NODE_ANSWER_GENERATOR, node_async(new AnswerGeneratorNode()))
                .addNode(NODE_ANSWER_VALIDATOR, node_async(new AnswerValidatorNode()))
                .addNode(NODE_INSUFFICIENT_EVIDENCE, node_async(new InsufficientEvidenceNode()))
                .addNode(NODE_PERSIST_TRACE, node_async(new PersistTraceNode()))
                .addEdge(START, NODE_NORMALIZE_QUERY)
                .addConditionalEdges(
                        NODE_NORMALIZE_QUERY,
                        edge_async(new NormalizeRouter()),
                        Map.of(
                                ROUTE_SUFFICIENT, NODE_QUERY_PLANNER,
                                ROUTE_NO_EVIDENCE, NODE_INSUFFICIENT_EVIDENCE
                        )
                )
                .addEdge(NODE_QUERY_PLANNER, NODE_RETRIEVAL_AGENT)
                .addEdge(NODE_RETRIEVAL_AGENT, NODE_CONTEXT_GRADER)
                .addConditionalEdges(
                        NODE_CONTEXT_GRADER,
                        edge_async(new ContextGradeRouter()),
                        Map.of(
                                ROUTE_SUFFICIENT, NODE_ANSWER_GENERATOR,
                                ROUTE_RETRY, NODE_REWRITE_QUERY,
                                ROUTE_NO_EVIDENCE, NODE_INSUFFICIENT_EVIDENCE
                        )
                )
                .addEdge(NODE_REWRITE_QUERY, NODE_RETRIEVAL_AGENT)
                .addEdge(NODE_ANSWER_GENERATOR, NODE_ANSWER_VALIDATOR)
                .addConditionalEdges(
                        NODE_ANSWER_VALIDATOR,
                        edge_async(new ValidationRouter()),
                        Map.of(
                                ROUTE_GROUNDED, NODE_PERSIST_TRACE,
                                ROUTE_REVISE, NODE_ANSWER_GENERATOR
                        )
                )
                .addEdge(NODE_INSUFFICIENT_EVIDENCE, NODE_PERSIST_TRACE)
                .addEdge(NODE_PERSIST_TRACE, END)
                .compile();
    }

    private class NormalizeQueryNode implements NodeAction<RagAgentState> {
        @Override
        public Map<String, Object> apply(RagAgentState state) {
            String query = state.userQuery().orElse("");
            String normalized = query.trim().replaceAll("\\s+", " ");
            String existingThreadId = state.threadId().orElse("");
            String rewritten = state.rewrittenQuery().orElse("");
            safeCheckpointUpdate(
                    state,
                    checkpointId -> checkpointService.markPlanningCompleted(checkpointId, normalized, rewritten)
            );

            return Map.of(
                    RagAgentState.THREAD_ID, existingThreadId.isBlank() ? UUID.randomUUID().toString() : existingThreadId,
                    RagAgentState.NORMALIZED_QUERY, normalized,
                    RagAgentState.RETRIEVAL_ATTEMPTS, 0,
                    RagAgentState.REWRITE_ATTEMPTS, 0,
                    RagAgentState.ANSWER_REVISION_ATTEMPTS, 0,
                    RagAgentState.VISITED, NODE_NORMALIZE_QUERY,
                    RagAgentState.FEEDBACK, "Normalized query and initialized counters."
            );
        }
    }

    private class QueryPlannerNode implements NodeAction<RagAgentState> {
        @Override
        public Map<String, Object> apply(RagAgentState state) {
            String baseQuery = state.rewrittenQuery()
                    .filter(s -> !s.isBlank())
                    .orElse(state.normalizedQuery().orElse(""));

            String prompt = """
                    You are a retrieval planner for RAG.
                    Given a user query, produce 3 concise search queries.
                    Return exactly 3 lines and no extra text.

                    User query:
                    %s
                    """.formatted(baseQuery);

            List<String> searchQueries = parseSearchQueries(
                    safeGenerate(prompt, "agent.query_planner"),
                    baseQuery
            );

            return Map.of(
                    RagAgentState.PLAN, List.of("retrieve", "grade", "generate", "validate"),
                    RagAgentState.SEARCH_QUERIES, searchQueries,
                    RagAgentState.VISITED, NODE_QUERY_PLANNER,
                    RagAgentState.FEEDBACK, "Planner prepared " + searchQueries.size() + " search queries."
            );
        }
    }

    private class RetrievalAgentNode implements NodeAction<RagAgentState> {
        @Override
        public Map<String, Object> apply(RagAgentState state) {
            int attempts = state.retrievalAttempts() + 1;
            String query = state.rewrittenQuery()
                    .filter(s -> !s.isBlank())
                    .orElse(state.normalizedQuery().orElse(""));
            List<String> queryPlan = state.searchQueries().isEmpty() ? List.of(query) : state.searchQueries();
            int perToolLimit = 5;

            List<String> plannedTools = (attempts == 1)
                    ? List.of("vector_search", "hybrid_search", "rerank_results")
                    : List.of("keyword_search", "mmr_search", "rerank_results");

            Map<String, SearchResult> merged = new LinkedHashMap<>();
            List<String> toolTrace = new ArrayList<>();

            for (int i = 0; i < plannedTools.size() && i < MAX_TOOL_CALLS; i++) {
                String toolName = plannedTools.get(i);
                RetrievalTool tool = retrievalToolRegistry.find(toolName).orElse(null);
                if (tool == null) {
                    toolTrace.add(toolName + "(missing)");
                    continue;
                }
                if (finishRetrievalTool.shouldFinish(new ArrayList<>(merged.values()), MAX_EVIDENCE)) {
                    toolTrace.add("finish_retrieval");
                    break;
                }

                String toolQuery = queryPlan.get(Math.min(i, queryPlan.size() - 1));
                List<SearchResult> results;
                try {
                    results = tool.execute(toolQuery, perToolLimit);
                } catch (RuntimeException ex) {
                    toolTrace.add(toolName + "(error)");
                    continue;
                }

                toolTrace.add(toolName + "(n=" + results.size() + ")");
                for (SearchResult result : results) {
                    String key = buildResultKey(result);
                    SearchResult existing = merged.get(key);
                    if (existing == null || result.getSimilarity() > existing.getSimilarity()) {
                        merged.put(key, result);
                    }
                }
            }

            List<SearchResult> selected = merged.values().stream()
                    .sorted(Comparator.comparingDouble(SearchResult::getSimilarity).reversed())
                    .limit(MAX_EVIDENCE)
                    .toList();

            List<String> evidence = new ArrayList<>();
            for (SearchResult result : selected) {
                evidence.add(toEvidence(result));
            }

            String strategy = "tools:" + String.join(",", plannedTools);
            safeCheckpointUpdate(state, checkpointId -> checkpointService.markRetrievalCompleted(
                    checkpointId,
                    strategy,
                    extractDocumentIds(evidence),
                    extractChunkIds(evidence),
                    evidence
            ));

            return Map.of(
                    RagAgentState.RETRIEVAL_ATTEMPTS, attempts,
                    RagAgentState.RETRIEVED_CHUNKS, evidence,
                    RagAgentState.SELECTED_EVIDENCE, evidence,
                    RagAgentState.VISITED, NODE_RETRIEVAL_AGENT,
                    RagAgentState.FEEDBACK,
                    "Retrieval attempt " + attempts + " tools=" + String.join(" -> ", toolTrace) + " selected=" + evidence.size()
            );
        }

        private static String buildResultKey(SearchResult result) {
            return result.getDocumentId() + "::" + result.getText();
        }

        private static String toEvidence(SearchResult result) {
            String snippet = result.getText() == null ? "" : result.getText().replaceAll("\\s+", " ").trim();
            if (snippet.length() > 600) {
                snippet = snippet.substring(0, 600) + "...";
            }
            return "doc=" + result.getDocumentId() + " score=" + String.format(Locale.ROOT, "%.4f", result.getSimilarity()) + " text=" + snippet;
        }
    }

    private class ContextGraderNode implements NodeAction<RagAgentState> {
        @Override
        public Map<String, Object> apply(RagAgentState state) {
            String grade = ROUTE_RETRY;
            List<String> evidence = state.selectedEvidence();
            int attempts = state.retrievalAttempts();

            if (evidence.isEmpty()) {
                grade = attempts >= MAX_RETRIEVAL_ATTEMPTS ? ROUTE_NO_EVIDENCE : ROUTE_RETRY;
            } else {
                String prompt = """
                        You are grading retrieval evidence for RAG.
                        Decide if evidence is enough to answer the user question.
                        Return exactly one token: SUFFICIENT or RETRY or NO_EVIDENCE

                        Question:
                        %s

                        Evidence:
                        %s
                        """.formatted(
                        state.normalizedQuery().orElse(""),
                        String.join("\n", evidence)
                );
                grade = normalizeRoute(safeGenerate(prompt, "agent.context_grader"), ROUTE_RETRY);
                // Only allow NO_EVIDENCE when we actually have zero evidence.
                // If evidence exists, keep iterating until max attempts, then answer with available context.
                if (ROUTE_NO_EVIDENCE.equals(grade)) {
                    grade = attempts >= MAX_RETRIEVAL_ATTEMPTS ? ROUTE_SUFFICIENT : ROUTE_RETRY;
                } else if (ROUTE_RETRY.equals(grade) && attempts >= MAX_RETRIEVAL_ATTEMPTS) {
                    grade = ROUTE_SUFFICIENT;
                }
            }

            return Map.of(
                    RagAgentState.CONTEXT_GRADE, grade,
                    RagAgentState.VISITED, NODE_CONTEXT_GRADER,
                    RagAgentState.FEEDBACK, "Context grader selected route " + grade + "."
            );
        }
    }

    private class RewriteQueryNode implements NodeAction<RagAgentState> {
        @Override
        public Map<String, Object> apply(RagAgentState state) {
            int rewriteAttempts = state.rewriteAttempts() + 1;
            String prompt = """
                    Rewrite the query for better document retrieval.
                    Keep it to one line and preserve intent.

                    Original query:
                    %s
                    """.formatted(state.normalizedQuery().orElse(""));
            String rewritten = sanitizeSingleLine(safeGenerate(prompt, "agent.query_rewriter"));
            if (rewritten.isBlank()) {
                rewritten = state.normalizedQuery().orElse("") + " include definitions, constraints, and exceptions";
            }
            String normalized = state.normalizedQuery().orElse("");
            String rewrittenSnapshot = rewritten;
            safeCheckpointUpdate(
                    state,
                    checkpointId -> checkpointService.markPlanningCompleted(checkpointId, normalized, rewrittenSnapshot)
            );

            return Map.of(
                    RagAgentState.REWRITE_ATTEMPTS, rewriteAttempts,
                    RagAgentState.REWRITTEN_QUERY, rewritten,
                    RagAgentState.VISITED, NODE_REWRITE_QUERY,
                    RagAgentState.FEEDBACK, "Rewrite query attempt " + rewriteAttempts + " prepared."
            );
        }
    }

    private class AnswerGeneratorNode implements NodeAction<RagAgentState> {
        @Override
        public Map<String, Object> apply(RagAgentState state) {
            String evidence = String.join("\n", state.selectedEvidence());
            String prompt = """
                    Answer the user question using only the evidence below.
                    If the evidence contains relevant facts, synthesize a clear answer from those facts.
                    Say the evidence is insufficient only when the evidence has no relevant facts for the question.
                    Do not include inline citation markers, source IDs, or tokens like [doc=123] in the answer.
                    Use 2 to 4 short sentences unless the question needs a list.

                    Question:
                    %s

                    Evidence:
                    %s
                    """.formatted(state.normalizedQuery().orElse(""), evidence);

            String answer = sanitizeAnswer(safeGenerate(prompt, "agent.answer_generator"));
            if (answer.isBlank()) {
                answer = "I do not have enough evidence in the indexed documents to answer this confidently.";
            }
            String answerSnapshot = answer;
            List<String> citations = extractCitations(state.selectedEvidence());
            safeCheckpointUpdate(state, checkpointId -> checkpointService.markAnswerGenerated(checkpointId, answerSnapshot, citations));

            return Map.of(
                    RagAgentState.FINAL_ANSWER, answer,
                    RagAgentState.CITATIONS, citations,
                    RagAgentState.VISITED, NODE_ANSWER_GENERATOR,
                    RagAgentState.FEEDBACK, "Answer generator produced a draft answer."
            );
        }
    }

    private class AnswerValidatorNode implements NodeAction<RagAgentState> {
        @Override
        public Map<String, Object> apply(RagAgentState state) {
            String prompt = """
                    Validate whether the answer is grounded in the evidence.
                    Return exactly one token: GROUNDED or REVISE

                    Question:
                    %s

                    Answer:
                    %s

                    Evidence:
                    %s
                    """.formatted(
                    state.normalizedQuery().orElse(""),
                    state.finalAnswer().orElse(""),
                    String.join("\n", state.selectedEvidence())
            );
            String outcome = normalizeRoute(safeGenerate(prompt, "agent.answer_validator"), ROUTE_REVISE);
            int revisions = state.answerRevisionAttempts();
            safeCheckpointUpdate(state, checkpointId -> checkpointService.markValidationCompleted(checkpointId, outcome));

            return Map.of(
                    RagAgentState.VALIDATION_OUTCOME, outcome,
                    RagAgentState.ANSWER_REVISION_ATTEMPTS, ROUTE_REVISE.equals(outcome) ? revisions + 1 : revisions,
                    RagAgentState.VISITED, NODE_ANSWER_VALIDATOR,
                    RagAgentState.FEEDBACK, "Answer validator selected route " + outcome + "."
            );
        }
    }

    private static class InsufficientEvidenceNode implements NodeAction<RagAgentState> {
        @Override
        public Map<String, Object> apply(RagAgentState state) {
            return Map.of(
                    RagAgentState.FINAL_ANSWER, "I do not have enough evidence in the indexed documents to answer this confidently.",
                    RagAgentState.VISITED, NODE_INSUFFICIENT_EVIDENCE,
                    RagAgentState.FEEDBACK, "Insufficient evidence fallback applied."
            );
        }
    }

    private class PersistTraceNode implements NodeAction<RagAgentState> {
        @Override
        public Map<String, Object> apply(RagAgentState state) {
            Map<String, Double> metrics = Map.of(
                    "retrieval_attempts", (double) state.retrievalAttempts(),
                    "rewrite_attempts", (double) state.rewriteAttempts(),
                    "answer_revision_attempts", (double) state.answerRevisionAttempts(),
                    "evidence_count", (double) state.selectedEvidence().size()
            );

            Map<String, String> params = new LinkedHashMap<>();
            params.put("thread_id", state.threadId().orElse(""));
            params.put("context_grade", state.contextGrade().orElse(""));
            params.put("validation_outcome", state.validationOutcome().orElse(""));
            params.put("query", state.normalizedQuery().orElse(""));

            mlflowActionTrackingService.logActionSuccess(
                    "chat.agent.ask",
                    0L,
                    metrics,
                    params
            );
            safeCheckpointUpdate(state, checkpointService::markCompleted);

            return Map.of(
                    RagAgentState.VISITED, NODE_PERSIST_TRACE,
                    RagAgentState.FEEDBACK, "Persist trace completed."
            );
        }
    }

    private static class NormalizeRouter implements EdgeAction<RagAgentState> {
        @Override
        public String apply(RagAgentState state) {
            return state.normalizedQuery().orElse("").isBlank() ? ROUTE_NO_EVIDENCE : ROUTE_SUFFICIENT;
        }
    }

    private static class ContextGradeRouter implements EdgeAction<RagAgentState> {
        @Override
        public String apply(RagAgentState state) {
            return state.contextGrade().orElse(ROUTE_RETRY);
        }
    }

    private static class ValidationRouter implements EdgeAction<RagAgentState> {
        @Override
        public String apply(RagAgentState state) {
            String outcome = state.validationOutcome().orElse(ROUTE_REVISE);
            if (ROUTE_REVISE.equals(outcome) && state.answerRevisionAttempts() >= 1) {
                return ROUTE_GROUNDED;
            }
            return outcome;
        }
    }

    private String safeGenerate(String prompt, String operationName) {
        try {
            return generationModelGateway.generate(prompt, operationName);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static List<String> parseSearchQueries(String llmOutput, String fallback) {
        List<String> parsed = new ArrayList<>();
        if (llmOutput != null) {
            for (String line : llmOutput.split("\\R")) {
                String cleaned = line.replaceFirst("^[-*\\d.\\s]+", "").trim();
                if (!cleaned.isBlank()) {
                    parsed.add(cleaned);
                }
                if (parsed.size() == 3) {
                    break;
                }
            }
        }
        if (parsed.isEmpty()) {
            parsed = List.of(
                    fallback,
                    fallback + " key facts",
                    fallback + " eligibility and constraints"
            );
        }
        return parsed;
    }

    private static String normalizeRoute(String llmOutput, String defaultRoute) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return defaultRoute;
        }
        String token = sanitizeSingleLine(llmOutput).toUpperCase(Locale.ROOT);
        if (token.contains(ROUTE_SUFFICIENT)) {
            return ROUTE_SUFFICIENT;
        }
        if (token.contains(ROUTE_NO_EVIDENCE)) {
            return ROUTE_NO_EVIDENCE;
        }
        if (token.contains(ROUTE_RETRY)) {
            return ROUTE_RETRY;
        }
        if (token.contains(ROUTE_GROUNDED)) {
            return ROUTE_GROUNDED;
        }
        if (token.contains(ROUTE_REVISE)) {
            return ROUTE_REVISE;
        }
        return defaultRoute;
    }

    private static String sanitizeSingleLine(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", " ").trim();
    }

    private static String sanitizeAnswer(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("\r\n", "\n")
                .replaceAll("\\s*\\[doc=\\d+]", "")
                .replaceAll("\\s*\\(doc=\\d+\\)", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static List<String> extractCitations(List<String> evidence) {
        List<String> citations = new ArrayList<>();
        for (String entry : evidence) {
            int start = entry.indexOf("doc=");
            if (start < 0) {
                continue;
            }
            int end = entry.indexOf(" ", start);
            String token = end > start ? entry.substring(start, end) : entry.substring(start);
            citations.add("[" + token + "]");
        }
        return citations.stream().distinct().toList();
    }

    private static List<Long> extractDocumentIds(List<String> evidence) {
        List<Long> documentIds = new ArrayList<>();
        for (String entry : evidence) {
            Matcher matcher = DOC_ID_PATTERN.matcher(entry);
            if (matcher.find()) {
                documentIds.add(Long.parseLong(matcher.group(1)));
            }
        }
        return documentIds.stream().distinct().toList();
    }

    private static List<String> extractChunkIds(List<String> evidence) {
        List<String> chunkIds = new ArrayList<>();
        for (String entry : evidence) {
            Matcher matcher = CHUNK_ID_PATTERN.matcher(entry);
            if (matcher.find()) {
                chunkIds.add(matcher.group(1));
            }
        }
        return chunkIds.stream().distinct().toList();
    }

    private void safeCheckpointUpdate(RagAgentState state, java.util.function.Consumer<UUID> updater) {
        String checkpointIdRaw = state.checkpointId().orElse("");
        if (checkpointIdRaw.isBlank()) {
            return;
        }
        try {
            updater.accept(UUID.fromString(checkpointIdRaw));
        } catch (Exception ex) {
            log.warn("Checkpoint update failed for checkpointId={}: {}", checkpointIdRaw, ex.getMessage());
        }
    }
}
