package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@Service("vector")
public class VectorSearchService implements RetrievalStrategy {

    private static final String VECTOR_SEARCH_SQL = """
            SELECT chunk.chunk_id::text AS chunk_id,
                   chunk.chunk_text,
                   chunk.document_id,
                   chunk.chunk_index,
                   (1 - (embedding.embedding <=> ?::vector)) AS similarity,
                   embedding.embedding::text AS embedding_text
            FROM document_etl.text_chunks chunk
            JOIN document_etl.chunk_embeddings embedding
              ON embedding.chunk_id = chunk.chunk_id
             AND embedding.content_hash = chunk.content_hash
             AND embedding.embedding_status = 'COMPLETED'
            JOIN document_etl.source_documents source
              ON source.document_id = chunk.document_id
             AND source.content_hash = chunk.content_hash
             AND source.status = 'COMPLETED'
            ORDER BY embedding.embedding <=> ?::vector
            LIMIT ?
            """;

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    public VectorSearchService(EmbeddingModel embeddingModel, JdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SearchResult> retrieve(String query, int limit) {
        return retrieveCandidates(query, limit).stream()
                .map(candidate -> new SearchResult(
                        candidate.getChunkText(),
                        candidate.getDocumentId(),
                        candidate.getQuerySimilarity(),
                        candidate.getEmbedding()
                ))
                .toList();
    }

    public List<CandidateChunk> retrieveCandidates(String query, int limit) {
        float[] queryEmbedding = embedQuery(query);
        return retrieveCandidates(queryEmbedding, limit);
    }

    public List<CandidateChunk> retrieveCandidates(float[] queryEmbedding, int limit) {
        validateLimit(limit);
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            throw new IllegalStateException("Query embedding vector must not be empty");
        }

        String queryVector = toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(
                VECTOR_SEARCH_SQL,
                (rs, rowNum) -> new CandidateChunk(
                        rs.getString("chunk_id"),
                        rs.getString("chunk_text"),
                        toDocumentId(rs.getObject("document_id")),
                        rs.getInt("chunk_index"),
                        rs.getDouble("similarity"),
                        parseVectorLiteral(rs.getString("embedding_text"))
                ),
                queryVector,
                queryVector,
                limit
        );
    }

    public float[] embedQuery(String query) {
        validateQuery(query);
        Response<Embedding> embeddingResponse = embeddingModel.embed(query);
        Embedding embedding = embeddingResponse.content();
        float[] vector = embedding.vector();
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("Embedding vector must not be empty");
        }
        return vector;
    }

    private static void validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
    }

    private static void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
    }

    private static Long toDocumentId(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(rawValue.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("document_id is not numeric: " + rawValue, ex);
        }
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

    private static String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    public static class CandidateChunk {
        private final String chunkId;
        private final String chunkText;
        private final Long documentId;
        private final int chunkIndex;
        private final double querySimilarity;
        private final float[] embedding;

        public CandidateChunk(String chunkId,
                              String chunkText,
                              Long documentId,
                              int chunkIndex,
                              double querySimilarity,
                              float[] embedding) {
            this.chunkId = chunkId;
            this.chunkText = chunkText;
            this.documentId = documentId;
            this.chunkIndex = chunkIndex;
            this.querySimilarity = querySimilarity;
            this.embedding = embedding;
        }

        public String getChunkId() {
            return chunkId;
        }

        public String getChunkText() {
            return chunkText;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public double getQuerySimilarity() {
            return querySimilarity;
        }

        public float[] getEmbedding() {
            return embedding;
        }
    }
}
