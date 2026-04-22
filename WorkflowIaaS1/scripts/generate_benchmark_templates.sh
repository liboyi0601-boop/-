#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT_DIR/scripts/build.sh"

cd "$ROOT_DIR"

if [[ $# -eq 0 ]]; then
  set -- --family all
elif [[ "$1" != --* ]]; then
  first_arg="$1"
  shift
  set -- --family "$first_arg" "$@"
fi

java -cp "$ROOT_DIR/bin:$ROOT_DIR/Lib/*:$ROOT_DIR/ComparisonAlgorithm" workflow.BenchmarkTemplateBuilder "$@"
