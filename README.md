# DocumentETL

DocumentETL is a document intelligence and RAG platform built with Spring Boot, PostgreSQL, pgvector, Kafka, Vertex AI Gemini, MLflow, and an optional Python RAGAS evaluation pipeline.

It supports:

- document staging, parsing, chunking, embedding, and vector storage
- event-driven ETL orchestration with Kafka
- vector, hybrid, MMR, and reranked retrieval
- classic RAG chat, retrieval review, and agentic RAG chat
- persisted agent workflow checkpoints and thread history
- groundedness scoring, citation coverage, and unsupported-claim tracking
- token usage summaries and correlated model-action history
- MLflow experiment tracking and PostgreSQL-backed RAGAS trace bridging
- a unified browser-based operations console

## 1. Tech Stack

- Java 21
- Spring Boot 3.2.5
- Maven Wrapper (`mvnw`)
- PostgreSQL + pgvector
- Apache Kafka or a Kafka-compatible broker
- LangChain4j + Vertex AI Gemini
- LangGraph4j for the agentic workflow
- MLflow with a PostgreSQL backend store
- Python 3.10+ for optional RAGAS evaluation

## 2. Architecture

The application has three complementary persistence and observability layers:

| Layer | Purpose |
| --- | --- |
| PostgreSQL application schema (`knowledge`) | Documents, ETL events, embeddings, token usage, trace bridge events, and agent workflow checkpoints |
| MLflow PostgreSQL backend | Experiment runs, parameters, metrics, and RAGAS scores |
| MLflow artifacts volume | Artifact storage for the MLflow server |

PostgreSQL checkpoint tables remain the source of truth for interactive agent-thread replay and checkpoint review. MLflow is the experiment and analytics layer for classic chat and review runs. Agentic checkpoint metrics are persisted in PostgreSQL; publishing them as `chat.ask.agentic` MLflow runs is a future extension.

### Agentic Workflow

The agentic RAG flow persists progress as a workflow checkpoint:

1. Query planning
2. Retrieval agent
3. Context grading
4. Answer validation
5. Grounding score calculation
6. Completion or failure

Each checkpoint can retain the thread ID, query variants, retrieval strategy, retrieved document IDs, retrieved chunk IDs, context snapshot, generated answer, citations, validation result, grounding metrics, status, and error message.

### Grounding Metrics

The Java grounding service calculates:

- `groundednessScore`: supported answer claims divided by evaluated answer claims
- `citationCoverageScore`: cited retrieved chunks divided by retrieved chunks
- `unsupportedClaimsCount`: evaluated claims without sufficient retrieved-context overlap
- `groundingStatus`: `GROUNDED`, `PARTIALLY_GROUNDED`, or `WEAKLY_GROUNDED`

This is a deterministic lexical-overlap calculation for immediate UI feedback. Optional RAGAS evaluation provides model-assisted quality scoring through MLflow.

## 3. Repo Layout

- `src/main/java/` - Spring Boot backend
- `src/main/resources/static/` - unified dashboard and legacy browser tools
- `src/main/resources/documents/` - default local staging directory
- `docker/init/` - database initialization scripts
- `docker/mlflow/` - MLflow server Dockerfile
- `python_eval/` - RAGAS evaluation and MLflow trace ingestion
- `database_setup_and_upgrades.sql` - consolidated schema evolution script
- `.env.example` - environment-variable template

## 4. Prerequisites

Install:

- JDK 21
- Docker + Docker Compose
- PostgreSQL with the `vector` extension
- Kafka or a Kafka-compatible broker
- Python 3.10+ only when running RAGAS evaluation

Chat and embedding calls require valid Vertex AI credentials. ETL status pages and database-backed history can be used without invoking Gemini.

## 5. Environment Configuration

Create a local environment file:

```powershell
Copy-Item .env.example .env
```

Update the values for your environment:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ai_advisor_db?currentSchema=knowledge,public
SPRING_DATASOURCE_USERNAME=your_db_user
SPRING_DATASOURCE_PASSWORD=your_db_password
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092

VERTEX_PROJECT_ID=your-gcp-project-id
VERTEX_LOCATION=us-central1
SPRING_CLOUD_GCP_CREDENTIALS_LOCATION=classpath:gcp-vertex-key.json

MLFLOW_TRACKING_URI=http://localhost:5000
APP_MLFLOW_EXPERIMENT_NAME=DocumentETL
MLFLOW_BACKEND_STORE_URI=postgresql://your_db_user:your_db_password@host.docker.internal:5432/ai_advisor_db
MLFLOW_DEFAULT_ARTIFACT_ROOT=/mlflow/artifacts
```

The Spring application reads environment variables through `src/main/resources/application.properties`. JPA schema generation is disabled with `spring.jpa.hibernate.ddl-auto=none`, so initialize the schema before starting the app.

## 6. Start Infrastructure

### 6.1 MLflow + Floci

The repo Docker Compose configuration starts:

- `mlflow_server` at `http://localhost:5000`
- `floci` at `http://localhost:4566`

