package com.document.documentetl.service;

import com.document.documentetl.model.ParsedContent;
import com.document.documentetl.repository.ParsedContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LangChainChunkingOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(LangChainChunkingOrchestratorService.class);

    private final ParsedContentRepository parsedContentRepository;
    private final LangChainChunkingService langChainChunkingService;

    public LangChainChunkingOrchestratorService(ParsedContentRepository parsedContentRepository,
                                                LangChainChunkingService langChainChunkingService) {
        this.parsedContentRepository = parsedContentRepository;
        this.langChainChunkingService = langChainChunkingService;
    }

    public List<Long> chunkAllParsedContent() {
        List<ParsedContent> parsedContents = parsedContentRepository.findByParsingStatus("PARSED");
        List<Long> processedDocumentIds = new ArrayList<>();
        int skipped = 0;
        int failed = 0;

        for (ParsedContent parsedContent : parsedContents) {
            Long documentId = parsedContent.getDocumentId();
            if (documentId == null || isBlank(parsedContent.getRawText()) || isBlank(parsedContent.getContentHash())) {
                skipped++;
                log.warn("LangChain chunking skipped: documentId={}, reason=missing fields", documentId);
                continue;
            }

            try {
                langChainChunkingService.process(documentId, parsedContent.getRawText(), parsedContent.getContentHash());
                processedDocumentIds.add(documentId);
            } catch (RuntimeException ex) {
                failed++;
                log.error("LangChain chunking failed: documentId={}, error={}", documentId, ex.getMessage(), ex);
            }
        }

        log.info("LangChain chunking completed: parsedContents={}, processed={}, skipped={}, failed={}",
                parsedContents.size(),
                processedDocumentIds.size(),
                skipped,
                failed);
        return processedDocumentIds;
    }

    public void chunkSingleDocument(Long documentId) {
        ParsedContent parsedContent = parsedContentRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException("No parsed content found for documentId: " + documentId));

        if (isBlank(parsedContent.getRawText())) {
            throw new IllegalStateException("Parsed content rawText is empty for documentId: " + documentId);
        }
        if (isBlank(parsedContent.getContentHash())) {
            throw new IllegalStateException("Parsed content hash is empty for documentId: " + documentId);
        }

        langChainChunkingService.process(documentId, parsedContent.getRawText(), parsedContent.getContentHash());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
