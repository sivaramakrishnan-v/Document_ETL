---
# Technical Documentation: SearchOrchestrator

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `SearchOrchestrator` is a central routing and delegation service that sits at the heart of the retrieval system. Its purpose is to manage multiple, different retrieval algorithms (`RetrievalStrategy` implementations) and execute the correct one based on a string identifier. It acts as a single, unified entry point for any part of the application that needs to perform a search, abstracting away the details of which specific search algorithm is being used.

*   **How does it fit into the RAG/ETL pipeline?**
    This service is the brain of the **retrieval** phase. It doesn't perform any retrieval itself but orchestrates the process.
    1.  **Input**: It receives a query, a limit, and a `strategyType` string (e.g., `"mmr"`, `"vector"`).
    2.  **Process (Orchestrate)**: It looks up the requested `strategyType` in an internal map to find the corresponding `RetrievalStrategy` bean.
    3.  **Output (Delegate)**: It calls the `retrieve` method on the selected strategy bean and returns the results. This embodies the Strategy design pattern, allowing for dynamic selection of algorithms at runtime.

## 2. Core Dependencies & Frameworks
*   **`Map<String, RetrievalStrategy>`**: This is the most critical dependency. Spring's dependency injection mechanism automatically populates this map with all beans that implement the `RetrievalStrategy` interface. The key of the map is the bean's name (e.g., `"vectorSearchService"`), and the value is the bean instance itself. This is how the orchestrator discovers all available strategies.
*   **`RetrievalStrategy` (Interface)**: The orchestrator depends on this interface to guarantee that every strategy object it finds will have a `retrieve(query, limit)` method it can call.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   `query` (String): The user's search query.
    *   `limit` (int): The maximum number of results to return.
    *   `strategyType` (String): A string identifying which retrieval algorithm to use (e.g., `"mmr"`, `"vector"`, `"reranker"`).

*   **Core Workflows:**
    1.  **Input Validation**: The `retrieve` method first validates that the `query` is not blank and the `limit` is positive.
    2.  **Strategy Normalization**: It calls `normalizeStrategyType` to clean up the input `strategyType`. This is a crucial step for user-friendliness.
        *   If the type is null or blank, it defaults to `"vector"`.
        *   It converts the type to lowercase.
        *   It uses the private `StrategyType` enum to map common aliases (e.g., `"semantic"`, `"vector-search"`) to the canonical bean name (`"vector"`). This makes the API more forgiving.
    3.  **Strategy Selection**: It uses the normalized strategy type as a key to look up the corresponding `RetrievalStrategy` object in its `retrievalStrategies` map.
    4.  **Error Handling**: If no strategy is found for the given key, it throws an `IllegalArgumentException` with a helpful message listing all available, supported strategies.
    5.  **Delegation**: If a strategy is found, it calls the `retrieve(query, limit)` method on that strategy object and returns the result directly.

*   **`StrategyType` Enum**: This private enum is a powerful internal component that provides two key functions:
    *   It defines the canonical names for the core strategies (`VECTOR`, `MMR`, etc.).
    *   The `fromAlias` static method acts as a flexible mapping layer, allowing users to use various intuitive names for the same underlying strategy.

*   **Outputs/Side Effects:**
    *   **Return Value**: Returns a `List<SearchResult>` passed through from the selected concrete strategy.
    *   **Side Effects**: This service has no side effects of its own. All side effects are produced by the strategy it delegates to.

## 4. End-to-End Request Flow (How it runs)
1.  **Request In**: The `MmrSearchService` calls `searchOrchestrator.retrieve("What is our revenue?", 10, "mmr")`.
2.  **Processing**:
    a. The orchestrator validates the inputs.
    b. `normalizeStrategyType("mmr")` is called, which confirms `"mmr"` is a valid type and returns it.
    c. The orchestrator looks up `"mmr"` in its `retrievalStrategies` map and finds the `MmrRetrievalService` bean.
    d. It then calls `mmrRetrievalService.retrieve("What is our revenue?", 10)`.
3.  **Delegation & Response**: The `MmrRetrievalService` executes its logic (which involves vector search and MMR filtering) and returns a `List<SearchResult>`. The `SearchOrchestrator` receives this list and passes it straight back to the `MmrSearchService`.
---
