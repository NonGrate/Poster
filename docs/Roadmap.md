# Roadmap

What the template does not have yet, in the order it is worth adding. Tiers
are by how much more universal each item makes the template against the work
it costs; effort is an estimate against this codebase. Items marked **done**
shipped after the first version; the rest are notes on where each would go.

## Tier 1 — gaps almost every "posts" app hits

| Feature | Status | Where it goes |
|---|---|---|
| **Comments** | done — `feature.comments`, [`Comments.md`](Comments.md) | `Comment` table, `/posts/{id}/comments`, `CommentsSection` on Details, count on cards, admin page. |
| **Push notifications** with an in-app activity list | done — `feature.pushNotifications`, [`PushNotifications.md`](PushNotifications.md) | `Device` and `Notification` tables, `FcmSender` / `ApnsSender`, `Notifier` behind likes, comments and group changes, `NotificationsScreen` and the Home bell. Not yet: tapping a push opens the post. |
| **Passwordless sign-in** (magic link by email) | done — `feature.magicLink`, [`SignIn.md`](SignIn.md#passwordless-sign-in-magic-link) | `AccountTokens` purpose `MAGIC_LINK` (15 min), `POST /auth/magic/request` + `POST /auth/magic`, the `/magic` page handing off to `…://magic?token=`, `MagicLinkDialog` on the login screen, `MagicDialog` on arrival. Sign-in only: an unknown address gets nothing. |
| **Authors on posts** (name, avatar) | done — `feature.authors` | `Post.authorName/authorPhoto`, `Comment.authorName/authorPhoto` filled by the server; `Avatar` composable on cards, comments and the profile; avatars are uploads (`User.photo`), served to any signed-in reader, replaced files deleted. Off = anonymous, with the liked-by opt-in as before. |
| **Search** | done | `GET /posts?q=` (case-insensitive `instr` over title and message, combined with the tag and group filters and the cursor), `PostFilter.query`, a search field on Home behind the magnifier. FTS5 is the upgrade when a LIKE over the table gets slow. |
| **Store release automation** | done — `.github/workflows/release-android.yml`, `release-ios.yml`, [`ReleaseSigning.md`](ReleaseSigning.md#automated-releases-github-actions) | On a `v*` tag: signed `.aab` to the Play internal track, archived and exported `.ipa` to TestFlight, artifacts kept either way. Plain `xcodebuild`/Gradle plus the `apple-actions` and `upload-google-play` actions; no fastlane. Not exercised here (needs the store accounts). |

## Tier 2 — more product shapes

| Feature | Where it goes |
|---|---|
| **Public / discoverable groups** | done — `feature.publicGroups`: `Groups.visibility` (schema v5), `GET /groups/public` with member counts, join by id for public groups only, owner toggle, the list in the groups sheet, admin create form. |
| **Follow model** | done — `feature.follows`: `Follow` table (schema v6), `/follows` routes, `GET /posts?following=true`, a "Following" chip in the feed filter, Follow/Unfollow from a post's author line. No follower counts or notifications yet; the table has `countFollowers` for the first, `Notifier` is the seam for the second. |
| **Bookmarks** and **drafts** | done — `feature.bookmarks`: `Bookmark` table (schema v8), `/bookmarks` routes, `GET /posts?saved=true`, "Save for later" in the card menu and a "Saved" filter chip. `feature.drafts`: one draft in `AppPreferences`, restored into the next Add. Several named drafts would need a table and a list; the seam is `PostDraft`. |
| **Offline outbox** | done — `feature.offlineOutbox`: adds and edits that fail on the connection wait in an `Outbox` table (schema v9) and go out at the start of the next feed refresh (arrival, pull, periodic). No connectivity listener: the refresh cadence is the retry. Likes keep their rollback; images are not queued. |
| **Desktop (JVM) target** | Add `jvm()` to `composeApp`; Koin, SQLDelight and Ktor all have JVM drivers. Web (Wasm) is a larger step because of the SQLDelight driver. |
| **Large-screen layouts** | Two-pane feed + details on tablets/desktop; `ReadableWidth` is the only adaptation now. |
| **S3-compatible uploads** | A second `UploadStore` implementation behind the same seam, for hosts without a persistent volume. |
| **Postgres / multi-instance** | Repositories in `server/.../model/` are the seam. SQLite, one writer, one instance today. |

## Tier 3 — polish forks usually add by hand

| Feature | Where it goes |
|---|---|
| Dynamic colour (Material You) and a user-chosen accent | `AppTheme.kt`, on top of the `poster.properties` palette. |
| Onboarding and "what's new" | Driven by `AppPreferences` version markers. |
| In-app review prompt, "new version" nudge | `/config` already carries a runtime switch; add `minVersion`. |
| Pluggable analytics / crash sinks | One interface each; the self-hosted intake stays the default, Sentry/Crashlytics as adapters behind flags. |
| Screenshot tests (Roborazzi) in CI | Far cheaper than the instrumented suite for visual regressions. |
| `scripts/add-language.sh`, RTL check | Scaffold `values-xx` from English; the architecture check already enforces parity. |
| Material Symbols | `compose.materialIconsExtended` is deprecated upstream; pinned for now. |

## Deliberately not planned

| Feature | Why |
|---|---|
| **Direct messaging** | Contradicts the shipped child-safety and privacy pages; a fork adding it needs a moderation story first. |
| **Ads** | Same pages promise no advertising identifiers. |
| **More than one image, video, files** | The model is one `Post.image` on purpose ([`Images.md`](Images.md)); several would want a `PostImage` table, video an object store and transcoding. |

| **Blocking / muting**, **web client**, **rate limiting beyond auth/mail**, **analytics**, **runtime feature flags**, **Gradle feature modules** | Reports + admin ban; API is there for a client; login/reset/mail are throttled; only crash reports and fixed diagnostic events; flags are compile-time, `GET /config` is the one runtime switch; splitting into modules was not worth the coupling at this size. |
