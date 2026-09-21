# Configuration: `poster.properties`

One file at the repository root decides what the app is called, which features
are compiled in, and what colours it wears. Gradle reads it at configuration
time and generates Kotlin constants into the `shared` module, so **Android, iOS
(through the shared framework) and the server** all see the same values.

```properties
app.name=Poster
app.scheme=poster
app.webOrigin=https://poster.example.com

feature.groups=true
feature.likes=true
...

color.primary=#4F46E5
color.accent=#F59E0B
...
```

Change a value, rebuild. Nothing else to edit.

## What gets generated

`./gradlew :shared:generatePosterConfig` (runs automatically before every compile)
writes three files into `shared/build/generated/poster/commonMain/kotlin/com/example/poster/config/`:

```kotlin
object Features {
    const val GROUPS: Boolean = true
    const val LIKES: Boolean = true
    // ...
}
object BrandPalette {
    object Light { const val primary: Long = 0xFF8D4F3A /* ... every Material role */ }
    object Dark  { /* ... */ }
}
object AppInfo {
    const val NAME = "Poster"
    const val SCHEME = "poster"
    const val WEB_ORIGIN = "https://poster.example.com"
}
```

They are `const val` on purpose. `if (Features.LIKES) { … }` with the flag false
is dead code to the Kotlin compiler and to R8, so a feature that is off is not
merely hidden — it is not in the APK. An unknown key in the file fails the build
rather than being ignored, so a typo cannot silently turn a feature back on.

The same file also feeds, in `composeApp/build.gradle.kts`:

- the Android manifest's deep-link filters (`${posterScheme}`, `${posterWebHost}`),
- the generated `values/colors.xml` and `values-night/colors.xml` behind the
  status/navigation bars (from `color.*.background`),
- which billing source set to compile (see *feature.support* below).

## App identity

| Key | Used for |
|---|---|
| `app.name` | The name on web pages, in emails, in the admin panel title. The in-app name is `app_name` in `composeApp/src/commonMain/composeResources/values/strings.xml`, the Android `res/values/strings.xml`, and `CFBundleDisplayName` in `Info.plist` — `scripts/rename-app.sh --name` sets all four. |
| `app.scheme` | The custom URL scheme: `poster://join/CODE`, `poster://post/TOKEN`, `poster://verify?token=…`, `poster://auth/apple`. Android reads it from the manifest placeholder; iOS from `POSTER_URL_SCHEME` in `Config.xcconfig` (Xcode cannot read this file — keep the two equal, `rename-app.sh --scheme` does). |
| `app.webOrigin` | Where the server lives. Invite links (`/join/CODE`) and share links (`/p/TOKEN`) are built on it, the Android App Links filter claims its host, and the server uses it for links in emails unless `POSTER_BASE_URL` overrides it. |

## Features

Every flag defaults to `true` except `feature.liquidDesign`, `feature.liquidNavBar`
and `feature.desktop`, which are opt-in. Turning one off removes it from the app
**and** from the server (the routes are not registered, so a stale client gets 404).
The table reads "what the flag controls" on each side; for the opt-in flags it is
what turning them on adds.

