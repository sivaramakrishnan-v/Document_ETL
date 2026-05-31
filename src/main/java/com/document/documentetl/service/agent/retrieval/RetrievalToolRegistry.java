package com.document.documentetl.service.agent.retrieval;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RetrievalToolRegistry {

    private final Map<String, RetrievalTool> toolsByName;

    public RetrievalToolRegistry(List<RetrievalTool> tools) {
        Map<String, RetrievalTool> indexed = new LinkedHashMap<>();
        for (RetrievalTool tool : tools) {
            indexed.put(tool.name(), tool);
        }
        this.toolsByName = Map.copyOf(indexed);
    }

    public Optional<RetrievalTool> find(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }
}

