#!/usr/bin/env bash
# Turns raw device captures into the images the Play listing shows.
#
#   build-store-screenshots.sh <captures-dir> <output-dir> ["en ru"] [phone|tablet]
#
# A bare capture is legible but reads as unfinished beside listings that put a
# caption on a coloured ground. The ground is the same warm paper as the site
# and the app, so the listing and the thing it is advertising look related.
#
# Expects <captures-dir>/<lang>/<shot>.png, as scripts/store-screenshots.sh
# leaves them, and writes <output-dir>/<play-locale>/<shot>.png at 1080x1920,
# which is what Play wants for a phone.
#
# Adding a language means adding its captions below and its Play locale to
# play_locale. Adding a screen means adding it to SHOTS and writing a caption
# for it in every language.
set -euo pipefail

CAPTURES="${1:?where the raw captures are}"
OUT="${2:?where to write}"
LANGUAGES="${3:-en ru}"
DEVICE="${4:-phone}"

source "$(cd "$(dirname "$0")" && pwd)/lib/palette.sh"; palette_ensure
PAPER="$(palette light.background "#FDF8F4")"
INK="$(palette light.onSurface "#2B211D")"
PAPER_WARM="$(palette light.surfaceContainerLow "#F6E7DE")"

# One profile per Play screenshot slot. phone is the 1080x1920 phone image;
# tablet is a portrait 10" tablet (1600x2560, the Pixel Tablet in portrait), with
# a larger shot and caption to match the bigger canvas. The inner capture must
# match the canvas orientation — a tablet capture for tablet, a phone one for phone.
case "$DEVICE" in
  phone)  CANVAS=1080x1920; SHOT_H=1480; RADIUS=28; CAP_W=900;  CAP_H=210; CAP_PT=58; CAP_Y=90;  SHOT_Y=330 ;;
  tablet) CANVAS=1600x2560; SHOT_H=1970; RADIUS=36; CAP_W=1330; CAP_H=300; CAP_PT=76; CAP_Y=120; SHOT_Y=470 ;;
  *) echo "unknown device: $DEVICE (want phone|tablet)" >&2; exit 2 ;;
esac

# The six screens the listing shows, in the order somebody meets them. The dark
# feed goes last: it is the same screen as the second one, and its job is to
# say the app has a dark mode rather than to explain anything new.
SHOTS="${SHOTS:-00-login light-01-home light-02-my-posts light-06-favorites light-07-settings light-13-support-paywall light-09-post-details dark-01-home}"

play_locale() {
  case $1 in
    en) echo "en-US" ;;
    ru) echo "ru-RU" ;;
    *)  echo "$1" ;;
  esac
}

caption() {  # caption <lang> <shot>
  case "$1/$2" in
    en/00-login)            echo "A quiet place to share what matters" ;;
    en/light-01-home)       echo "See what the people around you are posting" ;;
    en/light-02-my-posts)   echo "Write a post, for everyone or just your group" ;;
    en/light-06-favorites)  echo "Keep the posts you liked in one place" ;;
    en/light-07-settings)   echo "Set a daily reminder to check in" ;;
    en/light-09-post-details) echo "Follow how a post turned out" ;;
    en/light-13-support-paywall) echo "Free forever — buy me a coffee if it helps" ;;
    en/light-08-groups) echo "A circle for your family, and one for your friends" ;;
    en/dark-01-home)        echo "Made for reading late, and for reading quietly" ;;
    ru/00-login)            echo "Тихое место, чтобы делиться важным" ;;
    ru/light-01-home)       echo "Смотрите, что пишут люди рядом" ;;
    ru/light-02-my-posts)   echo "Напишите пост — для всех или только для своих" ;;
    ru/light-06-favorites)  echo "Понравившиеся посты — в одном месте" ;;
    ru/light-07-settings)   echo "Установите ежедневное напоминание" ;;
    ru/light-09-post-details) echo "Узнайте, чем всё закончилось" ;;
    ru/light-13-support-paywall) echo "Бесплатно навсегда — угостите кофе, если помогает" ;;
    ru/light-08-groups) echo "Круг для семьи и круг для друзей" ;;
    ru/dark-01-home)        echo "Чтобы читать поздно вечером и негромко" ;;
    *) echo "" ;;
  esac
}

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

for language in $LANGUAGES; do
  target="$OUT/$(play_locale "$language")"
  mkdir -p "$target"
  for shot in $SHOTS; do
    src="$CAPTURES/$language/$shot.png"
    [ -s "$src" ] || { echo "  missing $src — skipped"; continue; }
    text=$(caption "$language" "$shot")
    [ -n "$text" ] || { echo "  no caption for $language/$shot — skipped"; continue; }

    # The device shot, scaled to leave room for the caption, with its corners
    # rounded so it reads as a phone rather than a pasted rectangle.
    magick "$src" -resize "x${SHOT_H}" "$TMP/shot.png"
    magick "$TMP/shot.png" \
      \( +clone -alpha extract -draw "fill black polygon 0,0 0,$RADIUS $RADIUS,0 fill white circle $RADIUS,$RADIUS $RADIUS,0" \
         \( +clone -flip \) -compose Multiply -composite \
         \( +clone -flop \) -compose Multiply -composite \) \
      -alpha off -compose CopyOpacity -composite "$TMP/rounded.png"

    # A caption block rather than a line of text: these wrap, and a caption
    # running off both edges of the canvas is what -annotate gives you.
    magick -size "${CAP_W}x${CAP_H}" -background none -fill "$INK" \
      -font "Helvetica-Bold" -pointsize "$CAP_PT" -gravity center \
      caption:"$text" "$TMP/caption.png"

    magick -size "$CANVAS" "gradient:$PAPER-$PAPER_WARM" \
      \( "$TMP/caption.png" \) -gravity north -geometry "+0+${CAP_Y}" -composite \
      \( "$TMP/rounded.png" \) -gravity north -geometry "+0+${SHOT_Y}" -composite \
      "$target/$shot.png"
    echo "  $(play_locale "$language")/$shot.png"
  done
done
