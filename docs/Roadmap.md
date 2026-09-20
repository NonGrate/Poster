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
| **Desktop (JVM) target** | done — `feature.desktop` (off by default) adds `jvm("desktop")`, `composeApp/src/desktopMain` holds the platform pieces (file-backed preferences and session, AWT image picker, clipboard sharing, Material look). Needs `feature.support=false`. |
| **Web (Kotlin/Wasm) target** | done, online only — `feature.web` (off by default) adds `wasmJs()`; `composeApp/src/wasmJsMain` holds the entry point, `index.html` and the browser actuals; `localStorage` for session and preferences; the Ktor server hosts the bundle under `/app` (`POSTER_WEB_DIR`) or allows other origins (`POSTER_WEB_ORIGINS`). The SQLite cache in the browser is the open item (`docs/Web.md`). |
| **Large-screen layouts** | done — from 840dp the post opens beside the list (`MainScreen`, `TwoPaneMinWidth`), the tabs move to a left rail (`SideNavigation`: icons, expandable to icons + titles, remembered), the content is capped at 1280dp instead of 640dp; profile, groups, feedback and notifications keep the full width. No flag: a phone never reaches the breakpoint. |
| **S3-compatible uploads** | A second `UploadStore` implementation behind the same seam, for hosts without a persistent volume. |
| **Postgres / multi-instance** | not planned here — the seam is documented in `docs/Database.md` (six repository interfaces, eight concrete classes that would need one). SQLDelight would need a second, Postgres-dialect schema; a JDBC implementation of the same interfaces is the cheaper port. SQLite, one writer, one instance today. |

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

## Housekeeping the review left for later

Found in the 2026-09 review, not done because each is a larger move than its
payoff today; all are mechanical.

| Item | Where |
|---|---|
| Split `server/.../Application.kt` into route files | done — `posts/`, `groups/`, `accounts/`, `favorites/`, `social/`, `feedback/`, `tags/`, `diagnostics/` route files plus `RouteSupport.kt`; `Application.kt` is wiring. The admin panel is split the same way (`admin/AdminChrome.kt`, `AdminPostsPages.kt`, `AdminTagsPages.kt`, `AdminGroupsPages.kt`). |
| Drop the `{userId}` segment from the `/favorites` paths | done — `POST\|DELETE /favorites/{postId}`, `GET /favorites/check/{postId}`; the liker is the caller. |
| Drop the `Post.likes` column | not doing it — the server ignores it, but the device cache reads it back for the like count offline (like `comments`). |
| Split the largest screens | done — `LoginScreen` → `RegisterForm`, `LoginDialogs`; `GroupsScreen` → `GroupAddSheet`, `GroupDetailPane`; `SettingsScreen` → `SettingsDialogs`, `SettingsSupportSection`; `MainScreen` → `MainInvites`, `MainPostForm`; `HomeScreen` → `HomeDialogs`, `FeedTopBar`. `PostsViewModel` stays one class (its remaining state is shared by every method). |
| A `FeedQuery` value for the feed parameters | `PostApi.getPostPage` has nine parameters repeated in three places; worth it with the next filter. |
| `PeriodicRefreshTest` on virtual time | The 400 ms negative assertion needs `mainClock` control; the test runs on the iOS simulator only. |

## Deliberately not planned

| Feature | Why |
|---|---|
| **Direct messaging** | Contradicts the shipped child-safety and privacy pages; a fork adding it needs a moderation story first. |
| **Ads** | Same pages promise no advertising identifiers. |
| **More than one image, video, files** | The model is one `Post.image` on purpose ([`Images.md`](Images.md)); several would want a `PostImage` table, video an object store and transcoding. |

