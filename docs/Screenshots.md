# Screenshots and store graphics

Everything a listing needs, from a running app to composed images, without
touching a screenshot by hand. Rerun after a UI change and the listing catches up.

## Prerequisites

`brew install librsvg imagemagick` for the composers; an emulator or simulator;
a server the app can reach (the local one is fine — the demo content is
seeded for you).

## Android

```bash
scripts/run-local-backend.sh                 # in another shell
scripts/store-screenshots.sh --host 10.0.2.2:8080 --languages "en ru"
```

`store-screenshots.sh` sequences the whole thing: wipes the backend, captures
the empty states, seeds every language (`seed-demo-data.sh`), captures the full
app in light and dark in each language, then composes the listing images into
`store/screenshots/<locale>/` at 1080×1920 with a caption on the app's own paper
colour (the composers read `light.background`, `light.onSurface` and friends from
`shared/build/generated/poster/palette.properties`, so they follow `color.primary`
/ `color.accent` in `poster.properties` without edits). `--only "light-13-support-paywall"` recaptures one screen;
`--skip-empty` skips the empty pass; `--no-build` reuses the installed APK.

Under it, `ui-screenshots.sh` drives the real app over `adb` — taps are resolved
from the uiautomator hierarchy by label (looked up in `strings.xml`, so a Russian
run finds the Russian labels), never by coordinates — and writes raw PNGs to
`screenshots/<lang>/`. Flags: `--local` (e2e flavour), `--host`, `--build`,
`--themes light`, `--locale ru-RU`, `--empty`, `--compact` (720×1600 pass),
`--only`, `--serial`.

Captions live in `scripts/build-store-screenshots.sh` (`caption()`), one per
language per shot. Adding a language = captions there + content in
`seed-demo-data.sh` + a Play locale in `play_locale()`.

## Desktop and web

```bash
scripts/desktop-screenshots.sh     # -> screenshots/desktop/ (light, dark, wide)
scripts/web-screenshots.sh         # -> screenshots/web/ (feed, tabs, wide two-pane)
```

Both sign in as the demo reader without touching the UI — the session is
planted where the app reads it (`POSTER_HOME` for desktop, `localStorage` for
web) — so they need only the local backend and the seeded demo data. Desktop
renders itself into PNGs (`POSTER_RENDER_TO`, no window, no screen-recording
permission, CI-friendly); web is headless Chrome over the DevTools protocol
against the bundle the Ktor server hosts (`POSTER_WEB_DIR`). Both need their
flag on and `feature.support=false`; the desktop script flips and restores
`poster.properties` itself, the web one expects the bundle to exist.

## The pages under `screenshots/`

`scripts/screenshot-pages.sh` rewrites `screenshots/<platform>.md` from the
PNGs present — one section per screen, light beside dark — and the README's
gallery links to them. Run it after any capture.

## iOS

```bash
scripts/ios-screenshots.sh                    # auto-picks a booted or the newest iPhone
scripts/ios-screenshots.sh --lang ru --only light-01-home
```

`simctl` cannot tap or type, so the app is driven from inside an XCUITest
(`PosterScreenshotTests` in `iosApp/iosAppUITests/PosterUITests.swift`); the
shots come back as attachments in the result bundle and the script unpacks them
to `screenshots/ios/<lang>/`, then recomposes the App Store tiles
(`build-ios-store-screenshots.sh`) at 1284×2778 (iPhone 6.7") and 2064×2752 (iPad 13")
into `store/ios-screenshots*/`.

## Demo content

`scripts/seed-demo-data.sh --host <host> [--reset] [--favorites-only]` creates,
per language, an *author* (five posts, one resolved, one group-only) and a
*reader* (three posts, one private; likes two of the author's), three
groups, and a liked-by roster with named and anonymous people. The
screenshot scripts sign in as the reader. The content is in four small functions
at the top of the script — edit freely; tag ids must be ones from
`CuratedTags.kt`.

## Icons and marks

| Script | Produces |
|---|---|
| `build-icons.sh [--install]` | adaptive + legacy launcher icons for every density from `assets/icon-*.svg`, and `build/icons/play-icon-512.png`. Without `--install` it writes to `build/icons/` only |
| `build-brand-assets.sh` | `poster_mark.png`, `poster_mark_dark.png` and the three `empty_*.png` drawables from `assets/poster-mark*.svg` |
| `build-feature-graphic.sh [out] ["en ru"]` | Play's 1024×500 feature graphic per language |
| `build-thumbnail.sh [out] ["en ru"]` (`PITCH=1` adds a second line) | a 1200×800 3:2 tile for directories and hackathons |

The name on the graphics comes from `app.name` in `poster.properties`; taglines
are in each script.

`screenshots/` and `store/` are git-ignored outputs. Commit the ones you ship if
you want them versioned.
