package com.document.documentetl.service.v2;

import com.document.documentetl.events.DocumentStagedEvent;
import com.document.documentetl.messaging.DocumentEventProducer;
import com.document.documentetl.model.v2.ExtractedContent;
import com.document.documentetl.model.v2.SourceDocument;
import com.document.documentetl.model.v2.TextChunk;
import com.document.documentetl.repository.v2.ExtractedContentRepository;
import com.document.documentetl.repository.v2.SourceDocumentRepository;
import com.document.documentetl.repository.v2.TextChunkRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentV2ParseChunkService {

    private final Tika tika = new Tika();
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ExtractedContentRepository extractedContentRepository;
    private final TextChunkRepository textChunkRepository;
    private final DocumentSplitter documentSplitter;
    private final Tokenizer tokenizer;
    private final DocumentEventProducer documentEventProducer;

    public DocumentV2ParseChunkService(SourceDocumentRepository sourceDocumentRepository,
                                       ExtractedContentRepository extractedContentRepository,
                                       TextChunkRepository textChunkRepository,
                                       DocumentSplitter documentSplitter,
                                       Tokenizer tokenizer,
                                       DocumentEventProducer documentEventProducer) {
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.extractedContentRepository = extractedContentRepository;
        this.textChunkRepository = textChunkRepository;
        this.documentSplitter = documentSplitter;
        this.tokenizer = tokenizer;
        this.documentEventProducer = documentEventProducer;
    }

    @Transactional
    public ParseChunkResult parseAndChunk(DocumentStagedEvent event) {
        SourceDocument sourceDocument = sourceDocumentRepository.findById(event.documentId())
                .orElseThrow(() -> new IllegalStateException("V2 source document not found: " + event.documentId()));
        if (!event.contentHash().equals(sourceDocument.getContentHash())) {
            return new ParseChunkResult(sourceDocument.getDocumentId(), null, 0, true);
        }

        ExtractedContent extractedContent = extractedContentRepository
                .findByDocumentIdAndContentHash(sourceDocument.getDocumentId(), sourceDocument.getContentHash())
                .orElseGet(() -> extractContent(sourceDocument));

        if (!textChunkRepository.existsByContentId(extractedContent.getContentId())) {
            List<TextChunk> chunks = buildChunks(extractedContent);
            textChunkRepository.saveAll(chunks);
        }

        List<TextChunk> persistedChunks = textChunkRepository.findByContentIdOrderByChunkIndex(extractedContent.getContentId());
        sourceDocument.setStatus("CHUNKED");
        sourceDocument.setUpdatedAt(OffsetDateTime.now());
        sourceDocumentRepository.save(sourceDocument);

        documentEventProducer.publishDocumentChunksReady(
                sourceDocument.getDocumentId(),
                extractedContent.getContentId(),
                extractedContent.getContentHash(),
                persistedChunks.size()
        );

        return new ParseChunkResult(
                sourceDocument.getDocumentId(),
                extractedContent.getContentId(),
                persistedChunks.size(),
                false
        );
    }

    private ExtractedContent extractContent(SourceDocument sourceDocument) {
        try {
            String rawText = tika.parseToString(new File(sourceDocument.getSourceUri()));
            if (rawText == null || rawText.isBlank()) {
                throw new IllegalStateException("Extracted text is empty for documentId: " + sourceDocument.getDocumentId());
            }

            ExtractedContent content = new ExtractedContent();
            content.setContentId(UUID.randomUUID());
            content.setDocumentId(sourceDocument.getDocumentId());
            content.setContentHash(sourceDocument.getContentHash());
            content.setVersionNumber(sourceDocument.getVersionNumber());
            content.setRawText(rawText);
            content.setExtractionStatus("EXTRACTED");
            content.setExtractedAt(OffsetDateTime.now());
            return extractedContentRepository.save(content);
        } catch (Exception e) {
            sourceDocument.setStatus("FAILED");
            sourceDocument.setUpdatedAt(OffsetDateTime.now());
            sourceDocumentRepository.save(sourceDocument);
            throw new IllegalStateException("V2 parsing failed for documentId: " + sourceDocument.getDocumentId(), e);
        }
    }

    private List<TextChunk> buildChunks(ExtractedContent extractedContent) {
        Metadata metadata = new Metadata()
                .put("document_id", extractedContent.getDocumentId())
                .put("content_id", extractedContent.getContentId())
                .put("content_hash", extractedContent.getContentHash());
        Document document = Document.from(extractedContent.getRawText(), metadata);
        List<TextSegment> segments = documentSplitter.split(document);
        if (segments.isEmpty()) {
            throw new IllegalStateException("No V2 chunks generated for documentId: " + extractedContent.getDocumentId());
        }

        List<TextChunk> chunks = new ArrayList<>(segments.size());
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < segments.size(); i++) {
            String chunkText = segments.get(i).text();
            TextChunk chunk = new TextChunk();
            chunk.setChunkId(UUID.randomUUID());
            chunk.setDocumentId(extractedContent.getDocumentId());
            chunk.setContentId(extractedContent.getContentId());
            chunk.setContentHash(extractedContent.getContentHash());
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunkText);
            chunk.setTokenCount(tokenizer.estimateTokenCountInText(chunkText));
            chunk.setCreatedAt(now);
            chunks.add(chunk);
        }
        return chunks;
    }

    public record ParseChunkResult(Long documentId, UUID contentId, int chunkCount, boolean staleEvent) {
    }
}
