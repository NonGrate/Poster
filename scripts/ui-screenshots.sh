#!/usr/bin/env bash
# Capture Poster UI screenshots (light + dark) from an Android device/emulator.
#
#   scripts/ui-screenshots.sh                  # capture using the installed APK
#   scripts/ui-screenshots.sh --build          # rebuild + reinstall first
#   scripts/ui-screenshots.sh --local          # use the local backend (scripts/run-local-backend.sh)
#   scripts/ui-screenshots.sh --compact        # also capture at 720x1600 @320dpi (slow, best effort)
#   scripts/ui-screenshots.sh --themes light   # skip the dark pass
#   scripts/ui-screenshots.sh --only 13-support-paywall --themes light  # one screen only
#   scripts/ui-screenshots.sh --locale ru-RU   # capture in another locale
#   scripts/ui-screenshots.sh --empty          # empty states, from a brand-new account
#   scripts/ui-screenshots.sh --out shots/v3   # custom output dir
#
# Taps are resolved from the uiautomator hierarchy by label, not by fixed
# coordinates, so a redesign that moves things around does not break the run.
#
# --empty registers a throwaway account and skips seeding, which gives empty
# My Posts and Favorites. Home only goes empty when the backend itself has no
# posts, because it shows other people's — for that shot, start the server on a
# scratch database:  scripts/run-local-backend.sh -Pposter.database=/tmp/empty.db
set -euo pipefail

PKG=com.example.poster
HOST=poster.example.com
SEED_HOST=$HOST
SCHEME=https
PORT=443
# Who to sign in as, and which post to open for the details shot. Both are
# asked of the seeder rather than repeated here — it owns the demo content, and
# a second copy of these goes stale the first time that content is edited. Set
# below, once --locale has been read.
DETAIL_POST=
FLAVOR=Remote
APK=remote/debug/composeApp-remote-debug.apk
USER_NAME=
USER_PASS=

ROOT=$(cd "$(dirname "$0")/.." && pwd)
ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
OUT=$ROOT/screenshots/$(date +%Y%m%d-%H%M%S)
BUILD=0
COMPACT=0
EMPTY=0
THEMES="light dark"
LOCALE=
# --only limits the run to the named shots (a space-separated list, matched as
# substrings of the shot suffix), so recapturing one screen costs one screen.
# The store composers already take the matching subset via SHOTS=.
ONLY=

while [ $# -gt 0 ]; do
  case $1 in
    --build) BUILD=1 ;;
    # The e2e flavor is already wired to 10.0.2.2:8080, which is the host machine
    # as seen from the emulator; curl reaches the same server on localhost.
    --local)
      PKG=com.example.poster.test
      SEED_HOST=localhost:8080
      FLAVOR=E2e
      APK=e2e/debug/composeApp-e2e-debug.apk
      ;;
    # The remote flavour against a local server. Not the same as --local, which
    # switches to e2e — and e2e ships a blank Google client id on purpose, so
    # the login screen it captures has no Google button on it. For a store
    # listing that is the one screen where the button matters.
    --host)
      HOST=${2%%:*}
      PORT=${2##*:}
      [ "$PORT" = "$HOST" ] && PORT=8080
      SCHEME=http
      # The address the app is given and the address this script seeds through
      # are not the same one: 10.0.2.2 is what the emulator calls the host
      # machine, and the host machine cannot resolve it at all.
      case $HOST in
        10.0.2.2|127.0.0.1|localhost) SEED_HOST=localhost:$PORT ;;
        *) SEED_HOST=$HOST:$PORT ;;
      esac
      shift ;;
    --compact) COMPACT=1 ;;
    --themes) THEMES=$2; shift ;;
    --empty) EMPTY=1 ;;
    --locale) LOCALE=$2; shift ;;
    --only) ONLY=$2; shift ;;
    --out) OUT=$2; shift ;;
    --serial) SERIAL=$2; shift ;;
    *) echo "unknown flag: $1" >&2; exit 2 ;;
  esac
  shift
done

# Prefer an emulator over a plugged-in handset; --serial overrides.
if [ -z "${SERIAL:-}" ]; then
  devices=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
  SERIAL=$(echo "$devices" | grep -m1 '^emulator-' || echo "$devices" | head -1)
