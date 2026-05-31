package com.document.documentetl.service.agent.retrieval;

import com.document.documentetl.dto.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FinishRetrievalTool {

    public boolean shouldFinish(List<SearchResult> aggregatedResults, int maxEvidence) {
        return aggregatedResults != null && aggregatedResults.size() >= maxEvidence;
    }
}

