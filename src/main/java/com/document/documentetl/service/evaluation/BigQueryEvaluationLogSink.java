package com.document.documentetl.service.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class BigQueryEvaluationLogSink implements EvaluationLogSink {

    private static final Logger log = LoggerFactory.getLogger(BigQueryEvaluationLogSink.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final boolean enabled;
    private final String projectId;
    private final String dataset;
    private final String table;
    private final String keyLocation;

    private final AtomicBoolean tableInitialized = new AtomicBoolean(false);
    private final Object tableInitLock = new Object();

    public BigQueryEvaluationLogSink(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${app.evaluation.bigquery.enabled:true}") boolean enabled,
            @Value("${app.evaluation.bigquery.project-id:${spring.ai.vertex.ai.gemini.project-id:}}") String projectId,
            @Value("${app.evaluation.bigquery.dataset:rag_analytics}") String dataset,
            @Value("${app.evaluation.bigquery.table:evaluation_logs}") String table,
            @Value("${spring.cloud.gcp.credentials.location:}") String keyLocation) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.enabled = enabled;
        this.projectId = projectId;
        this.dataset = dataset;
        this.table = table;
        this.keyLocation = keyLocation;
    }

    @Override
    @Async("evaluationTaskExecutor")
    @CircuitBreaker(name = "bigqueryWrite", fallbackMethod = "logFallback")
    public void log(EvaluationLogEntry entry) {
        if (!enabled) {
            return;
        }
        if (isBlank(projectId) || isBlank(dataset) || isBlank(table) || isBlank(keyLocation)) {
            log.warn("Skipping BigQuery evaluation log due to incomplete configuration.");
            return;
        }

        ensureTableExists();
        insertRow(entry);
    }

    private void logFallback(EvaluationLogEntry entry, Throwable throwable) {
        log.warn("BigQuery evaluation log skipped. Circuit breaker fallback applied: {}", throwable.getMessage());
    }

    private void ensureTableExists() {
        if (tableInitialized.get()) {
            return;
        }
        synchronized (tableInitLock) {
            if (tableInitialized.get()) {
                return;
            }
            createTableIfMissing();
            tableInitialized.set(true);
        }
    }

    private void createTableIfMissing() {
        createDatasetIfMissing();

        String endpoint = "https://bigquery.googleapis.com/bigquery/v2/projects/%s/datasets/%s/tables"
                .formatted(projectId, dataset);

        Map<String, Object> tableReference = new HashMap<>();
        tableReference.put("projectId", projectId);
        tableReference.put("datasetId", dataset);
        tableReference.put("tableId", table);

        Map<String, Object> schema = new HashMap<>();
        schema.put("fields", tableFields());

        Map<String, Object> body = new HashMap<>();
        body.put("tableReference", tableReference);
        body.put("schema", schema);

        try {
            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + resolveAccessToken())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                return;
            }
            throw e;
        }
    }

    private void createDatasetIfMissing() {
        String endpoint = "https://bigquery.googleapis.com/bigquery/v2/projects/%s/datasets".formatted(projectId);

        Map<String, Object> datasetReference = new HashMap<>();
        datasetReference.put("projectId", projectId);
        datasetReference.put("datasetId", dataset);

        Map<String, Object> body = new HashMap<>();
        body.put("datasetReference", datasetReference);

        try {
            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + resolveAccessToken())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                return;
            }
            throw e;
        }
    }

    private List<Map<String, String>> tableFields() {
        List<Map<String, String>> fields = new ArrayList<>();
        fields.add(field("created_at", "TIMESTAMP", "REQUIRED"));
        fields.add(field("question", "STRING", "NULLABLE"));
        fields.add(field("retrieved_context", "STRING", "NULLABLE"));
        fields.add(field("answer", "STRING", "NULLABLE"));
        fields.add(field("golden_answer", "STRING", "NULLABLE"));
        fields.add(field("metric", "STRING", "REQUIRED"));
        fields.add(field("score", "FLOAT", "REQUIRED"));
        fields.add(field("reasoning", "STRING", "NULLABLE"));
        fields.add(field("evaluation_model", "STRING", "NULLABLE"));
        return fields;
    }

    private static Map<String, String> field(String name, String type, String mode) {
        Map<String, String> field = new HashMap<>();
        field.put("name", name);
        field.put("type", type);
        field.put("mode", mode);
        return field;
    }

    private void insertRow(EvaluationLogEntry entry) {
        String endpoint = "https://bigquery.googleapis.com/bigquery/v2/projects/%s/datasets/%s/tables/%s/insertAll"
                .formatted(projectId, dataset, table);

        Map<String, Object> rowJson = new HashMap<>();
        rowJson.put("created_at", entry.createdAt().toString());
        rowJson.put("question", entry.question());
        rowJson.put("retrieved_context", entry.retrievedContext());
        rowJson.put("answer", entry.answer());
        rowJson.put("golden_answer", entry.goldenAnswer());
        rowJson.put("metric", entry.metric());
        rowJson.put("score", entry.score());
        rowJson.put("reasoning", entry.reasoning());
        rowJson.put("evaluation_model", entry.evaluationModel());

        Map<String, Object> row = new HashMap<>();
        row.put("insertId", UUID.randomUUID().toString());
        row.put("json", rowJson);

        Map<String, Object> body = new HashMap<>();
        body.put("rows", Collections.singletonList(row));

        String response = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + resolveAccessToken())
                .body(body)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode insertErrors = root.path("insertErrors");
            if (insertErrors.isArray() && !insertErrors.isEmpty()) {
                log.warn("BigQuery insertAll reported errors: {}", insertErrors);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse BigQuery insertAll response", e);
        }
    }

    private String resolveAccessToken() {
        try {
            Resource resource = resourceLoader.getResource(keyLocation);
            GoogleCredentials credentials = GoogleCredentials.fromStream(resource.getInputStream())
                    .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve Google access token from " + keyLocation, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
