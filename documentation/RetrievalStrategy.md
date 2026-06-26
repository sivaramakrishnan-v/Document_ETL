---
# Technical Documentation: RetrievalStrategy

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    `RetrievalStrategy` is a Java interface, not a class. Its purpose is to define a formal contract or a common blueprint for all retrieval-related services in the application. By defining a single method, `retrieve(String query, int limit)`, it ensures that any class implementing this interface will have a consistent way to be called. This promotes a clean, plug-and-play architecture where different retrieval algorithms can be developed and swapped without changing the code that uses them.

*   **How does it fit into the RAG/ETL pipeline?**
    This interface is the central abstraction for the **retrieval** phase of the RAG pipeline. It embodies the Strategy design pattern. The `SearchOrchestrator` uses this interface to work with various retrieval algorithms (like simple vector search, MMR, etc.) in a uniform way. Concrete classes like `VectorSearchService` and `MmrRetrievalService` will implement this interface, providing the actual logic for the `retrieve` method.

## 2. Core Dependencies & Frameworks
*   This interface has **no dependencies** on frameworks or other services. It only depends on the `SearchResult` DTO, which defines the data structure for a single search result item.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   The interface defines one method, `retrieve`, which accepts two parameters:
        *   `query` (String): The user's question or search term.
        *   `limit` (int): The maximum number of results to return.

*   **Core Workflows:**
    *   As an interface, it has no implementation and therefore no workflow. It only specifies the method signature that all concrete retrieval strategies must implement. Any class that declares `implements RetrievalStrategy` must provide a public method with the exact signature `List<SearchResult> retrieve(String query, int limit)`.

*   **Outputs/Side Effects:**
    *   **Return Value**: The interface mandates that the `retrieve` method must return a `List<SearchResult>`.
    *   **Side Effects**: The interface itself specifies no side effects. Any side effects (like database queries or logging) are the responsibility of the classes that implement it.

## 4. End-to-End Request Flow (How it runs)
This interface is not an active service and cannot be run directly. Instead, it defines a pattern that other services follow. Here is how it is used in the broader system:

1.  **Implementation**: A developer creates a new class, `public class SimpleVectorSearch implements RetrievalStrategy`, and writes the logic for a basic vector search inside the `retrieve` method.
2.  **Configuration**: In a configuration class, this new service is mapped to a key, for example: `retrievalStrategies.put("simple_vector", new SimpleVectorSearch())`.
3.  **Orchestration**: A user's request comes into the `SearchOrchestrator` with the strategy key `"simple_vector"`.
4.  **Execution**: The `SearchOrchestrator` looks up the `RetrievalStrategy` object associated with the key and calls its `retrieve()` method, confident that the method exists and has the correct signature. It does not need to know that it is talking to a `SimpleVectorSearch` object specifically; it only knows it is talking to a `RetrievalStrategy`.
---