fi
[ -n "$SERIAL" ] || { echo "no adb device" >&2; exit 1; }
echo "device: $SERIAL"
mkdir -p "$OUT"
TMP=$(mktemp -d)

a() { "$ADB" -s "$SERIAL" "$@"; }

# Screen size and locale are device-global: always put them back, including on
# failure, or the emulator is left in whatever state the run died in.
cleanup() {
  rm -rf "$TMP"
  [ $COMPACT -eq 1 ] && { a shell wm size reset >/dev/null 2>&1; a shell wm density reset >/dev/null 2>&1; }
  # `|| true`: on older devices `cmd locale` does not exist and returns non-zero,
  # which under `set -e` would abort here in the EXIT trap before `return 0` and
  # surface as a spurious failure of an otherwise-good run.
  [ -n "$LOCALE" ] && a shell cmd locale set-app-locales "$PKG" --locales "" >/dev/null 2>&1 || true
  return 0
}
trap cleanup EXIT
# On a signal, clean up *and stop*: without the exit the script kept running
# against a temp directory it had just deleted.
trap 'cleanup; exit 130' INT TERM

case $SEED_HOST in
  localhost*|127.0.0.1*|10.0.2.2*) BASE=http://$SEED_HOST ;;
  *) BASE=https://$SEED_HOST ;;
esac

# The demo content is seeded in every language at once and the feed's own
# language filter decides which of them a reader sees, so the account signed in
# here is what makes the screenshots come out in the right language.
case $LOCALE in
  ru*) DEMO_LANG=ru ;;
  *)   DEMO_LANG=en ;;
esac
SEEDER="$ROOT/scripts/seed-demo-data.sh"
USER_NAME=${POSTER_USER:-$("$SEEDER" --reader-email "$DEMO_LANG")}
USER_PASS=${POSTER_PASS:-$("$SEEDER" --password)}
DETAIL_POST=$("$SEEDER" --detail-title "$DEMO_LANG")

# ---- element lookup -------------------------------------------------------

# uiautomator refuses to dump while the UI is animating, and it leaves the previous
# dump on disk when it fails — which reads as a successful dump of the wrong screen
# and sends taps somewhere unrelated. Delete the file first so a failure is visible,
# then retry.
dump() {
  local i
  for i in 1 2 3; do
    a shell rm -f /sdcard/ui.xml >/dev/null 2>&1
    a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    a shell cat /sdcard/ui.xml > "$TMP/ui.xml" 2>/dev/null || true
    grep -q '<hierarchy' "$TMP/ui.xml" 2>/dev/null && return 0
    sleep 1
  done
  : > "$TMP/ui.xml"
}

# center_of "<label>" -> "x y" (empty if absent). Matches text= or content-desc=.
# Compose renders buttons and tab labels as plain TextViews with no clickable flag,
# so a label like "Login" matches both the screen title and the button. The last
# match wins: titles are emitted before the controls that repeat their text.
center_of() {
  python3 - "$1" "$TMP/ui.xml" <<'PY'
import re, sys
label, path = sys.argv[1], sys.argv[2]
found = None
for node in re.finditer(r'<node[^>]*>', open(path, encoding='utf-8').read()):
    s = node.group(0)
    text = re.search(r'text="([^"]*)"', s).group(1)
    desc = re.search(r'content-desc="([^"]*)"', s).group(1)
    if label in (text, desc) or text.startswith(label + '&#10;'):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', re.search(r'bounds="([^"]*)"', s).group(1)))
        found = ((x1 + x2) // 2, (y1 + y2) // 2)
if found:
    print(*found)
PY
}

# Every label this script looks for is an English string resource, so a localized
# run finds none of them. Rather than keep a translation table here, look the
# English text up in values/strings.xml and return the same key's value from the
# locale's file — the app and the script then always agree.
label() {
  [ -z "$LOCALE" ] && { printf '%s' "$1"; return; }
  # One English word can be several resources. "Login" is the screen title, the
  # button and a bare noun; Russian renders the title "Вход" and the button
  # "Войти", so the key has to be named explicitly.
  local key=""
  case "$1" in
    Login) key=login_button ;;
    # "Email" is registration_email, profile_email and login_email; the login
    # flow means the last one.
    Email) key=login_email ;;
    # "Home" is the feed's tab and its title, which are the same resource.
    Home) key=nav_home ;;
  esac
  python3 - "$1" "$ROOT/composeApp/src/commonMain/composeResources" "${LOCALE%%-*}" "$key" <<'PY'
