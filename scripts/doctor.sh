#!/usr/bin/env bash
# Checks the machine has what the template needs, and says what is missing.
#
#   scripts/doctor.sh
#
# Nothing here installs anything. It only tells you which of the steps in
# docs/GettingStarted.md you still have to do.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$1"; }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$1"; }

echo "Required for the server and the shared module:"
if command -v java >/dev/null; then
  v=$(java -version 2>&1 | head -1)
  # The major version is the first number in the quoted string ("21.0.7" -> 21; "1.8.0" -> 8).
  major=$(printf '%s' "$v" | sed -n 's/.*"\([0-9]*\)\.\([0-9]*\).*/\1 \2/p' | awk '{ print ($1 == 1) ? $2 : $1 }')
  if [ -n "$major" ] && [ "$major" -ge 21 ] 2>/dev/null; then ok "java: $v"; else warn "java found but 21+ is expected: $v"; fi
else bad "java not found — install a JDK 21 (e.g. brew install --cask temurin@21)"; fi
[ -x ./gradlew ] && ok "gradlew present" || bad "gradlew missing or not executable (chmod +x gradlew)"
command -v sqlite3 >/dev/null && ok "sqlite3 (backups, inspecting the database)" || warn "sqlite3 not found — only needed for scripts/backup-database.sh"

echo
echo "Android:"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$SDK" ] && [ -f local.properties ]; then SDK=$(sed -n 's/^sdk\.dir=//p' local.properties); fi
# Android Studio's default install location, when nothing else says.
[ -n "$SDK" ] || { [ -d "$HOME/Library/Android/sdk" ] && SDK="$HOME/Library/Android/sdk"; }
[ -n "$SDK" ] || { [ -d "$HOME/Android/Sdk" ] && SDK="$HOME/Android/Sdk"; }
if [ -n "$SDK" ] && [ -d "$SDK" ]; then
  ok "Android SDK at $SDK"
  # Gradle reads the path from local.properties, which is git-ignored, so a fresh
  # clone fails with "SDK location not found" until somebody writes it. Do it.
  if [ ! -f local.properties ]; then printf 'sdk.dir=%s\n' "$SDK" > local.properties; ok "wrote local.properties (sdk.dir)"; fi
else bad "Android SDK not found — install Android Studio, then write sdk.dir=/path/to/sdk into local.properties"; fi
command -v adb >/dev/null || [ -x "$SDK/platform-tools/adb" ] && ok "adb" || warn "adb not on PATH (add \$ANDROID_HOME/platform-tools)"

echo
echo "iOS (macOS only):"
if [ "$(uname)" = "Darwin" ]; then
  command -v xcodebuild >/dev/null && ok "xcodebuild: $(xcodebuild -version 2>/dev/null | head -1)" || bad "Xcode not found — install from the App Store, then xcode-select --install"
  xcrun simctl list devices available 2>/dev/null | grep -q iPhone && ok "an iPhone simulator is available" || warn "no iPhone simulator — Xcode → Settings → Platforms"
else
  warn "not macOS — the iOS app cannot be built here (everything else can)"
fi

echo
echo "Optional tooling:"
command -v rsvg-convert >/dev/null && ok "rsvg-convert (icons, store graphics)" || warn "rsvg-convert missing — brew install librsvg (only for scripts/build-*.sh)"
command -v magick >/dev/null && ok "ImageMagick (icons, store graphics)" || warn "magick missing — brew install imagemagick (only for scripts/build-*.sh)"
command -v python3 >/dev/null && ok "python3 (scripts)" || bad "python3 missing — several scripts use it"
command -v docker >/dev/null && ok "docker (server image)" || warn "docker missing — only needed to build/run the server image locally"
command -v cloudflared >/dev/null || command -v ngrok >/dev/null && ok "a tunnel tool (cloudflared/ngrok)" || warn "no cloudflared/ngrok — only for testing on a phone off your Wi-Fi (scripts/dev-tunnel.sh public)"

echo
echo "Project configuration:"
[ -f poster.properties ] && ok "poster.properties present" || bad "poster.properties missing"
grep -q '^posterGoogleClientId=.\+' gradle.properties 2>/dev/null && ok "Google web client id set" || warn "Google sign-in off (posterGoogleClientId blank in gradle.properties) — docs/SignIn.md"
grep -q '^POSTER_GOOGLE_CLIENT_ID=.\+' iosApp/Configuration/Config.xcconfig 2>/dev/null && ok "iOS Google client id set" || warn "iOS Google sign-in off (Config.xcconfig) — docs/SignIn.md"
[ -f iosApp/Configuration/Local.xcconfig ] && ok "Local.xcconfig present (RevenueCat iOS key / TEAM_ID)" || warn "no iosApp/Configuration/Local.xcconfig — in-app purchases off on iOS (docs/InAppPurchases.md)"
