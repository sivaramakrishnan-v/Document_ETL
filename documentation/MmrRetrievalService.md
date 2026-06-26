---
# Technical Documentation: MmrRetrievalService

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `MmrRetrievalService` is a concrete implementation of the `RetrievalStrategy` interface that provides a complete **Maximal Marginal Relevance (MMR)** search. It combines a standard vector search with a diversity filter to produce a final list of results that is both relevant to the query and internally diverse. It is designed to be a high-quality, general-purpose retrieval method for the RAG system.

*   **How does it fit into the RAG/ETL pipeline?**
    This service is a key component in the **retrieval** phase. It is one of the primary strategies that can be selected and executed by the `SearchOrchestrator`. Its workflow is a two-step process:
    1.  First, it fetches a large number of potentially relevant candidates using a simple vector search.
    2.  Second, it uses the `MmrDiversityFilter` to refine this large list down to a smaller, more diverse set.

## 2. Core Dependencies & Frameworks
*   **`VectorSearchService`**: This service is used to perform the initial, broad retrieval of candidate documents. The `MmrRetrievalService` relies on it to provide the raw material for its filtering process.
*   **`MmrDiversityFilter`**: This is the core engine of the service. After getting the initial candidates, this component is called to perform the actual MMR algorithm, re-ranking the candidates to balance relevance and diversity.
*   **`RetrievalStrategy` (Interface)**: The class implements this interface, which makes it discoverable by the `SearchOrchestrator` and guarantees it adheres to the standard retrieval contract.
*   **`@Service("mmr")`**: This Spring annotation is crucial. It registers this class as a Spring bean and, importantly, gives it the specific name `"mmr"`. This is the key that the `SearchOrchestrator` uses to find and invoke this specific strategy.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   `CANDIDATE_MULTIPLIER` (Constant): A factor of 4 used to determine the size of the initial candidate pool. For example, if the user asks for 10 final results, this service will first fetch `10 * 4 = 40` candidates. This "over-fetching" provides a richer set of documents for the diversity filter to work with.
    *   `DEFAULT_LAMBDA` (Constant): The MMR lambda value is hardcoded to `0.7`. This value was likely chosen as a good default that moderately favors relevance over diversity.

*   **Core Workflows:**
    1.  **`retrieve(String query, int limit)`**: This is the main method that fulfills the `RetrievalStrategy` contract.
        *   **Over-fetch Candidates**: It first calculates the `candidatePoolSize` by multiplying the requested `limit` by the `CANDIDATE_MULTIPLIER`.
        *   **Initial Retrieval**: It calls `vectorSearchService.retrieve()` with the query and the larger `candidatePoolSize` to get a large list of relevant documents.
        *   **Filter for Diversity**: It passes this large list of candidates to the `mmrDiversityFilter.filter()` method, along with the original `limit` and the hardcoded `DEFAULT_LAMBDA`.
        *   **Return Results**: The `MmrDiversityFilter` returns the final, smaller, diverse list of results, which this service immediately returns to the caller (the `SearchOrchestrator`).
    2.  **`selectDiverse(List<SearchResult> candidates, int limit)`**: This is a public helper method that exposes the diversity filtering logic directly. It allows other services to apply MMR filtering to a list of candidates they have already fetched, without needing to re-run the initial vector search.

*   **Outputs/Side Effects:**
    *   **Return Value**: Returns a `List<SearchResult>` of size `limit` that has been filtered for diversity.
    *   **Side Effects**: This service has no side effects of its own. It delegates all I/O (database queries) to the `VectorSearchService`.

## 4. End-to-End Request Flow (How it runs)
1.  **Request In**: The `SearchOrchestrator`, having been asked for the `"mmr"` strategy, calls `mmrRetrievalService.retrieve("Tell me about our cloud infrastructure", 5)`.
2.  **Processing**:
    a. The service calculates `candidatePoolSize = 5 * 4 = 20`.
    b. It calls `vectorSearchService.retrieve("Tell me about our cloud infrastructure", 20)`. The vector service queries the database and returns the top 20 most similar document chunks.
    c. This list of 20 candidates is then passed to `mmrDiversityFilter.filter(candidates, 5, 0.7)`.
    d. The filter runs the MMR algorithm and returns a new list of 5 chunks that are both relevant and diverse.
3.  **Response Out**: The `MmrRetrievalService` returns this final list of 5 chunks to the `SearchOrchestrator`.
---
