---
# Technical Documentation: ChatReviewService

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `ChatReviewService` is a highly specialized, "white-box" version of the RAG pipeline, designed for diagnostics, evaluation, and tuning. Unlike `ChatService`, which provides a simple answer, this service executes a multi-stage retrieval process (Vector Search -> MMR -> Reranker) and returns a detailed trace of every intermediate step. Its purpose is to give developers and MLOps engineers deep visibility into how the retrieval system is behaving, allowing them to experiment with parameters and understand the impact of each stage.

*   **How does it fit into the RAG/ETL pipeline?**
    This service is a **diagnostic and evaluation tool** that runs parallel to the main RAG pipeline. It combines several retrieval and generation components to provide a complete, end-to-end trace:
    1.  **Initial Retrieval**: Uses `VectorSearchService` to get a large set of candidate documents.
    2.  **Diversity Re-ranking**: Implements its own Maximal Marginal Relevance (MMR) logic to create a diverse subset of those candidates.
    3.  **Relevance Re-ranking**: Uses `RerankerRetrievalService` to perform a final, fine-grained relevance sort.
    4.  **Generation & Evaluation**: Generates an answer from the final context and evaluates it, similar to `ChatService`.
    5.  **Trace Logging**: Logs extensive metrics and parameters to MLflow.

## 2. Core Dependencies & Frameworks
*   **`VectorSearchService`**: Called first to perform a broad, initial vector similarity search to gather a large pool of candidate chunks.
*   **`RerankerRetrievalService`**: Called in the final retrieval stage to take a smaller, diversified set of chunks and re-order them based on a more powerful, cross-encoder-based relevance model.
*   **`GenerationModelGateway`**: Used to generate the final answer from the context that survives the multi-stage retrieval process.
*   **`EvaluationService`**: Used to score the quality of the final generated answer.
*   **`MlflowActionTrackingService` & `MlflowTraceBridgeService`**: Used for extensive logging of the entire process, including all input parameters (like `topK`, `lambda`), intermediate results, and final evaluation scores to MLflow.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   `question` (String): The user's query.
    *   `goldenAnswer` (String, optional): A known-correct answer for evaluation.
    *   `topK` (int): The final number of chunks to use for the context.
    *   `candidateK` (int): The larger number of initial candidates to fetch from the vector search.
    *   `lambda` (double): The MMR lambda parameter, controlling the trade-off between relevance and diversity (0.0 = max diversity, 1.0 = max relevance).

*   **Core Workflows:**
    1.  **Parameter Normalization**: The service first sanitizes the input parameters to ensure they are within valid ranges (e.g., `candidateK` must be at least as large as `topK`).
    2.  **Stage 1: Vector Search**: It calls `vectorSearchService.retrieve()` to fetch `candidateK` initial results.
    3.  **Stage 2: MMR Re-ranking**: It calls the private `buildMmrTrace()` method. This method implements the MMR algorithm from scratch: it iteratively selects chunks that maximize a combination of similarity to the query and dissimilarity to already selected chunks. It returns a detailed trace of this process.
    4.  **Stage 3: Reranker Re-ranking**: The results from the MMR stage are passed to `rerankerRetrievalService.rerankWithDetails()`, which performs the final relevance scoring and returns a trace of the reranker scores.
    5.  **Answer Generation**: The `topK` results from the reranker are used to build the final context, which is then used to generate an answer via `GenerationModelGateway`.
    6.  **Evaluation & Logging**: The answer is evaluated, and a comprehensive set of metrics and parameters from all three retrieval stages is logged to MLflow.
    7.  **Error Handling**: The entire process is wrapped in a `try-catch` block to log failures to MLflow.

*   **`buildMmrTrace()` & `cosineSimilarity()`**: These private methods contain a from-scratch implementation of the MMR algorithm. `buildMmrTrace` iteratively builds the diverse set of results, and `cosineSimilarity` is a mathematical utility function required to calculate the diversity between chunk embeddings.

*   **Outputs/Side Effects:**
    *   **Return Value**: Returns a `ChatReviewAnswer` object. This is a large data-transfer object that contains not only the final answer but also the full, unabridged lists of results from each stage of the retrieval pipeline (the vector candidates, the MMR trace, and the reranker trace).
    *   **Side Effects**: Logs a highly detailed run to MLflow, capturing all input parameters and output metrics, making it an invaluable tool for hyperparameter tuning and debugging the retrieval process.

## 4. End-to-End Request Flow (How it runs)
1.  **Request In**: A developer wanting to debug the pipeline calls `askWithTrace("Why was our project delayed?", null, topK=5, candidateK=50, lambda=0.5)`.
2.  **Processing**:
    a. The service fetches 50 candidate chunks using `VectorSearchService`.
    b. The internal MMR logic processes the 50 candidates, selecting a diverse subset of 10.
    c. These 10 chunks are passed to the `RerankerRetrievalService`, which re-sorts them for relevance.
    d. The top 5 chunks from the reranker are used to build a context.
    e. An answer is generated from this context.
    f. The answer is evaluated.
    g. A run is logged to MLflow with parameters `top_k=5`, `candidate_k=50`, `mmr_lambda=0.5`, and metrics about the size of the output from each stage.
3.  **Response Out**: The service returns a `ChatReviewAnswer` object. The developer can inspect `getVectorTopK()` (list of 50), `getMmrOutput()` (list of 10), and `getRerankerOutput()` (list of 10 with new scores) to see exactly how the context was selected and refined at each step.
---
