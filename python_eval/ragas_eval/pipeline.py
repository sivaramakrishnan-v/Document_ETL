from argparse import Namespace

from .constants import (
    ANSWER_RELEVANCY_METRIC_NAME,
    DEFAULT_DOTENV_PATH,
    FAITHFULNESS_METRIC_NAME,
    LOGGER,
)
from .eval_logic import evaluate_and_log
from .runs import find_candidate_runs
from .runtime_init import build_ragas_metrics, configure_logging, initialize_mlflow, load_env_file
from .trace_bridge import ingest_trace_bridge_events


def run_pipeline(args: Namespace) -> None:
    configure_logging()
    load_env_file(DEFAULT_DOTENV_PATH)

    client, experiment_id = initialize_mlflow()

    LOGGER.info("Trace bridge ingestion source: %s", args.trace_bridge_source)
    ingest_trace_bridge_events(args)

    faithfulness_metric, answer_relevancy_metric = build_ragas_metrics()

    candidates = find_candidate_runs(client, experiment_id, max(1, args.limit))
    if not candidates:
        LOGGER.info("No eligible runs found without %s and %s.", FAITHFULNESS_METRIC_NAME, ANSWER_RELEVANCY_METRIC_NAME)
        return

    success_count = 0
    for run in candidates:
        if evaluate_and_log(client, run, faithfulness_metric, answer_relevancy_metric):
            success_count += 1

    LOGGER.info("Completed scoring: %d/%d run(s) updated.", success_count, len(candidates))
