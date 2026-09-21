#!/usr/bin/env bash
# Writes screenshots/<platform>.md for every platform folder under screenshots/:
# a gallery of the PNGs found there, light and dark side by side where both
# exist. Run after any of the capture scripts; the README links to these pages.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT/screenshots"
title_of() { case $1 in android) echo Android ;; ios) echo iOS ;; ios-liquid) echo "iOS, Liquid Glass tab bar" ;; ipad) echo "iPad" ;; desktop) echo "Desktop (JVM)" ;; web) echo "Web (Kotlin/Wasm)" ;; *) echo "$1" ;; esac; }
how_of() {
  case $1 in
    android) echo "\`scripts/ui-screenshots.sh --local --build --out screenshots/android\` against \`scripts/run-local-backend.sh\` and \`scripts/seed-demo-data.sh\`; the store composites come from \`scripts/store-screenshots.sh\` (docs/Screenshots.md)." ;;
    ios) echo "\`scripts/ios-screenshots.sh\` on a simulator against the local backend; the app is driven by the XCUITest suite (docs/Screenshots.md)." ;;
    ipad) echo "\`scripts/ios-screenshots.sh --sim \"iPad Pro 11-inch (M5)\"\`: the same XCUITest run on an iPad, where the tabs sit in the left rail and the post opens beside the list." ;;
    ios-liquid) echo "the same run with \`feature.liquidNavBar=true\`: the tabs are a SwiftUI TabView, the system's Liquid Glass bar on iOS 26 (docs/LiquidDesign.md)." ;;
    desktop) echo "\`scripts/desktop-screenshots.sh\`: the desktop app renders itself into PNGs headlessly (\`POSTER_RENDER_TO\`), signed in as the demo reader (docs/Desktop.md)." ;;
    web) echo "\`scripts/web-screenshots.sh\`: headless Chrome over the DevTools protocol against the bundle the Ktor server hosts under \`/app\` (docs/Web.md)." ;;
  esac
}
for platform in android ios ios-liquid ipad desktop web; do
  [ -d "$platform" ] || continue
  page="$platform.md"
  # ios-screenshots.sh writes per language (screenshots/ios/en/); the others flat.
  dir=$platform
  if ! ls "$platform"/*.png >/dev/null 2>&1 && [ -d "$platform/en" ]; then dir="$platform/en"; fi
  {
    echo "# $(title_of "$platform") screenshots"
    echo
    echo "Captured from the running app with demo data, in the default palette. How: $(how_of "$platform")"
    echo
    echo "[← README](../README.md) · other platforms:"
    for other in android ios ios-liquid ipad desktop web; do
      if [ "$other" != "$platform" ] && [ -d "$other" ]; then printf '[%s](%s.md) · ' "$(title_of "$other")" "$other"; fi
    done; echo; echo
    # One row per screen: the light shot and, when there is one, its dark twin.
    # HTML tables with explicit widths, so a screen without a twin does not
    # stretch across the page: a phone shot at 280px, a wide one at 640px.
    img() { # path alt
      local w=280; case $1 in *wide*|ipad/*) w=640 ;; esac
      printf '<img src="%s" width="%s" alt="%s">' "$1" "$w" "$2"
    }
    for light in $(ls "$dir" | { grep -E '^light-.*\.png$' || true; } | sort); do
      name=${light#light-}; name=${name%.png}
      dark="dark-$name.png"
      label=$(echo "$name" | sed -E 's/^[0-9]+[a-z]?-//; s/-/ /g')
      echo "## $label"; echo
      if [ -f "$dir/$dark" ]; then
        echo "<table><tr><th>Light</th><th>Dark</th></tr><tr><td>$(img "$dir/$light" "$label, light")</td><td>$(img "$dir/$dark" "$label, dark")</td></tr></table>"
      else
        echo "<table><tr><td>$(img "$dir/$light" "$label")</td></tr></table>"
      fi
      echo
    done
    # Anything not in the light-/dark- convention (login, register, store composites).
    for file in $(ls "$dir" | { grep -E '\.png$' || true; } | { grep -vE '^(light|dark)-' || true; } | sort); do
      echo "## ${file%.png}"; echo; echo "<table><tr><td>$(img "$dir/$file" "${file%.png}")</td></tr></table>"; echo
    done
  } > "$page"
  echo "  screenshots/$page"
done
