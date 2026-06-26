---
# Technical Documentation: ParsingService

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `ParsingService` is a key component in the "Extract" phase of the project's ETL (Extract, Transform, Load) pipeline. Its sole responsibility is to take raw, unstructured files (such as PDFs, Word documents, etc.) that have been marked as "STAGED" and extract their plain text content. It acts as a universal content extractor, preparing diverse file formats for downstream processing.

*   **How does it fit into the RAG/ETL pipeline?**
    This service is one of the first steps in the data ingestion pipeline. Its workflow is as follows:
    1.  **Input**: It reads records from a `staged_documents` table, which points to files on disk.
    2.  **Process (Extract)**: It uses the Apache Tika library to parse these files and extract raw text.
    3.  **Output**: It saves the extracted text into a `parsed_content` table and updates the original document's status to "PARSED", signaling that it's ready for the next step (chunking).

## 2. Core Dependencies & Frameworks
*   **`StagedDocumentRepository`**: A Spring Data repository used to find documents that are ready to be parsed (i.e., have a status of "STAGED"). It is also used to update the document's status to "PARSED" upon successful extraction.
*   **`ParsedContentRepository`**: The repository for saving the results of the parsing operation. It stores the extracted raw text and associated metadata in the `parsed_content` table.
*   **`Apache Tika`**: The core third-party library for content extraction. Tika is a powerful and versatile toolkit that can detect and extract text and metadata from thousands of different file types, abstracting away the complexity of handling individual formats.
*   **`Spring Transaction` (`PlatformTransactionManager`, `TransactionTemplate`)**: Used to ensure data consistency. The processing of each file (extracting text, saving it, and updating the staged document's status) is wrapped in a single database transaction. This guarantees that the operation either completes fully or is rolled back, preventing partial or corrupt data states.
*   **`SLF4J`**: A logging facade used to provide visibility into the parsing process, logging which files are being processed and reporting successes or failures.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   The service does not take direct method arguments. Instead, its input is the state of the `staged_documents` table in the database. It actively queries for documents with the status `"STAGED"`.

*   **Core Workflows:**
    1.  **`processStagedFiles()`**: This is the main public method. It fetches all documents marked as "STAGED" from the database. It then iterates through them one by one, wrapping each in a `try-catch` block to ensure that a failure on one file does not stop the entire batch. The processing for each file is delegated to `processSingleStagedDocument` within a new database transaction.
    2.  **`processSingleStagedDocument()`**: This private method contains the logic for a single file. It reads the file path from the `StagedDocument` object and uses `tika.parseToString()` to extract the text content. If Tika fails, an exception is thrown, which causes the transaction to roll back.
    3.  **`upsertParsedContent()`**: This method handles the database persistence logic.
        *   It first checks if parsed content already exists for the given document ID.
        *   **Update (Upsert) Path**: If it exists, the existing `ParsedContent` record is updated with the new text, a new content hash, and a fresh timestamp. This is useful for reprocessing files that have changed.
        *   **Insert Path**: If it does not exist, a new `ParsedContent` entity is created.
        *   After saving the `ParsedContent`, it critically updates the status of the `StagedDocument` to `"PARSED"`. This state change prevents the file from being picked up and processed again in future runs.

*   **Outputs/Side Effects:**
    *   **Return Value**: The `processStagedFiles` method returns a `List<ParsedContent>` containing all the content that was successfully parsed and saved during the run.
    *   **Side Effects**:
        *   **Database Writes**: Creates or updates records in the `parsed_content` table.
        *   **Database Updates**: Modifies records in the `staged_documents` table, changing their status from `"STAGED"` to `"PARSED"`.
        *   **File System Reads**: Reads the content of files from the local disk.

## 4. End-to-End Request Flow (How it runs)
1.  **Trigger**: A scheduler or another service calls `parsingService.processStagedFiles()`.
2.  **Fetch**: The service queries the database and finds a `StagedDocument` with `status = "STAGED"`, pointing to a file like `/app/data/project-brief.docx`.
3.  **Execute Transaction**:
    a. A new database transaction begins.
    b. `tika.parseToString()` is called with the file path. Tika identifies it as a Word document, extracts its text content, and returns a single string.
    c. `upsertParsedContent` is called. It sees no existing entry for this document in the `parsed_content` table.
    d. A new `ParsedContent` record is created with the extracted text and saved to the database.
    e. The original `StagedDocument`'s status is updated to `"PARSED"` and saved.
    f. The transaction is successfully committed.
4.  **Completion**: The service logs a success message and moves to the next file. The extracted text is now available for the `ChunkingService` to process.
---
