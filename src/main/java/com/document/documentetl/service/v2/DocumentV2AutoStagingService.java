package com.document.documentetl.service.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(
        name = {"app.etl.v2.enabled", "app.etl.v2.auto-stage.enabled"},
        havingValue = "true",
        matchIfMissing = true
)
public class DocumentV2AutoStagingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentV2AutoStagingService.class);

    private final DocumentV2StagingService stagingService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DocumentV2AutoStagingService(DocumentV2StagingService stagingService) {
        this.stagingService = stagingService;
    }

    @Scheduled(
            initialDelayString = "${app.etl.v2.auto-stage.initial-delay-ms:5000}",
            fixedDelayString = "${app.etl.v2.auto-stage.fixed-delay-ms:10000}"
    )
    public void stageChangedDocuments() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            DocumentV2StagingService.StagingSummary summary = stagingService.stageLocalFiles();
            if (summary.emittedEvents() > 0 || summary.unreadableFiles() > 0) {
                log.info(
                        "V2 auto-stage completed: scannedFiles={}, persistedDocuments={}, emittedEvents={}, metadataOnlyUpdates={}, unchangedFiles={}, unreadableFiles={}",
                        summary.scannedFiles(),
                        summary.persistedDocuments().size(),
                        summary.emittedEvents(),
                        summary.metadataOnlyUpdates(),
                        summary.unchangedFiles(),
                        summary.unreadableFiles()
                );
            }
        } finally {
            running.set(false);
        }
    }
}
