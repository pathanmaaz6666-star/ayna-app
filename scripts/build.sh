#!/usr/bin/env bash
# Ayna — build script.
# Run on a NORMAL machine (your laptop / Android Studio / a build server).
# This VM cannot run the Gradle daemon (the sandbox blocks the daemon's
# loopback TCP), so the APK is built here, not in the agent VM.
#
# Requires: JDK 17, Android SDK (platform-tools, platforms;android-34,
# build-tools;34.0.0). No card, no paid tools, no secrets.
#
# Usage:
#   ./scripts/build.sh            # debug APK
#   ./scripts/build.sh release    # release APK (unsigned unless signing is set up)
set -euo pipefail
cd "$(dirname "$0")/.."

# --- Locate JDK 17 ---
if [ -z "${JAVA_HOME:-}" ]; then
  for cand in "$HOME/workspace/android-sdk/jdk17" /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"; do
    if [ -x "$cand/bin/java" ]; then JAVA_HOME="$cand"; break; fi
  done
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "ERROR: JDK 17 not found. Set JAVA_HOME to a JDK 17 install." >&2
  exit 1
fi
export JAVA_HOME

# --- Locate Android SDK ---
if [ -z "${ANDROID_HOME:-}" ]; then
  for cand in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/workspace/android-sdk"; do
    if [ -d "$cand/platforms/android-34" ]; then ANDROID_HOME="$cand"; break; fi
  done
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME/platforms/android-34" ]; then
  echo "ERROR: Android SDK with platforms;android-34 not found." >&2
  echo "Install via Android Studio or sdkmanager, then set ANDROID_HOME." >&2
  exit 1
fi
export ANDROID_HOME
export PATH="$ANDROID_HOME/platform-tools:$PATH"

VARIANT="${1:-debug}"
echo "Building Ayna ($VARIANT) with JDK $JAVA_HOME and SDK $ANDROID_HOME ..."
./gradlew "assemble${VARIANT^}" --console=plain

APK="app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"
if [ -f "$APK" ]; then
  echo ""
  echo "OK: $APK"
  ls -la "$APK"
  echo ""
  echo "Install on a phone:"
  echo "  adb install -r $APK"
  echo "Or send the APK on WhatsApp and tap it on the phone (allow 'Install unknown apps')."
else
  echo "ERROR: APK not found at $APK" >&2
  exit 1
fi
