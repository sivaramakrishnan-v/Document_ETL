import asyncio
import logging
import os
from pathlib import Path
from typing import Any

import mlflow
from google import genai
from mlflow.tracking import MlflowClient
from ragas.embeddings import GoogleEmbeddings
from ragas.llms import llm_factory
from ragas.metrics.collections import AnswerRelevancy, Faithfulness

from .constants import EXPERIMENT_NAME, LOGGER, REPO_ROOT


def configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )


def load_env_file(path: Path) -> None:
    if not path.exists():
        return

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key:
            os.environ.setdefault(key, value)


def require_env(name: str) -> str:
    value = os.getenv(name)
    if not value or not value.strip():
        raise ValueError(f"Missing required environment variable: {name}")
    return value.strip()


def first_non_blank(*values: str | None) -> str | None:
    for value in values:
        if value and value.strip():
            return value.strip()
    return None


def resolve_credentials_path(raw_value: str | None) -> Path | None:
    if raw_value is None:
        return None
    value = raw_value.strip()
    if not value:
        return None
    if value.startswith("classpath:"):
        relative = value.removeprefix("classpath:").lstrip("/\\")
        return (REPO_ROOT / "src" / "main" / "resources" / relative).resolve()
    path = Path(value)
    if not path.is_absolute():
        path = (REPO_ROOT / path).resolve()
    return path


def build_genai_client() -> tuple[Any, str, str | None, str | None]:
    google_api_key = first_non_blank(os.getenv("GOOGLE_API_KEY"), os.getenv("GEMINI_API_KEY"))
    use_vertex_raw = os.getenv("RAGAS_USE_VERTEX", "true").strip().lower()
    use_vertex = use_vertex_raw in {"1", "true", "yes", "y", "on"}

    if use_vertex:
        project_id = first_non_blank(
            os.getenv("RAGAS_VERTEX_PROJECT_ID"),
            os.getenv("VERTEX_PROJECT_ID"),
            os.getenv("GOOGLE_CLOUD_PROJECT"),
            "demokafkabook",
        )
        location = first_non_blank(
            os.getenv("RAGAS_VERTEX_LOCATION"),
            os.getenv("VERTEX_LOCATION"),
            os.getenv("GOOGLE_CLOUD_LOCATION"),
            "us-central1",
        )
        credentials_path = resolve_credentials_path(
            first_non_blank(
                os.getenv("GOOGLE_APPLICATION_CREDENTIALS"),
                os.getenv("SPRING_CLOUD_GCP_CREDENTIALS_LOCATION"),
                "classpath:gcp-vertex-key.json",
            )
        )
        if credentials_path is not None:
            if credentials_path.exists():
                os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = str(credentials_path)
            else:
                LOGGER.warning("Vertex credentials file not found at %s", credentials_path)

        client = genai.Client(vertexai=True, project=project_id, location=location)
        return client, "vertex", project_id, location

    if not google_api_key:
        raise ValueError("GOOGLE_API_KEY is required when RAGAS_USE_VERTEX is false.")
    client = genai.Client(api_key=google_api_key)
    return client, "api_key", None, None


def initialize_mlflow() -> tuple[MlflowClient, str]:
    tracking_uri = require_env("MLFLOW_TRACKING_URI")
    mlflow.set_tracking_uri(tracking_uri)
    client = MlflowClient()
    experiment = client.get_experiment_by_name(EXPERIMENT_NAME)
    if experiment is None:
        raise ValueError(f"MLflow experiment not found: {EXPERIMENT_NAME}")
    experiment_id = experiment.experiment_id
    LOGGER.info("Connected to MLflow experiment '%s' (id=%s).", EXPERIMENT_NAME, experiment_id)
    return client, experiment_id


def build_ragas_metrics() -> tuple[Faithfulness, AnswerRelevancy]:
    gemini_model = os.getenv("RAGAS_GEMINI_MODEL", "gemini-2.5-pro")
    gemini_embedding_model = os.getenv("RAGAS_GEMINI_EMBEDDING_MODEL", "gemini-embedding-001")
    genai_client, auth_mode, vertex_project_id, vertex_location = build_genai_client()
    LOGGER.info("Using Gemini evaluator auth mode: %s", auth_mode)

    try:
        judge_llm = llm_factory(
            model=gemini_model,
            provider="google",
            client=genai_client,
        )
    except Exception as exc:
        if auth_mode == "vertex":
            raise RuntimeError("Failed to initialize Gemini judge in Vertex mode.") from exc

        google_api_key = first_non_blank(os.getenv("GOOGLE_API_KEY"), os.getenv("GEMINI_API_KEY"))
        LOGGER.warning("Google provider initialization failed, using Gemini OpenAI-compatible endpoint: %s", exc)
        try:
            from openai import OpenAI
        except ImportError as import_error:
            raise RuntimeError(
                "Failed to initialize Gemini judge via google-genai, and openai package is unavailable for fallback."
            ) from import_error

        judge_llm = llm_factory(
            model=gemini_model,
            provider="openai",
            client=OpenAI(
                api_key=google_api_key,
                base_url="https://generativelanguage.googleapis.com/v1beta/openai/",
            ),
        )

    # RAGAS metrics rely on ascore() which calls llm.agenerate().
    # Some provider/client combos only expose synchronous generate() in current versions.
    if not getattr(judge_llm, "is_async", False):
        LOGGER.info("Wrapping synchronous judge LLM for async RAGAS scoring.")
        sync_generate = judge_llm.generate

        async def _agenerate(prompt: str, response_model: Any) -> Any:
            return await asyncio.to_thread(sync_generate, prompt, response_model)

        judge_llm.agenerate = _agenerate  # type: ignore[method-assign]

    if auth_mode == "vertex":
        embeddings = GoogleEmbeddings(
            client=genai_client,
            model=gemini_embedding_model,
            use_vertex=True,
            project_id=vertex_project_id,
            location=vertex_location,
        )
    else:
        embeddings = GoogleEmbeddings(client=genai_client, model=gemini_embedding_model)

    faithfulness_metric = Faithfulness(llm=judge_llm)
    answer_relevancy_metric = AnswerRelevancy(llm=judge_llm, embeddings=embeddings)
    return faithfulness_metric, answer_relevancy_metric
