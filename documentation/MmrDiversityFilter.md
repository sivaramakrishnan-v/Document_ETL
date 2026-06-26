---
# Technical Documentation: MmrDiversityFilter

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `MmrDiversityFilter` is a self-contained, reusable component that implements the **Maximal Marginal Relevance (MMR)** algorithm. Its sole purpose is to take a list of search results (which are assumed to be sorted by relevance) and re-rank them to produce a new list that balances relevance with diversity. It is a pure algorithmic component, designed to be used by any service that needs to reduce redundancy in a list of candidates.

*   **How does it fit into the RAG/ETL pipeline?**
    This component is a key building block for the **retrieval** phase. It is not a full retrieval strategy itself but a *filter* that can be applied as part of a larger, more complex strategy. For example, the `MmrRetrievalService` uses this filter as its core engine. By encapsulating the MMR logic in this component, the project avoids code duplication (e.g., the `ChatReviewService` has its own, similar implementation) and makes the core algorithm easily testable and maintainable.

## 2. Core Dependencies & Frameworks
*   This class has **no dependencies** on other services or frameworks. It is a standalone, stateless Spring `@Component` that operates only on the data passed to it as method arguments. It depends only on the `SearchResult` DTO.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   `candidates` (List<SearchResult>): The initial list of search results, which should ideally be sorted by similarity score (relevance).
    *   `topK` (int): The desired number of results in the final, filtered list.
    *   `lambda` (double): The parameter that controls the trade-off between relevance and diversity.
        *   `lambda = 1.0`: The output will be the most relevant items (equivalent to a simple top-K selection).
        *   `lambda = 0.0`: The output will be the most diverse items, with no regard for their initial relevance score.
        *   `lambda = 0.5`: A balance between the two.

*   **Core Workflows:**
    1.  **Initialization**: The `filter` method starts by sanitizing the `lambda` parameter to ensure it's between 0.0 and 1.0. It initializes an empty `selected` list and copies the input candidates into a `remaining` list.
    2.  **Select First Candidate**: It finds the most relevant item in the entire candidate list (the one with the highest similarity score) and moves it from `remaining` to `selected`. This ensures the most relevant document is always included.
    3.  **Iterative Selection Loop**: The method then enters a loop that continues until the `selected` list reaches the desired `topK` size or there are no more candidates. In each iteration:
        *   It iterates through every `candidate` in the `remaining` list.
        *   For each `candidate`, it calculates its MMR score using the formula: `mmr_score = (lambda * relevance) - ((1 - lambda) * max_similarity_to_selected)`.
        *   The `max_similarity_to_selected` is found by comparing the current `candidate`'s embedding to the embeddings of all items already in the `selected` list and taking the highest similarity score. This is the "diversity" penalty.
        *   After calculating the MMR score for all remaining candidates, it identifies the one with the highest score.
        *   This "best" candidate is moved from the `remaining` list to the `selected` list.
    4.  **Completion**: Once the loop finishes, the `selected` list, now containing a diverse and relevant set of results, is returned.

*   **`maxSimilarityToSelected` & `calculateCosineSimilarity`**: These private helper methods provide the mathematical functions needed for the main algorithm. `calculateCosineSimilarity` computes the similarity between two vector embeddings, and `maxSimilarityToSelected` uses it to find the highest similarity between a candidate and the set of already chosen results.

*   **Outputs/Side Effects:**
    *   **Return Value**: Returns a `List<SearchResult>` containing the re-ranked and filtered results. The size of the list will be at most `topK`.
    *   **Side Effects**: This component is stateless and has no side effects. It does not log, access databases, or call other services.

## 4. End-to-End Request Flow (How it runs)
1.  **Request In**: The `MmrRetrievalService` has already performed a vector search and has a list of 100 `SearchResult` candidates. It calls `mmrDiversityFilter.filter(candidates, 10, 0.7)`.
2.  **Processing**:
    a. The filter selects the single most relevant candidate from the 100 and adds it to the `selected` list.
    b. It then loops 9 more times. In each loop, it calculates the MMR score for the 99 (then 98, 97, etc.) remaining candidates. A candidate gets a high score if it has a high initial relevance score *and* is not too similar to the items already in the `selected` list.
    c. The candidate with the best MMR score is chosen and moved to the `selected` list.
3.  **Response Out**: After the loop completes, the filter returns the `selected` list, which now contains 10 items that represent a good balance of relevance to the query and diversity among themselves.
---
