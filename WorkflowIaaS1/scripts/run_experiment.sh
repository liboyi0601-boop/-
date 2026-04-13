#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT_DIR/scripts/build.sh"

cd "$ROOT_DIR"
java -cp "$ROOT_DIR/bin:$ROOT_DIR/Lib/*:$ROOT_DIR/ComparisonAlgorithm" main.ExperimentPlatform
