import json
from typing import Any

import mlflow


def normalize_retrieved_chunks(raw_output: Any) -> list[dict[str, Any]]:
    normalized: list[dict[str, Any]] = []
    if raw_output is None:
        return normalized

    if isinstance(raw_output, dict):
        for key in ("documents", "chunks", "results", "output"):
            value = raw_output.get(key)
            if isinstance(value, list):
                return normalize_retrieved_chunks(value)
        if "page_content" in raw_output or "metadata" in raw_output:
            page_content = str(raw_output.get("page_content") or "").strip()
            metadata = raw_output.get("metadata")
            if not isinstance(metadata, dict):
                metadata = {}
            if page_content:
                normalized.append({"page_content": page_content, "metadata": metadata})
        return normalized

    if isinstance(raw_output, list):
        for item in raw_output:
            normalized.extend(normalize_retrieved_chunks(item))
        return normalized

    if isinstance(raw_output, str):
        text = raw_output.strip()
        if text:
            normalized.append({"page_content": text, "metadata": {}})
    return normalized


def read_json_field(value: str | None) -> dict[str, Any]:
    if not value:
        return {}
    try:
        parsed = json.loads(value)
        return parsed if isinstance(parsed, dict) else {}
    except json.JSONDecodeError:
        return {}


def trace_root_span(trace: Any) -> Any | None:
    spans = getattr(getattr(trace, "data", None), "spans", None) or []
    for span in spans:
        if getattr(span, "parent_id", None) in (None, ""):
            return span
    return spans[0] if spans else None


def extract_question_answer_from_trace(trace: Any) -> tuple[str | None, str | None]:
    root_span = trace_root_span(trace)
    question = None
    answer = None

    if root_span is not None:
        inputs = getattr(root_span, "inputs", None)
        outputs = getattr(root_span, "outputs", None)
        if isinstance(inputs, dict):
            question = (
                inputs.get("question")
                or inputs.get("user_input")
                or inputs.get("input")
            )
        if isinstance(outputs, dict):
            answer = (
                outputs.get("answer")
                or outputs.get("response")
                or outputs.get("output")
                or outputs.get("result")
            )

    metadata = getattr(getattr(trace, "info", None), "request_metadata", {}) or {}
    if not question:
        trace_inputs = read_json_field(metadata.get("mlflow.traceInputs"))
        question = trace_inputs.get("question") or trace_inputs.get("user_input")
    if not answer:
        trace_outputs = read_json_field(metadata.get("mlflow.traceOutputs"))
        answer = trace_outputs.get("answer") or trace_outputs.get("response") or trace_outputs.get("result")

    if question is not None:
        question = str(question).strip()
    if answer is not None:
        answer = str(answer).strip()

    return (question or None, answer or None)


def extract_retrieved_chunks_from_trace(trace: Any) -> list[dict[str, Any]]:
    retriever_spans = []
    search_spans = getattr(trace, "search_spans", None)
    if callable(search_spans):
        try:
            retriever_spans = search_spans(span_type="RETRIEVER") or []
        except Exception:
            retriever_spans = []

    if not retriever_spans:
        spans = getattr(getattr(trace, "data", None), "spans", None) or []
        for span in spans:
            attributes = getattr(span, "attributes", {}) or {}
            if str(attributes.get("span_type", "")).upper() == "RETRIEVER":
                retriever_spans.append(span)

    documents: list[dict[str, Any]] = []
    for span in retriever_spans:
        documents.extend(normalize_retrieved_chunks(getattr(span, "outputs", None)))
    return documents


def extract_run_linked_run_id(trace: Any) -> str | None:
    metadata = getattr(getattr(trace, "info", None), "request_metadata", {}) or {}
    value = metadata.get("mlflow.sourceRun")
    if value is None:
        return None
    run_id = str(value).strip()
    return run_id or None


def load_latest_trace_for_run(run_id: str, experiment_id: str | None = None) -> Any | None:
    search_kwargs: dict[str, Any] = {
        "run_id": run_id,
        "max_results": 25,
        "return_type": "list",
        "include_spans": True,
    }
    if experiment_id:
        search_kwargs["locations"] = [str(experiment_id)]

    try:
        traces = mlflow.search_traces(**search_kwargs)
    except TypeError:
        traces = mlflow.search_traces(
            run_id=run_id,
            max_results=25,
            return_type="list",
            include_spans=True,
        )
    if not traces:
        return None
    linked = [t for t in traces if extract_run_linked_run_id(t) == run_id]
    pool = linked if linked else traces
    pool.sort(key=lambda t: getattr(getattr(t, "info", None), "timestamp_ms", 0), reverse=True)
    return pool[0]

