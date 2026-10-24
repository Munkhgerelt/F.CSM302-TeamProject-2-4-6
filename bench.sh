#!/usr/bin/env bash
# Timing measurements for the report (1 thread vs. 2 sorting + 1 merging thread).
# Results: tests/results/benchmark.md and tests/results/benchmark.csv. Starts 3 separate JVMs; takes about 1-3 minutes.
set -euo pipefail
cd "$(dirname "$0")"

./build.sh
rm -rf out-test
mkdir -p out-test
javac --release 17 -encoding UTF-8 -cp out -d out-test tests/Benchmark.java
java -cp "out:out-test" Benchmark "$(pwd)" "$@"
