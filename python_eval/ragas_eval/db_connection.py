import os
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse

from .runtime_init import first_non_blank


def sanitize_sql_identifier(value: str, label: str) -> str:
    candidate = (value or "").strip()
    if not candidate:
        raise ValueError(f"Missing required SQL identifier for {label}.")
    if not candidate.replace("_", "").isalnum() or candidate[0].isdigit():
        raise ValueError(f"Unsafe SQL identifier for {label}: {candidate}")
    return candidate


def extract_schema_from_jdbc_query(jdbc_url: str) -> str | None:
    try:
        parsed = urlparse(jdbc_url.removeprefix("jdbc:"))
        query = parse_qs(parsed.query or "")
        current_schema = query.get("currentSchema")
        if not current_schema:
            return None
        first_value = (current_schema[0] or "").split(",")[0].strip()
        return first_value or None
    except Exception:
        return None


def resolve_trace_bridge_table_names(
    schema: str,
    events_table: str,
    documents_table: str,
) -> tuple[str, str, str]:
    resolved_schema = sanitize_sql_identifier(schema, "trace schema")
    resolved_events_table = sanitize_sql_identifier(events_table, "trace events table")
    resolved_documents_table = sanitize_sql_identifier(documents_table, "trace documents table")
    return (
        resolved_schema,
        f"{resolved_schema}.{resolved_events_table}",
        f"{resolved_schema}.{resolved_documents_table}",
    )


def import_pg_driver() -> tuple[str, Any]:
    try:
        import psycopg2  # type: ignore

        return "psycopg2", psycopg2
    except ImportError:
        try:
            import psycopg  # type: ignore

            return "psycopg", psycopg
        except ImportError as exc:
            raise RuntimeError(
                "Missing PostgreSQL driver. Install psycopg2-binary (or psycopg) in python_eval environment."
            ) from exc


def normalize_postgres_url(raw_url: str) -> str:
    value = raw_url.strip()
    if value.startswith("jdbc:"):
        value = value.removeprefix("jdbc:")
    if value.startswith("postgresql+psycopg2://"):
        return "postgresql://" + value.removeprefix("postgresql+psycopg2://")
    if value.startswith("postgresql+psycopg://"):
        return "postgresql://" + value.removeprefix("postgresql+psycopg://")
    return value


def resolve_trace_db_connect_kwargs() -> dict[str, Any]:
    raw_url = first_non_blank(
        os.getenv("RAGAS_TRACE_DB_URL"),
        os.getenv("SPRING_DATASOURCE_URL"),
    )
    if not raw_url:
        raise ValueError("Missing DB URL: set RAGAS_TRACE_DB_URL or SPRING_DATASOURCE_URL.")

    parsed = urlparse(normalize_postgres_url(raw_url))
    if parsed.scheme not in {"postgresql", "postgres"}:
        raise ValueError(f"Unsupported DB URL scheme for trace bridge: {parsed.scheme}")

    dbname = unquote((parsed.path or "").lstrip("/"))
    if not dbname:
        raise ValueError("Trace DB URL is missing database name.")

    username = first_non_blank(
        os.getenv("RAGAS_TRACE_DB_USERNAME"),
        os.getenv("SPRING_DATASOURCE_USERNAME"),
        os.getenv("POSTGRES_USER"),
        parsed.username,
    )
    password = first_non_blank(
        os.getenv("RAGAS_TRACE_DB_PASSWORD"),
        os.getenv("SPRING_DATASOURCE_PASSWORD"),
        os.getenv("POSTGRES_PASSWORD"),
        parsed.password,
    )

    if not username:
        raise ValueError("Missing DB username: set RAGAS_TRACE_DB_USERNAME or SPRING_DATASOURCE_USERNAME.")

    return {
        "host": parsed.hostname or "localhost",
        "port": parsed.port or 5432,
        "dbname": dbname,
        "user": unquote(username),
        "password": unquote(password) if password is not None else "",
    }


def fetch_pending_bridge_events_from_db(
    events_fqn: str,
    documents_fqn: str,
    batch_size: int,
) -> list[dict[str, Any]]:
    driver_name, driver_module = import_pg_driver()
    connect_kwargs = resolve_trace_db_connect_kwargs()

    with driver_module.connect(**connect_kwargs) as conn:
        if driver_name == "psycopg2":
            conn.autocommit = False

        with conn.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT event_id, run_id, question, answer, root_span_name, retriever_span_name, retriever_span_type
                FROM {events_fqn}
                WHERE ingested = FALSE
                ORDER BY event_time_epoch_ms ASC
                LIMIT %s
                """,
                (max(1, batch_size),),
            )
            rows = cursor.fetchall()

            if not rows:
                return []

            events = []
            event_ids = []
            for row in rows:
                event_ids.append(str(row[0]))
                events.append(
                    {
                        "event_id": str(row[0]),
                        "run_id": str(row[1] or "").strip(),
                        "question": str(row[2] or "").strip(),
                        "answer": str(row[3] or "").strip(),
                        "root_span_name": str(row[4] or "chat.ask.root"),
                        "retriever_span_name": str(row[5] or "chat.ask.retriever"),
                        "retriever_span_type": str(row[6] or "RETRIEVER"),
                    }
                )

            placeholders = ", ".join(["%s"] * len(event_ids))
            cursor.execute(
                f"""
                SELECT event_id, doc_id, source, source_path, rank_order, similarity, page_content
                FROM {documents_fqn}
                WHERE event_id IN ({placeholders})
                ORDER BY event_id ASC, rank_order ASC
                """,
                tuple(event_ids),
            )
            doc_rows = cursor.fetchall()

            docs_by_event: dict[str, list[dict[str, Any]]] = {}
            for doc_row in doc_rows:
                event_id = str(doc_row[0])
                docs_by_event.setdefault(event_id, []).append(
                    {
                        "page_content": str(doc_row[6] or "").strip(),
                        "metadata": {
                            "doc_id": doc_row[1],
                            "source": doc_row[2],
                            "source_path": doc_row[3],
                            "rank": doc_row[4],
                            "similarity": doc_row[5],
                        },
                    }
                )

            for event in events:
                event["retriever_output"] = docs_by_event.get(event["event_id"], [])

            return events


def mark_bridge_events_ingested_in_db(events_fqn: str, event_ids: list[str]) -> None:
    if not event_ids:
        return

    driver_name, driver_module = import_pg_driver()
    connect_kwargs = resolve_trace_db_connect_kwargs()

    with driver_module.connect(**connect_kwargs) as conn:
        if driver_name == "psycopg2":
            conn.autocommit = False

        with conn.cursor() as cursor:
            placeholders = ", ".join(["%s"] * len(event_ids))
            cursor.execute(
                f"""
                UPDATE {events_fqn}
                SET ingested = TRUE, ingested_at = CURRENT_TIMESTAMP
                WHERE event_id IN ({placeholders})
                """,
                tuple(event_ids),
            )
        conn.commit()

