import asyncio
import inspect
from typing import Any

from mlflow.entities import Run
from mlflow.tracking import MlflowClient
from ragas.metrics.collections import AnswerRelevancy, Faithfulness

from .constants import ANSWER_RELEVANCY_METRIC_NAME, FAITHFULNESS_METRIC_NAME, LOGGER
from .trace_utils import (
    extract_question_answer_from_trace,
    extract_retrieved_chunks_from_trace,
    load_latest_trace_for_run,
)


def as_float_score(result: Any) -> float:
    return float(getattr(result, "value", result))


def is_sync_client_error(exc: Exception) -> bool:
    message = str(exc).lower()
    return "synchronous client" in message and "use generate() instead" in message


def score_run(
    faithfulness_metric: Faithfulness,
    answer_relevancy_metric: AnswerRelevancy,
    question: str,
    answer: str,
    retrieved_chunks: list[dict[str, Any]],
) -> tuple[float, float]:
    contexts = [
        str(chunk.get("page_content") or "").strip()
        for chunk in retrieved_chunks
        if str(chunk.get("page_content") or "").strip()
    ]
    if not contexts:
        raise ValueError("No retrieved contexts available in retriever span output.")

    base_kwargs = {
        "user_input": question,
        "response": answer,
        "retrieved_contexts": contexts,
    }

    faithfulness_kwargs = {
        key: value
        for key, value in base_kwargs.items()
        if key in inspect.signature(faithfulness_metric.ascore).parameters
    }
    answer_relevancy_kwargs = {
        key: value
        for key, value in base_kwargs.items()
        if key in inspect.signature(answer_relevancy_metric.ascore).parameters
    }

    try:
        faithfulness_result = asyncio.run(
            faithfulness_metric.ascore(**faithfulness_kwargs)
        )
        answer_relevancy_result = asyncio.run(
            answer_relevancy_metric.ascore(**answer_relevancy_kwargs)
        )
        return as_float_score(faithfulness_result), as_float_score(answer_relevancy_result)
    except TypeError as exc:
        if not is_sync_client_error(exc):
            raise
        raise RuntimeError(
            "RAGAS metrics require an async-capable LLM client for ascore(). "
            "Initialize the judge LLM with an async client (for example google.genai.Client(...).aio)."
        ) from exc


def evaluate_and_log(
    client: MlflowClient,
    run: Run,
    faithfulness_metric: Faithfulness,
    answer_relevancy_metric: AnswerRelevancy,
) -> bool:
    run_id = run.info.run_id
    trace = load_latest_trace_for_run(run_id, getattr(run.info, "experiment_id", None))
    if trace is None:
        LOGGER.warning("Run %s skipped: no trace found.", run_id)
        return False

    question, answer = extract_question_answer_from_trace(trace)
    if not question:
        question = (run.data.params or {}).get("question")
    if not answer:
        answer = (run.data.params or {}).get("answer")

    if not question or not answer:
        LOGGER.warning("Run %s skipped: question/answer missing.", run_id)
        return False

    retrieved_chunks = extract_retrieved_chunks_from_trace(trace)
    if not retrieved_chunks:
        LOGGER.warning("Run %s skipped: retriever span output missing.", run_id)
        return False

    try:
        faithfulness_score, answer_relevancy_score = score_run(
            faithfulness_metric, answer_relevancy_metric, question, answer, retrieved_chunks
        )
    except Exception as exc:
        LOGGER.exception("RAGAS scoring failed for run %s: %s", run_id, exc)
        return False

    client.log_metric(run_id, FAITHFULNESS_METRIC_NAME, faithfulness_score)
    client.log_metric(run_id, ANSWER_RELEVANCY_METRIC_NAME, answer_relevancy_score)
    LOGGER.info(
        "Run %s scored: faithfulness=%.4f answer_relevancy=%.4f",
        run_id,
        faithfulness_score,
        answer_relevancy_score,
    )
    return True
