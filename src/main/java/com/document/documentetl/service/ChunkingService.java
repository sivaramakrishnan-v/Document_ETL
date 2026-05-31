package com.document.documentetl.service;

import com.document.documentetl.model.DocumentChunk;
import com.document.documentetl.model.ParsedContent;
import com.document.documentetl.repository.DocumentChunkRepository;
import com.document.documentetl.repository.ParsedContentRepository;
import com.document.documentetl.repository.StagedDocumentRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);
    private static final int BOUNDARY_PREVIEW_LENGTH = 20;

    private final ParsedContentRepository parsedContentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final StagedDocumentRepository stagedDocumentRepository;
    private final VertexAiEmbeddingService vertexAiEmbeddingService;
    private final DocumentSplitter documentSplitter;
    private final TransactionTemplate transactionTemplate;
    private final int chunkSize;
    private final int chunkOverlap;

    public ChunkingService(ParsedContentRepository parsedContentRepository,
                           DocumentChunkRepository documentChunkRepository,
                           StagedDocumentRepository stagedDocumentRepository,
                           VertexAiEmbeddingService vertexAiEmbeddingService,
                           PlatformTransactionManager transactionManager,
                           DocumentSplitter documentSplitter,
                           @Value("${app.chunk.size:500}") int chunkSize,
                           @Value("${app.chunk.overlap:100}") int chunkOverlap) {
        this.parsedContentRepository = parsedContentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.stagedDocumentRepository = stagedDocumentRepository;
        this.vertexAiEmbeddingService = vertexAiEmbeddingService;
        this.documentSplitter = documentSplitter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<DocumentChunk> chunkAllParsedContent() {
        long startedAtNanos = System.nanoTime();
        log.info("Chunking started: chunkSize={}, chunkOverlap={}", chunkSize, chunkOverlap);
        List<ParsedContent> parsedContents = parsedContentRepository.findByParsingStatus("PARSED");
        if (parsedContents.isEmpty()) {
            log.info("Chunking completed: parsedContents=0, chunksPersisted=0, durationMs=0");
            return List.of();
        }

        List<DocumentChunk> allSavedChunks = new ArrayList<>();
        int processedDocuments = 0;
        int skippedDocuments = 0;
        int upToDateDocuments = 0;
        int resetDocuments = 0;
        int failedDocuments = 0;

        for (ParsedContent parsedContent : parsedContents) {
            Long documentId = parsedContent.getDocumentId();
            UUID contentId = parsedContent.getContentId();
            long documentStartedAtNanos = System.nanoTime();
            log.info("Chunking document started: documentId={}, contentId={}", documentId, contentId);

            if (parsedContent.getRawText() == null || parsedContent.getRawText().isBlank()) {
                skippedDocuments++;
                log.warn("Chunking skipped: documentId={}, contentId={}, reason=empty parsed content", documentId, contentId);
                continue;
            }

            boolean hadExistingChunks = documentChunkRepository.existsByDocumentId(documentId);
            try {
                List<DocumentChunk> savedChunks = transactionTemplate.execute(status -> chunkParsedContentInternal(parsedContent));
                if (savedChunks == null) {
                    throw new IllegalStateException("Chunking transaction returned null result");
                }
                if (savedChunks.isEmpty()) {
                    upToDateDocuments++;
                    continue;
                }
                if (hadExistingChunks) {
                    resetDocuments++;
                }
                allSavedChunks.addAll(savedChunks);
                processedDocuments++;

                long durationMs = (System.nanoTime() - documentStartedAtNanos) / 1_000_000;
                log.info("Chunking document completed: documentId={}, contentId={}, chunksPersisted={}, durationMs={}",
                        documentId,
                        contentId,
                        savedChunks.size(),
                        durationMs);
            } catch (RuntimeException e) {
                failedDocuments++;
                long durationMs = (System.nanoTime() - documentStartedAtNanos) / 1_000_000;
                log.error("Chunking document failed: documentId={}, contentId={}, durationMs={}, error={}",
                        documentId,
                        contentId,
                        durationMs,
                        e.getMessage(),
                        e);
                throw e;
            }
        }

        long totalDurationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
        log.info(
                "Chunking completed: parsedContents={}, processedDocuments={}, skippedDocuments={}, upToDateDocuments={}, resetDocuments={}, failedDocuments={}, chunksPersisted={}, durationMs={}",
                parsedContents.size(),
                processedDocuments,
                skippedDocuments,
                upToDateDocuments,
                resetDocuments,
                failedDocuments,
                allSavedChunks.size(),
                totalDurationMs);
        return allSavedChunks;
    }

    @Transactional
    public List<DocumentChunk> chunkParsedContent(ParsedContent parsedContent) {
        return chunkParsedContentInternal(parsedContent);
    }

    private List<DocumentChunk> chunkParsedContentInternal(ParsedContent parsedContent) {
        Long documentId = parsedContent.getDocumentId();
        if (documentId == null) {
            throw new IllegalArgumentException("Parsed content documentId must not be null");
        }
        String rawText = parsedContent.getRawText();
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalStateException("Parsed content rawText is empty for documentId: " + documentId);
        }
        String incomingHash = parsedContent.getContentHash();
        if (incomingHash == null || incomingHash.isBlank()) {
            throw new IllegalStateException("Parsed content hash is missing for documentId: " + documentId);
        }

        if (documentChunkRepository.existsByDocumentIdAndContentHash(documentId, incomingHash)) {
            log.info("Vectors already up to date. Skipping Vertex AI calls.");
            return List.of();
        }

        long deleted = documentChunkRepository.deleteByDocumentId(documentId);
        if (deleted > 0) {
            log.info("Existing chunks cleared: documentId={}, deleted={}", documentId, deleted);
        }

        List<TextSegment> segments = splitIntoSegments(parsedContent, rawText);
        logChunkBoundaries(documentId, segments);
        List<DocumentChunk> chunks = buildChunksWithEmbeddings(parsedContent, segments);
        List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(chunks);
        stagedDocumentRepository.findById(documentId).ifPresentOrElse(stagedDocument -> {
                    stagedDocument.setStatus("COMPLETED");
                    stagedDocumentRepository.save(stagedDocument);
                },
                () -> log.warn("Staged document not found during chunk completion: documentId={}", documentId));

        return savedChunks;
    }

    private List<TextSegment> splitIntoSegments(ParsedContent parsedContent, String text) {
        Metadata metadata = buildDocumentMetadata(parsedContent);
        Document document = Document.from(text, metadata);

        List<TextSegment> splitSegments = documentSplitter.split(document);
        if (splitSegments.isEmpty()) {
            throw new IllegalStateException("No chunks produced for documentId: " + parsedContent.getDocumentId());
        }
        return applyDocumentMetadata(splitSegments, document.metadata());
    }

    private List<DocumentChunk> buildChunksWithEmbeddings(ParsedContent parsedContent, List<TextSegment> segments) {
        List<DocumentChunk> chunks = new ArrayList<>(segments.size());
        LocalDateTime createdAt = LocalDateTime.now();

        for (int chunkIndex = 0; chunkIndex < segments.size(); chunkIndex++) {
            TextSegment segment = segments.get(chunkIndex);
            String chunkText = segment.text();
            float[] embedding = vertexAiEmbeddingService.embed(chunkText);
            if (embedding == null || embedding.length == 0) {
                throw new IllegalStateException("Vertex AI returned empty embedding for documentId: " + parsedContent.getDocumentId());
            }

            DocumentChunk chunk = new DocumentChunk(
                    UUID.randomUUID(),
                    parsedContent.getDocumentId(),
                    parsedContent.getContentId(),
                    chunkText,
                    chunkIndex,
                    parsedContent.getContentHash(),
                    createdAt
            );
            chunk.setEmbedding(embedding);
            chunks.add(chunk);
        }

        return chunks;
    }

    private static Metadata buildDocumentMetadata(ParsedContent parsedContent) {
        Metadata metadata = new Metadata()
                .put("document_id", parsedContent.getDocumentId())
                .put("content_hash", parsedContent.getContentHash());

        if (parsedContent.getContentId() != null) {
            metadata.put("content_id", parsedContent.getContentId());
        }
        return metadata;
    }

    private static List<TextSegment> applyDocumentMetadata(List<TextSegment> splitSegments, Metadata sourceMetadata) {
        List<TextSegment> segments = new ArrayList<>(splitSegments.size());
        for (TextSegment segment : splitSegments) {
            Metadata merged = sourceMetadata == null ? new Metadata() : sourceMetadata.copy();
            Metadata segmentMetadata = segment.metadata();
            if (segmentMetadata != null) {
                for (Map.Entry<String, Object> entry : segmentMetadata.toMap().entrySet()) {
                    merged.add(entry.getKey(), entry.getValue());
                }
            }
            segments.add(TextSegment.from(segment.text(), merged));
        }
        return segments;
    }

    private void logChunkBoundaries(Long documentId, List<TextSegment> segments) {
        if (segments.isEmpty()) {
            log.info("Chunk validation: documentId={}, chunksCreated=0", documentId);
            return;
        }

        String firstChunkStart = firstChars(segments.get(0).text(), BOUNDARY_PREVIEW_LENGTH);
        String lastChunkEnd = lastChars(segments.get(segments.size() - 1).text(), BOUNDARY_PREVIEW_LENGTH);
        log.info(
                "Chunk validation: documentId={}, chunksCreated={}, firstChunkStart='{}', lastChunkEnd='{}'",
                documentId,
                segments.size(),
                firstChunkStart,
                lastChunkEnd
        );
    }

    private static String firstChars(String value, int length) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int end = Math.min(length, value.length());
        return sanitizePreview(value.substring(0, end));
    }

    private static String lastChars(String value, int length) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int start = Math.max(0, value.length() - length);
        return sanitizePreview(value.substring(start));
    }

    private static String sanitizePreview(String value) {
        return value.replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
