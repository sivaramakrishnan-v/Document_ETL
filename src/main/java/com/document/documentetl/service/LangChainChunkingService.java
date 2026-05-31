package com.document.documentetl.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

@Service
public class LangChainChunkingService {

    private static final Logger log = LoggerFactory.getLogger(LangChainChunkingService.class);
    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;
    private static final int VECTOR_DIMENSION = 768;

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final JdbcTemplate jdbcTemplate;

    public LangChainChunkingService(ObjectProvider<EmbeddingModel> embeddingModelProvider, JdbcTemplate jdbcTemplate) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void process(Long documentId, String rawText, String contentHash) {
        validateInputs(documentId, rawText, contentHash);

        List<String> existingHashes = jdbcTemplate.query(
                "SELECT DISTINCT content_hash FROM knowledge.document_chunks WHERE document_id = ?",
                (rs, rowNum) -> rs.getString(1),
                documentId
        );

        boolean hasRows = !existingHashes.isEmpty();
        boolean isUpToDate = hasRows && existingHashes.stream().allMatch(existingHash -> Objects.equals(existingHash, contentHash));
        if (isUpToDate) {
            log.info("Up to date: documentId={}", documentId);
            return;
        }

        if (hasRows) {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM knowledge.document_chunks WHERE document_id = ?",
                    documentId
            );
            log.info("Deleted stale chunks: documentId={}, deleted={}", documentId, deleted);
        }

        DocumentSplitter recursiveSplitter = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);
        Metadata metadata = new Metadata().add("document_id", documentId);
        Document document = Document.from(rawText, metadata);

        List<TextSegment> splitSegments = recursiveSplitter.split(document);
        if (splitSegments.isEmpty()) {
            log.warn("No segments generated: documentId={}", documentId);
            return;
        }

        List<TextSegment> segments = attachDocumentIdMetadata(splitSegments, documentId);
        EmbeddingStore<TextSegment> chunkStore =
                new JdbcDocumentChunkEmbeddingStore(jdbcTemplate, documentId, contentHash);
        EmbeddingModel embeddingModel = requireEmbeddingModel();

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(ignored -> segments)
                .embeddingModel(embeddingModel)
                .embeddingStore(chunkStore)
                .build();

        ingestor.ingest(document);
        log.info("LangChain ingestion completed: documentId={}, chunksPersisted={}", documentId, segments.size());
    }

    private static List<TextSegment> attachDocumentIdMetadata(List<TextSegment> splitSegments, Long documentId) {
        List<TextSegment> segments = new ArrayList<>(splitSegments.size());
        for (TextSegment segment : splitSegments) {
            Metadata metadata = segment.metadata() == null ? new Metadata() : segment.metadata().copy();
            metadata.put("document_id", documentId);
            segments.add(TextSegment.from(segment.text(), metadata));
        }
        return segments;
    }

    private static void validateInputs(Long documentId, String rawText, String contentHash) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("rawText must not be blank");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
    }

    private EmbeddingModel requireEmbeddingModel() {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("No EmbeddingModel bean is available for LangChain ingestion");
        }
        return model;
    }

    private static final class JdbcDocumentChunkEmbeddingStore implements EmbeddingStore<TextSegment> {

        private static final String INSERT_SQL = """
                INSERT INTO knowledge.document_chunks
                    (chunk_id, document_id, chunk_text, embedding, content_hash, chunk_index)
                VALUES (?::uuid, ?, ?, ?::vector, ?, ?)
                """;

        private final JdbcTemplate jdbcTemplate;
        private final Long documentId;
        private final String contentHash;

        private JdbcDocumentChunkEmbeddingStore(JdbcTemplate jdbcTemplate, Long documentId, String contentHash) {
            this.jdbcTemplate = jdbcTemplate;
            this.documentId = documentId;
            this.contentHash = contentHash;
        }

        @Override
        public String add(Embedding embedding) {
            throw new UnsupportedOperationException("TextSegment is required to insert chunk data");
        }

        @Override
        public void add(String id, Embedding embedding) {
            throw new UnsupportedOperationException("TextSegment is required to insert chunk data");
        }

        @Override
        public String add(Embedding embedding, TextSegment segment) {
            return addAll(List.of(embedding), List.of(segment)).get(0);
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            throw new UnsupportedOperationException("TextSegment list is required to insert chunk data");
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
            if (embeddings.size() != segments.size()) {
                throw new IllegalArgumentException("embeddings size must match segments size");
            }

            List<String> insertedChunkIds = new ArrayList<>(embeddings.size());
            for (int i = 0; i < embeddings.size(); i++) {
                Embedding embedding = embeddings.get(i);
                float[] vector = embedding.vector();
                if (vector == null || vector.length != VECTOR_DIMENSION) {
                    throw new IllegalStateException("Expected embedding dimension " + VECTOR_DIMENSION + " but got " +
                            (vector == null ? 0 : vector.length));
                }

                TextSegment segment = segments.get(i);
                Long metadataDocumentId = extractDocumentId(segment);
                if (!documentId.equals(metadataDocumentId)) {
                    throw new IllegalStateException("Segment metadata document_id does not match process documentId");
                }

                String chunkId = UUID.randomUUID().toString();
                int updated = jdbcTemplate.update(
                        INSERT_SQL,
                        chunkId,
                        metadataDocumentId,
                        segment.text(),
                        toVectorLiteral(vector),
                        contentHash,
                        i
                );

                if (updated != 1) {
                    throw new IllegalStateException("Failed to insert chunk row for documentId: " + documentId);
                }
                insertedChunkIds.add(chunkId);
            }
            return insertedChunkIds;
        }

        private static String toVectorLiteral(float[] vector) {
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (float value : vector) {
                joiner.add(Float.toString(value));
            }
            return joiner.toString();
        }

        private static Long extractDocumentId(TextSegment segment) {
            if (segment.metadata() == null) {
                throw new IllegalStateException("Segment metadata is required");
            }
            Long value = segment.metadata().getLong("document_id");
            if (value == null) {
                throw new IllegalStateException("Segment metadata must contain document_id");
            }
            return value;
        }
    }
}
