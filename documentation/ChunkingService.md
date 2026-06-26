---
# Technical Documentation: ChunkingService

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `ChunkingService` is a critical component in the "Transform" and "Load" stages of the ETL pipeline. Its primary purpose is to take large blocks of extracted text, break them down into smaller, semantically meaningful segments (chunks), and then generate a vector embedding for each chunk. These embedded chunks are the final, queryable units of information that the RAG system's retrieval mechanism will search through.

*   **How does it fit into the RAG/ETL pipeline?**
    This service operates directly after the `ParsingService`.
    1.  **Input**: It queries for content that has been marked as "PARSED".
    2.  **Process (Transform & Load)**:
        *   It splits the raw text into smaller chunks of a configured size with overlap.
        *   For each chunk, it calls an external service (`VertexAiEmbeddingService`) to compute a vector embedding.
        *   It saves each chunk, along with its embedding and metadata, to the `document_chunks` table.
    3.  **Output**: It updates the original document's status to "COMPLETED", indicating that it has been fully processed and its content is now searchable.

## 2. Core Dependencies & Frameworks
*   **`ParsedContentRepository`**: Used to find documents that have been successfully parsed and are ready for chunking.
*   **`DocumentChunkRepository`**: The main repository for persisting the final `DocumentChunk` entities, including their text, metadata, and vector embeddings, to the database.
*   **`StagedDocumentRepository`**: Used at the end of the process to update the status of the source document to "COMPLETED".
*   **`VertexAiEmbeddingService`**: An essential external dependency that provides access to a machine learning model (like Google's Vertex AI) to convert text chunks into numerical vector embeddings.
*   **`langchain4j.data.document.DocumentSplitter`**: A key component from the LangChain4j library that handles the logic of splitting a large document into smaller text segments based on the configured chunk size and overlap.
*   **`Spring Transaction` (`PlatformTransactionManager`, `TransactionTemplate`)**: Ensures that the chunking process for a single document is atomic. The deletion of old chunks, creation of new chunks, and embedding generation are all wrapped in a single transaction to prevent inconsistent states.
*   **`@Value` Annotation**: Used to inject configuration properties (`app.chunk.size`, `app.chunk.overlap`) from `application.properties`, making the chunking strategy easily configurable.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   The service queries for `ParsedContent` with a status of `"PARSED"`.
    *   `chunkSize`: The target size of each text chunk (in characters).
    *   `chunkOverlap`: The number of characters that should be repeated between adjacent chunks to maintain semantic context at the boundaries.

*   **Core Workflows:**
    1.  **`chunkAllParsedContent()`**: The main public method that orchestrates the batch processing. It fetches all "PARSED" documents and iterates through them, calling `chunkParsedContentInternal` for each one within a dedicated transaction and robust error handling. It maintains detailed logs and metrics about the batch run.
    2.  **`chunkParsedContentInternal()`**: This is the core transactional method for a single document.
        *   **Up-to-Date Check**: It first performs a critical optimization: it checks if chunks already exist for the document's current `contentHash`. If they do, it means the content has not changed, and the entire expensive process of re-chunking and re-embedding is skipped.
        *   **Clear Old Chunks**: If the content is new or has changed, it deletes all existing chunks associated with the `documentId`. This ensures no stale data remains.
        *   **Split**: It uses the `DocumentSplitter` to break the raw text into a list of `TextSegment` objects.
        *   **Embed and Build**: It iterates through each `TextSegment`. For each one, it calls `vertexAiEmbeddingService.embed()` to get the vector embedding. It then constructs a `DocumentChunk` entity, populating it with the text, embedding, content hash, and other metadata.
        *   **Persist**: It saves the complete list of new `DocumentChunk` entities to the database using `saveAll`.
        *   **Finalize Status**: It updates the status of the original `StagedDocument` to `"COMPLETED"`.

*   **Outputs/Side Effects:**
    *   **Return Value**: Returns a list of the `DocumentChunk` entities that were successfully created and saved.
    *   **Side Effects**:
        *   **Database Deletes/Writes**: Deletes old chunks and writes new chunks (with embeddings) to the `document_chunks` table.
        *   **Database Updates**: Updates the status of records in the `staged_documents` table to `"COMPLETED"`.
        *   **External API Calls**: Makes network calls to the Vertex AI service for each chunk, which can incur costs and latency.

## 4. End-to-End Request Flow (How it runs)
1.  **Trigger**: A scheduler calls `chunkingService.chunkAllParsedContent()`.
2.  **Fetch**: The service finds a `ParsedContent` record with status `"PARSED"`. The content hash is `new_hash_123`.
3.  **Execute Transaction**:
    a. The service checks the `document_chunks` table for any chunks with the hash `new_hash_123`. None are found.
    b. It deletes any old chunks for this document ID.
    c. The `DocumentSplitter` breaks the document's text into 15 segments.
    d. The service enters a loop 15 times. In each iteration, it sends a text segment to `VertexAiEmbeddingService` and receives a 768-dimension float array (the embedding). It creates a `DocumentChunk` object with this data.
    e. `documentChunkRepository.saveAll()` is called to save the 15 new chunks to the database in a single batch operation.
    f. The status of the original `StagedDocument` is updated to `"COMPLETED"`.
    g. The transaction is committed.
4.  **Completion**: The service logs a summary of the operation. The document's content is now fully indexed and ready to be used in RAG queries.
---
