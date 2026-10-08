#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
: "${ANDROID_JAR:?Set ANDROID_JAR}"
: "${BUILD_TOOLS:?Set BUILD_TOOLS}"
: "${FLIGHTS_KEYSTORE:?Set FLIGHTS_KEYSTORE}"
: "${FLIGHTS_STOREPASS:?Set FLIGHTS_STOREPASS}"
JAVAC=${JAVAC:-javac}
JAR=${JAR:-jar}
OUT="$PROJECT_ROOT/build"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/generated"
"$BUILD_TOOLS/aapt2" compile --dir "$PROJECT_ROOT/app/src/main/res" -o "$OUT/resources.zip"
"$BUILD_TOOLS/aapt2" link -o "$OUT/base.apk" --manifest "$PROJECT_ROOT/app/src/main/AndroidManifest.xml" -I "$ANDROID_JAR" -A "$PROJECT_ROOT/app/src/main/assets" --java "$OUT/generated" "$OUT/resources.zip"
find "$PROJECT_ROOT/app/src/main/java" "$OUT/generated" -name '*.java' > "$OUT/sources.txt"
"$JAVAC" -encoding UTF-8 -source 17 -target 17 -classpath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"
"$JAR" cf "$OUT/classes.jar" -C "$OUT/classes" .
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 28 --output "$OUT/dex" "$OUT/classes.jar"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
python3 - "$OUT" <<'PY'
import pathlib,sys,zipfile
p=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(p/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in (p/'dex').glob('*.dex'):z.write(f,f.name)
PY
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$FLIGHTS_KEYSTORE" --ks-pass env:FLIGHTS_STOREPASS --ks-key-alias flights --out "$OUT/Decision-Flights-v0.1.0.apk" "$OUT/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT/Decision-Flights-v0.1.0.apk"
