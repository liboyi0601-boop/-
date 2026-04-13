#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="$ROOT_DIR/src"
BIN_DIR="$ROOT_DIR/bin"
LIB_CP="$ROOT_DIR/Lib/*"
COMPARE_CP="$ROOT_DIR/ComparisonAlgorithm"

mkdir -p "$BIN_DIR"

javac \
  -encoding GBK \
  -cp "$LIB_CP:$COMPARE_CP:$SRC_DIR" \
  -d "$BIN_DIR" \
  $(find "$SRC_DIR" -name '*.java' | sort)

echo "Build completed: $BIN_DIR"
