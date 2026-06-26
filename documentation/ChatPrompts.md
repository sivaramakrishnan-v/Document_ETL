---
# Technical Documentation: ChatPrompts

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    `ChatPrompts` is a utility class that serves as a centralized repository for prompt templates. Its purpose is to store and provide standardized, reusable text prompts that are sent to Large Language Models (LLMs). By centralizing these templates, the application ensures consistency in how it instructs the LLM, making it easier to manage, version, and refine the prompts used in the RAG process.

*   **How does it fit into the RAG/ETL pipeline?**
    This class directly supports the **generation** phase of the RAG pipeline. The templates it holds are the instructions that guide the LLM in generating a final answer. A service will retrieve a template from this class, inject the user's question and the retrieved context (evidence) into it, and then send the completed prompt to the generation model.

## 2. Core Dependencies & Frameworks
*   This class has **no dependencies**. It is a standalone, plain Java utility class that holds static string constants. It is used *by* other services but does not interact with any external libraries or frameworks itself.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   This class does not accept any inputs. It is designed as a static container for prompt strings.

*   **Core Workflows:**
    *   The class contains no active logic or methods. Its sole function is to expose one or more `public static final String` constants.
    *   **`ANSWER_FROM_CONTEXT_TEMPLATE`**: This is the primary component. It is a multi-line string (using a text block) that defines a specific set of instructions for the LLM. The template explicitly tells the model:
        1.  To answer a question using *only* the provided context.
        2.  To politely decline to answer if the information is not available in the context. This is a crucial instruction to prevent the model from hallucinating or making up information.
        3.  It includes placeholders (`{{context}}` and `{{question}}`) that are intended to be replaced with dynamic data at runtime.

*   **Outputs/Side Effects:**
    *   The class produces no output and has no side effects. It simply allows other parts of the application to read its static string constants.

## 4. End-to-End Request Flow (How it runs)
This class is not an active service, so data does not "flow through" it. Instead, it provides a template that other services use as follows:

1.  **Template Retrieval**: A service (e.g., `ChatService`) responsible for generating an answer accesses the `ChatPrompts.ANSWER_FROM_CONTEXT_TEMPLATE` constant.
2.  **Prompt Formatting**: The service uses this template string and replaces the `{{context}}` placeholder with the actual evidence retrieved from the vector database and the `{{question}}` placeholder with the user's original query.
3.  **LLM Invocation**: The fully formatted prompt is then sent as part of a request to the LLM (e.g., via `GenerationModelGateway`) to produce the final answer.
---
