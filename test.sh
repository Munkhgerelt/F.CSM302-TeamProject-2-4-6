#!/usr/bin/env bash
# Build the application, compile tests/TestRunner.java and run all automated tests.
# The result table is written to tests/results/test-results.md.
set -euo pipefail
cd "$(dirname "$0")"

./build.sh
rm -rf out-test
mkdir -p out-test
javac --release 17 -encoding UTF-8 -cp out -d out-test tests/TestRunner.java
java -cp "out:out-test" TestRunner "$(pwd)"
