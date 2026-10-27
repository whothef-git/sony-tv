#!/usr/bin/env bash
# Build the launcher, install it on the TV, make it HOME and check that it is.
#
# Usage: tools/build-install.sh                 build + install + set as home
#        tools/build-install.sh --build-only    just build launcher/app/build/outputs/apk/debug/app-debug.apk
#        GRADLE_FLAGS=--daemon tools/build-install.sh   faster repeat builds
#
# Works in Git Bash on Windows, on macOS and on Linux. Needs JDK 17 and the Android SDK (platform 34,
# build-tools 34). It uses JAVA_HOME / ANDROID_HOME when set, otherwise a portable toolchain/ folder
# in the repo (gitignored):
#   toolchain/jdk/jdk-17*/        a JDK 17 (Temurin zip/tar.gz unpacked here)
#   toolchain/android-sdk/        with platforms/android-34 and build-tools/34.x
#   toolchain/gradle-home/        optional GRADLE_USER_HOME (the Gradle download and caches land here)
set -euo pipefail

BUILD_ONLY=0
[ "${1:-}" = "--build-only" ] && BUILD_ONLY=1
[ "$BUILD_ONLY" = 1 ] && NEED_TV=0
source "$(dirname "$0")/env.sh"

TC="$ROOT/toolchain"
missing() {
  cat >&2 <<EOF
$1

Install, then run this script again:
  - JDK 17 (e.g. Eclipse Temurin 17): set JAVA_HOME, or unpack it to toolchain/jdk/jdk-17.../
  - Android SDK command-line tools, then:
      sdkmanager "platforms;android-34" "build-tools;34.0.0"
    set ANDROID_HOME, or put the SDK in toolchain/android-sdk/
Android Studio installs both; its SDK is usually ~/Library/Android/sdk (macOS),
~/Android/Sdk (Linux) or %LOCALAPPDATA%\\Android\\Sdk (Windows).
EOF
  exit 1
}

# --- JDK 17
if [ -z "${JAVA_HOME:-}" ]; then
  jdk="$(ls -d "$TC"/jdk/jdk-17*/ 2>/dev/null | head -1 || true)"
  jdk="${jdk%/}"
  [ -d "$jdk/Contents/Home" ] && jdk="$jdk/Contents/Home" # macOS tarball layout
  [ -n "$jdk" ] && export JAVA_HOME="$jdk"
fi
{ [ -n "${JAVA_HOME:-}" ] && { [ -x "$JAVA_HOME/bin/java" ] || [ -x "$JAVA_HOME/bin/java.exe" ]; }; } \
  || missing "No JDK found (JAVA_HOME unset and nothing in toolchain/jdk/)."

# --- Android SDK
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then export ANDROID_HOME="$ANDROID_SDK_ROOT"
  elif [ -d "$TC/android-sdk" ]; then export ANDROID_HOME="$TC/android-sdk"; fi
fi
[ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/platforms/android-34" ] \
  || missing "No Android SDK with platforms/android-34 found (ANDROID_HOME='${ANDROID_HOME:-}')."
ls -d "$ANDROID_HOME"/build-tools/34* >/dev/null 2>&1 || missing "Android SDK has no build-tools 34.x."
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# --- Gradle home: the repo's toolchain/ when present, otherwise Gradle's default (~/.gradle)
if [ -z "${GRADLE_USER_HOME:-}" ] && [ -d "$TC/gradle-home" ]; then export GRADLE_USER_HOME="$TC/gradle-home"; fi

# Gradle (a JVM) needs a native path; on Windows that is C:/..., not /c/...
echo "sdk.dir=$(local_path "$ANDROID_HOME")" > "$ROOT/launcher/local.properties"

# Thought of the day: content/thoughts.json is the source of truth.
cp "$ROOT/content/thoughts.json" "$ROOT/launcher/app/src/main/res/raw/thoughts.json"

APK="$ROOT/launcher/app/build/outputs/apk/debug/app-debug.apk"
(cd "$ROOT/launcher" && sh ./gradlew ${GRADLE_FLAGS:---no-daemon} -q assembleDebug)
echo "built $APK ($(wc -c < "$APK") bytes)"
[ "$BUILD_ONLY" = 1 ] && exit 0

# --- Install. Push + pm install: plain "adb install" can hang on Android 14 waiting for a prompt.
adb_connect
adb_tv push "$(local_path "$APK")" /data/local/tmp/launcher.apk >/dev/null
result="$(tv_sh pm install -r /data/local/tmp/launcher.apk)"
echo "pm install: $result"
adb_tv shell rm -f /data/local/tmp/launcher.apk
[[ "$result" == *Success* ]] || { echo "Install failed. See docs/TROUBLESHOOTING.md#install-fails" >&2; exit 1; }

# Location only lets the Wi-Fi button show the network name; nothing else reads it.
tv_sh pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
tv_sh pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION
echo "set-home-activity: $(tv_sh cmd package set-home-activity "$HOME_ACTIVITY")"
adb_tv shell input keyevent KEYCODE_HOME
sleep 2
focused="$(tv_sh dumpsys activity activities | grep -m1 mFocusedApp)"
echo "$focused"
if [[ "$focused" != *"$PKG"* ]]; then
  cat >&2 <<'EOF'

WARNING: HOME still opens another app. On Google TV "set-home-activity" can print Success and change
nothing; the stock launcher (and sometimes the setup wizard) must be disabled first.
Follow docs/TROUBLESHOOTING.md#home-takeover, then run this script again.
EOF
  exit 3
fi
echo "The launcher is HOME ($PKG)."
