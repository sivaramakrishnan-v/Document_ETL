package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;

import java.util.List;

public interface RetrievalStrategy {

    List<SearchResult> retrieve(String query, int limit);
}
