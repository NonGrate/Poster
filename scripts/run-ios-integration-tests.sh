#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SIMULATOR_NAME="${IOS_SIMULATOR_NAME:-iPhone 17 Pro}"
DERIVED_DATA_PATH="${IOS_DERIVED_DATA_PATH:-/private/tmp/poster-ios-derived}"
FIXTURE_URL="http://127.0.0.1:8080/debug/fixtures/integration"

if ! curl -fsS -X POST "$FIXTURE_URL" -o /dev/null; then
    echo "Local backend is unavailable. Start ./scripts/run-local-backend.sh first." >&2
    exit 1
fi

# The fixtures above are applied to the local backend, so the app has to be
# built against it too — Config.xcconfig points at staging by default, which is
# what made this suite silently test the wrong server.
cd "$REPO_ROOT"
xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -configuration Debug \
    -destination "platform=iOS Simulator,name=$SIMULATOR_NAME,OS=latest" \
    -derivedDataPath "$DERIVED_DATA_PATH" \
    test \
    -only-testing:iosAppUITests/PosterUITests \
    POSTER_SERVER_HOST=127.0.0.1 \
    POSTER_SERVER_PORT=8080 \
    POSTER_SERVER_SCHEME=http
