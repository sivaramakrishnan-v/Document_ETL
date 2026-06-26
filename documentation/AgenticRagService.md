---
# Technical Documentation: AgenticRagService

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `AgenticRagService` is the primary entry point for executing an "agentic" Retrieval-Augmented Generation (RAG) workflow. Unlike a simple RAG pipeline, this service uses a state machine (a graph) to dynamically decide on and execute a series of steps—such as rewriting a user's query, retrieving evidence, and validating the generated answer—to produce a high-quality, well-supported response. It orchestrates the entire agentic process from receiving a user question to returning a final, grounded answer.

*   **How does it fit into the RAG/ETL pipeline?**
    This service sits at the core of the **retrieval and generation** phase of the RAG pipeline. It consumes a user query and leverages other services to fetch context, generate an answer, and ensure that the answer is factually grounded in the retrieved evidence. It also integrates with tracking services to log the entire workflow for evaluation and debugging.

## 2. Core Dependencies & Frameworks
*   **`RagStateGraphFactory`**: A critical dependency used to construct the compiled state graph that defines the agent's logic and possible paths (e.g., "retrieve," "grade context," "generate answer").
*   **`RagWorkflowCheckpointService`**: Used for lifecycle management of the RAG process. It logs the start, failure, and successful completion of a workflow, creating a persistent record of each agentic run.
*   **`GroundingScoreService`**: After the agent generates a final answer, this service is called to evaluate how well the answer is supported by the retrieved citations. It provides metrics on "groundedness" and citation coverage.
*   **`langgraph4j`**: This external framework provides the foundation for building and executing the state graph (`CompiledGraph`). The agent's logic is defined as a graph of nodes and edges that process and transition between different states (`RagAgentState`).


## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   `question` (String): The user's query that needs to be answered.
    *   `threadId` (String, optional): An identifier for a conversation thread. If not provided, a new one is generated. This allows the agent to potentially maintain context across multiple turns in a conversation.

*   **Core Workflows:**
    1.  **Initialization**: Upon receiving an `ask` request, the service first validates the question.
    2.  **Checkpoint Start**: It initiates a new workflow record using `RagWorkflowCheckpointService`, which logs the initial state of the request (question, thread ID).
    3.  **Graph Execution**: It prepares an input map containing the user query, thread ID, and the new checkpoint ID. This input is passed to the `langgraph4j` compiled graph (`graph.invoke()`).
    4.  **Agentic Processing (Inside the Graph)**: The graph executes a series of steps defined in `RagStateGraphFactory`. This typically includes:
        *   Retrieving relevant documents (evidence).
        *   Grading whether the evidence is sufficient to answer the question.
        *   Generating a draft answer.
        *   Validating the answer against the evidence.
        *   Potentially re-writing the query or re-retrieving if the initial results are poor.
    5.  **Error Handling**: If the graph execution fails, the service catches the exception, marks the workflow checkpoint as "failed," and re-throws the exception.
    6.  **Grounding and Finalization**: Once the graph successfully returns a final state (`RagAgentState`), the service calculates a grounding score for the final answer using `GroundingScoreService`.
    7.  **Checkpoint Completion**: It updates the workflow checkpoint with the grounding score and marks the workflow as complete.

*   **Outputs/Side Effects:**
    *   **Return Value**: The method returns an `AgenticAskResponse` object. This is a comprehensive DTO containing the final answer, the validation outcome, retrieval/rewrite attempt counts, citations, feedback, and the calculated grounding scores.
    *   **Side Effects**: The primary side effect is the creation and updating of a `RagWorkflowCheckpoint` entity in a database, providing a complete audit trail of the agent's execution path and results.

## 4. End-to-End Request Flow (How it runs)
1.  **Request In**: A user calls the `ask()` method with a question like `"What is the new policy on remote work?"`.
2.  **Orchestration**: The `AgenticRagService` starts a checkpoint and invokes the state graph. The graph executes its internal loop: it retrieves relevant policy documents, grades them as relevant, generates a draft answer, and validates that the answer is supported by the documents.
3.  **Response Out**: The graph concludes, and the service calculates a grounding score. It then packages the final answer, the source documents (citations), and performance metrics (like `retrievalAttempts`) into an `AgenticAskResponse` object and returns it to the user. A complete record of this run is stored via the checkpoint service.
---
