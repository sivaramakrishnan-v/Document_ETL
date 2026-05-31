#!/usr/bin/env python
from ragas_eval.cli import parse_args


def main() -> None:
    args = parse_args()
    from ragas_eval.pipeline import run_pipeline

    run_pipeline(args)


if __name__ == "__main__":
    main()
