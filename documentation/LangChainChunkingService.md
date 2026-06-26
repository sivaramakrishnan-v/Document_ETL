---
# Technical Documentation: LangChainChunkingService

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `LangChainChunkingService` is an alternative implementation of the chunking and embedding process, built entirely using the **LangChain4j library's native components**. Its purpose is to leverage LangChain4j's high-level abstractions (`DocumentSplitter`, `EmbeddingStore`, `EmbeddingStoreIngestor`) to perform the same task as the original `ChunkingService`. This approach can lead to more concise, declarative code and allows the project to stay within the LangChain4j ecosystem.

*   **How does it fit into the RAG/ETL pipeline?**
    This service is a drop-in replacement for the `ChunkingService`, operating in the **Transform and Load** stages of the ETL pipeline.
    1.  **Input**: It takes a `documentId`, the `rawText` of a document, and its `contentHash`.
    2.  **Process**: It checks if the document is already up-to-date. If not, it deletes old chunks and then uses an `EmbeddingStoreIngestor` to orchestrate the splitting, embedding, and storing of new chunks.
    3.  **Output**: It persists the new chunks and their embeddings directly into the database via a custom `EmbeddingStore` implementation.

## 2. Core Dependencies & Frameworks
*   **`langchain4j`**: This is the primary dependency. The service makes extensive use of its core classes:
    *   `DocumentSplitters`: A factory for creating document splitters.
    *   `EmbeddingModel`: The interface for an embedding model.
    *   `EmbeddingStore`: The interface for a vector store.
    *   `EmbeddingStoreIngestor`: The high-level orchestrator that ties the splitter, model, and store together.
*   **`JdbcTemplate`**: Used for direct, low-level database operations. It's used to check for existing chunks, delete stale chunks, and is the core of the custom `EmbeddingStore` implementation.
*   **`ObjectProvider<EmbeddingModel>`**: Instead of a direct dependency, this service uses an `ObjectProvider`. This allows the service to lazily request the `EmbeddingModel` bean only when it's actually needed, which can help manage bean lifecycle and availability.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   `documentId` (Long): The ID of the document being processed.
    *   `rawText` (String): The full text content of the document.
    *   `contentHash` (String): The hash of the document's content, used for up-to-date checks.
    *   `CHUNK_SIZE` / `CHUNK_OVERLAP`: Hardcoded constants defining the chunking strategy.

*   **Core Workflows:**
    1.  **`process(...)`**: The main transactional method.
        *   **Up-to-Date Check**: It first queries the database to see if chunks with the current `contentHash` already exist for the given `documentId`. If so, it logs a message and returns immediately, avoiding redundant work.
        *   **Delete Stale Chunks**: If the document is out of date, it deletes all old chunks associated with the `documentId`.
        *   **Instantiate LangChain Components**:
            *   It creates a `DocumentSplitter` using `DocumentSplitters.recursive()`.
            *   It creates an instance of its own private, inner class `JdbcDocumentChunkEmbeddingStore`, which is a custom implementation of LangChain4j's `EmbeddingStore` interface.
            *   It lazily fetches an `EmbeddingModel` instance.
        *   **Create and Run Ingestor**: It builds an `EmbeddingStoreIngestor`, configuring it to use the custom splitter, embedding model, and embedding store. The `ingestor.ingest(document)` call triggers the entire pipeline: the ingestor gets the segments from the splitter, sends them to the embedding model, and passes the resulting embeddings and segments to the `EmbeddingStore` for persistence.

*   **`JdbcDocumentChunkEmbeddingStore` (Inner Class)**: This is the most complex part of the service. It's a custom implementation of the `EmbeddingStore<TextSegment>` interface that bridges the gap between LangChain4j's abstract world and the project's specific database schema.
    *   It holds a `JdbcTemplate` instance to communicate with the database.
    *   The `addAll(...)` method is the core of its logic. It receives lists of embeddings and text segments from the `EmbeddingStoreIngestor`.
    *   It iterates through them, and for each pair, it constructs and executes a raw `INSERT` SQL statement to save the chunk text, embedding (as a `vector` literal), and other metadata to the `document_etl.document_chunks` table.
    *   It performs validation to ensure the embedding dimensions are correct and the document ID in the metadata matches the one being processed.

*   **Outputs/Side Effects:**
    *   **Return Value**: The `process` method is `void`.
    *   **Side Effects**:
        *   Performs database reads, deletes, and inserts on the `document_etl.document_chunks` table.
        *   Makes network calls to the embedding model service for each new chunk.

## 4. End-to-End Request Flow (How it runs)
1.  **Request In**: An orchestrator service calls `langChainChunkingService.process(123, "some long text...", "new_hash_abc")`.
2.  **Processing**:
    a. The service queries the DB and finds that the existing chunks for document 123 have a different hash.
    b. It executes a `DELETE` statement to remove the old chunks.
    c. It creates an `EmbeddingStoreIngestor`.
    d. It calls `ingestor.ingest()`.
    e. **Inside the Ingestor**:
        i. The `DocumentSplitter` breaks the text into segments.
        ii. The ingestor sends these segments to the `EmbeddingModel`, which returns a list of vector embeddings.
        iii. The ingestor calls the `addAll` method on the custom `JdbcDocumentChunkEmbeddingStore` with the segments and embeddings.
    f. **Inside the Embedding Store**:
        i. The `addAll` method loops through the segments and embeddings.
        ii. In each loop, it executes an `INSERT` statement using `JdbcTemplate` to save a new row to the `document_chunks` table.
3.  **Completion**: The transaction commits. The document is now chunked, embedded, and stored in the database, all orchestrated by LangChain4j components.
---
