---
# Technical Documentation: StagingService

## 1. High-Level Overview
*   **What is the core business/architectural purpose of this specific service?**
    The `StagingService` is the very first step in the ETL pipeline. Its purpose is to scan a designated local directory for files, identify new or modified documents, and create or update corresponding records in a database. This process, known as "staging," effectively registers files into the system, making them available for subsequent processing steps like parsing and chunking. It is designed to be efficient, avoiding reprocessing of unchanged files by checking metadata and content hashes.

*   **How does it fit into the RAG/ETL pipeline?**
    This service initiates the entire data ingestion workflow.
    1.  **Source**: It monitors a specific folder on the file system (`src/main/resources/documents`).
    2.  **Process (Stage)**: It compares the files on disk against records in the `staged_documents` table.
    3.  **Output**: It creates new records for new files or updates existing records for modified files, setting their status to `"STAGED"`. This status acts as a signal for the `ParsingService` to pick them up.

## 2. Core Dependencies & Frameworks
*   **`StagedDocumentRepository`**: A Spring Data repository that provides the interface to the `staged_documents` table. The service uses it to check for the existence of a file record and to save new or updated `StagedDocument` entities.
*   **`ChecksumUtils`**: A custom utility class used to calculate the SHA-256 hash of a file's content. This hash is the definitive way the service determines if a file's content has actually changed, preventing unnecessary reprocessing.
*   **`java.nio.file`**: The modern Java file I/O API is used extensively to list files in the directory (`Files.list`), read file attributes like size and last modified time (`Files.readAttributes`), and handle file paths.
*   **`SLF4J`**: Used for detailed logging of the staging process, including the number of new, updated, and skipped files, as well as any errors encountered.
*   **`Spring Transactional`**: The main `stageLocalFiles` method is annotated with `@Transactional`, ensuring that all database operations performed during a single run are committed together, maintaining data integrity.

## 3. Key Components & Internal Logic
*   **Inputs & Configuration:**
    *   `DOCUMENTS_DIRECTORY` (Constant): A hardcoded path (`"src/main/resources/documents"`) that defines the single source directory the service will scan.

*   **Core Workflows:**
    1.  **`stageLocalFiles()`**: This is the main public method. It begins by listing all regular files in the `DOCUMENTS_DIRECTORY`. It then iterates through each file ("candidate") and calls `processCandidate` to handle the core logic for each one. It keeps detailed counts of outcomes (new, updated, unchanged, etc.) and logs a summary at the end.
    2.  **`processCandidate()`**: This is the heart of the service. For a given file path:
        *   It reads the file's basic attributes (size, last modified time).
        *   It queries the database using the absolute file path to see if a `StagedDocument` record already exists.
        *   **New File Path**: If no record exists, it creates a new `StagedDocument`, calculates its SHA-256 hash, populates all its fields (file name, path, size, version 1, etc.), sets the status to `"STAGED"`, and saves it.
        *   **Existing File Path**: If a record exists, it performs a series of checks:
            1.  **Metadata Check**: It first compares the file's current size and last modified time with the stored values. If they are identical, it assumes the file is unchanged and skips it (`StageAction.UNCHANGED`). This is a fast-path optimization to avoid the expensive hash calculation.
            2.  **Content Hash Check**: If the metadata is different, it proceeds to calculate the file's current SHA-256 hash. It compares this new hash with the hash stored in the database.
            3.  **Metadata-Only Change**: If the hashes are the same (but metadata was different), it updates the metadata (e.g., file name, size) but leaves the status and content hash as is (`StageAction.METADATA_ONLY`).
            4.  **Content Change**: If the hashes are different, it means the file content has been modified. It updates the metadata, increments the `versionNumber`, calculates and sets the new `contentHash`, and crucially, resets the `status` back to `"STAGED"` so the file will be re-processed by the entire pipeline (`StageAction.CONTENT_UPDATED`).

*   **Outputs/Side Effects:**
    *   **Return Value**: Returns a `List<StagedDocument>` containing all the new or updated records that were persisted to the database during the run.
    *   **Side Effects**:
        *   **Database Writes/Updates**: Creates or modifies records in the `staged_documents` table. This is the primary function of the service.
        *   **File System Reads**: Reads file attributes and content from the local disk. It does not write to the file system.

## 4. End-to-End Request Flow (How it runs)
1.  **Trigger**: A scheduler calls `stagingService.stageLocalFiles()`.
2.  **Scan**: The service lists files in `./src/main/resources/documents/` and finds two files: `report.pdf` (already in the DB and unchanged) and `new-data.csv` (not in the DB).
3.  **Process `report.pdf`**:
    a. `processCandidate` is called for `report.pdf`.
    b. It finds the existing record in the database.
    c. The `isMetadataUnchanged` check passes because the file size and last modified time on disk match the database record.
    d. The service logs "Skipping: Metadata unchanged" and does nothing further with this file.
4.  **Process `new-data.csv`**:
    a. `processCandidate` is called for `new-data.csv`.
    b. It does not find a record in the database.
    c. It creates a new `StagedDocument` entity, calculates the file's SHA-256 hash, and sets the status to `"STAGED"`.
    d. The new record is saved to the database.
5.  **Completion**: The service logs a summary (e.g., "persisted=1, newDocuments=1, metadataUnchanged=1") and returns a list containing the newly created `StagedDocument` for `new-data.csv`.
---
