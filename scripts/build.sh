#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
: "${ANDROID_JAR:?Set ANDROID_JAR to platform android.jar (API 35+)}"
: "${BUILD_TOOLS:?Set BUILD_TOOLS to Android build tools directory}"
: "${LUMI_KEYSTORE:?Set LUMI_KEYSTORE to private release keystore path}"
: "${LUMI_STOREPASS:?Set LUMI_STOREPASS}"
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
import zipfile,pathlib,sys
p=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(p/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in (p/'dex').glob('*.dex'):z.write(f,f.name)
PY
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$LUMI_KEYSTORE" --ks-pass env:LUMI_STOREPASS --ks-key-alias lumi --out "$OUT/LUMI-Orbit-v0.2.0.apk" "$OUT/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT/LUMI-Orbit-v0.2.0.apk"
