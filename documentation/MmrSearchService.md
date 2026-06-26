---
# Technical Documentation: MmrSearchService

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `MmrSearchService` is a specialized retrieval service that implements the **Maximal Marginal Relevance (MMR)** search strategy. Its purpose is to find a set of document chunks that are not only relevant to the user's query but also diverse and non-redundant. This helps prevent the final context from being cluttered with multiple chunks that say the same thing, leading to higher-quality and more comprehensive generated answers.

*   **How does it fit into the RAG/ETL pipeline?**
    This service is a key component in the **retrieval** phase of the RAG pipeline. It acts as a specific implementation or "flavor" of search. Other services, like `ChatService`, call this service to get a high-quality, diverse set of evidence (context) that will be used to generate an answer. It is an abstraction layer that hides the complexity of how different retrieval strategies are orchestrated.

## 2. Core Dependencies & Frameworks
*   **`SearchOrchestrator`**: This is the single, critical dependency. The `MmrSearchService` does not contain any search logic itself. Instead, it delegates the actual work to the `SearchOrchestrator`. It acts as a simplified facade, telling the orchestrator *what* it wants (an MMR search) but not *how* to do it.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   `query` (String): The user's question or search term.
    *   `limit` (int): The maximum number of search results to return.
    *   `MMR_STRATEGY_TYPE` (Constant): A private constant hardcoded to `"mmr"`. This string is the key that tells the `SearchOrchestrator` which retrieval algorithm to execute.

*   **Core Workflows:**
    *   The service has only one method, `search()`.
    *   The workflow is extremely simple:
        1.  It receives a `query` and a `limit` from a calling service (e.g., `ChatService`).
        2.  It immediately calls the `searchOrchestrator.retrieve()` method.
        3.  It passes the `query` and `limit` directly through, and adds the hardcoded strategy type `"mmr"` as the third argument.

*   **Outputs/Side Effects:**
    *   **Return Value**: It returns a `List<SearchResult>`, which is the result passed back directly from the `SearchOrchestrator`. The service does not modify or process this list.
    *   **Side Effects**: This service has no side effects of its own. Any side effects (like logging or database queries) are managed by the downstream `SearchOrchestrator`.

## 4. End-to-End Request Flow (How it runs)
1.  **Request In**: The `ChatService` needs to find context for a user's question and calls `mmrSearchService.search("What are our Q3 earnings?", 5)`.
2.  **Delegation**: The `MmrSearchService` receives the call and immediately invokes `searchOrchestrator.retrieve("What are our Q3 earnings?", 5, "mmr")`.
3.  **Orchestration**: The `SearchOrchestrator` receives the request, identifies the `"mmr"` strategy, and executes the complex logic required for an MMR search (which likely involves an initial vector search followed by a re-ranking process to promote diversity).
4.  **Response Out**: The `SearchOrchestrator` returns a `List<SearchResult>` containing 5 diverse and relevant document chunks. The `MmrSearchService` passes this list directly back to the `ChatService` without inspection or modification.
---
