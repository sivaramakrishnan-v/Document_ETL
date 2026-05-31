package com.document.documentetl.service;

import com.document.documentetl.model.StagedDocument;
import com.document.documentetl.repository.StagedDocumentRepository;
import com.document.documentetl.util.ChecksumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class StagingService {

    private static final String DOCUMENTS_DIRECTORY = "src/main/resources/documents";
    private static final Logger log = LoggerFactory.getLogger(StagingService.class);
    private final StagedDocumentRepository stagedDocumentRepository;

    public StagingService(StagedDocumentRepository stagedDocumentRepository) {
        this.stagedDocumentRepository = stagedDocumentRepository;
    }

    @Transactional
    public List<StagedDocument> stageLocalFiles() {
        long startedAtNanos = System.nanoTime();
        Path documentsPath = Paths.get(DOCUMENTS_DIRECTORY);
        if (Files.notExists(documentsPath) || !Files.isDirectory(documentsPath)) {
            log.warn("Documents directory not found or not a directory: {}", documentsPath.toAbsolutePath());
            return List.of();
        }

        log.info("Staging started: sourceDirectory={}", documentsPath.toAbsolutePath());

        try (Stream<Path> files = Files.list(documentsPath)) {
            List<Path> candidates = files
                    .filter(Files::isRegularFile)
                    .toList();

            int fileCount = candidates.size();
            log.debug("Staging scan completed: fileCount={}", fileCount);

            List<StagedDocument> persistedDocuments = new ArrayList<>();
            int unreadableCount = 0;
            int metadataUnchangedCount = 0;
            int metadataOnlyUpdateCount = 0;
            int contentUpdatedCount = 0;
            int newDocumentCount = 0;

            for (Path candidate : candidates) {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(candidate, BasicFileAttributes.class);
                    StageResult result = processCandidate(candidate, attributes);

                    if (result.action == StageAction.UNCHANGED) {
                        metadataUnchangedCount++;
                        continue;
                    }

                    persistedDocuments.add(result.document);
                    if (result.action == StageAction.NEW_DOCUMENT) {
                        newDocumentCount++;
                    } else if (result.action == StageAction.METADATA_ONLY) {
                        metadataOnlyUpdateCount++;
                    } else if (result.action == StageAction.CONTENT_UPDATED) {
                        contentUpdatedCount++;
                    }
                } catch (IOException | RuntimeException e) {
                    unreadableCount++;
                    log.error("File unreadable; skipping: filePath={}, error={}", candidate.toAbsolutePath(), e.getMessage(), e);
                }
            }

            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.info(
                    "Staging completed: fileCount={}, persisted={}, newDocuments={}, metadataOnlyUpdates={}, contentUpdates={}, metadataUnchanged={}, unreadable={}, durationMs={}",
                    fileCount,
                    persistedDocuments.size(),
                    newDocumentCount,
                    metadataOnlyUpdateCount,
                    contentUpdatedCount,
                    metadataUnchangedCount,
                    unreadableCount,
                    durationMs);
            return persistedDocuments;
        } catch (IOException e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error(
                    "Staging failed during file scan: sourceDirectory={}, durationMs={}, error={}",
                    documentsPath.toAbsolutePath(),
                    durationMs,
                    e.getMessage(),
                    e);
            throw new UncheckedIOException("Failed to stage local files", e);
        }
    }

    private StageResult processCandidate(Path path, BasicFileAttributes attributes) {
        String absolutePath = path.toAbsolutePath().toString();
        String fileName = path.getFileName().toString();
        long fileSize = attributes.size();
        OffsetDateTime diskLastModifiedAt = OffsetDateTime.ofInstant(attributes.lastModifiedTime().toInstant(), ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now();

        Optional<StagedDocument> existingOptional = stagedDocumentRepository.findByFilePath(absolutePath);
        if (existingOptional.isEmpty()) {
            StagedDocument newDocument = new StagedDocument();
            newDocument.setFileName(fileName);
            newDocument.setFilePath(absolutePath);
            newDocument.setFileSize(fileSize);
            newDocument.setStatus("STAGED");
            newDocument.setContentHash(ChecksumUtils.calculateSHA256(path));
            newDocument.setLastModifiedAt(diskLastModifiedAt);
            newDocument.setVersionNumber(1);
            newDocument.setCreatedAt(now);
            newDocument.setUpdatedAt(now);
            StagedDocument saved = stagedDocumentRepository.save(newDocument);
            return new StageResult(StageAction.NEW_DOCUMENT, saved);
        }

        StagedDocument existing = existingOptional.get();
        if (isMetadataUnchanged(existing, fileSize, diskLastModifiedAt)) {
            log.info("Skipping: Metadata unchanged");
            return new StageResult(StageAction.UNCHANGED, null);
        }

        String calculatedHash = ChecksumUtils.calculateSHA256(path);

        existing.setFileName(fileName);
        existing.setFileSize(fileSize);
        existing.setLastModifiedAt(diskLastModifiedAt);
        existing.setUpdatedAt(now);

        if (calculatedHash.equals(existing.getContentHash())) {
            StagedDocument saved = stagedDocumentRepository.save(existing);
            return new StageResult(StageAction.METADATA_ONLY, saved);
        }

        existing.setVersionNumber(existing.getVersionNumber() + 1);
        existing.setStatus("STAGED");
        existing.setContentHash(calculatedHash);
        StagedDocument saved = stagedDocumentRepository.save(existing);
        return new StageResult(StageAction.CONTENT_UPDATED, saved);
    }

    private boolean isMetadataUnchanged(StagedDocument document, long fileSize, OffsetDateTime diskLastModifiedAt) {
        if (document.getFileSize() != fileSize) {
            return false;
        }
        if (document.getLastModifiedAt() == null) {
            return false;
        }
        return document.getLastModifiedAt().toInstant().equals(diskLastModifiedAt.toInstant());
    }

    private enum StageAction {
        UNCHANGED,
        NEW_DOCUMENT,
        METADATA_ONLY,
        CONTENT_UPDATED
    }

    private static final class StageResult {
        private final StageAction action;
        private final StagedDocument document;

        private StageResult(StageAction action, StagedDocument document) {
            this.action = action;
            this.document = document;
        }
    }
}
