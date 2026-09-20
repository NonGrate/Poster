# Desktop (JVM) target

`feature.desktop=true` in `poster.properties` adds `jvm("desktop")` to
`composeApp`. It is off by default because it changes dependency resolution
for every build and because the billing SDK (RevenueCat, `feature.support`)
has no desktop build: the Gradle script refuses `desktop=true` with
`support=true` and says so.

```
feature.desktop=true
feature.support=false
./gradlew :composeApp:run
```

What the desktop app does differently:

| Concern | Desktop |
|---|---|
| Server | `POSTER_SERVER_HOST`, `POSTER_SERVER_PORT`, `POSTER_SERVER_SCHEME` (env or `-Dposter.server.host=` etc.), default `localhost:8080` over http. |
| Storage | Everything under `~/.poster`: the SQLite cache (`poster.db`), preferences (`preferences.properties`), the session (`session.json`, owner-readable). Override the database with `-Dposter.database=`. |
| Look | The Android (Material 3) components, see `ui/platform/Adaptive.desktop.kt`. No system back gesture. From 840 dp the tabs sit in a rail on the left (icons; a toggle adds the titles, remembered across launches) and the post opens beside the list. |
| Images | The AWT file dialog; the picked file is scaled to the phones' limits with ImageIO. |
| Sharing | Copies the link to the clipboard. |
| Sign-in | Email and password, magic link. No Google or Apple sign-in. |
| Notifications, daily reminder, push | Not available; the switches report that. |
| Packaging | `compose.desktop.application` is configured with the main class only. Add `nativeDistributions { }` (formats, icons, version) when you ship. |

The window is phone-sized (480×900) on purpose: the layouts are single-column.
Two-pane layouts for wide windows are a roadmap item.
