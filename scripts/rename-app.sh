#!/usr/bin/env bash
# Makes the template yours: package, application id, display name, deep-link
# scheme and public domain, across every file in the repository.
#
#   scripts/rename-app.sh \
#     --package  com.acme.chirp \
#     --app-id   com.acme.chirp \
#     --name     "Chirp" \
#     --scheme   chirp \
#     --domain   chirp.acme.com
#
# Every flag is optional; anything not given keeps the template's value. Run it
# once, on a clean checkout, before the first build — it rewrites source,
# resources, the Xcode project, scripts and docs, and moves the Kotlin source
# directories to the new package path. Review the diff, build, commit.
#
# What it deliberately does NOT touch: the word "Post" for the thing people
# write. That is a product decision with a hundred string resources behind it;
# see docs/Renaming.md for how to do it.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

PACKAGE=""; APP_ID=""; NAME=""; SCHEME=""; DOMAIN=""
while [ $# -gt 0 ]; do
  case $1 in
    --package) PACKAGE=$2; shift ;;
    --app-id)  APP_ID=$2; shift ;;
    --name)    NAME=$2; shift ;;
    --scheme)  SCHEME=$2; shift ;;
    --domain)  DOMAIN=$2; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "unknown flag: $1" >&2; exit 2 ;;
  esac
  shift
done

if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
  echo "Working tree is not clean. Commit or stash first, so the rename is one reviewable diff." >&2
  exit 1
fi

[ -n "$PACKAGE" ] && [[ ! "$PACKAGE" =~ ^[a-z][a-z0-9]*(\.[a-z][a-z0-9_]*)+$ ]] && { echo "--package must look like com.acme.app (lowercase, dots, no dashes)" >&2; exit 2; }
[ -n "$SCHEME" ] && [[ ! "$SCHEME" =~ ^[a-z][a-z0-9]*$ ]] && { echo "--scheme must be lowercase letters/digits (it is a URL scheme)" >&2; exit 2; }

python3 - "$PACKAGE" "$APP_ID" "$NAME" "$SCHEME" "$DOMAIN" <<'PY'
import os, re, sys
package, app_id, name, scheme, domain = sys.argv[1:6]

OLD_PACKAGE = "com.example.poster"
OLD_APP_ID = "com.example.poster"
OLD_NAME = "Poster"
OLD_SCHEME = "poster"
OLD_DOMAIN = "poster.example.com"

rules = []
if package:
    rules.append((OLD_PACKAGE, package))                        # Kotlin package, namespace
    rules.append((OLD_PACKAGE.replace(".", "/"), package.replace(".", "/")))
if app_id:
    rules.append((OLD_APP_ID, app_id))                          # applicationId, bundle id
if domain:
    rules.append((OLD_DOMAIN, domain))
if scheme:
    rules.append((f"{OLD_SCHEME}://", f"{scheme}://"))
    rules.append((f"app.scheme={OLD_SCHEME}", f"app.scheme={scheme}"))
    rules.append((f"POSTER_URL_SCHEME={OLD_SCHEME}", f"POSTER_URL_SCHEME={scheme}"))
if name:
    rules.append((f"app.name={OLD_NAME}", f"app.name={name}"))
    rules.append((f"APP_NAME={OLD_NAME}", f"APP_NAME={name}"))
    rules.append((f'<string name="app_name">{OLD_NAME}</string>', f'<string name="app_name">{name}</string>'))
    rules.append((f"<string>{OLD_NAME}</string>", f"<string>{name}</string>"))   # CFBundleDisplayName
    rules.append((f'rootProject.name = "{OLD_NAME}"', f'rootProject.name = "{name.replace(" ", "")}"'))
    # Compose resources package is derived from rootProject.name.
    rules.append(("poster.composeapp.generated.resources", f"{name.replace(' ', '').lower()}.composeapp.generated.resources"))

if not rules:
    print("nothing to do — pass at least one flag"); sys.exit(0)

TEXT = {".kt", ".kts", ".swift", ".xml", ".sq", ".sqm", ".sh", ".yml", ".yaml", ".md", ".plist",
        ".pbxproj", ".xcconfig", ".xcscheme", ".properties", ".pro", ".json", ".txt", ".toml", ".svg",
        ".entitlements", ".resolved"}
NAMES = {"Dockerfile", "Dockerfile.runtime", ".gitignore", ".dockerignore"}
SKIP_DIRS = {".git", "build", ".gradle", ".idea", ".kotlin", "node_modules"}
# Never rewrite this script itself: bash reads it incrementally while it runs,
# and the OLD_* constants above must keep describing the template.
SKIP_FILES = {os.path.join(".", "scripts", "rename-app.sh")}

changed = 0
for root, dirs, files in os.walk("."):
    dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
    for fn in files:
        p = os.path.join(root, fn)
        if p in SKIP_FILES:
            continue
        if fn not in NAMES and os.path.splitext(fn)[1] not in TEXT:
            continue
        raw = open(p, "rb").read()
        if b"\0" in raw:
            continue
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        new = text
        for old, rep in rules:
            new = new.replace(old, rep)
        if new != text:
            open(p, "wb").write(new.encode("utf-8"))
            changed += 1
print(f"rewrote {changed} files")

if package:
    old_path = OLD_PACKAGE.replace(".", "/")
    new_path = package.replace(".", "/")
    moved = 0
    for root, dirs, files in os.walk("."):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        if root.replace(os.sep, "/").endswith("/" + old_path):
            base = root[: -len(old_path)]
            dst = os.path.join(base, new_path)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            os.rename(root, dst)
            # remove now-empty old parents
            parent = os.path.dirname(root)
            while parent.rstrip("/") != base.rstrip("/"):
                try: os.rmdir(parent)
                except OSError: break
                parent = os.path.dirname(parent)
            moved += 1
            dirs[:] = []
    print(f"moved {moved} package directories to {new_path}")
PY

echo
echo "Done. Next:"
echo "  1. git diff --stat            review what changed"
echo "  2. ./gradlew :shared:jvmTest  make sure it still builds"
echo "  3. open iosApp/iosApp.xcodeproj and let Xcode re-resolve the bundle id"
echo "  4. docs/Renaming.md            for the things a script cannot decide (the word 'Post', icons, store names)"
