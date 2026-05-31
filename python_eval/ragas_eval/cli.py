import argparse
from pathlib import Path

from .constants import (
    DEFAULT_TRACE_DB_BATCH_SIZE,
    DEFAULT_TRACE_DB_DOCUMENTS_TABLE,
    DEFAULT_TRACE_DB_EVENTS_TABLE,
    DEFAULT_TRACE_DB_SCHEMA,
    DEFAULT_TRACE_DB_SOURCE,
    TRACE_BRIDGE_PATH,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compute and backfill RAGAS metrics to MLflow runs.")
    parser.add_argument("--limit", type=int, default=5, help="Max number of runs to evaluate (default: 5)")
    parser.add_argument(
        "--trace-bridge-source",
        choices=["db", "file"],
        default=DEFAULT_TRACE_DB_SOURCE,
        help=f"Trace bridge source: db or file (default: {DEFAULT_TRACE_DB_SOURCE})",
    )
    parser.add_argument(
        "--trace-bridge-file",
        type=Path,
        default=TRACE_BRIDGE_PATH,
        help=f"Bridge file from Java trace emitter (default: {TRACE_BRIDGE_PATH})",
    )
    parser.add_argument(
        "--trace-db-schema",
        default=DEFAULT_TRACE_DB_SCHEMA,
        help=f"Trace bridge schema name (default: {DEFAULT_TRACE_DB_SCHEMA})",
    )
    parser.add_argument(
        "--trace-db-events-table",
        default=DEFAULT_TRACE_DB_EVENTS_TABLE,
        help=f"Trace bridge events table (default: {DEFAULT_TRACE_DB_EVENTS_TABLE})",
    )
    parser.add_argument(
        "--trace-db-documents-table",
        default=DEFAULT_TRACE_DB_DOCUMENTS_TABLE,
        help=f"Trace bridge documents table (default: {DEFAULT_TRACE_DB_DOCUMENTS_TABLE})",
    )
    parser.add_argument(
        "--trace-db-batch-size",
        type=int,
        default=DEFAULT_TRACE_DB_BATCH_SIZE,
        help=f"Max pending DB events to ingest per run (default: {DEFAULT_TRACE_DB_BATCH_SIZE})",
    )
    return parser.parse_args()

