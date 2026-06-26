---
# Technical Documentation: VectorSearchService

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `VectorSearchService` is the foundational retrieval service in the RAG pipeline. Its core purpose is to perform a pure **vector similarity search**. It takes a user's text query, converts it into a numerical vector embedding, and then queries a database (likely PostgreSQL with the `pgvector` extension) to find the document chunks with the most similar embeddings. It is the fastest and most direct method of finding semantically relevant content.

*   **How does it fit into the RAG/ETL pipeline?**
    This service is a primary implementation of `RetrievalStrategy` and sits at the very bottom of the **retrieval** stack.
    1.  **Input**: It receives a text query and a limit.
    2.  **Process**:
        *   It uses an `EmbeddingModel` to convert the query into a vector.
        *   It executes a raw SQL query against the database, using the `<=>` (cosine distance) operator to find the nearest neighbors to the query vector.
    3.  **Output**: It returns a list of the most similar document chunks. It also serves as a building block for more complex strategies, like `MmrRetrievalService`, which use it to get an initial set of candidates.

## 2. Core Dependencies & Frameworks
*   **`EmbeddingModel` (from LangChain4j)**: This is a crucial dependency used to convert the user's text query into a `float[]` vector embedding. This model is the bridge between the text world and the vector world.
*   **`JdbcTemplate` (from Spring JDBC)**: The service uses `JdbcTemplate` to execute a raw, hand-optimized SQL query for vector search. This approach is chosen over a higher-level ORM (like JPA/Hibernate) because it provides direct access to the specialized features of `pgvector` (like the `<=>` operator and `vector` type casting), which are essential for performance.
*   **`RetrievalStrategy` (Interface)**: The class implements this interface and is registered with the name `"vector"`, making it the default strategy selectable by the `SearchOrchestrator`.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   `query` (String): The user's search query.
    *   `limit` (int): The maximum number of results to return.

*   **Core Workflows:**
    1.  **`retrieve(String query, int limit)`**: This is the main public method that fulfills the `RetrievalStrategy` contract. It first calls `retrieveCandidates` to get a detailed list of chunks and then maps this list to the simpler `SearchResult` DTO for the caller.
    2.  **`retrieveCandidates(String query, int limit)`**: This method orchestrates the search for a text query. It first calls `embedQuery` to get the vector, then delegates to the overloaded `retrieveCandidates` that works with a raw vector.
    3.  **`embedQuery(String query)`**: A helper method that takes a text query, calls the `embeddingModel` to get the `Embedding` object, and extracts the `float[]` vector from it.
    4.  **`retrieveCandidates(float[] queryEmbedding, int limit)`**: This is the core database interaction method.
        *   It converts the `float[]` query embedding into a string literal format that `pgvector` understands (e.g., `"[0.1, 0.2, ...]"`) using the `toVectorLiteral` helper.
        *   It executes the `VECTOR_SEARCH_SQL` query using `jdbcTemplate.query()`.
        *   It passes the vector literal string as a parameter to the SQL query twice (once for the similarity calculation and once for the `ORDER BY` clause, which is a common pattern for `pgvector` to allow it to use an index).
        *   It maps each row of the `ResultSet` to a `CandidateChunk` object, which includes the full text, metadata, and the chunk's own embedding.

*   **`VECTOR_SEARCH_SQL` (Constant)**: This is a carefully crafted raw SQL query.
    *   It calculates similarity as `1 - (embedding <=> ?::vector)`, which converts cosine *distance* (where 0 is identical) to cosine *similarity* (where 1 is identical).
    *   It joins `text_chunks`, `chunk_embeddings`, and `source_documents` tables, ensuring that it only retrieves chunks from documents that have been fully processed (`status = 'COMPLETED'`).
    *   The `ORDER BY embedding <=> ?::vector` clause is the most critical part, as it tells PostgreSQL to use the HNSW index on the `embedding` column for a fast nearest-neighbor search.

*   **`toVectorLiteral` & `parseVectorLiteral`**: These are utility methods for serializing a `float[]` into the `[... , ...]` string format required by `pgvector` and for parsing that format back into a `float[]` from the database result.

*   **Outputs/Side Effects:**
    *   **Return Value**: Returns a `List<SearchResult>` containing the document chunks most similar to the query.
    *   **Side Effects**:
        *   Makes a network call to the embedding model service.
        *   Executes a read-only query against the database.

## 4. End-to-End Request Flow (How it runs)
1.  **Request In**: The `SearchOrchestrator` calls `vectorSearchService.retrieve("What is the latest security policy?", 10)`.
2.  **Processing**:
    a. The service calls `embedQuery("What is the latest security policy?")`, which in turn calls the `EmbeddingModel` and gets back a 768-dimension float array.
    b. The `retrieveCandidates` method is called with this embedding.
    c. The `toVectorLiteral` method converts the float array into a long string like `"[0.123, -0.456, ...]"`.
    d. `jdbcTemplate` executes the `VECTOR_SEARCH_SQL` query, passing the vector string as a parameter. The database uses its vector index to efficiently find the 10 closest matches.
    e. The results are mapped into a `List<CandidateChunk>`.
    f. This list is then mapped to a `List<SearchResult>`.
3.  **Response Out**: The service returns the list of 10 `SearchResult` objects to the `SearchOrchestrator`.
---
