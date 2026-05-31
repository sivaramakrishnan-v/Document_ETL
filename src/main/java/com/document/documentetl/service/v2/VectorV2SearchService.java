package com.document.documentetl.service.v2;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.RetrievalStrategy;
import com.document.documentetl.service.VertexAiEmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@Service("vector-v2")
public class VectorV2SearchService implements RetrievalStrategy {

    private static final String VECTOR_SEARCH_SQL = """
            SELECT chunk.chunk_text,
                   chunk.document_id,
                   (1 - (embedding.embedding <=> ?::vector)) AS similarity,
                   embedding.embedding::text AS embedding_text
            FROM document_etl.chunk_embeddings embedding
            JOIN document_etl.text_chunks chunk
              ON chunk.chunk_id = embedding.chunk_id
            JOIN document_etl.source_documents source
              ON source.document_id = chunk.document_id
             AND source.content_hash = chunk.content_hash
            WHERE embedding.model_provider = ?
              AND embedding.embedding_model = ?
              AND embedding.embedding_dimension = ?
              AND embedding.embedding_status = 'COMPLETED'
              AND source.status = 'COMPLETED'
            ORDER BY embedding.embedding <=> ?::vector
            LIMIT ?
            """;

    private final VertexAiEmbeddingService vertexAiEmbeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final String modelProvider;
    private final String embeddingModel;
    private final int embeddingDimension;

    public VectorV2SearchService(VertexAiEmbeddingService vertexAiEmbeddingService,
                                 JdbcTemplate jdbcTemplate,
                                 @Value("${app.etl.v2.embedding.provider}") String modelProvider,
                                 @Value("${app.etl.v2.embedding.model}") String embeddingModel,
                                 @Value("${app.etl.v2.embedding.dimension}") int embeddingDimension) {
        this.vertexAiEmbeddingService = vertexAiEmbeddingService;
        this.jdbcTemplate = jdbcTemplate;
        this.modelProvider = modelProvider;
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
    }

    @Override
    public List<SearchResult> retrieve(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }

        float[] queryEmbedding = vertexAiEmbeddingService.embed(query);
        if (queryEmbedding == null || queryEmbedding.length != embeddingDimension) {
            throw new IllegalStateException("Expected query embedding dimension " + embeddingDimension);
        }

        String queryVector = toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(
                VECTOR_SEARCH_SQL,
                (rs, rowNum) -> new SearchResult(
                        rs.getString("chunk_text"),
                        toDocumentId(rs.getObject("document_id")),
                        rs.getDouble("similarity"),
                        parseVectorLiteral(rs.getString("embedding_text"))
                ),
                queryVector,
                modelProvider,
                embeddingModel,
                embeddingDimension,
                queryVector,
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

    private static String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    private static float[] parseVectorLiteral(String vectorText) {
        if (vectorText == null || vectorText.isBlank()) {
            return new float[0];
        }
        String trimmed = vectorText.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
            return new float[0];
        }
        String body = trimmed.substring(1, trimmed.length() - 1);
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        List<Float> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            String numeric = part.trim();
            if (!numeric.isEmpty()) {
                values.add(Float.parseFloat(numeric));
            }
        }
        float[] parsed = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            parsed[i] = values.get(i);
        }
        return parsed;
    }
}
