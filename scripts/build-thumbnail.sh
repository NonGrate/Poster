#!/usr/bin/env bash
# A 3:2 thumbnail, for places that want that shape — a hackathon entry, a
# directory listing, a card in somebody's grid.
#
#   scripts/build-thumbnail.sh [output-dir] ["en ru"]
#
# Not a crop of the Play feature graphic. That one is 1024x500, close to 2:1,
# and built around a mark to the right of the wordmark; taking 3:2 out of it
# would either cut the mark off or leave the text stranded in a band of paper.
# A taller canvas wants a stacked composition, so this is its own script that
# shares the palette, the face and the centre-of-mass measurement.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
OUT="${1:-store/graphics}"
# The name on the graphic, from poster.properties so it follows the app.
APP_NAME=$(sed -n 's/^app\.name=//p' poster.properties)
APP_NAME=${APP_NAME:-Poster}
LANGUAGES="${2:-en ru}"

# Vendored for the same reason the feature graphic vendors it: a font asked for
# by name is resolved from whatever the machine has, and ImageMagick
# substitutes silently when it is missing.
FONT_BOLD=assets/fonts/Roboto-Bold.ttf
FONT_REGULAR=assets/fonts/Roboto-Regular.ttf

PAPER="#FDF8F4"
PAPER_WARM="#F6E7DE"
INK="#2B211D"
QUIET="#6D5A51"

# 1200x800. Big enough that nobody has to upscale it, small enough to upload
# anywhere, and exactly 3:2.
WIDTH=1200
HEIGHT=800
MARK_H=300

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

# The second line, shown only when PITCH=1. The plain thumbnail is for places
# that already say what the app is; this one is for a grid of entries where
# nobody knows yet, and the thing worth knowing is that none of it is paid for.
pitch() {
  case $1 in
    en) echo "Free forever · supported by a coffee" ;;
    ru) echo "Полностью бесплатно · можете поддержать чашкой кофе" ;;
    *)  echo "" ;;
  esac
}

NAME="thumbnail-3x2.png"
[ "${PITCH:-0}" = "1" ] && NAME="thumbnail-3x2-pitch.png"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# Rendered large and scaled down: rsvg thins the hairlines at small sizes and
# the circles come out patchy.
rsvg-convert -h 1400 assets/poster-mark.svg -o "$TMP/mark-big.png"
magick "$TMP/mark-big.png" -resize "x${MARK_H}" "$TMP/mark.png"
MARK_W=$(magick identify -format "%w" "$TMP/mark.png")

# Where the mark's weight actually sits, measured rather than assumed — two
# large circles above one small one put the mass high, so centring the bounding
# box reads as riding up. Flattened onto the paper first, because how heavy
# something looks is a question about what reaches the eye rather than about
# the alpha channel.
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

# The mark's weight sits at 38% of the height, not the middle: the wordmark and
# tagline below it carry their own weight, and a stack balanced on the canvas
# centre leaves the whole composition sitting low.
MARK_CY=$((HEIGHT * 38 / 100))
MARK_X=$(((WIDTH - MARK_W) / 2))
MARK_Y=$((MARK_CY - CENTROID))

for language in $LANGUAGES; do
  text=$(tagline "$language")
  [ -n "$text" ] || { echo "  no tagline for $language — skipped"; continue; }
  target="$OUT/$(play_locale "$language")"
  mkdir -p "$target"

  # Name and tagline as one block, so the gap between them is fixed rather than
  # drifting with the length of a translation.
  magick -background none -fill "$INK" -font "$FONT_BOLD" -pointsize 88 \
    label:"$APP_NAME" "$TMP/name.png"
  magick -background none -fill "$QUIET" -font "$FONT_REGULAR" -pointsize 34 \
    label:"$text" "$TMP/tagline.png"
  magick "$TMP/name.png" -trim +repage "$TMP/name-t.png"
  magick "$TMP/tagline.png" -trim +repage "$TMP/tagline-t.png"
  magick -background none "$TMP/name-t.png" \
    \( "$TMP/tagline-t.png" -splice 0x26 \) -gravity center -append "$TMP/text.png"

  # The pitch line, quieter than the tagline and further from it, so the block
  # still reads as name-then-tagline with a footnote rather than three
  # competing lines.
  if [ "${PITCH:-0}" = "1" ]; then
    magick -background none -fill "$QUIET" -font "$FONT_REGULAR" -pointsize 26 \
      label:"$(pitch "$language")" "$TMP/pitch.png"
    magick "$TMP/pitch.png" -trim +repage "$TMP/pitch-t.png"
    magick -background none "$TMP/text.png" \
      \( "$TMP/pitch-t.png" -splice 0x34 \) -gravity center -append "$TMP/text2.png"
    mv "$TMP/text2.png" "$TMP/text.png"
  fi

  magick -size "${WIDTH}x${HEIGHT}" "gradient:${PAPER}-${PAPER_WARM}" \
    \( "$TMP/mark.png" \) -geometry "+${MARK_X}+${MARK_Y}" -composite \
    \( "$TMP/text.png" \) -gravity north -geometry "+0+$((MARK_CY + CENTROID + 56))" -composite \
    "$target/$NAME"
  echo "  $(play_locale "$language")/$NAME"
done
