package com.document.documentetl.service;

import com.document.documentetl.dto.AgenticAskResponse;
import com.document.documentetl.service.agent.RagAgentState;
import com.document.documentetl.service.agent.RagStateGraphFactory;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AgenticRagService {

    private final CompiledGraph<RagAgentState> graph;

    public AgenticRagService(RagStateGraphFactory ragStateGraphFactory) {
        try {
            this.graph = ragStateGraphFactory.build();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to compile agentic RAG graph.", ex);
        }
    }

    public AgenticAskResponse ask(String question, String threadId) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put(RagAgentState.USER_QUERY, question);
        if (threadId != null && !threadId.isBlank()) {
            input.put(RagAgentState.THREAD_ID, threadId);
        }

        RunnableConfig config = (threadId == null || threadId.isBlank())
                ? RunnableConfig.empty()
                : RunnableConfig.builder().threadId(threadId).build();

        Optional<RagAgentState> result = graph.invoke(input, config);
        RagAgentState state = result.orElseThrow(() -> new IllegalStateException("Graph execution returned no state."));

        return new AgenticAskResponse(
                state.threadId().orElse(""),
                state.finalAnswer().orElse(""),
                state.contextGrade().orElse(""),
                state.validationOutcome().orElse(""),
                state.retrievalAttempts(),
                state.rewriteAttempts(),
                state.answerRevisionAttempts(),
                state.citations(),
                state.visited(),
                state.feedback()
        );
    }
}