```powershell
docker compose up -d
```

The MLflow server uses `MLFLOW_BACKEND_STORE_URI` for its PostgreSQL backend and the `mlflow_artifacts` Docker volume for artifacts.

### 6.2 PostgreSQL + pgvector

If PostgreSQL is not already available:

```powershell
docker run -d --name pgvector-db `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=postgres `
  -e POSTGRES_DB=ai_advisor_db `
  -p 5432:5432 `
  pgvector/pgvector:pg16
```

### 6.3 Kafka

Use a Kafka broker reachable through `SPRING_KAFKA_BOOTSTRAP_SERVERS`. The default is `localhost:9092`.

A local Kafka-compatible Redpanda container can be started with:

```powershell
docker run -d --name redpanda `
  -p 9092:9092 -p 9644:9644 `
  docker.redpanda.com/redpandadata/redpanda:v24.1.9 `
  redpanda start --overprovisioned --smp 1 --memory 1G --reserve-memory 0M --node-id 0 --check=false `
  --kafka-addr PLAINTEXT://0.0.0.0:9092 `
  --advertise-kafka-addr PLAINTEXT://localhost:9092
```

## 7. Initialize the Database

For a local container named `pgvector-db`, run the two idempotent schema scripts:

```powershell
Get-Content docker/init/01-schema.sql | docker exec -i pgvector-db psql -U postgres -d ai_advisor_db -v ON_ERROR_STOP=1 -f -
Get-Content docker/init/02-document-etl-schema.sql | docker exec -i pgvector-db psql -U postgres -d ai_advisor_db -v ON_ERROR_STOP=1 -f -
```

For an existing PostgreSQL environment, `database_setup_and_upgrades.sql` is the consolidated upgrade reference. Review its user and database creation statements before applying it.

The application schemas include:

- `knowledge.staged_documents`
- `knowledge.parsed_content`
- `knowledge.document_chunks`
- `document_etl.source_documents`
- `document_etl.extracted_contents`
- `document_etl.text_chunks`
- `document_etl.chunk_embeddings`
- `document_etl.embedding_jobs`
- `document_etl.pipeline_events`
- `knowledge.mlflow_trace_bridge_events`
- `knowledge.mlflow_trace_bridge_documents`
- `knowledge.token_usage_events`
- `knowledge.rag_workflow_checkpoint`

## 8. Build and Run

Windows PowerShell:

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
./mvnw clean compile
./mvnw spring-boot:run
```

Open the application at:

```text
http://localhost:8080/
```

## 9. Unified Operations Console

The main dashboard is served from `http://localhost:8080/`.

| Panel | Functionality |
| --- | --- |
| Agentic Chat | Runs agentic RAG requests, reuses optional thread IDs, clears the composer after send, and shows response time, workflow status, and checkpoint ID |
| Full ETL Pipeline | Shows ingestion stages, database counts, recent Kafka events, server-side document staging, and embedding reconciliation |
| Agentic Workflow | Shows workflow stages, persisted checkpoint details, global history, thread-filtered history, and selectable checkpoint review |
| Token Usage | Shows token totals, request health, top operations, correlated runs, per-run model/tool actions, and a frontend chunk-preview estimate |
| Grounding & Citations | Shows groundedness metrics, citations, retrieved chunks, confidence explanation, global history, thread-filtered history, and selectable checkpoint evidence review |

The grounding summary is intentionally kept out of the main chat panel. Detailed evidence review is available from the dedicated Grounding & Citations panel.

### Legacy Tools

The earlier standalone pages remain available:

- `http://localhost:8080/agentic-chat.html`
- `http://localhost:8080/chat-review.html`
- `http://localhost:8080/token-manager.html`

## 10. Key APIs

### Chat

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/chat/ask` | Classic RAG chat using JSON body fields `question` and optional `goldenAnswer` |
| `GET` | `/api/chat/ask?question=...` | Classic RAG chat query helper |
| `GET` | `/api/chat/review?question=...&topK=5&candidateK=20&mmrLambda=0.7` | Retrieval review with vector candidates, MMR selection, reranking, sources, and evaluations |
| `POST` | `/api/chat/agent/ask` | Agentic RAG chat using JSON body fields `question` and optional `threadId` |
| `GET` | `/api/chat/agent/ask?question=...&threadId=...` | Agentic RAG chat query helper |

### Checkpoints and Dashboard History

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/rag/checkpoints/{threadId}/latest` | Latest persisted checkpoint for a thread |
| `GET` | `/api/rag/checkpoints/{threadId}/history` | Persisted checkpoint history for a thread |
| `GET` | `/api/dashboard/checkpoints?limit=100` | Recent checkpoints across threads |
| `GET` | `/api/dashboard/token-runs?limit=100` | Recent token events grouped into correlated runs |

