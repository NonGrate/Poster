#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib/palette.sh"; palette_ensure
ICON_BG="$(palette light.surfaceContainerLow "#FBEDE4")"

# Builds every launcher icon from assets/icon-*.svg.
#
# Two ladders, because Android has two ideas of what an icon is:
#   ic_launcher_foreground/background — 108dp, adaptive, API 26+
#   ic_launcher/ic_launcher_round     — 48dp, legacy, API 24-25
#
# The legacy pair is cropped to the middle two thirds, because that is what an
# adaptive mask shows and the two should look like the same icon.

cd "$(dirname "${BASH_SOURCE[0]}")/.."

# Writes to build/icons by default. The app still ships the candle, and
# overwriting it from a script somebody ran to look at something would be a
# surprise. Pass --install to replace the launcher icons for real.
RES=build/icons
if [ "${1:-}" = "--install" ]; then
  RES=composeApp/src/androidMain/res
  echo "installing into $RES"
else
  echo "writing to $RES (pass --install to replace the app's icons)"
fi
for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do mkdir -p "$RES/mipmap-$density"; done
mkdir -p build/icons
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

# density : adaptive px : legacy px : which weight
BUCKETS="mdpi:108:48:dense hdpi:162:72:dense xhdpi:216:96:light xxhdpi:324:144:light xxxhdpi:432:192:light"

for entry in $BUCKETS; do
  IFS=: read -r density adaptive legacy weight <<< "$entry"
  fg=assets/icon-foreground.svg
  [ "$weight" = "dense" ] && fg=assets/icon-foreground-dense.svg

  rsvg-convert -w "$adaptive" -h "$adaptive" "$fg" -o "$RES/mipmap-$density/ic_launcher_foreground.png"
  # Flattened onto an opaque colour rather than left as rsvg wrote it. rsvg
  # antialiases the rect's own boundary, so the corner pixels come out at 98%
  # alpha; an adaptive background is meant to be fully opaque, and a launcher
  # is entitled to do what it likes with one that is not.
  rsvg-convert -w "$adaptive" -h "$adaptive" assets/icon-background.svg -o "$OUT/bg-raw.png"
  magick "$OUT/bg-raw.png" -background "$ICON_BG" -alpha remove -alpha off \
    "$RES/mipmap-$density/ic_launcher_background.png"

  # Legacy: composite at high resolution, crop to the masked area, then size.
  big=$((legacy * 8))
  rsvg-convert -w "$big" -h "$big" assets/icon-background.svg -o "$OUT/bg.png"
  rsvg-convert -w "$big" -h "$big" "$fg" -o "$OUT/fg.png"
  magick "$OUT/bg.png" "$OUT/fg.png" -composite \
    -gravity center -crop "$((big * 2 / 3))x$((big * 2 / 3))+0+0" +repage \
    -resize "${legacy}x${legacy}" "$OUT/flat.png"

  radius=$((legacy * 22 / 100))
  magick "$OUT/flat.png" \
    \( -size "${legacy}x${legacy}" xc:none -fill white \
       -draw "roundrectangle 0,0 $((legacy - 1)),$((legacy - 1)) $radius,$radius" \) \
    -alpha set -compose DstIn -composite "$RES/mipmap-$density/ic_launcher.png"

  magick "$OUT/flat.png" \
    \( -size "${legacy}x${legacy}" xc:none -fill white \
       -draw "circle $((legacy / 2)),$((legacy / 2)) $((legacy / 2)),0" \) \
    -alpha set -compose DstIn -composite "$RES/mipmap-$density/ic_launcher_round.png"

  echo "  $density: adaptive ${adaptive}px, legacy ${legacy}px ($weight)"
done

# The Play listing wants one 512 square.
#
# From the launcher artwork, not from the logo. They are the same mark at two
# weights: the logo has 2.2-unit strokes and pale washes, which is right in the
# app where it appears large, and which washes out at the sizes Play lists an
# icon at. Building it from assets/poster-mark.svg instead is a few
# lines — see the history — if the delicate version is ever wanted.
#
# It goes to build/ even when the launcher icons are being installed: res/ is
# for what the app packages, and a store asset dropped in there becomes a
# drawable nobody meant to ship.
#
# A light centred zoom, not the safe-zone crop. This is a flat icon — Play and
# the App Store round its corners, they do not apply the adaptive circular mask
# — so the middle-66% zoom the launcher needs made the mark read too big and,
# with the foreground's 18-of-512 downward offset, too low. Instead: lift the
# mark back to the middle (144 of 4096), then crop 3470 of 4096 — about an 18%
# zoom, enough presence for a store tile without the safe-zone crop's bulk.
rsvg-convert -w 4096 -h 4096 assets/icon-background.svg -o "$OUT/bg.png"
rsvg-convert -w 4096 -h 4096 assets/icon-foreground.svg -o "$OUT/fg.png"
magick "$OUT/bg.png" -background "$ICON_BG" -alpha remove -alpha off \
  \( "$OUT/fg.png" \) -gravity center -geometry +0-144 -composite \
  -gravity center -crop 3470x3470+0+0 +repage -resize 512x512 build/icons/play-icon-512.png
echo "  build/icons/play-icon-512.png"
