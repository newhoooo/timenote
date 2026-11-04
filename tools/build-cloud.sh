#!/usr/bin/env bash
# Reproducible cloud build. Writes SDK/JDK/Gradle and build outputs only under /workspace.
set -euo pipefail
TASK_PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_TOOLS=/workspace/.toolchain
mkdir -p "$TASK_TOOLS/downloads" "$TASK_TOOLS/gradle-home"
fetch() {
  if [ ! -s "$2" ]; then
    curl --fail --location --retry 2 --connect-timeout 15 --max-time 420 --silent --show-error "$1" --output "$2.part"
    mv "$2.part" "$2"
  fi
}
if [ ! -x "$TASK_TOOLS/jdk/bin/javac" ]; then
  echo 'Downloading JDK 17 for Android compilation...'
  fetch 'https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse' "$TASK_TOOLS/downloads/jdk.tar.gz"
  mkdir -p "$TASK_TOOLS/jdk"
  tar -xzf "$TASK_TOOLS/downloads/jdk.tar.gz" --strip-components=1 -C "$TASK_TOOLS/jdk"
fi
export JAVA_HOME="$TASK_TOOLS/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$TASK_TOOLS/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$TASK_TOOLS/gradle-home"
if [ -f /etc/ssl/certs/java/cacerts ]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
if [ ! -x "$TASK_TOOLS/gradle-8.11.1/bin/gradle" ]; then
  echo 'Downloading and verifying Gradle 8.11.1...'
  fetch 'https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip' "$TASK_TOOLS/downloads/gradle.zip"
  fetch 'https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256' "$TASK_TOOLS/downloads/gradle.sha256"
  TASK_EXPECTED="$(tr -d '[:space:]' < "$TASK_TOOLS/downloads/gradle.sha256")"
  printf '%s  %s\n' "$TASK_EXPECTED" "$TASK_TOOLS/downloads/gradle.zip" | sha256sum -c -
  unzip -q -o "$TASK_TOOLS/downloads/gradle.zip" -d "$TASK_TOOLS"
fi
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo 'Downloading Android command line tools...'
  fetch 'https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip' "$TASK_TOOLS/downloads/android-cli.zip"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q -o "$TASK_TOOLS/downloads/android-cli.zip" -d "$TASK_TOOLS/android-cli-unpack"
  mv "$TASK_TOOLS/android-cli-unpack/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
TASK_PROXY_HOST="$(python3 - <<'PY'
import json,urllib.parse
p=json.load(open('/etc/codex/network-policy.json'))
print(urllib.parse.urlparse(p['http_proxy']).hostname)
PY
)"
TASK_PROXY_PORT="$(python3 - <<'PY'
import json,urllib.parse
p=json.load(open('/etc/codex/network-policy.json'))
print(urllib.parse.urlparse(p['http_proxy']).port or 80)
PY
)"
TASK_SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ] || [ ! -x "$ANDROID_HOME/build-tools/35.0.0/apksigner" ]; then
  echo 'Installing Android API 35 and build tools...'
  python3 - <<'PY' > "$TASK_TOOLS/sdk-license-input.txt"
print('y\n'*200)
PY
  "$TASK_SDKMANAGER" --sdk_root="$ANDROID_HOME" --proxy=http --proxy_host="$TASK_PROXY_HOST" --proxy_port="$TASK_PROXY_PORT" --licenses < "$TASK_TOOLS/sdk-license-input.txt" > "$TASK_TOOLS/sdk-licenses.log" 2>&1
  "$TASK_SDKMANAGER" --sdk_root="$ANDROID_HOME" --proxy=http --proxy_host="$TASK_PROXY_HOST" --proxy_port="$TASK_PROXY_PORT" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools' < "$TASK_TOOLS/sdk-license-input.txt"
fi
cd "$TASK_PROJECT"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
echo 'Compiling, testing and packaging Moment...'
"$TASK_TOOLS/gradle-8.11.1/bin/gradle" --no-daemon --console=plain --max-workers=2 \
  -Dhttp.proxyHost="$TASK_PROXY_HOST" -Dhttp.proxyPort="$TASK_PROXY_PORT" \
  -Dhttps.proxyHost="$TASK_PROXY_HOST" -Dhttps.proxyPort="$TASK_PROXY_PORT" \
  -Dorg.gradle.internal.http.connectionTimeout=20000 -Dorg.gradle.internal.http.socketTimeout=120000 \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug 2>&1 | tee "$TASK_TOOLS/build.log"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk /workspace/library-files/moment-0.1.0-debug.apk
echo 'APK: /workspace/library-files/moment-0.1.0-debug.apk'