### ETL v2

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/etl/v2/stage` | Scan the configured server-side documents directory and publish staging events |
| `GET` | `/api/etl/v2/reconcile-embeddings` | Submit reconciliation for missing embeddings |
| `GET` | `/api/etl/v2/status` | Return document, embedding, job, Kafka-event, and recent-event counts |

The dashboard file picker is informational at present. A browser upload endpoint has not yet been implemented; use server-side staging.

### Token Usage

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/tokens/summary?topOperations=10` | Token totals, success/failure counts, and top operations |
| `GET` | `/api/tokens/events?limit=100` | Recent raw token-usage events |
| `DELETE` | `/api/tokens/events` | Delete all token-usage events |
| `DELETE` | `/api/tokens/events/before?before=2026-05-31T17:00:00Z` | Delete events before a timestamp |

Token counts are application-side estimates based on text length. They are intended for operational monitoring, not billing reconciliation.

## 11. MLflow and RAGAS

MLflow is already configured in this project with:

- a Dockerized MLflow tracking server
- a PostgreSQL backend store
- the Java `mlflow-client`
- async logging with a Resilience4j circuit breaker
- action runs for classic chat, chat review, staging, search, and chunking operations
- a PostgreSQL-backed Java-to-Python trace bridge

Classic `chat.ask` and `chat.ask.review` calls log metrics and parameters to MLflow. The trace bridge stores question, answer, and retrieved-document context in:

- `knowledge.mlflow_trace_bridge_events`
- `knowledge.mlflow_trace_bridge_documents`

The Python RAGAS pipeline ingests pending bridge events into MLflow traces, computes `faithfulness_score` and `answer_relevancy_score`, and logs them back to the matching MLflow runs.

Install Python dependencies:

```powershell
pip install -r python_eval\requirements.txt
```

Run the evaluator:

```powershell
python python_eval\eval_ragas.py --trace-bridge-source db --limit 5
```

See `python_eval/README.md` for credential modes, trace ingestion details, and optional overrides.

## 12. Useful Configuration

Important optional environment overrides:

| Variable | Default | Purpose |
| --- | --- | --- |
| `APP_ETL_V2_DOCUMENTS_DIRECTORY` | `src/main/resources/documents` | Server-side document staging directory |
| `APP_ETL_V2_AUTO_STAGE_ENABLED` | `false` | Periodically scan for new or changed documents |
| `APP_ETL_V2_RECONCILIATION_ENABLED` | `false` | Periodically reconcile missing embeddings |
| `APP_RAG_GENERATION_MODEL` | `gemini-2.5-flash` | Answer-generation model |
| `APP_RAG_EVALUATION_MODEL` | `gemini-2.5-pro` | Evaluation model |
| `APP_MLFLOW_EXPERIMENT_NAME` | `DocumentETL` | MLflow experiment name |
| `APP_MLFLOW_ACTION_LOGGING_ENABLED` | `true` | Enable Java action-run logging |
| `APP_MLFLOW_STARTUP_TEST_ENABLED` | `false` | Write a startup verification metric |
| `APP_EVALUATION_BIGQUERY_ENABLED` | `true` in application defaults | Enable BigQuery evaluation logging |

## 13. Troubleshooting

- **`release version 21 not supported` or class-file version mismatch**
  Ensure `JAVA_HOME` and the active `java` command point to JDK 21. Remove stale `target/` output after switching JDK versions.

- **Kafka connection errors**
  Verify `SPRING_KAFKA_BOOTSTRAP_SERVERS` and broker availability.

- **Database permission or schema errors**
  Re-run both `docker/init/` scripts and confirm `currentSchema=knowledge,public` is present in the JDBC URL.

- **MLflow connection errors**
  Verify `MLFLOW_TRACKING_URI`, confirm the `mlflow_server` container is running, and confirm its PostgreSQL backend URI is reachable from inside Docker.

- **No RAGAS results in MLflow**
  Run `python_eval/eval_ragas.py`, confirm pending trace bridge rows exist, and verify Gemini credentials.

- **No browser upload**
  Use `/api/etl/v2/stage` or the dashboard's **Stage server documents** action. The browser upload endpoint is still pending.

## 14. Security Notes

- Do not commit `.env`, service-account keys, database passwords, or local credential files.
- Keep MLflow and database credentials outside version control.
- Rotate any credentials before sharing an environment.

## 15. License

No license file is currently included in this repository.
