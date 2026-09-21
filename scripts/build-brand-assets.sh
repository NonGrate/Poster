#!/usr/bin/env bash
# Regenerates the PNGs the app packages from the SVG sources in assets/:
#
#   composeResources/drawable/poster_mark.png, poster_mark_dark.png   (900x778)
#   composeResources/drawable/empty_home.png, empty_my_posts.png,
#                              empty_favorites.png                    (560x420)
#
# Run it after replacing assets/poster-mark.svg and assets/poster-mark-dark.svg
# with your own artwork. The launcher icons are a separate script
# (scripts/build-icons.sh --install) because they come from different sources.
#
# The three empty-state images are the same mark by default. Give each screen
# its own illustration by dropping a 560x420 PNG over the generated file, or by
# adding an SVG per screen here.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
command -v rsvg-convert >/dev/null || { echo "rsvg-convert missing: brew install librsvg" >&2; exit 1; }
command -v magick >/dev/null || { echo "magick missing: brew install imagemagick" >&2; exit 1; }

D=composeApp/src/commonMain/composeResources/drawable
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

rsvg-convert -w 900 -h 778 assets/poster-mark.svg -o "$D/poster_mark.png"
rsvg-convert -w 900 -h 778 assets/poster-mark-dark.svg -o "$D/poster_mark_dark.png"
echo "  $D/poster_mark.png, poster_mark_dark.png"

rsvg-convert -h 360 assets/poster-mark.svg -o "$TMP/mark.png"
for name in empty_home empty_my_posts empty_favorites; do
  magick "$TMP/mark.png" -background none -gravity center -extent 560x420 "$D/$name.png"
  echo "  $D/$name.png"
done
