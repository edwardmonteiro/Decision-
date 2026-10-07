#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR to org.json json-20250517.jar}"
mkdir -p build/direct-tests
javac -cp "$JSON_JAR" -d build/direct-tests app/src/main/java/com/edward/lumi/DirectAi.java tests/java/com/edward/lumi/DirectAiTest.java
java -cp "build/direct-tests:$JSON_JAR" com.edward.lumi.DirectAiTest
