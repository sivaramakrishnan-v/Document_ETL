package com.documentetl.mcp.tools;

import com.documentetl.mcp.client.DocumentEtlApiException;
import com.documentetl.mcp.client.DocumentEtlClient;
import com.documentetl.mcp.model.AskDocumentsRequest;
import com.documentetl.mcp.model.SearchChunksRequest;
import com.documentetl.mcp.model.UploadDocumentRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class DocumentEtlTools {

    private static final Logger log = LoggerFactory.getLogger(DocumentEtlTools.class);

    private final DocumentEtlClient documentEtlClient;
    private final ObjectMapper objectMapper;

    public DocumentEtlTools(DocumentEtlClient documentEtlClient, ObjectMapper objectMapper) {
        this.documentEtlClient = documentEtlClient;
        this.objectMapper = objectMapper;
    }

    public List<McpServerFeatures.SyncToolSpecification> toolSpecifications() {
        return List.of(
                uploadDocumentTool(),
                getDocumentStatusTool(),
                askUploadedDocumentsTool(),
                searchDocumentChunksTool()
        );
    }

    private McpServerFeatures.SyncToolSpecification uploadDocumentTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("upload_document")
                        .description("Upload a local document file into DocumentETL for ingestion.")
                        .inputSchema(schema(
                                Map.of("filePath", stringProperty("Absolute or current-process-relative path to the document file.")),
                                List.of("filePath")))
                        .build())
                .callHandler((exchange, request) -> executeTool("upload_document", () -> {
                    UploadDocumentRequest arguments = convertArguments(request.arguments(), UploadDocumentRequest.class);
                    if (arguments.filePath() == null || arguments.filePath().isBlank()) {
                        return toolError("VALIDATION_ERROR", "filePath must not be blank");
                    }
                    Path filePath = Path.of(arguments.filePath()).toAbsolutePath().normalize();
                    if (!Files.isRegularFile(filePath)) {
                        return toolError("FILE_NOT_FOUND", "File not found: " + filePath);
                    }
                    return toolSuccess(documentEtlClient.uploadDocument(filePath));
                }))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification getDocumentStatusTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("get_document_status")
                        .description("Fetch ingestion status for a previously uploaded DocumentETL document.")
                        .inputSchema(schema(
                                Map.of("documentId", stringProperty("Document identifier returned by upload_document.")),
                                List.of("documentId")))
                        .build())
                .callHandler((exchange, request) -> executeTool("get_document_status", () -> {
                    String documentId = stringArgument(request.arguments(), "documentId");
                    if (documentId == null || documentId.isBlank()) {
                        return toolError("VALIDATION_ERROR", "documentId must not be blank");
                    }
                    return toolSuccess(documentEtlClient.getDocumentStatus(documentId));
                }))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification askUploadedDocumentsTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("ask_uploaded_documents")
                        .description("Ask a question against uploaded documents, optionally constrained to one documentId.")
                        .inputSchema(schema(
                                Map.of(
                                        "question", stringProperty("Question to answer from ingested document content."),
                                        "documentId", stringProperty("Optional document identifier to scope retrieval.")),
                                List.of("question")))
                        .build())
                .callHandler((exchange, request) -> executeTool("ask_uploaded_documents", () -> {
                    AskDocumentsRequest arguments = convertArguments(request.arguments(), AskDocumentsRequest.class);
                    if (arguments.question() == null || arguments.question().isBlank()) {
                        return toolError("EMPTY_QUERY", "question must not be blank");
                    }
                    return toolSuccess(documentEtlClient.askUploadedDocuments(arguments.question(), arguments.documentId()));
                }))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification searchDocumentChunksTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("search_document_chunks")
                        .description("Search DocumentETL vector chunks and return matching chunk content with scores.")
                        .inputSchema(schema(
                                Map.of(
                                        "query", stringProperty("Search query."),
                                        "topK", Map.of(
                                                "type", "integer",
                                                "description", "Maximum number of chunks to return.",
                                                "minimum", 1,
                                                "maximum", 50,
                                                "default", 5)),
                                List.of("query")))
                        .build())
                .callHandler((exchange, request) -> executeTool("search_document_chunks", () -> {
                    SearchChunksRequest arguments = convertArguments(request.arguments(), SearchChunksRequest.class);
                    if (arguments.query() == null || arguments.query().isBlank()) {
                        return toolError("EMPTY_QUERY", "query must not be blank");
                    }
                    int topK = arguments.topK() == null ? 5 : arguments.topK();
                    if (topK < 1 || topK > 50) {
                        return toolError("VALIDATION_ERROR", "topK must be between 1 and 50");
                    }
                    return toolSuccess(documentEtlClient.searchDocumentChunks(arguments.query(), topK));
                }))
                .build();
    }

    private McpSchema.CallToolResult executeTool(String toolName, ToolCall call) {
        long startedAtNanos = System.nanoTime();
        log.info("MCP tool invocation started: tool={}", toolName);
        try {
            McpSchema.CallToolResult result = call.call();
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.info("MCP tool invocation completed: tool={}, durationMs={}, isError={}", toolName, durationMs, result.isError());
            return result;
        } catch (DocumentEtlApiException ex) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("MCP tool invocation failed via API: tool={}, statusCode={}, durationMs={}, error={}",
                    toolName, ex.statusCode(), durationMs, ex.getMessage());
            return toolError("DOCUMENT_ETL_API_ERROR", ex.getMessage(), Map.of("statusCode", ex.statusCode()));
        } catch (RuntimeException ex) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("MCP tool invocation failed: tool={}, durationMs={}, error={}", toolName, durationMs, ex.getMessage(), ex);
            return toolError("INTERNAL_ERROR", ex.getMessage());
        }
    }

    private <T> T convertArguments(Map<String, Object> arguments, Class<T> type) {
        return objectMapper.convertValue(arguments == null ? Map.of() : arguments, type);
    }

    private static String stringArgument(Map<String, Object> arguments, String key) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return null;
        }
        return arguments.get(key).toString();
    }

    private static McpSchema.JsonSchema schema(Map<String, Object> properties, List<String> required) {
        return new McpSchema.JsonSchema("object", properties, required, false, Map.of(), Map.of());
    }

    private static Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    private McpSchema.CallToolResult toolSuccess(Object body) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(writeJson(body))))
                .build();
    }

    private McpSchema.CallToolResult toolError(String code, String message) {
        return toolError(code, message, Map.of());
    }

    private McpSchema.CallToolResult toolError(String code, String message, Map<String, Object> details) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(writeJson(Map.of(
                        "error", Map.of(
                                "code", code,
                                "message", message == null ? "Unknown error" : message,
                                "details", details
                        )
                )))))
                .isError(true)
                .build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize MCP tool response", ex);
        }
    }

    @FunctionalInterface
    private interface ToolCall {
        McpSchema.CallToolResult call();
    }
}
