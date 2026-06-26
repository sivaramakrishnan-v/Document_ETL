# DocumentETL

DocumentETL is a document intelligence and Retrieval-Augmented Generation (RAG) platform built with Spring Boot, Angular, PostgreSQL (with pgvector), Kafka, and Vertex AI Gemini. It provides an end-to-end solution for ingesting documents, asking questions against them, and evaluating the quality of the AI-generated answers.

It supports:
- An event-driven ETL pipeline for document staging, parsing, chunking, embedding, and vector storage.
- A polished Angular UI for interacting with the entire system.
- Persistent, thread-based conversations with a complete chat history.
- Advanced retrieval strategies including vector search, reranking, and MMR.
- Detailed grounding and citation analysis to ensure answer quality.
- Token usage tracking and observability into the AI's operations.

## 1. Tech Stack

- **Backend:** Java 21, Spring Boot 3.2.5
- **Frontend:** Angular 17, TypeScript
- **Database:** PostgreSQL with the `pgvector` extension
- **AI & Orchestration:** LangChain4j, LangGraph4j, Google Vertex AI (Gemini)
- **Asynchronous Processing:** Apache Kafka
- **Build Tools:** Maven (for backend), Angular CLI (for frontend)
- **Evaluation (Optional):** Python 3.10+, MLflow, RAGAS

## 2. Architecture

The application is composed of a Spring Boot backend that serves a modern Angular single-page application (SPA).

| Layer | Purpose |
| --- | --- |
| **Angular Frontend** | Provides a rich user interface for document management, chat, and observability. |
| **Spring Boot Backend** | Exposes REST APIs for all core functionality, orchestrates the RAG pipeline, and manages data persistence. |
| **PostgreSQL Database** | Stores document metadata, text chunks, vector embeddings, and chat history (`rag_workflow_checkpoint`). |
| **Apache Kafka** | Manages the asynchronous ETL process, from document ingestion to embedding. |
| **Vertex AI (Gemini)** | The Large Language Model used for generating answers and evaluating text. |

The frontend is built into static files which are then served directly by the Spring Boot application, creating a single, cohesive deployment package.

## 3. Repo Layout

- `frontend/` - The Angular frontend application source code.
- `src/main/java/` - The Spring Boot backend application source code.
- `src/main/resources/static/` - The location where the built Angular app is placed to be served by Spring Boot.
- `src/main/resources/documents/` - The default local directory for staging documents to be ingested.
- `docker/` - Docker configurations for infrastructure like MLflow.
- `python_eval/` - Optional Python scripts for RAGAS evaluation.
- `database_setup_and_upgrades.sql` - A consolidated script for database schema setup.
- `.env.example` - A template for the required environment variables.

## 4. Prerequisites

- JDK 21
- Node.js and Angular CLI (for frontend development)
- Docker and Docker Compose
- PostgreSQL with the `vector` extension installed
- An Apache Kafka broker
- Valid Google Cloud (Vertex AI) credentials for chat and embedding features.

## 5. Environment Configuration

Create a local environment file by copying the template:
```powershell
Copy-Item .env.example .env
```

Update the `.env` file with the correct values for your local environment, including database connections, Kafka brokers, and GCP credentials.

## 6. How to Run

### 1. Start Infrastructure
The project requires a running PostgreSQL database and a Kafka broker. You can use the provided Docker configurations or your own instances.

### 2. Initialize the Database
The database schema must be created before starting the application. Use the provided SQL scripts to set up the necessary tables and schemas.
```powershell
# Example for a local Docker container named pgvector-db
Get-Content docker/init/01-schema.sql | docker exec -i pgvector-db psql -U postgres -d ai_advisor_db -v ON_ERROR_STOP=1 -f -
Get-Content docker/init/02-document-etl-schema.sql | docker exec -i pgvector-db psql -U postgres -d ai_advisor_db -v ON_ERROR_STOP=1 -f -
```

### 3. Build and Run the Application
The project is configured to build the Angular frontend and copy it into the Spring Boot application's static resources.

**From the project root:**
```powershell
# Build the frontend and copy it to the backend
cd frontend
npm install
npm run build:spring
cd ..

# Run the Spring Boot backend
.\mvnw.cmd spring-boot:run
```
The application will be available at `http://localhost:8080`.

## 7. The Angular UI

The frontend provides a seamless experience for managing and interacting with the RAG system.

| Page | Functionality |
| --- | --- |
| **Dashboard** | Provides a high-level overview of the system's status, including document processing metrics and recent pipeline events. |
| **Documents** | Lists all ingested documents, showing their status (e.g., Chunked, Embedded), file type, and the number of text chunks created from them. |
| **Ask Question** | The main chat interface. It supports persistent, thread-based conversations, allowing you to navigate away and return without losing your chat history. |
| **Results & Citations** | A detailed view for analyzing the AI's response. It shows the full conversation history, grounding metrics, and the specific document chunks used as sources. |
| **Token Usage** | An observability tool that provides insights into LLM token consumption, request counts, and success/failure rates for different operations. |

## 8. Key APIs

The Spring Boot backend exposes a set of REST APIs to power the frontend.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/chat/agent/ask` | The primary endpoint for the chat interface. Manages conversation history using a `threadId`. |
| `GET` | `/api/documents` | Retrieves the list of all ingested documents and their status. |
| `GET` | `/api/rag/checkpoints/{threadId}/history` | Fetches the complete message history for a given conversation thread. |
| `GET` | `/api/etl/v2/status` | Returns detailed status of the document processing pipeline. |
| `GET` | `/api/tokens/summary` | Provides a summary of token usage across the application. |

## 9. Chat Answer Behavior

The `/api/chat/agent/ask` endpoint generates grounded answers from retrieved document evidence. The answer-generation prompt is tuned for a modern assistant style while preserving strict grounding:

- Short factual questions receive brief, direct answers.
- Comparison questions start with the primary similarity, explain the most important differences, and end with a concise conclusion.
- Explain, why, how, analyze, and walk-through questions may include more detail when supported by evidence.
- Detailed or comprehensive requests receive longer structured answers when the uploaded documents contain enough information.
- Answers use only retrieved evidence and clearly state when the uploaded documents do not contain enough information.
- User-facing answers avoid internal document IDs, chunk IDs, retrieval metadata, and inline citation tokens.

Retrieval strategy, grounding checks, validation, and backend citation data are handled separately from the answer-generation wording prompt.

## 10. Security Notes

- Do not commit `.env` files, service account keys, or any other sensitive credentials to version control.
- Use the provided `.gitignore` file to prevent accidental exposure of secrets.
- Rotate any credentials before sharing an environment.
