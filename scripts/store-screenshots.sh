#!/usr/bin/env bash
# Everything the Play listing needs, from an emulator to composed images.
#
#   scripts/store-screenshots.sh [--host <host:port>] [--serial <device>]
#                                [--languages "en ru"] [--skip-empty]
#                                [--only "light-13-support-paywall"]
#
# --only recaptures just those screens and recomposes just those tiles (implies
# --skip-empty), so a wording change to one screen does not cost the whole set.
#
# Rerun it after a UI change and the listing catches up. Adding a language means
# adding its content to scripts/seed-demo-data.sh and its captions to
# scripts/build-store-screenshots.sh — nothing here.
#
# The order matters and is the whole point of this script existing:
#
#   1. wipe the backend        an empty app cannot be photographed next to a
#   2. capture the empty app   full one, and a capture tool that seeds its own
#   3. seed every language     data can never show you the empty case
#   4. capture the full app
#   5. compose the listing images
#
# Steps 1 and 3 go over HTTP. Seeding through the app would mean driving the
# add-post dialog a dozen times, which is slower, and which photographs
# whatever the dialog does today rather than the content we chose.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
HOST=10.0.2.2:8080
SERIAL=
LANGUAGES="en ru"
SKIP_EMPTY=0
NO_BUILD=0
ONLY=

while [ $# -gt 0 ]; do
  case $1 in
    --host)       HOST=$2; shift ;;
    --serial)     SERIAL=$2; shift ;;
    --languages)  LANGUAGES=$2; shift ;;
    --skip-empty) SKIP_EMPTY=1 ;;
    # Recapture only the named shots (space-separated) and recompose only those
    # tiles — the whole point being to refresh one screen without the other
    # twenty. Implies --skip-empty: the empty-state pass is a different set.
    --only)       ONLY=$2; SKIP_EMPTY=1; shift ;;
    # Assume the APK is already installed and skip the rebuild. Needed when the
    # backend runs from gradle (:server:run): that build and the APK build fight
    # over the transforms-cache lock, so the APK has to be built separately, up
    # front, with the backend stopped.
    --no-build)   NO_BUILD=1 ;;
    *) echo "unknown flag: $1" >&2; exit 2 ;;
  esac
  shift
done

# The address the app is given and the address this script talks to are not the
# same one: 10.0.2.2 is what the emulator calls the host machine, and the host
# machine cannot resolve it at all.
case ${HOST%%:*} in
  10.0.2.2|127.0.0.1|localhost) SEED_HOST=localhost:${HOST##*:} ;;
  *) SEED_HOST=$HOST ;;
esac
case $SEED_HOST in
  localhost*|127.0.0.1*) BASE=http://$SEED_HOST ;;
  *) BASE=https://$SEED_HOST ;;
esac

locale_for() {  # the Android locale tag for a language code
  case $1 in
    en) echo "en-US" ;;
    ru) echo "ru-RU" ;;
    *)  echo "$1" ;;
  esac
}

capture() {  # capture <language> <out-dir> [extra flags...]
  local language=$1 out=$2; shift 2
  rm -rf "$out"
  "$ROOT/scripts/ui-screenshots.sh" \
    --host "$HOST" ${SERIAL:+--serial "$SERIAL"} ${BUILD_ONCE:-} \
    ${ONLY:+--only "$ONLY"} \
    --locale "$(locale_for "$language")" --out "$out" "$@"
  BUILD_ONCE=
}

curl -sS -o /dev/null "$BASE/" || { echo "no backend at $BASE" >&2; exit 1; }

# The first capture rebuilds; the rest reuse what it installed.
#
# The remote flavour bakes its server address in at build time, so an APK put
# there by a plain `installRemoteDebug` talks to production — where the demo
# accounts do not exist. The symptom is every run stopping at the login screen
# saying "Login failed", with the credentials being correct.
BUILD_ONCE=--build
[ $NO_BUILD -eq 1 ] && BUILD_ONCE=

if [ $SKIP_EMPTY -eq 0 ]; then
  echo "== wiping the backend for the empty-state pass =="
  curl -sS -X POST "$BASE/debug/fixtures/integration" >/dev/null
  for language in $LANGUAGES; do
    echo "== empty states: $language =="
    # --empty signs in as a throwaway account: the seeded reader has posts,
    # and this pass is about what somebody sees before they have written one.
    capture "$language" "$ROOT/screenshots/$language-empty" --empty --themes light
  done
fi

echo "== seeding =="
"$ROOT/scripts/seed-demo-data.sh" --host "$SEED_HOST" --reset

for language in $LANGUAGES; do
  echo "== full app: $language =="
  capture "$language" "$ROOT/screenshots/$language"
done

echo "== composing the listing images =="
# SHOTS empty means the composer's full default set; --only narrows it to the
# recaptured tiles (composer names are theme-prefixed, e.g. light-13-support-paywall).
SHOTS="$ONLY" "$ROOT/scripts/build-store-screenshots.sh" "$ROOT/screenshots" "$ROOT/store/screenshots" "$LANGUAGES"

echo
echo "listing images: store/screenshots/"
for language in $LANGUAGES; do
  printf '  %s: ' "$language"
  ls "$ROOT/store/screenshots/$(locale_for "$language")" 2>/dev/null | tr '\n' ' '
  echo
done
