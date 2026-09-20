#!/usr/bin/env bash
# Capture the desktop app (feature.desktop) signed in as the demo reader.
#
#   scripts/run-local-backend.sh                              # in another shell
#   scripts/seed-demo-data.sh --host localhost:8080 --reset   # demo accounts and posts
#   scripts/desktop-screenshots.sh                            # -> screenshots/desktop/
#
# Flips feature.desktop on (and feature.support off, which the desktop target
# needs) for the duration of the run and restores poster.properties afterwards.
# The app is started with POSTER_HOME pointing at a scratch profile that already
# holds the session, and POSTER_RENDER_TO makes it draw itself into a PNG
# instead of opening a window — no clicking, no screen-recording permission,
# and it works on a CI runner.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=$ROOT/screenshots/desktop
HOST=localhost:8080
while [ $# -gt 0 ]; do case $1 in --out) OUT=$2; shift ;; --host) HOST=$2; shift ;; *) echo "unknown option $1" >&2; exit 2 ;; esac; shift; done
cd "$ROOT"; mkdir -p "$OUT"
TMP=$(mktemp -d)
cp poster.properties "$TMP/poster.properties.bak"
restore() { cp "$TMP/poster.properties.bak" poster.properties; rm -rf "$TMP"; }
trap restore EXIT
sed -i '' -e 's/^feature.desktop=false/feature.desktop=true/' -e 's/^feature.support=true/feature.support=false/' poster.properties

login=$(curl -sf -X POST "http://$HOST/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"demo.reader.en@example.com","password":"demo123456"}') || { echo "login failed: is the backend up and seeded?" >&2; exit 1; }
python3 -c 'import json,sys; d=json.load(sys.stdin); open(sys.argv[1],"w").write(json.dumps(d["tokens"])); open(sys.argv[2],"w").write(d["user"]["guid"])' "$TMP/session.json" "$TMP/user_id" <<<"$login"

echo "building the desktop jar"
./gradlew :composeApp:packageUberJarForCurrentOS -q --no-daemon
JAR=$(ls composeApp/build/compose/jars/*.jar | head -1)

capture() { # name dark window [extra preference lines]
  local name=$1 dark=$2 window=$3 extra=${4:-} home="$TMP/home-$1"
  mkdir -p "$home"
  printf 'user_id=%s\nfollow_system_theme=false\ndark_theme=%s\n%s\n' "$(cat "$TMP/user_id")" "$dark" "$extra" > "$home/preferences.properties"
  cp "$TMP/session.json" "$home/session.json"
  POSTER_HOME=$home POSTER_WINDOW=$window POSTER_RENDER_TO="$OUT/$name.png" \
    POSTER_SERVER_HOST=${HOST%%:*} POSTER_SERVER_PORT=${HOST##*:} \
    java -jar "$JAR" 2>&1 | grep -v "^SLF4J" || true
}
capture light-01-feed false 480x900
capture dark-01-feed true 480x900
capture light-05-wide-feed false 1200x800
capture light-06-wide-rail-expanded false 1200x800 'nav_rail_expanded=true'
