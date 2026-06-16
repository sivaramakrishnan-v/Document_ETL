package com.documentetl.mcp.client;

import com.documentetl.mcp.config.DocumentEtlProperties;
import com.documentetl.mcp.model.ApiAskRequest;
import com.documentetl.mcp.model.ApiAskResponse;
import com.documentetl.mcp.model.ApiDocumentStatusResponse;
import com.documentetl.mcp.model.ApiSearchResult;
import com.documentetl.mcp.model.ApiUploadResponse;
import com.documentetl.mcp.model.AskDocumentsResponse;
import com.documentetl.mcp.model.DocumentStatus;
import com.documentetl.mcp.model.DocumentStatusResponse;
import com.documentetl.mcp.model.SearchChunkResult;
import com.documentetl.mcp.model.UploadDocumentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class DocumentEtlClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentEtlClient.class);

    private final RestClient restClient;
    private final DocumentEtlProperties properties;

    public DocumentEtlClient(RestClient documentEtlRestClient, DocumentEtlProperties properties) {
        this.restClient = documentEtlRestClient;
        this.properties = properties;
    }

    public UploadDocumentResponse uploadDocument(Path filePath) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(filePath));

        ApiUploadResponse response = execute("upload_document", "POST", properties.endpoints().upload(), () -> restClient.post()
                .uri(properties.endpoints().upload())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    throw new DocumentEtlApiException(clientResponse.getStatusCode().value(),
                            "Upload failed with HTTP " + clientResponse.getStatusCode().value());
                })
                .body(ApiUploadResponse.class));

        String documentId = asString(response != null ? response.documentId() : null);
        String fileName = response != null && response.fileName() != null ? response.fileName() : filePath.getFileName().toString();
        DocumentStatus status = DocumentStatus.fromApiStatus(response != null ? response.status() : null);
        return new UploadDocumentResponse(documentId, fileName, status);
    }

    public DocumentStatusResponse getDocumentStatus(String documentId) {
        ApiDocumentStatusResponse response = execute("get_document_status", "GET", properties.endpoints().status(), () -> restClient.get()
                .uri(properties.endpoints().status(), documentId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    throw new DocumentEtlApiException(clientResponse.getStatusCode().value(),
                            "Status lookup failed with HTTP " + clientResponse.getStatusCode().value());
                })
                .body(ApiDocumentStatusResponse.class));

        String resolvedDocumentId = asString(response != null ? response.documentId() : null);
        if (resolvedDocumentId == null || resolvedDocumentId.isBlank()) {
            resolvedDocumentId = documentId;
        }
        int chunks = response != null && response.chunks() != null ? response.chunks() : 0;
        boolean embeddingsGenerated = response != null && Boolean.TRUE.equals(response.embeddingsGenerated());
        DocumentStatus status = DocumentStatus.fromApiStatus(response != null ? response.status() : null);
        return new DocumentStatusResponse(resolvedDocumentId, status, chunks, embeddingsGenerated);
    }

    public AskDocumentsResponse askUploadedDocuments(String question, String documentId) {
        ApiAskResponse response = execute("ask_uploaded_documents", "POST", properties.endpoints().ask(), () -> restClient.post()
                .uri(properties.endpoints().ask())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiAskRequest(question, documentId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    throw new DocumentEtlApiException(clientResponse.getStatusCode().value(),
                            "Question request failed with HTTP " + clientResponse.getStatusCode().value());
                })
                .body(ApiAskResponse.class));

        List<AskDocumentsResponse.Citation> citations = new ArrayList<>();
        if (response != null && response.citations() != null) {
            citations.addAll(response.citations().stream()
                    .map(citation -> new AskDocumentsResponse.Citation(asString(citation.chunkId()), citation.documentName()))
                    .toList());
        } else if (response != null && response.sources() != null) {
            citations.addAll(response.sources().stream()
                    .filter(Objects::nonNull)
                    .map(source -> new AskDocumentsResponse.Citation(asString(source), null))
                    .toList());
        }
        return new AskDocumentsResponse(response != null ? response.answer() : "", citations);
    }

    public List<SearchChunkResult> searchDocumentChunks(String query, int topK) {
        ApiSearchResult[] response = execute("search_document_chunks", "GET", properties.endpoints().search(), () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(properties.endpoints().search())
                        .queryParam("q", query)
                        .queryParam("maxResults", topK)
                        .queryParam("strategyType", "vector")
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    throw new DocumentEtlApiException(clientResponse.getStatusCode().value(),
                            "Chunk search failed with HTTP " + clientResponse.getStatusCode().value());
                })
                .body(ApiSearchResult[].class));

        if (response == null) {
            return List.of();
        }
        return Arrays.stream(response)
                .map(result -> new SearchChunkResult(
                        firstNonBlank(asString(result.chunkId()), asString(result.documentId())),
                        result.score() != null ? result.score() : result.similarity() != null ? result.similarity() : 0.0d,
                        firstNonBlank(result.content(), result.text())))
                .toList();
    }

    private <T> T execute(String operation, String method, String endpoint, ApiCall<T> call) {
        long startedAtNanos = System.nanoTime();
        log.info("DocumentETL API request started: operation={}, method={}, endpoint={}", operation, method, endpoint);
        try {
            T response = call.execute();
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.info("DocumentETL API request succeeded: operation={}, method={}, endpoint={}, durationMs={}",
                    operation, method, endpoint, durationMs);
            return response;
        } catch (ResourceAccessException ex) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("DocumentETL API request timed out or failed to connect: operation={}, endpoint={}, durationMs={}, error={}",
                    operation, endpoint, durationMs, ex.getMessage());
            throw new DocumentEtlApiException(0, "DocumentETL API is unavailable or timed out: " + ex.getMessage());
        } catch (DocumentEtlApiException ex) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("DocumentETL API request failed: operation={}, endpoint={}, statusCode={}, durationMs={}, error={}",
                    operation, endpoint, ex.statusCode(), durationMs, ex.getMessage());
            throw ex;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        T execute();
    }
}
