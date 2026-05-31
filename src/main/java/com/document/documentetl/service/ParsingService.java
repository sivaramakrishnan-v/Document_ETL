package com.document.documentetl.service;

import com.document.documentetl.model.ParsedContent;
import com.document.documentetl.model.StagedDocument;
import com.document.documentetl.repository.ParsedContentRepository;
import com.document.documentetl.repository.StagedDocumentRepository;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ParsingService {

    private static final Logger log = LoggerFactory.getLogger(ParsingService.class);
    private final Tika tika = new Tika();
    private final StagedDocumentRepository stagedDocumentRepository;
    private final ParsedContentRepository parsedContentRepository;
    private final TransactionTemplate transactionTemplate;

    public ParsingService(StagedDocumentRepository stagedDocumentRepository,
                          ParsedContentRepository parsedContentRepository,
                          PlatformTransactionManager transactionManager) {
        this.stagedDocumentRepository = stagedDocumentRepository;
        this.parsedContentRepository = parsedContentRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<ParsedContent> processStagedFiles() {
        List<StagedDocument> stagedDocuments = stagedDocumentRepository.findByStatus("STAGED");
        List<ParsedContent> parsedContents = new ArrayList<>();
        for (StagedDocument stagedDocument : stagedDocuments) {
            String fileName = stagedDocument.getFileName() != null
                    ? stagedDocument.getFileName()
                    : stagedDocument.getFilePath();
            log.info("Parsing [{}]...", fileName);

            try {
                ParsedContent parsedContent = transactionTemplate.execute(status -> processSingleStagedDocument(stagedDocument));
                if (parsedContent != null) {
                    parsedContents.add(parsedContent);
                    log.info("Success");
                }
            } catch (Exception ex) {
                log.error("Failed", ex);
                continue;
            }
        }

        return parsedContents;
    }

    private ParsedContent processSingleStagedDocument(StagedDocument stagedDocument) {
        try {
            String extractedText = tika.parseToString(new File(stagedDocument.getFilePath()));
            return upsertParsedContent(stagedDocument, extractedText);
        } catch (Exception e) {
            throw new IllegalStateException("Parsing failed for file: " + stagedDocument.getFilePath(), e);
        }
    }

    private ParsedContent upsertParsedContent(StagedDocument stagedDocument, String extractedText) {
        Long documentId = stagedDocument.getDocumentId();
        LocalDateTime extractedAt = LocalDateTime.now();
        Optional<ParsedContent> existing = parsedContentRepository.findByDocumentId(documentId);

        ParsedContent parsedContent;
        if (existing.isPresent()) {
            parsedContent = existing.get();
            parsedContent.setRawText(extractedText);
            parsedContent.setContentHash(stagedDocument.getContentHash());
            parsedContent.setParsingStatus("PARSED");
            parsedContent.setExtractedAt(extractedAt);
        } else {
            parsedContent = new ParsedContent(
                    UUID.randomUUID(),
                    documentId,
                    extractedText,
                    stagedDocument.getContentHash(),
                    "PARSED",
                    extractedAt);
        }

        ParsedContent savedParsedContent = parsedContentRepository.save(parsedContent);
        stagedDocument.setStatus("PARSED");
        stagedDocumentRepository.save(stagedDocument);
        return savedParsedContent;
    }
}