import re, sys, os
text, res, lang = sys.argv[1], sys.argv[2], sys.argv[3]
forced = sys.argv[4] if len(sys.argv) > 4 else ""
def table(path):
    if not os.path.exists(path): return {}
    return dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', open(path, encoding='utf-8').read(), re.S))
en = table(os.path.join(res, 'values', 'strings.xml'))
loc = table(os.path.join(res, 'values-%s' % lang, 'strings.xml'))
key = forced or next((k for k, v in en.items() if v == text), None)
# For English there is no values-en table (the default lives in values/), so
# fall back to the English string rather than the literal label — otherwise a
# renamed string (nav_home: "Home" -> "Shared") is never found on screen.
print((loc.get(key) or en.get(key, text)) if key else text, end='')
PY
}

has_text() { dump; [ -n "$(center_of "$(label "$1")")" ]; }

# True when --only was not given, or when a shot suffix this group produces and a
# requested name overlap either way round. Bidirectional so the bare group suffix
# ("13-support-paywall") matches both a loose token ("paywall") and the composer's
# theme-prefixed name ("light-13-support-paywall") — the latter lets one --only
# value drive capture and compose together via scripts/store-screenshots.sh.
want() {
  [ -z "$ONLY" ] && return 0
  local suffix tok
  for suffix in "$@"; do
    for tok in $ONLY; do
      case "$suffix" in *"$tok"*) return 0 ;; esac
      case "$tok" in *"$suffix"*) return 0 ;; esac
    done
  done
  return 1
}

# wait_text <label> [seconds]
wait_text() {
  local deadline=$((SECONDS + ${2:-20}))
  while [ $SECONDS -lt $deadline ]; do
    has_text "$1" && return 0
    sleep 1
  done
  # Leave evidence: a timeout means the app was on some other screen than expected.
  a exec-out screencap -p > "$OUT/timeout-$(echo "$1" | tr ' /' '__').png" 2>/dev/null || true
  echo "timed out waiting for: $1 (see timeout-*.png)" >&2
  return 1
}

tap_text() {
  wait_text "$1" "${2:-20}" || return 1
  local xy; xy=$(center_of "$(label "$1")")
  [ -n "$xy" ] || return 1
  a shell input tap $xy
  sleep 2
}

