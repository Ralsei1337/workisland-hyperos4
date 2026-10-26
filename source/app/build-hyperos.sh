#!/bin/bash
# 工作岛 · HyperOS 4 构建脚本（无 Gradle：aapt2 + javac + d8 + zipalign + apksigner）
# 用法：./build-hyperos.sh [输出目录]
set -e
cd "$(dirname "$0")"

SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
PLATFORM="${PLATFORM:-android-37.0}"
BUILD_TOOLS="${BUILD_TOOLS:-android-37.0}"
JAR="$SDK_ROOT/platforms/$PLATFORM/android.jar"
BT="$SDK_ROOT/build-tools/$BUILD_TOOLS"
KEYSTORE="${KEYSTORE:-/root/keys/workisland.jks}"
KS_PASS="${KS_PASS:-workisland}"
OUT="${1:-build}"
APP_NAME="WorkIsland-hyperos"

AAPT2="${AAPT2:-/opt/termux-tools/usr/bin/aapt2}"
TERMUX_LIB="${TERMUX_LIB:-/opt/termux-tools/usr/lib}"
aapt2() { LD_LIBRARY_PATH="$TERMUX_LIB" "$AAPT2" "$@"; }
for t in "$AAPT2" "$BT/d8" "$BT/apksigner" "$JAR"; do
  [ -e "$t" ] || { echo "缺少构建工具：$t" >&2; exit 1; }
done

rm -rf "$OUT" gen
mkdir -p "$OUT"

echo "[1/6] aapt2 compile"
aapt2 compile --dir res -o "$OUT/res.zip"

echo "[2/6] aapt2 link"
aapt2 link -o "$OUT/base.apk" -I "$JAR" \
  --manifest AndroidManifest.xml --java gen --auto-add-overlay "$OUT/res.zip"

echo "[3/6] javac"
find src gen -name '*.java' > "$OUT/sources.txt"
javac --release 11 -classpath "$JAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/6] d8"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
mkdir -p "$OUT/dex"
"$BT/d8" --release --lib "$JAR" --min-api "${MIN_API:-36}" --output "$OUT/dex" "$OUT/classes.jar"

echo "[5/6] package"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q -j unsigned.apk dex/classes.dex)
cp "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[6/6] sign"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias workisland \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$OUT/$APP_NAME.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT/$APP_NAME.apk" | head -5

echo "built: $(pwd)/$OUT/$APP_NAME.apk"
ls -l "$OUT/$APP_NAME.apk"
