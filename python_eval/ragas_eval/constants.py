import logging
import os
from pathlib import Path

LOGGER = logging.getLogger("eval_ragas")

EXPERIMENT_NAME = "DocumentETL"
FAITHFULNESS_METRIC_NAME = "faithfulness_score"
ANSWER_RELEVANCY_METRIC_NAME = "answer_relevancy_score"

TRACE_BRIDGE_PATH = Path(__file__).resolve().parent.parent / "mlflow_trace_bridge.jsonl"
TRACE_BRIDGE_STATE_PATH = Path(__file__).resolve().parent.parent / ".mlflow_trace_bridge_ingested_ids.txt"
DEFAULT_DOTENV_PATH = Path(__file__).resolve().parent.parent.parent / ".env"
REPO_ROOT = Path(__file__).resolve().parent.parent.parent

DEFAULT_TRACE_DB_SOURCE = os.getenv("RAGAS_TRACE_BRIDGE_SOURCE", "db").strip().lower() or "db"
DEFAULT_TRACE_DB_SCHEMA = os.getenv("RAGAS_TRACE_DB_SCHEMA", "knowledge").strip() or "knowledge"
DEFAULT_TRACE_DB_EVENTS_TABLE = os.getenv("RAGAS_TRACE_DB_EVENTS_TABLE", "mlflow_trace_bridge_events").strip()
DEFAULT_TRACE_DB_DOCUMENTS_TABLE = os.getenv("RAGAS_TRACE_DB_DOCUMENTS_TABLE", "mlflow_trace_bridge_documents").strip()
DEFAULT_TRACE_DB_BATCH_SIZE = int(os.getenv("RAGAS_TRACE_DB_BATCH_SIZE", "250"))

