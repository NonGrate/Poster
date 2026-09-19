#!/usr/bin/env bash
# The Play listing's feature graphic, one per language.
#
#   scripts/build-feature-graphic.sh [output-dir] ["en ru"]
#
# 1024x500 is the only size Play accepts, and it is shown small and cropped in
# places, so the wordmark carries it and the tagline is a supporting line rather
# than something anybody has to read.
#
# The name stays in English in every language — it is the name of the app, and
# the Play listing keeps it in Russian too. Only the tagline is translated.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
OUT="${1:-store/graphics}"
# The name on the graphic, from poster.properties so it follows the app.
APP_NAME=$(sed -n 's/^app\.name=//p' poster.properties)
APP_NAME=${APP_NAME:-Poster}
LANGUAGES="${2:-en ru}"

# Roboto, vendored in assets/fonts, because it is what the app itself renders
# in — the listing and the thing it advertises should be set in the same face.
#
# Vendored rather than named: a font asked for by name is resolved from
# whatever the machine happens to have, and ImageMagick substitutes silently
# when it is missing, so the graphic would change without anything failing.
# Android's own copy is no use here — it ships Roboto as a variable font, and
# ImageMagick renders only its default instance, so Bold comes out regular.
FONT_BOLD=assets/fonts/Roboto-Bold.ttf
FONT_REGULAR=assets/fonts/Roboto-Regular.ttf

PAPER="#FDF8F4"
INK="#2B211D"
QUIET="#6D5A51"

play_locale() {
  case $1 in
    en) echo "en-US" ;;
    ru) echo "ru-RU" ;;
    *)  echo "$1" ;;
  esac
}

tagline() {
  case $1 in
    en) echo "Like each other" ;;
    ru) echo "Делитесь важным" ;;
    *)  echo "" ;;
  esac
}

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# The canvas, and where the mark sits on it.
#
# 70% across, and vertically centred on the mark's centre of mass rather than
# on its bounding box.
#
# Two large circles above one small one put the weight high: measured against
# the paper, the mark's mass sits about 45% down its own box, not 50%. Centring
# the box therefore reads as riding up. Balancing the mass instead drops the
# box to roughly 52% — but that number is measured below rather than written
# here, so it stays true if the artwork is ever redrawn.
WIDTH=1024
HEIGHT=500
MARK_CX=$((WIDTH * 70 / 100))
MARK_CY=$((HEIGHT / 2))
# The height of the rendered SVG, which is larger than the height of what
# somebody sees: the artwork does not fill its own canvas, and the washes fade
# out rather than ending. 349 puts the visible mark at the 236 it has always
# been.
MARK_H=349
TEXT_X=106
TEXT_Y=203

# Rendered large and scaled down rather than rendered small: rsvg thins the
# hairlines at small sizes and the circles come out patchy.
rsvg-convert -h 1400 assets/poster-mark.svg -o "$TMP/mark-big.png"
magick "$TMP/mark-big.png" -resize "x${MARK_H}" "$TMP/mark.png"
MARK_W=$(magick identify -format "%w" "$TMP/mark.png")
MARK_X=$((MARK_CX - MARK_W / 2))

# Where the mark's weight actually sits, measured rather than assumed.
#
# Flattened onto the paper first, because how heavy something looks is a
# question about what reaches the eye and not about the alpha channel. Each row
# is weighted by how far it departs from the paper, in the proportions the eye
# uses, so a broad pale wash counts for less than a thin dark stroke of the
# same area.
CENTROID=$(magick "$TMP/mark.png" -background "$PAPER" -alpha remove -alpha off \
  -colorspace sRGB txt:- | python3 -c '
import sys
paper = (0xFD, 0xF8, 0xF4)
rows = {}
for line in sys.stdin.read().splitlines()[1:]:
    try:
        y = int(line.split(":")[0].split(",")[1]); h = line.split("#")[1][:6]
    except (IndexError, ValueError):
        continue
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    d = 0.299 * abs(r - paper[0]) + 0.587 * abs(g - paper[1]) + 0.114 * abs(b - paper[2])
    if d > 1.0:
        rows[y] = rows.get(y, 0.0) + d
total = sum(rows.values())
print(round(sum(y * w for y, w in rows.items()) / total))
')
MARK_Y=$((MARK_CY - CENTROID))

for language in $LANGUAGES; do
  text=$(tagline "$language")
  [ -n "$text" ] || { echo "  no tagline for $language — skipped"; continue; }
  target="$OUT/$(play_locale "$language")"
  mkdir -p "$target"

  # Name and tagline as one block, so the gap between them is fixed and does
  # not drift with the length of a translation.
  magick -background none -fill "$INK" -font "$FONT_BOLD" -pointsize 65 \
    label:"$APP_NAME" "$TMP/name.png"
  magick -background none -fill "$QUIET" -font "$FONT_REGULAR" -pointsize 25 \
    label:"$text" "$TMP/tagline.png"
  # Trimmed, so TEXT_X and TEXT_Y mean where the ink starts rather than where
  # the image does. A typeface carries its own ascender and line spacing, so
  # untrimmed the block lands lower for Roboto than it did for Helvetica —
  # which is a property of the font, not a decision anybody made.
  magick -background none "$TMP/name.png" \
    \( -size 1x14 xc:none \) "$TMP/tagline.png" -append \
    -trim +repage "$TMP/text.png"

  magick -size "${WIDTH}x${HEIGHT}" "gradient:$PAPER-#F9EFE9" \
    \( "$TMP/mark.png" \) -gravity northwest -geometry "+$MARK_X+$MARK_Y" -composite \
    \( "$TMP/text.png" \) -gravity northwest -geometry "+$TEXT_X+$TEXT_Y" -composite \
    "$target/feature-graphic.png"
  echo "  $(play_locale "$language")/feature-graphic.png"
done
