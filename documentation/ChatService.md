---
# Technical Documentation: ChatService

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    `ChatService` implements a complete, standard Retrieval-Augmented Generation (RAG) pipeline. Its primary function is to answer a user's question by first retrieving relevant information from a knowledge base and then using that information to generate a coherent, context-backed answer. It also includes robust mechanisms for evaluating the quality of the generated answer and logging the entire transaction for monitoring and analysis.

*   **How does it fit into the RAG/ETL pipeline?**
    This service is the centerpiece of the **retrieval, generation, and evaluation** stages. It orchestrates the flow of data from the user's question to the final answer by:
    1.  **Retrieving** context using `MmrSearchService`.
    2.  **Generating** an answer with `GenerationModelGateway`.
    3.  **Evaluating** the result via `EvaluationService`.
    4.  **Logging** everything to MLflow for MLOps.

## 2. Core Dependencies & Frameworks
*   **`MmrSearchService`**: Used to perform a search for relevant document chunks. It uses the Maximal Marginal Relevance (MMR) algorithm to ensure the retrieved context is both relevant to the query and diverse, preventing redundant information.
*   **`GenerationModelGateway`**: A gateway to an external Large Language Model (LLM). This service is responsible for sending the final, context-enriched prompt to the model and returning its generated answer.
*   **`EvaluationService`**: Called after an answer is generated to assess its quality based on metrics like faithfulness to the context and correctness (if a known "golden answer" is provided).
*   **`MlflowActionTrackingService`**: A crucial dependency for MLOps. It logs the outcome (success or failure), performance metrics (e.g., duration, text length), and key parameters of each `ask` operation to an MLflow tracking server.
*   **`MlflowTraceBridgeService`**: Works in tandem with the tracking service to emit a more detailed, structured trace of the RAG operation (including the question, answer, and retrieved chunks) to MLflow for in-depth debugging and visualization.
*   **`langchain4j.model.input.PromptTemplate`**: A utility from the LangChain4j library used to safely and reliably format the prompt string by injecting the retrieved context and user question into a predefined template from `ChatPrompts`.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   `question` (String): The user's query.
    *   `goldenAnswer` (String, optional): A known-correct answer that can be provided for more rigorous, comparative evaluation.
    *   `CONTEXT_CHUNK_LIMIT` (Constant): A hardcoded limit of 5, defining the maximum number of document chunks to retrieve for the context.

*   **Core Workflows:**
    1.  **Validation**: The service first ensures the `question` is not null or empty.
    2.  **Retrieval**: It calls `mmrSearchService.search()` to fetch the top 5 most relevant and diverse document chunks.
    3.  **Context Construction**: The text from the retrieved chunks is compiled into a single string, separated by newlines. If no chunks are found, a default message (`"No relevant context available."`) is used.
    4.  **Prompt Formatting**: It uses `PromptTemplate` to inject the context string and the user's question into the `ANSWER_FROM_CONTEXT_TEMPLATE`.
    5.  **Answer Generation**: The fully-formed prompt is passed to `generationModelGateway.generate()` to get the final answer from the LLM.
    6.  **Evaluation**: The service calls `evaluationService.evaluateAndLog()` to score the generated answer against the context and the optional `goldenAnswer`.
    7.  **Metrics & Logging**:
        *   It calculates the total execution time.
        *   It gathers extensive metrics (e.g., text lengths, chunk count) and parameters (the question, answer, and source IDs).
        *   It logs the entire transaction as a "successful action" to MLflow, including all metrics and parameters.
        *   It emits a detailed trace for observability.
    8.  **Error Handling**: The entire process is wrapped in a `try-catch` block. If any `RuntimeException` occurs, it logs a "failed action" to MLflow with the error details and then re-throws the exception.

*   **Outputs/Side Effects:**
    *   **Return Value**: It returns a `ChatAnswer` object, which is a simple data carrier containing the final `answer`, a list of `sources` (the IDs of the documents used for context), and the `evaluations` results.
    *   **Side Effects**: The most significant side effect is the comprehensive logging to MLflow. For every call, it creates a run with detailed parameters, metrics, and trace data, enabling thorough monitoring of the RAG system's performance and behavior.

## 4. End-to-End Request Flow (How it runs)
1.  **Request In**: A user calls the `askWithSources("What is our company's policy on parental leave?")` method.
2.  **Processing**:
    *   `ChatService` uses `MmrSearchService` to retrieve the top 5 chunks from internal HR policy documents.
    *   It assembles these chunks into a context block and formats a prompt for the LLM.
    *   `GenerationModelGateway` is called with the prompt, and it returns a generated answer.
    *   `EvaluationService` checks if the answer is factually consistent with the retrieved HR policy text.
    *   `MlflowActionTrackingService` logs the question, answer, source document IDs, evaluation scores, and execution time to MLflow.
3.  **Response Out**: The service returns a `ChatAnswer` object to the caller, containing the generated text about parental leave, the specific document IDs it was based on, and the evaluation scores.
---
