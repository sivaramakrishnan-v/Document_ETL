package com.document.documentetl.service;

import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.model.MlflowTraceBridgeDocument;
import com.document.documentetl.model.MlflowTraceBridgeEvent;
import com.document.documentetl.model.StagedDocument;
import com.document.documentetl.repository.MlflowTraceBridgeEventRepository;
import com.document.documentetl.repository.StagedDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class MlflowTraceBridgeService {

    private static final Logger log = LoggerFactory.getLogger(MlflowTraceBridgeService.class);

    private final ObjectMapper objectMapper;
    private final StagedDocumentRepository stagedDocumentRepository;
    private final MlflowTraceBridgeEventRepository traceBridgeEventRepository;
    private final boolean enabled;
    private final boolean fileWriteEnabled;
    private final Path bridgeFilePath;

    public MlflowTraceBridgeService(ObjectMapper objectMapper,
                                    StagedDocumentRepository stagedDocumentRepository,
                                    MlflowTraceBridgeEventRepository traceBridgeEventRepository,
                                    @Value("${app.mlflow.trace-bridge.enabled:true}") boolean enabled,
                                    @Value("${app.mlflow.trace-bridge.file-write-enabled:true}") boolean fileWriteEnabled,
                                    @Value("${app.mlflow.trace-bridge.file-path:python_eval/mlflow_trace_bridge.jsonl}") String bridgeFilePath) {
        this.objectMapper = objectMapper;
        this.stagedDocumentRepository = stagedDocumentRepository;
        this.traceBridgeEventRepository = traceBridgeEventRepository;
        this.enabled = enabled;
        this.fileWriteEnabled = fileWriteEnabled;
        this.bridgeFilePath = Paths.get(bridgeFilePath).toAbsolutePath().normalize();
    }

    @Transactional
    public void emitChatTrace(String runId, String question, String answer, List<SearchResult> retrievedResults) {
        if (!enabled || runId == null || runId.isBlank()) {
            return;
        }

        try {
            MlflowTraceBridgeEvent event = buildEvent(runId, question, answer, retrievedResults);
            traceBridgeEventRepository.save(event);
            if (fileWriteEnabled) {
                writeJsonLine(objectMapper.writeValueAsString(buildBridgePayload(event)));
            }
        } catch (Exception e) {
            log.warn("Trace bridge database write skipped for runId={} due to {}", runId, e.getMessage());
        }
    }

    private MlflowTraceBridgeEvent buildEvent(String runId,
                                              String question,
                                              String answer,
                                              List<SearchResult> retrievedResults) {
        OffsetDateTime now = OffsetDateTime.now();
        MlflowTraceBridgeEvent event = new MlflowTraceBridgeEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("chat.ask.trace");
        event.setEventTimeEpochMs(System.currentTimeMillis());
        event.setRunId(runId);
        event.setQuestion(question);
        event.setAnswer(answer);
        event.setRootSpanName("chat.ask.root");
        event.setRetrieverSpanName("chat.ask.retriever");
        event.setRetrieverSpanType("RETRIEVER");
        event.setIngested(false);
        event.setCreatedAt(now);

        for (MlflowTraceBridgeDocument document : buildRetrieverDocuments(retrievedResults, now)) {
            event.addDocument(document);
        }
        return event;
    }

    private List<MlflowTraceBridgeDocument> buildRetrieverDocuments(List<SearchResult> retrievedResults,
                                                                    OffsetDateTime createdAt) {
        if (retrievedResults == null || retrievedResults.isEmpty()) {
            return List.of();
        }

        List<Long> documentIds = retrievedResults.stream()
                .map(SearchResult::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, StagedDocument> stagedById = new LinkedHashMap<>();
        if (!documentIds.isEmpty()) {
            for (StagedDocument stagedDocument : stagedDocumentRepository.findAllById(documentIds)) {
                if (stagedDocument.getDocumentId() != null) {
                    stagedById.put(stagedDocument.getDocumentId(), stagedDocument);
                }
            }
        }

        List<MlflowTraceBridgeDocument> documents = new ArrayList<>(retrievedResults.size());
        for (int i = 0; i < retrievedResults.size(); i++) {
            SearchResult result = retrievedResults.get(i);
            Long docId = result.getDocumentId();
            StagedDocument staged = docId != null ? stagedById.get(docId) : null;

            MlflowTraceBridgeDocument document = new MlflowTraceBridgeDocument();
            document.setDocId(docId);
            document.setSource(staged != null ? staged.getFileName() : null);
            document.setSourcePath(staged != null ? staged.getFilePath() : null);
            document.setRankOrder(i + 1);
            document.setSimilarity(result.getSimilarity());
            document.setPageContent(result.getText());
            document.setCreatedAt(createdAt);
            documents.add(document);
        }
        return documents;
    }

    private Map<String, Object> buildBridgePayload(MlflowTraceBridgeEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", event.getEventId());
        payload.put("event_type", event.getEventType());
        payload.put("event_time_epoch_ms", event.getEventTimeEpochMs());
        payload.put("run_id", event.getRunId());
        payload.put("question", event.getQuestion());
        payload.put("answer", event.getAnswer());

        Map<String, Object> rootSpan = new LinkedHashMap<>();
        rootSpan.put("name", event.getRootSpanName());
        Map<String, Object> rootInput = new LinkedHashMap<>();
        rootInput.put("question", event.getQuestion());
        Map<String, Object> rootOutput = new LinkedHashMap<>();
        rootOutput.put("answer", event.getAnswer());
        rootSpan.put("input", rootInput);
        rootSpan.put("output", rootOutput);
        payload.put("root_span", rootSpan);

        Map<String, Object> retrieverSpan = new LinkedHashMap<>();
        retrieverSpan.put("name", event.getRetrieverSpanName());
        retrieverSpan.put("span_type", event.getRetrieverSpanType());
        Map<String, Object> retrieverAttributes = new LinkedHashMap<>();
        retrieverAttributes.put("span_type", event.getRetrieverSpanType());
        retrieverSpan.put("attributes", retrieverAttributes);

        List<Map<String, Object>> output = new ArrayList<>();
        for (MlflowTraceBridgeDocument document : event.getDocuments()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("doc_id", document.getDocId());
            metadata.put("source", document.getSource());
            metadata.put("source_path", document.getSourcePath());
            metadata.put("rank", document.getRankOrder());
            metadata.put("similarity", document.getSimilarity());

            Map<String, Object> docPayload = new LinkedHashMap<>();
            docPayload.put("page_content", document.getPageContent());
            docPayload.put("metadata", metadata);
            output.add(docPayload);
        }
        retrieverSpan.put("output", output);
        payload.put("retriever_span", retrieverSpan);
        return payload;
    }

    private void writeJsonLine(String json) throws IOException {
        Path parent = bridgeFilePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                bridgeFilePath,
                json + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }
}
