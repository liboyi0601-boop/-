#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT_DIR/scripts/build.sh"

cd "$ROOT_DIR"

if [[ $# -eq 0 ]]; then
  echo "Usage: ./scripts/generate_benchmark_workloads.sh <suite-name> [--overwrite]" >&2
  exit 1
elif [[ "$1" != --* ]]; then
  first_arg="$1"
  shift
  set -- --suite "$first_arg" "$@"
fi

java -cp "$ROOT_DIR/bin:$ROOT_DIR/Lib/*:$ROOT_DIR/ComparisonAlgorithm" workflow.BenchmarkWorkflowGenerator "$@"
