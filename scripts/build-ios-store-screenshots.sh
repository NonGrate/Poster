#!/usr/bin/env bash
# Turns the raw iOS captures into the images an App Store listing shows.
#
#   build-ios-store-screenshots.sh <captures-dir> <output-dir> ["en ru"]
#
# The iOS twin of build-store-screenshots.sh: same warm paper ground and the
# same captions, so the App Store and Play listings read as one app. What
# differs is the canvas — App Store screenshots must be an exact device size,
# not Play's flexible one. 1290x2796 is the 6.9" iPhone slot Apple now requires
# (it also accepts 1320x2868 there); a composed marketing image at that size is
# fine, the inner capture need not come from that exact device.
#
# Expects <captures-dir>/<lang>/<shot>.png as scripts/ios-screenshots.sh leaves
# them, and writes <output-dir>/<locale>/<shot>.png at 1290x2796.
#
# iOS shot names differ from Android's: the favourites tab is "liking", the
# support sheet is "10" not "13". Captions below are keyed to the iOS names.
set -euo pipefail

CAPTURES="${1:?where the raw captures are}"
OUT="${2:?where to write}"
LANGUAGES="${3:-en}"
DEVICE="${4:-iphone}"

PAPER="#FDF8F4"
INK="#2B211D"

# One profile per App Store device slot. The canvas must be an exact accepted
# size; the rest is layout — a taller iPad shot leaves less caption room, so it
# gets a larger caption and a little more of it.
case "$DEVICE" in
  iphone) WIDTH=1284; HEIGHT=2778; SHOT_H=2040; CAP_W=1120; CAP_H=260; CAP_PT=66; CAP_Y=140; SHOT_Y=470 ;;  # 6.7" (1284x2778)
  ipad)   WIDTH=2064; HEIGHT=2752; SHOT_H=1980; CAP_W=1700; CAP_H=320; CAP_PT=88; CAP_Y=180; SHOT_Y=560 ;;  # 13"
  *) echo "unknown device: $DEVICE (want iphone|ipad)" >&2; exit 2 ;;
esac

# The screens the listing shows, in the order somebody meets them. The dark feed
# goes last: same screen as the second, there to say the app has a dark mode.
SHOTS="${SHOTS:-00-login light-01-home light-02-my-posts light-06-liked light-07-settings light-10-support-paywall light-09-post-details dark-01-home}"

store_locale() {  # App Store locale codes
  case $1 in
    en) echo "en-US" ;;
    ru) echo "ru" ;;
    *)  echo "$1" ;;
  esac
}

caption() {  # caption <lang> <shot>
  case "$1/$2" in
    en/00-login)                 echo "A quiet place to share what matters" ;;
    en/light-01-home)            echo "See what the people around you are posting" ;;
    en/light-02-my-posts)        echo "Write a post, for everyone or just your group" ;;
    en/light-06-liked)          echo "Keep the posts you liked in one place" ;;
    en/light-07-settings)        echo "Set a daily reminder to check in" ;;
    en/light-09-post-details)    echo "Follow how a post turned out" ;;
    en/light-10-support-paywall) echo "Free forever — buy me a coffee if it helps" ;;
    en/dark-01-home)             echo "Made for reading late, and for reading quietly" ;;
    ru/00-login)                 echo "Тихое место, чтобы делиться важным" ;;
    ru/light-01-home)            echo "Смотрите, что пишут люди рядом" ;;
    ru/light-02-my-posts)        echo "Напишите пост — для всех или только для своих" ;;
    ru/light-06-liked)          echo "Понравившиеся посты — в одном месте" ;;
    ru/light-07-settings)        echo "Установите ежедневное напоминание" ;;
    ru/light-09-post-details)    echo "Узнайте, чем всё закончилось" ;;
    ru/light-10-support-paywall) echo "Бесплатно навсегда — угостите кофе, если помогает" ;;
    ru/dark-01-home)             echo "Чтобы читать поздно вечером и негромко" ;;
    *) echo "" ;;
  esac
}

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

for language in $LANGUAGES; do
  target="$OUT/$(store_locale "$language")"
  mkdir -p "$target"
  for shot in $SHOTS; do
    # Per-language subdir if there is one, else a flat dir — the iOS capture run
    # is single-language and writes straight into screenshots/ios.
    src="$CAPTURES/$language/$shot.png"
    [ -s "$src" ] || src="$CAPTURES/$shot.png"
    [ -s "$src" ] || { echo "  missing $shot — skipped"; continue; }
    text=$(caption "$language" "$shot")
    [ -n "$text" ] || { echo "  no caption for $language/$shot — skipped"; continue; }

    # The capture, scaled to leave room for the caption, corners rounded so it
    # reads as a phone rather than a pasted rectangle.
    magick "$src" -resize "x${SHOT_H}" "$TMP/shot.png"
    magick "$TMP/shot.png" \
      \( +clone -alpha extract -draw "fill black polygon 0,0 0,32 32,0 fill white circle 32,32 32,0" \
         \( +clone -flip \) -compose Multiply -composite \
         \( +clone -flop \) -compose Multiply -composite \) \
      -alpha off -compose CopyOpacity -composite "$TMP/rounded.png"

    # A caption block rather than a line: these wrap, and a caption running off
    # both edges is what a single -annotate line gives.
    magick -size "${CAP_W}x${CAP_H}" -background none -fill "$INK" \
      -font "Helvetica-Bold" -pointsize "$CAP_PT" -gravity center \
      caption:"$text" "$TMP/caption.png"

    magick -size "${WIDTH}x${HEIGHT}" "gradient:$PAPER-#F6E7DE" \
      \( "$TMP/caption.png" \) -gravity north -geometry "+0+${CAP_Y}" -composite \
      \( "$TMP/rounded.png" \) -gravity north -geometry "+0+${SHOT_Y}" -composite \
      "$target/$shot.png"
    echo "  $(store_locale "$language")/$shot.png ($(magick identify -format '%wx%h' "$target/$shot.png"))"
  done
done
