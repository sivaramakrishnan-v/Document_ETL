from mlflow.entities import Run
from mlflow.tracking import MlflowClient

from .constants import ANSWER_RELEVANCY_METRIC_NAME, FAITHFULNESS_METRIC_NAME


def find_candidate_runs(client: MlflowClient, experiment_id: str, limit: int) -> list[Run]:
    max_results = max(limit * 25, 50)
    runs: list[Run] = []
    seen_run_ids: set[str] = set()

    for action_name in ("chat.ask", "chat.ask.review"):
        action_runs = client.search_runs(
            experiment_ids=[experiment_id],
            filter_string=f"tags.action_name = '{action_name}'",
            max_results=max_results,
            order_by=["attributes.start_time DESC"],
        )
        for run in action_runs:
            run_id = run.info.run_id
            if run_id in seen_run_ids:
                continue
            seen_run_ids.add(run_id)
            runs.append(run)

    runs.sort(key=lambda run: getattr(run.info, "start_time", 0), reverse=True)
    candidates: list[Run] = []
    for run in runs:
        metrics = run.data.metrics or {}
        if FAITHFULNESS_METRIC_NAME in metrics or ANSWER_RELEVANCY_METRIC_NAME in metrics:
            continue
        candidates.append(run)
        if len(candidates) >= limit:
            break
    return candidates

