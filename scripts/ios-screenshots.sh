#!/usr/bin/env bash
# Capture Poster UI screenshots from an iOS simulator.
#
#   scripts/run-local-backend.sh          # in another shell, first
#   scripts/ios-screenshots.sh            # capture into screenshots/ios (auto-picks a sim)
#   scripts/ios-screenshots.sh --sim "iPhone 17 Pro"             # or name one
#   scripts/ios-screenshots.sh --only light-10-support-paywall   # one screen only
#
# Why this is not the adb script with a different flag: simctl can boot, install,
# launch and screenshot, but it has no equivalent of `adb shell input text` — there
# is no supported way to tap or type on a simulator from the command line. The app
# therefore has to be driven from inside a UI test, and the screenshots come back
# as XCTAttachments in the result bundle, which this script unpacks.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=$ROOT/screenshots/ios
OUT_DEFAULT=1
# Resolved below when --sim is not given: a hardcoded name goes stale every time
# Xcode drops the older device.
SIM_NAME=
HOST=127.0.0.1
PORT=8080
SCREENSHOT_LANG=en
ONLY=

while [ $# -gt 0 ]; do
  case $1 in
    --sim) SIM_NAME=$2; shift ;;
    --out) OUT=$2; OUT_DEFAULT=0; shift ;;
    --host) HOST=$2; shift ;;
    # Which language to capture. The app has no in-app switch, so the test forces
    # the device language and signs in as that language's demo reader.
    --lang) SCREENSHOT_LANG=$2; shift ;;
    # Limit the run to the named shots (space-separated, substring-matched
    # against the shot name), so recapturing one screen overwrites only its file.
    --only) ONLY=$2; shift ;;
    *) echo "unknown flag: $1" >&2; exit 2 ;;
  esac
  shift
done

# Default simulator: a booted iPhone if one is up (reuse what the developer is
# already looking at), else the newest available iPhone — resilient to Xcode
# retiring the previous default. --sim overrides.
if [ -z "$SIM_NAME" ]; then
  # The device name is everything before the " (" that starts its UDID; strip
  # leading indentation and that suffix. A booted device wins; else the last
  # iPhone listed (roughly the newest runtime's).
  avail=$(xcrun simctl list devices available)
  # `|| true` inside: a grep with no match exits 1, and under `set -e -o pipefail`
  # that would end the script here without a word.
  SIM_NAME=$(printf '%s\n' "$avail" | { grep -E 'iPhone .*\(Booted\)' || true; } | head -1 | sed -E 's/^[[:space:]]+//; s/ \(.*//')
  [ -n "$SIM_NAME" ] || SIM_NAME=$(printf '%s\n' "$avail" | { grep -E '^[[:space:]]*iPhone ' || true; } | tail -1 | sed -E 's/^[[:space:]]+//; s/ \(.*//')
  [ -n "$SIM_NAME" ] || { echo "no available iPhone simulator found — create one in Xcode or pass --sim" >&2; exit 1; }
  echo "auto-selected simulator: $SIM_NAME"
fi

curl -s -o /dev/null --max-time 3 "http://$HOST:$PORT/health" ||
  { echo "backend not answering on $HOST:$PORT — start scripts/run-local-backend.sh" >&2; exit 1; }

# Seed a clean demo database. --reset wipes first, so re-runs do not pile up
# duplicate posts; the screenshot test deliberately does not wipe (it would
# leave a blank feed), so this is the only thing setting the content it shows.
"$ROOT/scripts/seed-demo-data.sh" --host "$HOST:$PORT" --reset

# The device name is matched literally up to the " (" before its UDID, so a name
# that itself contains parentheses — "iPad Pro 13-inch (M4)" — still resolves;
# an awk split on "()" would grab "M4" instead of the id. The UDID is then the
# first 36-char UUID on that line.
SIM_ID=$(xcrun simctl list devices available |
  grep -F "$SIM_NAME (" | grep -oE '[0-9A-Fa-f-]{36}' | head -1)
[ -n "$SIM_ID" ] || { echo "no simulator named $SIM_NAME" >&2; exit 1; }
echo "simulator: $SIM_NAME ($SIM_ID)"

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
BUNDLE=$TMP/result.xcresult

