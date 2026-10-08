#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
: "${JSON_JAR:?Set JSON_JAR to org.json JSON-java jar}"
JAVAC=${JAVAC:-javac}
OUT="$PROJECT_ROOT/build/tests"
mkdir -p "$OUT"
"$JAVAC" -encoding UTF-8 -source 17 -target 17 -cp "$JSON_JAR" -d "$OUT" "$PROJECT_ROOT/app/src/main/java/com/edward/flights/FlightContracts.java" "$PROJECT_ROOT/app/src/main/java/com/edward/flights/FlightAgent.java" "$PROJECT_ROOT/tests/FlightAgentTest.java"
java -cp "$JSON_JAR:$OUT" com.edward.flights.FlightAgentTest
node --check "$PROJECT_ROOT/app/src/main/assets/app.js"
