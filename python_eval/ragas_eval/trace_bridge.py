import json
import os
from argparse import Namespace
from pathlib import Path

import mlflow

from .constants import DEFAULT_TRACE_DB_SCHEMA, LOGGER, TRACE_BRIDGE_STATE_PATH
from .db_connection import (
    extract_schema_from_jdbc_query,
    fetch_pending_bridge_events_from_db,
    mark_bridge_events_ingested_in_db,
    resolve_trace_bridge_table_names,
)
from .trace_utils import normalize_retrieved_chunks


def load_ingested_event_ids(path: Path) -> set[str]:
    if not path.exists():
        return set()
    return {line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()}


def mark_event_ingested(path: Path, event_id: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(event_id + "\n")


def ingest_trace_bridge_events_from_file(trace_bridge_file: Path) -> int:
    if not trace_bridge_file.exists():
        LOGGER.info("Trace bridge file not found at %s, skipping ingestion.", trace_bridge_file)
        return 0

    ingested_ids = load_ingested_event_ids(TRACE_BRIDGE_STATE_PATH)
    created_count = 0
    with trace_bridge_file.open("r", encoding="utf-8") as handle:
        for line in handle:
            raw = line.strip()
            if not raw:
                continue
            try:
                event = json.loads(raw)
            except json.JSONDecodeError:
                LOGGER.warning("Skipping invalid JSON line in trace bridge file.")
                continue

            event_id = str(event.get("event_id") or "").strip()
            if not event_id or event_id in ingested_ids:
                continue

            run_id = str(event.get("run_id") or "").strip()
            question = str(event.get("question") or "").strip()
            answer = str(event.get("answer") or "").strip()
            retriever_output = normalize_retrieved_chunks(
                ((event.get("retriever_span") or {}).get("output"))
            )
            if not run_id or not question or not answer:
                LOGGER.warning("Skipping bridge event missing run_id/question/answer: event_id=%s", event_id)
                continue

            root_span_name = str((event.get("root_span") or {}).get("name") or "chat.ask.root")
            retriever_span_name = str((event.get("retriever_span") or {}).get("name") or "chat.ask.retriever")

            try:
                with mlflow.start_run(run_id=run_id):
                    with mlflow.start_span(name=root_span_name, span_type="CHAIN") as root_span:
                        root_span.set_inputs({"question": question})
                        with mlflow.start_span(name=retriever_span_name, span_type="RETRIEVER") as retriever_span:
                            retriever_span.set_attributes({"span_type": "RETRIEVER"})
                            retriever_span.set_outputs(retriever_output)
                        root_span.set_outputs({"answer": answer})
                mark_event_ingested(TRACE_BRIDGE_STATE_PATH, event_id)
                ingested_ids.add(event_id)
                created_count += 1
            except Exception as exc:
                LOGGER.exception("Failed to ingest trace bridge event event_id=%s: %s", event_id, exc)

    if created_count > 0:
        LOGGER.info("Ingested %d trace bridge event(s) from file into MLflow traces.", created_count)
    return created_count


def ingest_trace_bridge_events_from_db(
    trace_db_schema: str,
    trace_db_events_table: str,
    trace_db_documents_table: str,
    trace_db_batch_size: int,
) -> int:
    spring_datasource_url = os.getenv("SPRING_DATASOURCE_URL")
    fallback_schema = extract_schema_from_jdbc_query(spring_datasource_url) if spring_datasource_url else None
    schema_name = trace_db_schema or fallback_schema or DEFAULT_TRACE_DB_SCHEMA
    schema_name, events_fqn, docs_fqn = resolve_trace_bridge_table_names(
        schema_name, trace_db_events_table, trace_db_documents_table
    )

    try:
        pending_events = fetch_pending_bridge_events_from_db(events_fqn, docs_fqn, max(1, trace_db_batch_size))
    except Exception as exc:
        LOGGER.exception("Failed to read trace bridge events from DB table %s: %s", events_fqn, exc)
        return 0

    if not pending_events:
        return 0

    created_count = 0
    successful_event_ids: list[str] = []
    for event in pending_events:
        event_id = str(event.get("event_id") or "").strip()
        run_id = str(event.get("run_id") or "").strip()
        question = str(event.get("question") or "").strip()
        answer = str(event.get("answer") or "").strip()
        retriever_output = normalize_retrieved_chunks(event.get("retriever_output"))
        if not run_id or not question or not answer:
            LOGGER.warning("Skipping DB bridge event missing run_id/question/answer: event_id=%s", event_id)
            continue

        root_span_name = str(event.get("root_span_name") or "chat.ask.root")
        retriever_span_name = str(event.get("retriever_span_name") or "chat.ask.retriever")
        retriever_span_type = str(event.get("retriever_span_type") or "RETRIEVER")

        try:
            with mlflow.start_run(run_id=run_id):
                with mlflow.start_span(name=root_span_name, span_type="CHAIN") as root_span:
                    root_span.set_inputs({"question": question})
                    with mlflow.start_span(name=retriever_span_name, span_type=retriever_span_type) as retriever_span:
                        retriever_span.set_attributes({"span_type": retriever_span_type})
                        retriever_span.set_outputs(retriever_output)
                    root_span.set_outputs({"answer": answer})
            successful_event_ids.append(event_id)
            created_count += 1
        except Exception as exc:
            LOGGER.exception("Failed to ingest DB trace bridge event event_id=%s: %s", event_id, exc)

    if successful_event_ids:
        try:
            mark_bridge_events_ingested_in_db(events_fqn, successful_event_ids)
        except Exception as exc:
            LOGGER.exception("Failed to mark DB bridge events ingested in %s: %s", events_fqn, exc)

    if created_count > 0:
        LOGGER.info("Ingested %d trace bridge event(s) from DB into MLflow traces.", created_count)
    return created_count


def ingest_trace_bridge_events(args: Namespace) -> int:
    source = (args.trace_bridge_source or "db").strip().lower()
    if source == "file":
        return ingest_trace_bridge_events_from_file(args.trace_bridge_file)
    return ingest_trace_bridge_events_from_db(
        trace_db_schema=args.trace_db_schema,
        trace_db_events_table=args.trace_db_events_table,
        trace_db_documents_table=args.trace_db_documents_table,
        trace_db_batch_size=args.trace_db_batch_size,
    )

