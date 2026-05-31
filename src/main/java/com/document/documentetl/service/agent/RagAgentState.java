package com.document.documentetl.service.agent;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RagAgentState extends AgentState {

    public static final String THREAD_ID = "threadId";
    public static final String USER_QUERY = "userQuery";
    public static final String NORMALIZED_QUERY = "normalizedQuery";
    public static final String REWRITTEN_QUERY = "rewrittenQuery";
    public static final String PLAN = "plan";
    public static final String SEARCH_QUERIES = "searchQueries";
    public static final String RETRIEVED_CHUNKS = "retrievedChunks";
    public static final String SELECTED_EVIDENCE = "selectedEvidence";
    public static final String CONTEXT_GRADE = "contextGrade";
    public static final String VALIDATION_OUTCOME = "validationOutcome";
    public static final String RETRIEVAL_ATTEMPTS = "retrievalAttempts";
    public static final String REWRITE_ATTEMPTS = "rewriteAttempts";
    public static final String ANSWER_REVISION_ATTEMPTS = "answerRevisionAttempts";
    public static final String VISITED = "visited";
    public static final String FEEDBACK = "feedback";
    public static final String FINAL_ANSWER = "finalAnswer";
    public static final String CITATIONS = "citations";

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(THREAD_ID, Channels.base(() -> "")),
            Map.entry(USER_QUERY, Channels.base(() -> "")),
            Map.entry(NORMALIZED_QUERY, Channels.base(() -> "")),
            Map.entry(REWRITTEN_QUERY, Channels.base(() -> "")),
            Map.entry(PLAN, Channels.base(ArrayList::new)),
            Map.entry(SEARCH_QUERIES, Channels.base(ArrayList::new)),
            Map.entry(RETRIEVED_CHUNKS, Channels.base(ArrayList::new)),
            Map.entry(SELECTED_EVIDENCE, Channels.base(ArrayList::new)),
            Map.entry(CONTEXT_GRADE, Channels.base(() -> "")),
            Map.entry(VALIDATION_OUTCOME, Channels.base(() -> "")),
            Map.entry(RETRIEVAL_ATTEMPTS, Channels.base(() -> 0)),
            Map.entry(REWRITE_ATTEMPTS, Channels.base(() -> 0)),
            Map.entry(ANSWER_REVISION_ATTEMPTS, Channels.base(() -> 0)),
            Map.entry(VISITED, Channels.appender(ArrayList::new)),
            Map.entry(FEEDBACK, Channels.appender(ArrayList::new)),
            Map.entry(FINAL_ANSWER, Channels.base(() -> "")),
            Map.entry(CITATIONS, Channels.base(ArrayList::new))
    );

    public RagAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> userQuery() {
        return this.value(USER_QUERY);
    }

    public Optional<String> threadId() {
        return this.value(THREAD_ID);
    }

    public Optional<String> normalizedQuery() {
        return this.value(NORMALIZED_QUERY);
    }

    public Optional<String> rewrittenQuery() {
        return this.value(REWRITTEN_QUERY);
    }

    public List<String> plan() {
        return this.<List<String>>value(PLAN).orElse(List.of());
    }

    public List<String> searchQueries() {
        return this.<List<String>>value(SEARCH_QUERIES).orElse(List.of());
    }

    public List<String> retrievedChunks() {
        return this.<List<String>>value(RETRIEVED_CHUNKS).orElse(List.of());
    }

    public List<String> selectedEvidence() {
        return this.<List<String>>value(SELECTED_EVIDENCE).orElse(List.of());
    }

    public Optional<String> contextGrade() {
        return this.value(CONTEXT_GRADE);
    }

    public Optional<String> validationOutcome() {
        return this.value(VALIDATION_OUTCOME);
    }

    public int retrievalAttempts() {
        return this.<Integer>value(RETRIEVAL_ATTEMPTS).orElse(0);
    }

    public int rewriteAttempts() {
        return this.<Integer>value(REWRITE_ATTEMPTS).orElse(0);
    }

    public int answerRevisionAttempts() {
        return this.<Integer>value(ANSWER_REVISION_ATTEMPTS).orElse(0);
    }

    public List<String> visited() {
        return this.<List<String>>value(VISITED).orElse(List.of());
    }

    public List<String> feedback() {
        return this.<List<String>>value(FEEDBACK).orElse(List.of());
    }

    public Optional<String> finalAnswer() {
        return this.value(FINAL_ANSWER);
    }

    public List<String> citations() {
        return this.<List<String>>value(CITATIONS).orElse(List.of());
    }
}
