#!/usr/bin/env bash
# Capture the web app (feature.web) signed in as the demo reader.
#
#   scripts/run-local-backend.sh                              # in another shell, with
#   POSTER_WEB_DIR=composeApp/build/dist/wasmJs/productionExecutable   # the bundle to serve
#   scripts/seed-demo-data.sh --host localhost:8080 --reset
#   scripts/web-screenshots.sh                                # -> screenshots/web/
#
# Needs Google Chrome (CHROME= to point at another Chromium) and Node 22+ (a
# global WebSocket); the Node the Kotlin/Wasm build downloaded under
# ~/.gradle/nodejs is used when none is on the PATH. Nothing is clicked by hand:
# the demo session is planted in localStorage before the app loads.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=$ROOT/screenshots/web
HOST=localhost:8080
while [ $# -gt 0 ]; do case $1 in --out) OUT=$2; shift ;; --host) HOST=$2; shift ;; *) echo "unknown option $1" >&2; exit 2 ;; esac; shift; done
mkdir -p "$OUT"
NODE=$(command -v node || ls -d "$HOME"/.gradle/nodejs/node-v2[2-9]*/bin/node "$HOME"/.gradle/nodejs/node-v[3-9]*/bin/node 2>/dev/null | tail -1)
[ -n "$NODE" ] || { echo "node 22+ not found" >&2; exit 1; }
curl -sf "http://$HOST/app/" >/dev/null || { echo "http://$HOST/app/ is not served: start the backend with POSTER_WEB_DIR (docs/Web.md)" >&2; exit 1; }
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
login=$(curl -sf -X POST "http://$HOST/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"demo.reader.en@example.com","password":"demo123456"}') || { echo "login failed: is the backend seeded?" >&2; exit 1; }
python3 -c 'import json,sys; d=json.load(sys.stdin); open(sys.argv[1],"w").write(json.dumps(d["tokens"])); open(sys.argv[2],"w").write(d["user"]["guid"])' "$TMP/session.json" "$TMP/user_id" <<<"$login"
"$NODE" "$ROOT/scripts/web-screenshots.mjs" "$OUT" "http://$HOST" "$TMP/session.json" "$TMP/user_id"