type_into() {  # type_into <field label> <text>
  wait_text "$1"
  local xy; xy=$(center_of "$(label "$1")")
  [ -n "$xy" ] || { echo "field not found: $1" >&2; return 1; }
  local text="${2// /%s}" i ch attempt
  # A visible (non-password) field is verified after typing and retyped if wrong:
  # the emulator's IME intermittently doubles the first keystroke ("eempty…") or
  # drops one, and one wrong character in an email fails the login. A password is
  # masked so its content cannot be read back — it is typed once, carefully.
  local verify=1; [ "$1" = "Password" ] && verify=0
  for attempt in 1 2 3; do
    a shell input tap $xy; sleep 2
    # Clear anything already there (a retry, or a doubled leading char): go to the
    # end, then backspace well past the field's length.
    a shell input keyevent 123 >/dev/null 2>&1   # MOVE_END
    for ((i = 0; i < ${#text} + 6; i++)); do a shell input keyevent 67 >/dev/null 2>&1; done
    # Type one character at a time. `input text` drops characters from a long
    # string on a software-rendered emulator; character by character lands each.
    for ((i = 0; i < ${#text}; i++)); do
      ch="${text:i:1}"
      [ "${text:i:2}" = "%s" ] && { ch="%s"; i=$((i + 1)); }
      a shell input text "$ch"
      sleep 0.12
    done
    sleep 1
    [ $verify -eq 0 ] && return 0
    # Confirm the exact value is now on screen; retry if the IME mangled it.
    dump
    [ -n "$(center_of "$2")" ] && return 0
    echo "  (retyping $1 — got mangled)" >&2
    hide_keyboard
  done
  return 0
}

# Typing a password and making sure it landed.
#
# A password field is masked, so type_into cannot read it back to check it — and
# the IME mangles credentials (worst on the Russian keyboard, where the app's own
# locale switches the layout: the email verified fine there, the password did not,
# and login failed). So type it, reveal it with the eye toggle to read the plain
# value, and retype if it is wrong. Hidden again before returning, so no later
# screenshot shows it.
type_password() {  # type_password <text>
  local attempt eye
  for attempt in 1 2 3; do
    type_into Password "$1"
    dump
    eye=$(center_of "$(label "Show the password")")
    [ -n "$eye" ] && { a shell input tap $eye; sleep 1; dump; }
    if [ -n "$(center_of "$1")" ]; then
      # Put the mask back so a stray screenshot never shows the password.
      local hide; hide=$(center_of "$(label "Hide the password")")
      [ -n "$hide" ] && { a shell input tap $hide; sleep 1; }
      return 0
    fi
    echo "  (retyping password — the keyboard mangled it)" >&2
    hide_keyboard
  done
  return 0
}

# The IME hides whatever sits under it from `uiautomator dump`, so it has to go
# before looking for a button. ESCAPE closes it without the back-navigation that
# BACK would trigger if the keyboard happened to be down already. dumpsys lags the
# keystroke that raised the IME, so this does not bother asking whether it is up.
hide_keyboard() {
  a shell input keyevent 111
  sleep 1
  # Some devices (Pixel) ignore ESCAPE and leave the IME up, which then covers
  # the Login button so the tap lands on the keyboard and never submits. If it
  # is still shown, BACK closes it — and while the keyboard is up BACK only
  # dismisses it, it does not navigate.
  if a shell dumpsys input_method 2>/dev/null | grep -q "mInputShown=true"; then
    a shell input keyevent 4
    sleep 1
  fi
}

# On a small screen the Add Post dialog overflows and its buttons are not
# reachable at all, so Cancel may not exist. BACK closes the dialog either way.
dismiss_dialog() {
  dump
  local xy; xy=$(center_of "$(label Cancel)")
  if [ -n "$xy" ]; then a shell input tap $xy; else a shell input keyevent 4; fi
  sleep 2
}

# shot <name> [settle-seconds]. The undo snackbar lives 4s and tap_text already
# spends 2 of them, so transient UI passes 0 here.
shot() {
  sleep "${2:-2}"
  a exec-out screencap -p > "$OUT/$1.png"
  echo "  $1.png"
}

# Resizing the screen restarts activities, and if this app hiccups the previously
# used task — the other flavor, pointed at another backend — can surface instead.
# Never assume a launch worked; confirm who owns the focus.
launch_app() {
  local deadline=$((SECONDS + 40))
  a shell am force-stop "$PKG"
  # am start over monkey: monkey silently fails to launch the app on some
  # emulator images (it did on poster_light), while am start reports its
  # status and is reliable. Resolve the launcher activity once so the class name
  # is not hardcoded here.
  local activity
  activity=$(a shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$PKG" 2>/dev/null | tr -d '\r' | tail -1)
  while [ $SECONDS -lt $deadline ]; do
    if [ -n "$activity" ]; then
      a shell am start-activity -n "$activity" >/dev/null 2>&1
    else
      a shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    fi
    sleep 3
    # Written to a file, not piped: `grep -q` exits early, adb dies of SIGPIPE, and
    # under `pipefail` the pipeline then reports failure even on a match.
    a shell dumpsys window > "$TMP/window.txt" 2>/dev/null || true
    grep -q "mCurrentFocus.*$PKG/" "$TMP/window.txt" && return 0
  done
  echo "$PKG did not come to the foreground" >&2
  return 1
}

# ---- screen geometry ------------------------------------------------------

refresh_width() {
  # Prefer an override size (set by --compact, or a portrait-tablet override) over
  # the panel's physical size — a rotated/overridden display reports both, and taps
  # keyed to the physical width would land off the actual content.
  local size
  size=$(a shell wm size)
  # `|| true`: with no override set, the grep finds nothing and returns non-zero,
  # which under `set -o pipefail` would abort the whole run before the fallback.
  WIDTH=$(printf '%s\n' "$size" | grep -m1 'Override size' | sed 's/.*: //' | cut -dx -f1 | tr -d '\r' || true)
  [ -n "$WIDTH" ] || WIDTH=$(printf '%s\n' "$size" | grep -m1 'Physical size' | sed 's/.*: //' | cut -dx -f1 | tr -d '\r')
}
refresh_width

# ---- theme ----------------------------------------------------------------

set_theme() {  # set_theme light|dark
  # Drive the system night mode rather than the in-app toggle: the app follows
  # the device theme by default, so this switches it without depending on the
  # settings layout (the "Dark Theme" row only appears once "Follow device
  # theme" is turned off, so it was not there to tap).
  case $1 in
    dark) a shell cmd uimode night yes >/dev/null 2>&1 ;;
    *)    a shell cmd uimode night no  >/dev/null 2>&1 ;;
  esac
  sleep 2
}

# ---- flows ----------------------------------------------------------------

# Registering over REST rather than through the form: the form is one more flaky
# multi-field flow, and the point here is the empty screens behind it.
register_throwaway_account() {
  USER_NAME="empty.$(date +%s)@example.com"
  USER_PASS=emptydemo123
  curl -sS -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
    -d "{\"name\":\"Ada\",\"surname\":\"Newcomer\",\"email\":\"$USER_NAME\",\"password\":\"$USER_PASS\"}" \
    >/dev/null
  echo "registered throwaway account $USER_NAME"
}

login_if_needed() {
  # A cold start can take longer than the launch sleep, and an empty hierarchy
  # looks exactly like "already logged in" — so wait for one of the two screens.
  local deadline=$((SECONDS + 30))
  while [ $SECONDS -lt $deadline ]; do
    dump
    [ -n "$(center_of "$(label Login)")" ] && break
    [ -n "$(center_of "$(label Home)")" ] && return 0
    sleep 1
  done
  [ -n "$(center_of "$(label Login)")" ] || { echo "app reached neither login nor feed" >&2; return 1; }
  echo "logging in as $USER_NAME"
  want 00-login && shot 00-login

  # The registration form is only reachable from here. Only press BACK if it
  # actually opened — an unconditional BACK when the tap missed exits the app to
  # the launcher, and then the login below runs against home screen and times out.
  if want 00b-register; then
    tap_text Register
    if wait_text "Create account"; then
      shot 00b-register
      a shell input keyevent 4
      sleep 2
    fi
  fi

  type_into Email "$USER_NAME"
  # Hide the keyboard before the next field: the IME hides whatever sits under
  # it from `uiautomator dump`, so with it up the Password field is invisible to
  # the lookup and type_into Password times out waiting for its label.
  hide_keyboard
  type_password "$USER_PASS"
  hide_keyboard
  tap_text Login
  # The feed, by its own tab rather than by the app's name: the name is on the
  # login screen now, so waiting for it would pass without going anywhere.
  wait_text "$(label Home)" 30
}

capture_set() {  # capture_set <prefix>
  local t=$1
  echo "capturing $t -> $OUT"

  # A longer settle than the rest, because this is the first shot after a
  # relaunch and the tag labels arrive over the network. Until they do, a chip
  # falls back to the tag's id — so the feed photographs as "job_search" rather
  # than "Поиск работы", which is only obvious in a language where the ids are
  # not themselves English words.
  want 01-home       && { tap_text Home;         shot "$t-01-home" 8; }
  want 02-my-posts && { tap_text "My Posts"; shot "$t-02-my-posts"; }

  # Each group re-anchors on a tab first and is allowed to fail on its own: one
  # unreachable dialog should cost that shot, not the other twenty.
  want 03-add-post-dialog 03b-add-post-scrolled && { capture_add_dialog "$t"  || echo "  (skipped add-post dialog)"; }
  want 04-edit-post-dialog 05-delete-confirmation && { capture_row_dialogs "$t" || echo "  (skipped edit/delete dialogs)"; }

  want 06-favorites && { tap_text Liked;  shot "$t-06-favorites"; }
  want 07-settings  && { tap_text Settings; shot "$t-07-settings"; }
  want 08-groups        && { capture_groups "$t" || echo "  (skipped groups dialog)"; }
  want 13-support-paywall    && { capture_paywall "$t"     || echo "  (skipped support paywall)"; }
  want 09-post-details     && { capture_details "$t"     || echo "  (skipped post details)"; }
  want 10-undo-snackbar      && { capture_undo "$t"        || echo "  (skipped undo snackbar)"; }
  want 12-sign-out-confirmation && { capture_sign_out "$t" || echo "  (skipped sign-out confirmation)"; }
  want 11-profile            && { capture_profile "$t"     || echo "  (skipped profile)"; }
}

# The support paywall. Only lands when there are tiers to show — a demo build,
# or a real offering — so on a build with neither it times out and is skipped.
capture_paywall() {
  local t=$1 i
  tap_text Settings || return 1
  # Settings scrolls, and the support row sits near the bottom (more so since the
  # name-visibility toggle was added), so bring it into view before tapping — a
  # bare tap_text only sees what is on screen and otherwise times out.
  for i in 1 2 3 4 5; do
    has_text "Support the developer" && break
    a shell input swipe $((WIDTH / 2)) 1500 $((WIDTH / 2)) 600 300
    sleep 1
  done
  # The support row was renamed from "Buy me a coffee". Only present at all when
  # the build has a RevenueCat (or demo) key; otherwise the section is hidden and
  # this times out and is skipped, which is fine — it is a bonus shot.
  tap_text "Support the developer" || return 1
  shot "$t-13-support-paywall"
  a shell input keyevent 4   # BACK dismisses the sheet
  sleep 1
}

capture_add_dialog() {
  local t=$1
  tap_text "My Posts" || return 1
  tap_text "Add Post" || return 1
  # The title field's placeholder, not a "Title" label — the form was redesigned
  # into a full screen and has no such label. On a miss, still fall through to
  # dismiss_dialog: the form covers the screen, and leaving it open cascades into
  # every later shot failing.
  if wait_text "A short title"; then
    shot "$t-03-add-post-dialog"
    # Tags and the buttons sit low in the form; on a small screen they are cut
    # off entirely, which is worth having a shot of.
    a shell input swipe $((WIDTH / 2)) 1400 $((WIDTH / 2)) 900 400
    sleep 1
    shot "$t-03b-add-post-scrolled"
  fi
  dismiss_dialog
}

capture_row_dialogs() {
  local t=$1
  tap_text "My Posts" || return 1
  tap_text "More options" || return 1
  # If Edit is not on this menu (e.g. the row is not the person's own, so the
  # overflow only offers Report), close the open menu before bailing — a menu
  # left up covers the bottom nav and fails every navigation tap after this.
  tap_text Edit || { dismiss_dialog; return 1; }
  # Same full-screen form, titled Edit Post. Dismiss whether or not the shot
  # lands, so an open form never covers the next step.
  wait_text "Edit Post" && shot "$t-04-edit-post-dialog"
  dismiss_dialog

  tap_text "My Posts" || return 1
  tap_text "More options" || return 1
  tap_text Delete || return 1
  wait_text "Delete this post?" && shot "$t-05-delete-confirmation"
  dismiss_dialog
}

capture_groups() {
  local t=$1
  tap_text Settings || return 1
  tap_text "Manage groups" || return 1
  wait_text Groups || return 1
  shot "$t-08-groups"
  tap_text Back || true
}

capture_details() {
  local t=$1
  tap_text Home || return 1
  tap_text "$DETAIL_POST" || return 1
  wait_text Post || return 1
  shot "$t-09-post-details"
  tap_text Back || true
}

# The undo snackbar lives for 4s after a favorite toggle, so shoot it right away,
# then toggle back to leave the seeded data as it was.
capture_undo() {
  local t=$1
  tap_text Home || return 1
  tap_text Like || return 1
  shot "$t-10-undo-snackbar" 0
  # The snackbar is gone within 4s — faster than a dump-and-tap round trip — and
  # tapping "Unfavorite" instead would resolve to the last match on screen, often
  # another card. Put the seeded state back over REST instead, then relaunch so the
  # app picks it up — its cache would otherwise keep showing every heart filled and
  # leave the next pass with nothing to tap.
  restore_favorites
  launch_app
}

# Puts the hearts back between passes.
#
# The undo screenshot toggles one off, so without this the light pass breaks
# what the dark pass photographs. Which posts should be starred is the
# seeder's to know, not this script's.
restore_favorites() {
  "$SEEDER" --host "$SEED_HOST" --favorites-only >/dev/null
}

# Opens the confirmation and backs out of it — never actually signs out, which
# would end the run.
capture_sign_out() {
  local t=$1
  tap_text Settings || return 1
  tap_text "Sign Out" || return 1
  wait_text "Sign out?" || return 1
  shot "$t-12-sign-out-confirmation"
  dismiss_dialog
}

capture_profile() {
  local t=$1
  tap_text Settings || return 1
  tap_text "Edit Profile" || return 1
  wait_text Profile || return 1
  shot "$t-11-profile"
  tap_text Back || true
}

capture_empty() {  # capture_empty <prefix>
  local t=$1
  echo "capturing $t -> $OUT"
  tap_text Home;         shot "$t-01-home"
  tap_text "My Posts"; shot "$t-02-my-posts"
  tap_text Liked;      shot "$t-03-favorites"
  tap_text Settings;     shot "$t-04-settings"
}

capture_themes() {  # capture_themes <prefix> [themes...]
  local prefix=$1 theme
  shift
  for theme in "${@:-light dark}"; do
    set_theme "$theme"
    # Before each pass rather than before the run: the previous pass's undo
    # shot left Favorites one card short.
    [ $EMPTY -eq 1 ] || { restore_favorites; launch_app; }
    if [ $EMPTY -eq 1 ]; then capture_empty "$prefix$theme"; else capture_set "$prefix$theme"; fi
  done
  set_theme light
}

# ---- run ------------------------------------------------------------------

if [ $BUILD -eq 1 ]; then
  echo "building ${FLAVOR} debug APK"
  # Demo paywall on, like the iOS capture (POSTER_DEMO_PAYWALL): it fills the
  # support sheet with placeholder tiers so it can be photographed against a
  # local server that has no real store products — otherwise the shot times out.
  (cd "$ROOT" && ./gradlew ":composeApp:assemble${FLAVOR}Debug" \
      -PposterRemoteHost="$HOST" -PposterRemoteScheme="$SCHEME" -PposterRemotePort="$PORT" \
      -PposterDemoPaywall=true -q)
  a install -r -d "$ROOT/composeApp/build/outputs/apk/$APK"
fi

a shell pm list packages > "$TMP/packages.txt" 2>/dev/null || true
grep -qx "package:$PKG" <(tr -d '\r' < "$TMP/packages.txt") ||
  { echo "$PKG not installed on $SERIAL — rerun with --build" >&2; exit 1; }

[ $EMPTY -eq 1 ] && register_throwaway_account

# Always start signed out. The login and registration screens are only reachable
# that way, and a logged-in run silently leaves their old shots on disk.
a shell pm clear "$PKG" >/dev/null

# After the clear, not before: wiping app data also drops the per-app locale.
if [ -n "$LOCALE" ]; then
  echo "locale: $LOCALE"
  # Per-app locale (Android 13+ / API 33). The system_locales setting is read at
  # boot and does nothing to an already-installed app. Older devices lack the
  # `locale` service entirely — do not let that abort the run, but warn, because
  # a non-English pass then captures the device language instead of the target.
  if ! a shell cmd locale set-app-locales "$PKG" --locales "$LOCALE" >/dev/null 2>&1; then
    echo "warn: per-app locale needs Android 13+ (API 33); this device is API $(a shell getprop ro.build.version.sdk | tr -d '\r'), so it stays in the device language" >&2
  fi
fi

launch_app

# No seeding here. The content belongs to scripts/seed-demo-data.sh, and
# scripts/store-screenshots.sh is what sequences the two — an empty database
# for the empty-state pass, a seeded one for the rest. A capture tool that
# also writes the data cannot photograph an empty app.

login_if_needed

capture_themes "" $THEMES

if [ $COMPACT -eq 1 ]; then
  echo "resizing to 720x1600 @320dpi"
  a shell wm size 720x1600 >/dev/null
  a shell wm density 320 >/dev/null
  sleep 3
  refresh_width
  launch_app
  # Best effort: at this size some screens behave differently (see BUGS.md), and a
  # miss here must not throw away a complete native run.
  # Light only: this pass is about layout at a smaller size, and the colour work is
  # already covered at native size.
  capture_themes "compact-" light || echo "compact pass incomplete — see timeout-*.png"
fi

echo
echo "done: $OUT"
ls "$OUT"
