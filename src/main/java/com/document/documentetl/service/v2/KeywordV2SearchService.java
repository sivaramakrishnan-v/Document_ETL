package com.document.documentetl.service.v2;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.RetrievalStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("keyword-v2")
public class KeywordV2SearchService implements RetrievalStrategy {

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
        return jdbcTemplate.query(
                KEYWORD_SEARCH_SQL,
                (rs, rowNum) -> new SearchResult(
                        rs.getString("chunk_text"),
                        toDocumentId(rs.getObject("document_id")),
                        rs.getDouble("keyword_score")
                ),
                query,
                query,
                limit
        );
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
}
