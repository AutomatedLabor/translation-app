#!/usr/bin/env bash
# Devcontainer provisioning: installs the Android SDK (command-line tools + the
# platforms/build-tools the project targets) so ./gradlew can compile the app
# LOCALLY inside the Codespace — no 5-min CI round-trip per Kotlin edit.
#
# Mirrors what CI's android-actions/setup-android installs, but persisted in the
# devcontainer so every Codespace session starts with a working compiler.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-/usr/local/android-sdk}"
CMDLINE_TOOLS_VERSION="12266708"   # commandlinetools-linux-12266708_latest.zip (2025)
mkdir -p "$ANDROID_HOME/cmdline-tools"

echo "=== [1/4] download Android command-line tools ==="
cd /tmp
wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -O cmdline-tools.zip
unzip -q -o cmdline-tools.zip
# sdkmanager expects $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager
rm -rf "$ANDROID_HOME/cmdline-tools/latest"
mv cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
rm cmdline-tools.zip

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "=== [2/4] accept SDK licenses (auto-yes) ==="
yes | sdkmanager --licenses >/dev/null 2>&1 || true

echo "=== [3/4] install platform-tools + platforms + build-tools ==="
sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "platforms;android-30" \
  "build-tools;35.0.0"

echo "=== [4/4] verify gradle + the project compiles ==="
cd "$HOME/work/translation-app" 2>/dev/null || cd /workspaces/translation-app 2>/dev/null || cd /home/codespace/translation-app 2>/dev/null || {
  echo "could not find project dir; skipping gradle verify (run ./gradlew manually)"
  exit 0
}
chmod +x gradlew
./gradlew --version
echo "✓ Android SDK installed. Compile with: ./gradlew :app:compileDebugKotlin"
