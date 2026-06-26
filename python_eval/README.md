# Python RAG Evaluation (RAGAS + MLflow)

## Run

Use your existing virtual environment:

```powershell
D:\GenAI\Virtual_environments\genailab-env\Scripts\python.exe python_eval\eval_ragas.py --limit 5
```

Default trace bridge source is DB:

```powershell
D:\GenAI\Virtual_environments\genailab-env\Scripts\python.exe python_eval\eval_ragas.py --trace-bridge-source db --limit 5
```

## Required environment variables

- `MLFLOW_TRACKING_URI` (example: `http://localhost:5000`)
- Gemini auth:
  - Vertex mode (default): service account credentials + project/location
  - or API key mode: `GOOGLE_API_KEY`

The script also auto-loads `../.env` (repo root) if present, then validates these values.

### Vertex mode (default)

Set:

- `RAGAS_USE_VERTEX=true`
- `VERTEX_PROJECT_ID` (or `GOOGLE_CLOUD_PROJECT`)
- `VERTEX_LOCATION` (or `GOOGLE_CLOUD_LOCATION`, default `us-central1`)
- `GOOGLE_APPLICATION_CREDENTIALS` or `SPRING_CLOUD_GCP_CREDENTIALS_LOCATION`

`SPRING_CLOUD_GCP_CREDENTIALS_LOCATION=classpath:gcp-vertex-key.json` is supported and resolves to:
`src/main/resources/gcp-vertex-key.json`.

### API key mode

Set:

- `RAGAS_USE_VERTEX=false`
- `GOOGLE_API_KEY`

Optional:

- `RAGAS_GEMINI_MODEL` (default: `gemini-2.5-pro`)
- `RAGAS_GEMINI_EMBEDDING_MODEL` (default: `gemini-embedding-001`)

## How Java traces are emitted

MLflow Java SDK (`mlflow-client`) in this project does not expose GenAI tracing APIs compatible with Python `mlflow.search_traces()`; therefore tracing is implemented via a lightweight bridge:

1. `ChatService` logs the normal MLflow run for `chat.ask`.
2. `MlflowTraceBridgeService` writes a bridge event to Postgres tables:
   - `document_etl.mlflow_trace_bridge_events`
   - `document_etl.mlflow_trace_bridge_documents`
3. Optional compatibility mirror can append JSONL to:
   - `python_eval/mlflow_trace_bridge.jsonl`
4. Each event contains:
   - `run_id`
   - root span input/output (`question` / `answer`)
   - retriever span with:
     - `span_type = "RETRIEVER"`
     - output documents in:
       - `page_content`
       - `metadata` (`doc_id`, `source`, `source_path`, `rank`, `similarity`)

## Trace bridge ingestion modes

`eval_ragas.py`:

1. Ingests new bridge events into MLflow traces (Python API).
   - DB mode (default): reads events where `ingested=false` from `document_etl.mlflow_trace_bridge_events` and related rows from `document_etl.mlflow_trace_bridge_documents`, then marks successful rows as `ingested=true`.
   - File mode: reads `python_eval/mlflow_trace_bridge.jsonl` and de-duplicates with `.mlflow_trace_bridge_ingested_ids.txt`.
2. Finds recent `chat.ask` and `chat.ask.review` runs in experiment `DocumentETL` that do not already have:
   - `faithfulness_score`
   - `answer_relevancy_score`
3. Extracts:
   - question from trace root input (fallback to run params)
   - answer from trace root output (fallback to run params)
   - retrieved context from spans where `span_type == "RETRIEVER"`
4. Computes RAGAS metrics with Gemini as judge.
5. Logs metrics back to the same MLflow run id.

### DB configuration used by `eval_ragas.py` in DB mode

- URL: `RAGAS_TRACE_DB_URL` or `SPRING_DATASOURCE_URL`
- Username: `RAGAS_TRACE_DB_USERNAME` or `SPRING_DATASOURCE_USERNAME` or `POSTGRES_USER`
- Password: `RAGAS_TRACE_DB_PASSWORD` or `SPRING_DATASOURCE_PASSWORD` or `POSTGRES_PASSWORD`

Optional overrides:

- `--trace-db-schema` (default: `knowledge`)
- `--trace-db-events-table` (default: `mlflow_trace_bridge_events`)
- `--trace-db-documents-table` (default: `mlflow_trace_bridge_documents`)
- `--trace-db-batch-size` (default: `250`)
