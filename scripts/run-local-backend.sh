#!/usr/bin/env bash
set -euo pipefail

# Starts the Ktor backend locally for emulator/device integration.
# App/test clients configured with SERVER_HOST=10.0.2.2 and SERVER_PORT=8080
# can reach this process when running on Android emulator.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOCAL_DATA_DIR="$REPO_ROOT/server/build/local"

cd "$REPO_ROOT"
mkdir -p "$LOCAL_DATA_DIR"
./gradlew :server:run \
    -Pktor.development=true \
    -Pposter.database="$LOCAL_DATA_DIR/post-integration.db" \
    "$@"
