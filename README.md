# DocumentETL

DocumentETL is a Spring Boot + Python project for:

- document ingestion and parsing
- chunking and embedding
- vector/hybrid/MMR retrieval
- classic and agentic RAG chat
- MLflow logging and RAGAS evaluation
- token usage tracking UI/API

## 1) Tech Stack

- Java 21
- Spring Boot 3.2.5
- Maven Wrapper (`mvnw`)
- PostgreSQL + pgvector
- Apache Kafka
- LangChain4j + Vertex AI Gemini
- MLflow
- Python 3.10+ (`python_eval/`)

## 2) Repo Layout

- `src/main/java/` - Spring Boot backend
- `src/main/resources/` - app config + sample documents + static UIs
- `docker/` - SQL init scripts and MLflow Dockerfile
- `python_eval/` - RAGAS evaluation pipeline
- `database_setup_and_upgrades.sql` - full schema evolution script
- `.env.example` - environment variable template

## 3) Prerequisites

Install:

- JDK 21
- Docker + Docker Compose
- Python 3.10+ (optional, for RAGAS evaluation)

You need these runtime services:

- PostgreSQL with `vector` extension (pgvector)
- Kafka broker reachable by the app
- MLflow server

## 4) Environment Configuration

1. Copy template:

```powershell
Copy-Item .env.example .env
```

2. Update `.env` values for your machine, especially:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- `MLFLOW_TRACKING_URI`
- `MLFLOW_BACKEND_STORE_URI`

3. Vertex AI (optional):

- If you want embedding/chat generation via Vertex, set:
  - `VERTEX_PROJECT_ID`
  - `VERTEX_LOCATION`
  - `SPRING_CLOUD_GCP_CREDENTIALS_LOCATION`
  - `LANGCHAIN4J_VERTEX_PROJECT`
  - `LANGCHAIN4J_VERTEX_LOCATION`

Notes:

- `application.properties` already reads from env vars.
- `spring.jpa.hibernate.ddl-auto=none`, so SQL schema must be created manually.

## 5) Start Infrastructure

### 5.1 MLflow + Floci (from this repo)

`docker-compose.yml` in this repo currently contains:

- `mlflow_server`
- `floci`

Start:

```powershell
docker compose up -d
```

### 5.2 PostgreSQL + pgvector

If you do not already have Postgres, run:

```powershell
docker run -d --name pgvector-db `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=postgres `
  -e POSTGRES_DB=ai_advisor_db `
  -p 5432:5432 `
  pgvector/pgvector:pg16
```

### 5.3 Kafka

Use any Kafka accessible from `SPRING_KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`).

If you already have Kafka running, keep using it.  
If not, this single-container Redpanda command is a simple local Kafka-compatible option:

```powershell
docker run -d --name redpanda `
  -p 9092:9092 -p 9644:9644 `
  docker.redpanda.com/redpandadata/redpanda:v24.1.9 `
  redpanda start --overprovisioned --smp 1 --memory 1G --reserve-memory 0M --node-id 0 --check=false `
  --kafka-addr PLAINTEXT://0.0.0.0:9092 `
  --advertise-kafka-addr PLAINTEXT://localhost:9092
```

## 6) Initialize Database Schema

Run both SQL scripts against your app database (`ai_advisor_db`):

```powershell
Get-Content docker/init/01-schema.sql | docker exec -i pgvector-db psql -U postgres -d ai_advisor_db -v ON_ERROR_STOP=1 -f -
Get-Content docker/init/02-document-etl-schema.sql | docker exec -i pgvector-db psql -U postgres -d ai_advisor_db -v ON_ERROR_STOP=1 -f -
```

If your app user is `advisor_user`, grant access to the token table too:

```sql
GRANT ALL PRIVILEGES ON TABLE knowledge.token_usage_events TO advisor_user;
GRANT USAGE, SELECT ON SEQUENCE knowledge.token_usage_events_id_seq TO advisor_user;
```

## 7) Build and Run the App

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

App default URL: `http://localhost:8080`

## 8) UIs

- Classic review UI: `http://localhost:8080/chat-review.html`
- Agentic chatbot UI: `http://localhost:8080/agentic-chat.html`
- Token manager UI: `http://localhost:8080/token-manager.html`

Redirect helpers:

- `/api/chat/ui`
- `/api/chat/agent/ui`
- `/api/tokens/ui`

## 9) Key APIs

### Chat

- `GET /api/chat/ask?question=...`
- `GET /api/chat/review?question=...&topK=5&candidateK=20&mmrLambda=0.7`
- `GET /api/chat/agent/ask?question=...&threadId=...`

### Search

- `GET /api/search?q=...&maxResults=5&strategyType=vector`

### ETL v2

- `GET /api/etl/v2/stage`
- `GET /api/etl/v2/reconcile-embeddings`
- `GET /api/etl/v2/status`

### Token Management

- `GET /api/tokens/summary`
- `GET /api/tokens/events?limit=100`
- `DELETE /api/tokens/events`
- `DELETE /api/tokens/events/before?before=2026-05-31T17:00:00Z`

## 10) Python RAGAS Evaluation (Optional)

Install dependencies:

```powershell
pip install -r python_eval\requirements.txt
```

Run evaluation:

```powershell
python python_eval\eval_ragas.py --limit 5
```

For details, see:

- `python_eval/README.md`

## 11) Optional Vertex AI Mode

You can run infrastructure and DB flows without Vertex, but chat/embedding paths that call Gemini require valid credentials.

Supported credential style:

- `SPRING_CLOUD_GCP_CREDENTIALS_LOCATION=classpath:gcp-vertex-key.json` (local file in `src/main/resources/`)
- or `GOOGLE_APPLICATION_CREDENTIALS=<absolute path>`

## 12) Common Troubleshooting

- **`release version 21 not supported`**  
  Your shell Java is not JDK 21. Set `JAVA_HOME` to a Java 21 JDK.

- **Kafka connection errors**  
  Verify `SPRING_KAFKA_BOOTSTRAP_SERVERS` and broker availability.

- **DB permission/schema errors**  
  Re-run schema scripts and grants; confirm `currentSchema=knowledge,public` in JDBC URL.

- **Token summary projection error (`Instant` vs `OffsetDateTime`)**  
  Pull latest code; this was fixed in `TokenUsageTotalsProjection` + `TokenUsageManagerService`.

## 13) Security Notes

- Do not commit secrets (`.env`, service account keys, passwords).
- Keep credential files local and gitignored.
- Rotate credentials before sharing environments.

## 14) License

No license file is currently included in this repository.
