package com.document.documentetl.controller;

import com.document.documentetl.model.ParsedContent;
import com.document.documentetl.model.StagedDocument;
import com.document.documentetl.service.MlflowActionTrackingService;
import com.document.documentetl.service.ParsingService;
import com.document.documentetl.service.StagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/etl")
public class StagingController {

    private static final Logger log = LoggerFactory.getLogger(StagingController.class);
    private final StagingService stagingService;
    private final ParsingService parsingService;
    private final MlflowActionTrackingService mlflowActionTrackingService;

    public StagingController(StagingService stagingService,
                             ParsingService parsingService,
                             MlflowActionTrackingService mlflowActionTrackingService) {
        this.stagingService = stagingService;
        this.parsingService = parsingService;
        this.mlflowActionTrackingService = mlflowActionTrackingService;
    }

    @GetMapping("/stage")
    public List<StagedDocument> getStagedDocuments() {
        long startedAtNanos = System.nanoTime();
        log.info("Stage request received: endpoint=/api/etl/stage");

        try {
            List<StagedDocument> stagedDocuments = stagingService.stageLocalFiles();
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.info("Stage request completed: endpoint=/api/etl/stage, persisted={}, durationMs={}",
                    stagedDocuments.size(),
                    durationMs);
            mlflowActionTrackingService.logActionSuccess(
                    "etl.stage",
                    durationMs,
                    Map.of("documents_staged", (double) stagedDocuments.size()),
                    Map.of("endpoint", "/api/etl/stage"));
            return stagedDocuments;
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("Stage request failed: endpoint=/api/etl/stage, durationMs={}, error={}",
                    durationMs,
                    e.getMessage(),
                    e);
            mlflowActionTrackingService.logActionFailure(
                    "etl.stage",
                    durationMs,
                    e,
                    Map.of("endpoint", "/api/etl/stage"));
            throw e;
        }
    }

    @GetMapping("/parse")
    public List<ParsedContent> parseStagedDocuments() {
        long startedAtNanos = System.nanoTime();
        log.info("Parse request received: endpoint=/api/etl/parse");

        try {
            List<ParsedContent> parsedContents = parsingService.processStagedFiles();
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.info("Parse request completed: endpoint=/api/etl/parse, parsed={}, durationMs={}",
                    parsedContents.size(),
                    durationMs);
            mlflowActionTrackingService.logActionSuccess(
                    "etl.parse",
                    durationMs,
                    Map.of("documents_parsed", (double) parsedContents.size()),
                    Map.of("endpoint", "/api/etl/parse"));
            return parsedContents;
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("Parse request failed: endpoint=/api/etl/parse, durationMs={}, error={}",
                    durationMs,
                    e.getMessage(),
                    e);
            mlflowActionTrackingService.logActionFailure(
                    "etl.parse",
                    durationMs,
                    e,
                    Map.of("endpoint", "/api/etl/parse"));
            throw e;
        }
    }
}