| Flag | App side | Server / build side |
|---|---|---|
| `feature.groups` | Groups screen and settings section, group picker in the post form, group filter, invite-code field at registration, invite deep links, "joined" announcements | `/groups/*`, `/join/{code}` page |
| `feature.tags` | Tag picker in the post form (and the "at least one tag" rule), tag chips on cards, tag filter | `/tags/*` (tags on posts are still stored and returned) |
| `feature.likes` | Like button and counts, the "Liked" tab, who-liked roster, "show my name" setting | `/favorites/*` |
| `feature.sharing` | Share action, opening `…://post/TOKEN` links | `POST /posts/{id}/share`, `GET /shared/{token}`, the `/p/{token}` web page |
| `feature.postCompletion` | "Mark as resolved" / "Reopen", the Resolved badge | `POST /posts/{id}/complete`, `/reopen` |
| `feature.postVisibility` | The Everyone / Group / Only me picker (every post is public), the default-visibility setting | nothing — visibility is still enforced for stored data |
| `feature.dailyReminder` | The reminder rows in Settings | — |
| `feature.feedback` | The Feedback section and screen | `/feedback` |
| `feature.support` | The whole Support section; also **drops the RevenueCat SDK from the Android build** (see below) | `/config`, `/support/interest` |
| `feature.reports` | "Report this post" | `POST /posts/{id}/report` |
| `feature.crashReports` | Installing the crash handler and uploading stored crashes | `POST /crashes` |
| `feature.telemetry` | Diagnostic events (`EventReporter` becomes a no-op) | `POST /events` |
| `feature.emailVerificationRequired` | — | The "confirm your email first" check on writing a post or creating a group. Verification emails are still sent. |
| `feature.multiLanguage` | Per-post language field, reading-languages profile fields | — (language is still stored; defaults to `en`) |
| `feature.googleSignIn` / `feature.appleSignIn` | The button, even if a client id is configured | — (the server enables a verifier when its env var is set; see `SignIn.md`) |
| `feature.images` | The picture row in the form, pictures on cards and details, the image loader | `POST/GET /uploads`, the `image` field on incoming posts (dropped), the share page's image, the upload directory. The privacy and child-safety pages switch wording. [`Images.md`](Images.md) |
| `feature.comments` | The thread on Details, the count on cards | `/posts/{id}/comments`, `/admin/comments`, the `comments` count on posts. [`Comments.md`](Comments.md) |
| `feature.publicGroups` | "Public groups" list and "Anyone can find and join" switch in the groups sheet, a visibility toggle for owners | `GET /groups/public`, `POST /groups/{id}/visibility`, joining by id allowed only for public groups. Off = every group is invite-only. |
| `feature.bookmarks` | "Save for later" in a post's menu, a "Saved" chip in the feed filter | `Bookmark` table, `GET/POST/DELETE /bookmarks[/{postId}]`, `GET /posts?saved=true`. Private, unlike likes. |
| `feature.drafts` | Closing the post form without posting keeps the words; the next "Add" starts from them | On the device only (`AppPreferences.postDraft`, one draft, no image). Cleared on post and on sign-out. |
| `feature.web` | The app in a browser (Kotlin/Wasm): `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` | Adds `wasmJs()` to `shared` and `composeApp`; online only, no SQLite cache; needs `feature.support=false`. Off by default. See [Web.md](Web.md). |
| `feature.desktop` | A JVM desktop app: `./gradlew :composeApp:run` | Adds `jvm("desktop")` to `composeApp`; needs `feature.support=false`. Off by default. See [Desktop.md](Desktop.md). |
| `feature.offlineOutbox` | A post written or edited with no connection is kept, shown under My Posts as "Not sent yet", and sent on the next refresh | Device only: `Outbox` table, `PostRepository.flushOutbox()`. Posts with a new image are not queued (the bytes live in memory). Likes already roll back on failure and are not queued. |
| `feature.follows` | "Following" chip in the feed filter, Follow/Unfollow by tapping a post's author (needs `feature.authors`) | `Follow` table, `GET/POST/DELETE /follows[/{userId}]`, `GET /posts?following=true`. Following never widens what a reader may see. |
| `feature.authors` | Name and avatar on every post and comment, a picture on the profile; hides the "show my name" opt-in | Posts and comments carry `authorName` / `authorPhoto`; avatars are uploads any signed-in reader may fetch; the liked-by roster names everybody. Off = the anonymous default. Child-safety page wording follows. |
| `feature.magicLink` | "Sign in with an emailed link" on the login screen and the link dialog | `POST /auth/magic/request`, `POST /auth/magic`, the `/magic` landing page. Needs the mailer. [`SignIn.md`](SignIn.md#passwordless-sign-in-magic-link) |
| `feature.pushNotifications` | The Activity screen, the bell on Home, device registration | `/notifications*`, `/devices`, the notifier behind likes, comments and group changes, the FCM/APNs senders. [`PushNotifications.md`](PushNotifications.md) |
| `feature.liquidDesign` (default off) | Switches shapes to the rounder liquid set and cards to translucent panes | — |
| `feature.liquidNavBar` (default off) | Replaces the docked tab bar with the floating glass capsule; screens leave room under it | — ([`LiquidDesign.md`](LiquidDesign.md)) |

The admin panel is not feature-flagged: it shows whatever data exists.

### How a flag is wired (and how to add one)

A flag is read at the *entry points* of a feature, not sprinkled everywhere:

- **UI**: the row in Settings, the tab in the bottom bar, the callback handed to
  `PostCard` (`onShare = if (Features.SHARING && …) … else null`), the section
  of a form or a sheet. Grep `Features.` in `composeApp/src/commonMain` for the
  full list — about twenty sites.
- **Server**: the `route(...)` or `post(...)` registration in
  `server/.../Application.kt` is wrapped in `if (Features.X)`.

To add your own, e.g. `feature.polls`:

1. Add one `PosterFeature("polls", …)` entry to the catalogue in
   `buildSrc/src/main/kotlin/PosterFeatures.kt`: title, area, default, what it
   `requires` or `conflicts` with, and what turning it off removes on each
   side. That is the registration: unknown keys fail the build, the constant
   `Features.POLLS` is generated, and `docs/Features.md` is rewritten.
2. Add `feature.polls=true` to `poster.properties` with a one-line comment.
3. Use `Features.POLLS` at the UI entry points, around the Koin bindings only
   that feature needs, and around the server routes; start its tests with
   `if (!Features.POLLS) return@withServer` / `assumeTrue(Features.POLLS)`.

### `feature.support` and the swappable billing source set

Flags remove code, not dependencies. For RevenueCat — a large SDK — that is not
enough, so the three files that import `com.revenuecat.*` live outside
`commonMain`:

```
composeApp/src/billing/enabled/kotlin/…   SupportRepository, SupportViewModel, SupportPaywall (real)
composeApp/src/billing/disabled/kotlin/…  the same three names as inert stubs
```

`composeApp/build.gradle.kts` adds one or the other as a source directory and adds
the `purchases-kmp` dependencies only when enabled. With `feature.support=false`
nothing from RevenueCat is on the classpath. The iOS side pulls
`purchases-ios-spm` through Xcode; remove that package from the project if you
want it gone there too (Xcode cannot read Gradle properties).

This pattern — a real and a stub directory swapped by a property — is the light
version of "feature modules". Use it for any third-party SDK you want to make
optional; plain feature code does not need it, R8 handles that.

## Colours

Two seeds:

```
color.primary=#4F46E5     # buttons, selected chips, the FAB, links on the web pages
color.accent=#F59E0B      # the Like control, the liked-by roster (Material "secondary")
```

Everything else is derived at build time by `buildSrc/src/main/kotlin/PosterPalette.kt`:
all 36 Material 3 roles for the light **and** the dark scheme, the Android
window colour behind the system bars, the iOS accent colour
(`AccentColor.colorset`, rewritten on every build), the web pages' CSS
(landing, account pages, admin panel) and the store-graphics scripts (through
`shared/build/generated/poster/palette.properties`). Change the two lines,
rebuild, and every surface follows.

Optional seeds: `color.tertiary` (tag chips, the Resolved badge; a third hue
derived from the primary if absent) and `color.neutral` (the tint of paper and
cards; the primary hue at low saturation if absent). Any explicit
`color.light.<role>=` / `color.dark.<role>=` overrides one derived value, so a
palette exported from Material Theme Builder can be pasted in whole.

How a role is derived: the seed's hue and saturation at a fixed lightness —
primary at the seed's own lightness in light mode when text reads on it (else
40 %), 80 % in dark mode; containers at 90 % / 30 %; paper at 98 % / 6 %. The
text on each coloured surface is the tone that reads (an amber seed gets dark
text, a blue one white), and if a pair still falls under **4.5:1 the build
fails and names it**, so a colour choice cannot silently make text unreadable.
The maths is HSL rather than Material's HCT, so a very light or very dark seed
may want a role or two overridden by hand.