# Put the simulator into the target language. The app follows the device
# language and the screenshot test reads it (Locale.preferredLanguages) to pick
# the matching demo account — xcodebuild does not forward this shell's
# environment to the runner, so the device language is the only channel that
# reaches both. Prefs are written to a booted device and persist in its data, so
# the xcodebuild boot below reuses them. Restored on exit so the simulator does
# not stay in another language.
if [ "$SCREENSHOT_LANG" != "en" ]; then
  case "$SCREENSHOT_LANG" in
    ru) LOCALE_CODE=ru_RU ;;
    *)  LOCALE_CODE="${SCREENSHOT_LANG}_$(printf '%s' "$SCREENSHOT_LANG" | tr '[:lower:]' '[:upper:]')" ;;
  esac
  xcrun simctl boot "$SIM_ID" 2>/dev/null || true
  xcrun simctl spawn "$SIM_ID" defaults write -globalDomain AppleLanguages -array "$SCREENSHOT_LANG" >/dev/null 2>&1 || true
  xcrun simctl spawn "$SIM_ID" defaults write -globalDomain AppleLocale -string "$LOCALE_CODE" >/dev/null 2>&1 || true
  restore_locale() {
    xcrun simctl spawn "$SIM_ID" defaults write -globalDomain AppleLanguages -array en >/dev/null 2>&1 || true
    xcrun simctl spawn "$SIM_ID" defaults write -globalDomain AppleLocale -string en_US >/dev/null 2>&1 || true
  }
  trap 'rm -rf "$TMP"; restore_locale' EXIT
fi

# The server host is a build setting rather than a flag, so it is overridden here
# instead of editing Config.xcconfig: captures run against the local backend while
# the checked-in configuration keeps pointing wherever it points.
# POSTER_ONLY reaches the test runner via the TEST_RUNNER_ prefix, which
# xcodebuild forwards only for a real environment variable of its own process
# (with the prefix stripped) — not for a build setting passed as an argument,
# which is how the POSTER_SERVER_* settings below travel (baked into the app
# via xcconfig). So this one is exported here, ahead of the command.
TEST_RUNNER_POSTER_ONLY="$ONLY" \
xcodebuild test \
  -project "$ROOT/iosApp/iosApp.xcodeproj" \
  -scheme iosApp \
  -destination "id=$SIM_ID" \
  -derivedDataPath "$TMP/dd" \
  -resultBundlePath "$BUNDLE" \
  -only-testing:iosAppUITests/PosterScreenshotTests \
  POSTER_SERVER_HOST="$HOST" \
  POSTER_SERVER_PORT="$PORT" \
  POSTER_SERVER_SCHEME=http \
  > "$TMP/xcodebuild.log" 2>&1 || {
    echo "xcodebuild test failed — tail of log:" >&2
    tail -30 "$TMP/xcodebuild.log" >&2
    exit 1
  }

# Write into the per-language subdir, which is where build-ios-store-screenshots.sh
# reads from ($CAPTURES/<lang>/): a single-language recapture then lands where the
# composer looks, instead of the flat dir it used to write — where a stale <lang>/
# copy would shadow it and the store tile would silently keep the old text.
CAPTURE_DIR="$OUT/$SCREENSHOT_LANG"
mkdir -p "$CAPTURE_DIR"
xcrun xcresulttool export attachments --path "$BUNDLE" --output-path "$TMP/attachments" >/dev/null

# Attachments come out with generated filenames; manifest.json maps them back to
# the names the test gave them.
python3 - "$TMP/attachments" "$CAPTURE_DIR" <<'PY'
import json, os, re, shutil, sys
src, dst = sys.argv[1], sys.argv[2]
manifest = json.load(open(os.path.join(src, "manifest.json")))
count = 0
for test in manifest:
    for att in test.get("attachments", []):
        name = att.get("suggestedHumanReadableName") or att.get("exportedFileName")
        exported = att.get("exportedFileName")
        if not exported or not name:
            continue
        # Xcode appends "_0_<uuid>" to the name it was given; strip it back off.
        stem = re.sub(r"_\d+_[0-9A-F-]{36}$", "", os.path.splitext(name)[0])
        target = os.path.join(dst, stem + ".png")
        shutil.copyfile(os.path.join(src, exported), target)
        count += 1
print(f"  {count} screenshots")
PY

echo
echo "done: $CAPTURE_DIR"
ls "$CAPTURE_DIR"

# Recompose the store tiles from what was just captured, so the App Store images
# never drift from the raw captures. Only for the standard output location — a
# custom --out is scratch, and we do not know where its tiles would go. --only
# passes through as SHOTS so a one-screen recapture updates only that tile.
if [ "$OUT_DEFAULT" -eq 1 ]; then
  echo
  echo "== updating App Store tiles =="
  SHOTS="$ONLY" "$ROOT/scripts/build-ios-store-screenshots.sh" "$OUT" "$ROOT/store/ios-screenshots"      "$SCREENSHOT_LANG" iphone
  SHOTS="$ONLY" "$ROOT/scripts/build-ios-store-screenshots.sh" "$OUT" "$ROOT/store/ios-screenshots-ipad" "$SCREENSHOT_LANG" ipad
fi
