package com.document.documentetl.service.v2;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.RetrievalStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service("keyword-v2")
public class KeywordV2SearchService implements RetrievalStrategy {

    private static final Logger log = LoggerFactory.getLogger(KeywordV2SearchService.class);

    private static final String KEYWORD_SEARCH_SQL = """
            SELECT chunk.chunk_text,
                   chunk.document_id,
                   ts_rank_cd(
                       to_tsvector('english', COALESCE(chunk.chunk_text, '')),
                       plainto_tsquery('english', ?)
                   ) AS keyword_score
            FROM document_etl.text_chunks chunk
            JOIN document_etl.source_documents source
              ON source.document_id = chunk.document_id
             AND source.content_hash = chunk.content_hash
            WHERE source.status = 'COMPLETED'
              AND to_tsvector('english', COALESCE(chunk.chunk_text, ''))
                  @@ plainto_tsquery('english', ?)
            ORDER BY keyword_score DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public KeywordV2SearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SearchResult> retrieve(String query, int limit) {
        return retrieve(query, limit, null);
    }

    @Override
    public List<SearchResult> retrieve(String query, int limit, List<Long> documentIds) {
        List<Long> scope = normalizeDocumentIds(documentIds);
        String sql = scope.isEmpty() ? KEYWORD_SEARCH_SQL : scopedKeywordSearchSql(scope.size());
        List<Object> args = new ArrayList<>();
        args.add(query);
        args.add(query);
        args.addAll(scope);
        args.add(limit);

        List<SearchResult> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SearchResult(
                        rs.getString("chunk_text"),
                        toDocumentId(rs.getObject("document_id")),
                        normalizeKeywordScore(rs.getDouble("keyword_score"))
                ),
                args.toArray()
        );
        log.info("Keyword retrieval completed: documentScope={} resultCount={}", scope, results.size());
        return results;
    }

    private static Long toDocumentId(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(rawValue.toString());
    }

    private static double normalizeKeywordScore(double keywordScore) {
        return Math.min(1.0d, 0.75d + Math.max(0.0d, keywordScore));
    }

    private static List<Long> normalizeDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        return documentIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private static String scopedKeywordSearchSql(int scopeSize) {
        return """
                SELECT chunk.chunk_text,
                       chunk.document_id,
                       ts_rank_cd(
                           to_tsvector('english', COALESCE(chunk.chunk_text, '')),
                           plainto_tsquery('english', ?)
                       ) AS keyword_score
                FROM document_etl.text_chunks chunk
                JOIN document_etl.source_documents source
                  ON source.document_id = chunk.document_id
                 AND source.content_hash = chunk.content_hash
                WHERE source.status = 'COMPLETED'
                  AND to_tsvector('english', COALESCE(chunk.chunk_text, ''))
                      @@ plainto_tsquery('english', ?)
                  AND chunk.document_id IN (%s)
                ORDER BY keyword_score DESC
                LIMIT ?
                """.formatted(placeholders(scopeSize));
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