Which role is used where is documented in
`composeApp/src/commonMain/kotlin/com/example/poster/theme/AppTheme.kt`:

- **primary** family — filled buttons, selected chips, the selected tab pill, the FAB
- **secondary** family — the Like control and the liked-by roster
- **tertiary** family — tag chips and the Resolved badge
- **error** — Delete, Close group
- **background / surface\*** — paper, cards (`surfaceContainerLowest`), nav bar (`surfaceContainer`), dialogs and pills (`surfaceContainerHigh`)
- **outlineVariant** — hairlines and card borders

The placeholder mark and icon in `assets/*.svg` carry the default palette's
hex values; they are yours to replace, or run `scripts/build-brand-assets.sh`
after recolouring them.

## What is *not* here

Values that differ per machine or per environment live elsewhere:

| Where | What |
|---|---|
| `gradle.properties` | public build-time ids: `posterGoogleClientId`, `posterAppleServiceId`; JVM memory |
| `~/.gradle/gradle.properties` | private per-developer values: `posterRevenueCatKey`, `posterKeystore`, `posterKeyAlias` |
| `iosApp/Configuration/Config.xcconfig` | server host/port, Google iOS client id, `POSTER_URL_SCHEME` |
| `iosApp/Configuration/Local.xcconfig` (git-ignored) | `TEAM_ID`, `POSTER_REVENUECAT_KEY`, local server overrides |

Every key across these files and the server, with where to obtain it: [`Credentials.md`](Credentials.md).
| Server environment variables | everything the server needs at runtime — [`Server.md`](Server.md) |
| `keystore.properties` (git-ignored) | Android release signing — [`ReleaseSigning.md`](ReleaseSigning.md) |
