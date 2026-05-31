package com.document.documentetl.service.v2;

import com.document.documentetl.messaging.DocumentEventProducer;
import com.document.documentetl.model.v2.SourceDocument;
import com.document.documentetl.repository.v2.SourceDocumentRepository;
import com.document.documentetl.util.ChecksumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class DocumentV2StagingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentV2StagingService.class);

    private final SourceDocumentRepository sourceDocumentRepository;
    private final DocumentEventProducer documentEventProducer;
    private final String documentsDirectory;

    public DocumentV2StagingService(SourceDocumentRepository sourceDocumentRepository,
                                    DocumentEventProducer documentEventProducer,
                                    @Value("${app.etl.v2.documents-directory}") String documentsDirectory) {
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.documentEventProducer = documentEventProducer;
        this.documentsDirectory = documentsDirectory;
    }

    @Transactional
    public StagingSummary stageLocalFiles() {
        Path documentsPath = Paths.get(documentsDirectory);
        if (Files.notExists(documentsPath) || !Files.isDirectory(documentsPath)) {
            log.warn("V2 documents directory not found or not a directory: {}", documentsPath.toAbsolutePath());
            return new StagingSummary(List.of(), 0, 0, 0, 0, 0);
        }

        try (Stream<Path> files = Files.list(documentsPath)) {
            List<Path> candidates = files.filter(Files::isRegularFile).toList();
            List<SourceDocument> persisted = new ArrayList<>();
            int emittedEvents = 0;
            int metadataOnlyUpdates = 0;
            int unchanged = 0;
            int unreadable = 0;

            for (Path candidate : candidates) {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(candidate, BasicFileAttributes.class);
                    StageResult result = processCandidate(candidate, attributes);
                    if (result.action == StageAction.UNCHANGED) {
                        unchanged++;
                        continue;
                    }

                    persisted.add(result.document);
                    if (result.action == StageAction.NEW_DOCUMENT || result.action == StageAction.CONTENT_UPDATED) {
                        documentEventProducer.publishDocumentStaged(result.document);
                        emittedEvents++;
                    } else {
                        metadataOnlyUpdates++;
                    }
                } catch (IOException | RuntimeException e) {
                    unreadable++;
                    log.error("V2 file unreadable; skipping: filePath={}, error={}",
                            candidate.toAbsolutePath(),
                            e.getMessage(),
                            e);
                }
            }

            return new StagingSummary(persisted, candidates.size(), emittedEvents, metadataOnlyUpdates, unchanged, unreadable);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stage V2 local files", e);
        }
    }

    private StageResult processCandidate(Path path, BasicFileAttributes attributes) {
        String sourceUri = path.toAbsolutePath().toString();
        String fileName = path.getFileName().toString();
        long fileSize = attributes.size();
        OffsetDateTime lastModifiedAt = OffsetDateTime.ofInstant(attributes.lastModifiedTime().toInstant(), ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now();

        Optional<SourceDocument> existingOptional = sourceDocumentRepository.findBySourceUri(sourceUri);
        if (existingOptional.isEmpty()) {
            SourceDocument document = new SourceDocument();
            document.setSourceUri(sourceUri);
            document.setFileName(fileName);
            document.setFileSizeBytes(fileSize);
            document.setContentHash(ChecksumUtils.calculateSHA256(path));
            document.setVersionNumber(1);
            document.setStatus("STAGED");
            document.setLastModifiedAt(lastModifiedAt);
            document.setStagedAt(now);
            document.setUpdatedAt(now);
            return new StageResult(StageAction.NEW_DOCUMENT, sourceDocumentRepository.save(document));
        }

        SourceDocument existing = existingOptional.get();
        if (isMetadataUnchanged(existing, fileSize, lastModifiedAt)) {
            return new StageResult(StageAction.UNCHANGED, existing);
        }

        String calculatedHash = ChecksumUtils.calculateSHA256(path);
        existing.setFileName(fileName);
        existing.setFileSizeBytes(fileSize);
        existing.setLastModifiedAt(lastModifiedAt);
        existing.setUpdatedAt(now);

        if (calculatedHash.equals(existing.getContentHash())) {
            return new StageResult(StageAction.METADATA_ONLY, sourceDocumentRepository.save(existing));
        }

        existing.setContentHash(calculatedHash);
        existing.setVersionNumber(existing.getVersionNumber() + 1);
        existing.setStatus("STAGED");
        return new StageResult(StageAction.CONTENT_UPDATED, sourceDocumentRepository.save(existing));
    }

    private boolean isMetadataUnchanged(SourceDocument document, long fileSize, OffsetDateTime lastModifiedAt) {
        return document.getFileSizeBytes() != null
                && document.getFileSizeBytes() == fileSize
                && document.getLastModifiedAt() != null
                && document.getLastModifiedAt().toInstant().equals(lastModifiedAt.toInstant());
    }

    public record StagingSummary(List<SourceDocument> persistedDocuments,
                                 int scannedFiles,
                                 int emittedEvents,
                                 int metadataOnlyUpdates,
                                 int unchangedFiles,
                                 int unreadableFiles) {
    }

    private enum StageAction {
        UNCHANGED,
        NEW_DOCUMENT,
        METADATA_ONLY,
        CONTENT_UPDATED
    }

    private record StageResult(StageAction action, SourceDocument document) {
    }
}
