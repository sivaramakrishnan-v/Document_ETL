# DocumentETL MCP Server

Java MCP server module that exposes DocumentETL through MCP tools while running as a separate process from the main Spring Boot application.

## Architecture

```text
MCP Client
  -> DocumentETL Java MCP Server (this module, STDIO)
  -> DocumentETL REST APIs
  -> Parsing -> Chunking -> Embeddings -> pgvector -> Retrieval -> LLM
```

## Prerequisites

- Java 21
- Maven 3.9+
- DocumentETL Spring Boot app running on `http://localhost:8080`
- PostgreSQL, Kafka, Vertex AI, and other DocumentETL runtime dependencies configured for the main app

## Start The Spring Boot App

From the repository root:

```powershell
.\mvnw.cmd spring-boot:run
```

The current DocumentETL app provides:

- `POST /api/chat/ask` for RAG questions
- `GET /api/search?q=...&maxResults=...&strategyType=vector` for retrieval
- `GET /api/etl/v2/status` for aggregate pipeline status
- `GET /api/etl/v2/stage` for server-side document staging

Note: the current repository README states that browser upload has not yet been implemented. This MCP module defaults to `POST /api/documents/upload` for multipart upload and `GET /api/documents/{documentId}/status` for per-document status so it can work once those REST endpoints exist. Override these paths if your app exposes different endpoints.

## Configure The MCP Server

Default `src/main/resources/application.yaml`:

```yaml
document-etl:
  base-url: http://localhost:8080
  connect-timeout: 5s
  read-timeout: 60s
  endpoints:
    upload: /api/documents/upload
    status: /api/documents/{documentId}/status
    ask: /api/chat/ask
    search: /api/search
```

Override values with Spring Boot arguments or environment variables, for example:

```powershell
$env:DOCUMENT_ETL_BASE_URL = "http://localhost:8080"
$env:DOCUMENT_ETL_ENDPOINTS_UPLOAD = "/api/documents/upload"
```

## Start The MCP Server

From this module:

```powershell
cd mcp-server
..\mvnw.cmd spring-boot:run
```

Or build and run the jar:

```powershell
cd mcp-server
..\mvnw.cmd clean package
java -jar target\mcp-server-0.0.1-SNAPSHOT.jar
```

The server uses STDIO transport. Logs are written to stderr so stdout remains reserved for MCP JSON-RPC traffic.

## MCP Client Configuration Example

```json
{
  "mcpServers": {
    "document-etl": {
      "command": "java",
      "args": [
        "-jar",
        "C:/Users/niyan/IdeaProjects/Java_Practice/DocumentETL/mcp-server/target/mcp-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

## Tools

### `upload_document`

Input:

```json
{
  "filePath": "C:/docs/banking_architecture.pdf"
}
```

Output:

```json
{
  "documentId": "12345",
  "fileName": "banking_architecture.pdf",
  "status": "UPLOADED"
}
```

### `get_document_status`

Input:

```json
{
  "documentId": "12345"
}
```

Output:

```json
{
  "documentId": "12345",
  "status": "READY",
  "chunks": 450,
  "embeddingsGenerated": true
}
```

Supported MCP-facing states are `UPLOADED`, `PARSING`, `CHUNKING`, `EMBEDDING`, `READY`, and `FAILED`.

### `ask_uploaded_documents`

Input:

```json
{
  "question": "What are the main risks in this architecture?",
  "documentId": "12345"
}
```

`documentId` is optional. The current `/api/chat/ask` endpoint does not use document scoping, but the MCP client forwards it for compatible REST endpoints.

Output:

```json
{
  "answer": "...",
  "citations": [
    {
      "chunkId": "c1",
      "documentName": "banking_architecture.pdf"
    }
  ]
}
```

If the existing chat endpoint returns `sources` as document IDs, they are mapped into citation `chunkId` values with `documentName: null`.

### `search_document_chunks`

Input:

```json
{
  "query": "credit risk scoring",
  "topK": 5
}
```

Output:

```json
[
  {
    "chunkId": "c1",
    "score": 0.92,
    "content": "..."
  }
]
```

## Error Handling

Tool-level errors are returned as MCP `CallToolResult` responses with `isError=true` and JSON content:

```json
{
  "error": {
    "code": "FILE_NOT_FOUND",
    "message": "File not found: C:/docs/missing.pdf",
    "details": {}
  }
}
```

Handled error codes include:

- `FILE_NOT_FOUND`
- `VALIDATION_ERROR`
- `EMPTY_QUERY`
- `DOCUMENT_ETL_API_ERROR`
- `INTERNAL_ERROR`

## Implementation Notes

- Uses official MCP Java SDK artifact `io.modelcontextprotocol.sdk:mcp`.
- Uses Spring Boot only for configuration, dependency injection, validation, logging, and `RestClient`.
- Runs as a separate process and does not depend on DocumentETL application classes.
- Uses constructor injection and Java 21 records for DTOs.
