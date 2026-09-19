# Conversion report: Share-Pray → Poster template

What was analysed, what was done, what was verified, what was not. Written for
the maintainer; delete before publishing if you prefer.

## Analysis of the source

Share-Pray at the time of copying: 1,160 tracked files, ~43k lines of Kotlin in
three modules plus an Xcode project, 456 commits. About 5,000 occurrences of
"pray*" across 366 files. Worth keeping, feature by feature:

| Kept as a generic feature | Prayer-specific, removed or rewritten |
|---|---|
| Posts (CRUD, visibility, language, tags, paging, offline cache) | The words: prayer/pray/praying/answered in code, strings, tests, scripts |
| Likes ("Pray" button, praying roster) → Like / liked-by roster | Curated tags (health/faith/hard things…) and their six groups |
| Communities, invites, roles | Landing/privacy/terms/child-safety copy, email copy, share page copy |
| Google + Apple sign-in, account merge | Brand mark (three circles), launcher icons, empty-state illustrations |
| RevenueCat support tiers | Demo seed content, store captions and taglines |
| Server, admin panel, moderation, crash/event intake, Telegram, Resend | Identity: `kg.nongrate.share_pray`, `sharepray.app`, `sharepray://`, `SHARE_PRAY_*` |
| Screenshot/store-asset/icon tooling, CI, deploy | Personal data: Google client ids, Apple team id, RevenueCat key, emails, fingerprints, Proxmox deploy task, hosting specifics |
| Tests (server 355, shared, ViewModel, 50 instrumented, 7 iOS UI) | Project history: PLAN/BUGS/COVERAGE/STRINGS_REVIEW/design docs, QA reports, screenshots, `store/`, 15 DB migrations and 3 committed `.db` files with real users |

## What was done

### 1. Copy (excluding data and history)

`git ls-files` minus: `.kotlin/`, `screenshots/`, `QA-reports/`, `store/`,
`design_handoff_*`, `backups/`, all `*.db`, the old `docs/` and root `*.md`, the
prayer artwork, Xcode `xcuserdata`, the untracked `Local.xcconfig` (held a
RevenueCat key), all `.DS_Store`. 432 files, 6 MB. Permissions normalised
(the source had 700 on everything).

### 2. Mechanical rename

One Python script, ordered longest-match-first, over contents and paths:

- `kg.nongrate.share_pray` / `kg.nongrate.sharepray` → `com.example.poster`;
  source directories moved to `com/example/poster`.
- `SHARE_PRAY_*` / `SHAREPRAY_*` → `POSTER_*`; `sharepray*` Gradle props → `poster*`;
  `sharepray://` → `poster://`; `sharepray.app` → `poster.example.com`;
  `sharepray.composeapp.generated.resources` → `poster.composeapp…`;
  `Share Pray`/`SharePray` → `Poster`.
- `Prayer(s)` → `Post(s)`, `pray` → `like`, `Praying` → `Liked`, `PrayingList`/`PrayingPerson`
  → `LikerList`/`Liker`, string keys (`prayer_pray` → `post_like`, `praying_*` → `likers_*`, …),
  phrase fixes ("praying for" → "liking"), then a hand pass over the awkward
  leftovers (`likingCount` → `likeCount`, "who is liking" → "who liked",
  `AnsweredBadge` → `ResolvedBadge`, test tags).
- SQL tables `Prayer`, `PrayerTag`, `UserPrayerFavorite`, `PrayerReport` →
  `Post…`; the database class `PrayerDatabase` → `PostDatabase`.
- Second pass (on request): `Community` → `Group` across code, SQL, strings
  (en + ru with gender fixes), tests, scripts and docs. Kotlin `Group`,
  `GroupsScreen`, `GroupViewModel`, `/groups` routes, `feature.groups`,
  visibility value `group`; SQL table `Groups` (keyword clash), membership
  `UserGroup`, `GroupInvite`, Post column `groupId`. Baseline `1.db` regenerated.
- Personal identifiers blanked or replaced (client ids, `DEVELOPMENT_TEAM`,
  `@arsenii.net` → `@example.com`, contact email, copyright name → env var).

### 3. Hand rewrites

- `strings.xml` (en, ru): all 268 entries re-worded for a posts app; every key kept.
- `CuratedTags.kt` + `TagGroup.kt`: 7 generic categories × 10 tags, en/ru. Tests and
  scripts remapped (`healing`→`wellbeing`, `body_and_mind`→`health_wellbeing`, `family_and_people`→`people`, …).
- Server pages (`LandingPage`, `PrivacyPage`, `TermsPage`, `ChildSafetyPage`,
  `PostSharePage`, `AccountPages`, `GroupPages`), emails (`AccountMail`,
  `GroupMail`), admin titles: generic copy, app name from `AppInfo.NAME`,
  deep links from `AppInfo.SCHEME`, origin from `AppInfo.WEB_ORIGIN`, "Operator:"
  markers for values that are yours.
- Fixtures/previews: `church-a` → `group-a`, `Youth Group` → `Book Club`, "Pastor John" → "Ada Lovelace".
- `seed-demo-data.sh` content, store captions, feature-graphic/thumbnail taglines.
- Artwork: new placeholder mark (`assets/poster-mark*.svg`), launcher icon SVGs,
  regenerated PNGs (mark, dark mark, three empty states, all mipmaps, Play icon).

### 4. Configuration layer (new)

- `poster.properties` at the root: `app.*`, 16 `feature.*` flags, full light/dark
  Material palette.
- `shared/build.gradle.kts`: `generatePosterConfig` task → `Features` (const),
  `BrandPalette` (const Long ARGB), `AppInfo`; unknown keys fail the build.
- `composeApp/build.gradle.kts`: manifest placeholders for scheme/host,
  generated `colors.xml`/`values-night/colors.xml` via the AGP variant API,
  swappable billing source set + conditional RevenueCat dependency,
  `versionCode` falls back to 1 without git history.
- `AppTheme.kt` builds both schemes from `BrandPalette`; `InviteLink` reads
  `AppInfo`; the Android manifest and iOS `Config.xcconfig`
  (`POSTER_URL_SCHEME`) carry the scheme.
- Flags wired at ~20 UI entry points (MainScreen tabs and form, SettingsScreen
  sections, PostCard controls, Home/Details/MyPosts callbacks, PostFormDialog
  sections, LoginScreen provider buttons and registration fields,
  FeedFilterSheet sections, ProfileScreen language fields, telemetry/crash
  installers) and around the server route groups in `Application.kt`.
- `composeApp/src/billing/enabled` (moved real files) and `…/disabled` (stubs).

### 5. Database

Migrations `1.sqm`–`15.sqm` and `databases/*.db` deleted; `1.db` regenerated
from the current `.sq` files. JVM `DatabaseDriverFactory` simplified (kept:
versioned stepping, pre-migration `VACUUM INTO` snapshot, foreign-key switch;
removed: pre-versioning column patch-ups). `SchemaMigrationTest` made
data-driven over recorded baselines.

### 6. Cleanup

Deleted: unused `integrated_backend/` fake API, `CachedPostApi`, `Greeting.kt`,
the Proxmox `deployServer` task, the old workflow (replaced by `ci.yml` and a
generic `deploy-backend.yml`), the GHCR-specific root `Dockerfile` (now a
documented `FROM ${SOURCE_IMAGE}`).

### 7. New scripts

`rename-app.sh` (package/app id/name/scheme/domain over the whole tree),
`doctor.sh` (environment check), `dev-tunnel.sh` (adb reverse / LAN / cloudflared
or ngrok), `build-brand-assets.sh` (mark and empty-state PNGs from SVG).

### 8. Documentation (all new)

`README.md`; `docs/`: GettingStarted, Configuration, Renaming, Architecture,
Database, Server, SignIn, InAppPurchases, Email, Deployment, ReleaseSigning,
Testing, Screenshots, StoreListing, Localization, Roadmap; `CONTRIBUTING.md`,
`CLAUDE.md`, `LICENSE`, `QUESTIONS.md`, this file.

### 9. Review round (after the Group rename)

Exercised rather than read: a live server smoke test (start from
`installDist`, seed, sign in, read the feed/groups/tags, fetch every web page,
admin login), a full `scripts/rename-app.sh` dry run on a scratch copy followed
by a build, and the Android instrumented suite class by class on an API 35
emulator. Found and fixed:

- `AppLink.kt` and `AndroidAppleCredentials.kt` still hard-coded the `poster`
  scheme; the privacy page hard-coded the domain. All read `AppInfo` now — a
  renamed app's share/verify/Apple-callback deep links would otherwise have
  silently stopped parsing.
- `rename-app.sh` rewrote itself while bash was still reading it (syntax error
  at the end of a run). It now skips its own file.
- Server tests asserted the literal app name; they use `AppInfo.NAME`, so a
  renamed fork's tests stay green.
- Instrumented tests asserted five UI strings that were re-worded.
- Dead "Google sign-in coming soon" path (button, string, `GoogleComingSoon`)
  removed — native iOS sign-in made it unreachable.
- Added `SECURITY.md`, `.github/dependabot.yml` (Gradle + Actions, grouped
  Kotlin/Compose/AGP bumps), a PR template and a bug issue template.

Running the instrumented suite class by class (it had not been run since the
copy) surfaced three problems that are not rename artefacts and existed in the
parent as well; all three are fixed here:

- **Tag catalogue empty in the feed filter.** `TagViewModel` is a Koin
  singleton created with the main screen, before sign-in, so its first
  `GET /tags` is refused (401) and nothing reloaded it until My Posts opened.
  The filter sheet therefore showed categories with no tags. `HomeScreen` now
  asks for the catalogue once it is on screen, and a refresh updates the
  catalogue too, not only the suggestions.
- **Fixtures never created invite rows.** Joining spends a `GroupInvite`;
  the fixture only set the code on the group, so every "join by code" flow
  answered "not valid any more" on a fresh database. `DebugFixtures` seeds one
  invite per fixture group.
- **Test wrapper hid the real failure.** `performLogout` caught `Exception`,
  but Compose's `ComposeTimeoutException` extends `Throwable`, so the UI
  sign-out never fell back to the programmatic one and a failed test's `after`
  timeout replaced its own error. Both fixed in `TestUtils`; failing tests now
  report what actually broke.

Test-only corrections in the same pass: stale tag-category ids
(`body_and_mind`, `family_and_people`, …) mapped to the new catalogue — which
was split into *Health and wellbeing* and *Family and people* so the
"opening one category hides the other's tags" assertions still hold; the dark/
light theme tests rewritten for the follow-system switch + selector that the
screen actually has (`dark_mode_toggle` never existed); join-by-code opens the
"Join or create" sheet first; the leave-group feed test seeds a public post,
because `refreshPosts` deliberately keeps a stale feed when the server returns
an empty page; two races given an explicit wait.

### 10. Images and liquid design (second request)

**Images** (`feature.images`, on): schema version 2 (`1.sqm` adds `Post.image`,
`databases/2.db`), `ImageRules` shared by form and server, `POST /uploads` /
`GET /uploads/{id}` behind bearer auth with the post's visibility rule, files
under `POSTER_UPLOADS_DIR` with random ids and an orphan sweep at start,
deletion with the post / replacement / account, the share page's public image,
the photo picker on both platforms (Android Photo Picker, iOS PHPicker; no
permissions; downscale to 1600 px JPEG on the device), `PostPhotoRow` in the
form, `PostImage` on cards (cropped band) and details (own proportions), an
authenticated in-memory image loader, privacy/child-safety wording that follows
the flag, backup script tars the directory, Docker env. Tests:
`UploadRoutesTest` (6), `UploadStoreTest` (3), `ChildSafetyPageTest` updated.
`docs/Images.md`.

While doing it: the migration numbering in the docs was wrong (SQLDelight's
`N.sqm` migrates *from* N), corrected in `Database.md`, `Architecture.md`,
`CLAUDE.md`.

**Liquid design** (`feature.liquidDesign`, `feature.liquidNavBar`, both off):
`ui/liquid/LiquidGlass.kt` records the screen content into a `GraphicsLayer`
and glass surfaces redraw it blurred and tinted (Compose has no backdrop
filter; no library added); `LiquidNavBar` is a floating capsule with a sliding
lens, same test tags as the docked bar; `LocalBottomBarInset` gives lists room
under it; `LiquidShapes` and translucent cards under `liquidDesign`.
`docs/LiquidDesign.md` explains the mechanism, the Android <12 fallback and
why it is not the native iOS 26 bar.

**Instrumented suite, second pass** on a Google-APIs (no Play) emulator with
hardware GPU: the previous emulator was starved (software GL under memory
pressure, load average 30–50, minutes between requests) and produced the
"1 minute test timeout" failures. Real fixes from this pass: the feed filter
sheet did not scroll (tags of an open category unreachable on a short screen);
tests now scroll chips into view; the "clear one thing" filter test asserted a
behaviour the app never had (removing the last tag leaves the category) and
now asserts what it does; one test looked in the merged semantics tree.
`docs/Testing.md` records the emulator lessons (image, GPU, English locale,
`ANDROID_SERIAL`, scratch copy).

### 11. Tier 1 of the roadmap (third request)

`docs/Roadmap.md` was rewritten as tiers; Tier 1 shipped in full, each item behind
its own flag with server tests:

- **Comments** (`feature.comments`) — schema v3, `/posts/{id}/comments`,
  thread and composer on Details, count chip on cards, admin page with
  hide/restore, child-safety wording. 5 server tests, 1 instrumented test.
- **Push notifications + activity list** (`feature.pushNotifications`) —
  schema v4 (`Device`, `Notification`), `FcmSender` (HTTP v1) and `ApnsSender`
  (ES256, HTTP/2 via the JDK client) with a logging fallback, `Notifier` behind
  likes, comments, invite joins and admin adds, `/notifications*` and
  `/devices`, Android via Firebase from four `gradle.properties` values (no
  `google-services.json`, swappable source set like billing), iOS via a Swift
  `AppDelegate`, `PushRegistrar` following the session, Activity screen and a
  bell on Home. 8 server tests.
- **Passwordless sign-in** (`feature.magicLink`) — 15-minute single-use link,
  `/auth/magic/request` + `/auth/magic`, `/magic` landing page, `AppLink.Magic`,
  dialogs on the login screen and on arrival; a new address gets an account
  named after it. 4 server tests.
- **Authors** (`feature.authors`, on) — `authorName`/`authorPhoto` on posts and
  comments, `Avatar` composable, profile picture through the upload store,
  liked-by roster names everybody, the opt-in hidden. 2 server tests; one
  existing roster test made flag-aware.
- **Search** — `GET /posts?q=` over title and message alongside tags, groups and
  the cursor; `PostFilter.query`; a search field on Home. 2 server tests.
- **Store release automation** — `release-android.yml` (Play internal track)
  and `release-ios.yml` (TestFlight) on `v*` tags, `ExportOptions.plist`,
  secrets documented in `ReleaseSigning.md`. Not exercised (needs store accounts).

Verified for this round: server 385 tests green, shared 72, app unit 58;
Android and iOS compile; `check-architecture.sh` 4/4; Xcode build; emulator
classes Login, Feed, Comments, Settings, Favourites, MyPosts, TagFilter green on
the final tree (MyPosts and Feed only on a freshly booted, settled emulator —
the same emulator crashed its own system process under load in between; the
instrumented test libraries were aligned to Compose 1.12 on the way). Not verified: a real push through FCM/APNs, a real magic-link
email (the link is printed by the logging mailer), the release workflows.

### 12. Decisions applied (2026-09-19)

- Images stay on local disk (no S3 seam beyond `UploadStore`).
- **Native iOS tab bar**: with `feature.liquidNavBar` on, `ContentView.swift`
  builds a SwiftUI `TabView` (the system's Liquid Glass bar on iOS 26) with one
  Compose view controller per tab (`IosTabs.kt`, `App(fixedTab)`); the Home
  instance alone handles links, invites and pushes; "browse the feed" and
  notification taps switch tabs through `NativeTabs.switcher`. Android keeps
  the Compose floating bar. Verified: compiles on both platforms, Xcode build;
  not yet seen running on a simulator with the flag on.
- Authors on by default confirmed.
- **Magic link is sign-in only**: `accountForMagicLink` is a lookup; an
  unknown address gets nothing and the same 204. Test and docs updated.
- Push senders and release workflows kept as they are, pending real accounts.

### 13. Tier 2, item 1: public groups (2026-09-19)

Behind `feature.publicGroups` (on by default):

- Schema v5: `Groups.visibility` (`private` | `public`, default private),
  migration `4.sqm`, `databases/5.db`.
- Server: `GET /groups/public` (name, visibility, member count),
  `POST /groups/{id}/visibility` (owner only, `private`/`public` or 400),
  `POST /groups/create` takes an optional `visibility`, and `/groups/join` by
  id succeeds only for public groups. Admin create form and detail page show
  the visibility. `PublicGroupsTest` covers listing, join-by-id, the private
  refusal and the owner-only toggle.
- App: a "Public groups" section in the join/create sheet with member counts
  and a Join button, an "Anyone can find and join" switch when creating, and
  a visibility switch in the owner's manage panel. Everything is compiled out
  when the flag is off (every group stays invite-only).
- Fixture `book-club` is public so demos and tests have one to browse.

### 14. Tier 2, item 2: follows (2026-09-19)

Behind `feature.follows` (on by default; the UI needs `feature.authors`):

- Schema v6: `Follow(follower, followed, created_at)`, migration `5.sqm`,
  `databases/6.db`.
- Server: `GET /follows` (ids), `POST /follows/{userId}` (400 for yourself,
  404 for nobody), `DELETE /follows/{userId}`; `GET /posts?following=true`
  adds one clause to the feed query, so a follow never widens what a
  reader may see. `FollowRoutesTest` covers the feed narrowing, the private
  post staying private, unfollow and the refusals.
- App: tapping the author line on a card opens Follow/Unfollow; the feed
  filter sheet gets a "Following" chip and the applied-filter row shows it.
  `FollowsViewModel` keeps the followed set (optimistic toggle, reverted on
  failure). `FollowsInstrumentedTest` walks follow → filter → unfollow.
- Not done on purpose: follower counts and "X followed you" notifications.
  `countFollowers` exists in `Follow.sq`; `Notifier` is the seam.
- Found by `FollowsInstrumentedTest` and fixed: the device cache dropped the
  author name and photo and the comment count, so once the feed came from
  disk (which is always, after the first page) cards had no author line and
  no comment badge. Schema v7 caches `Post.comments`; the author is a stub
  `User` row on the device (`cacheAuthor` / `updateAuthor`).
- Emulator runs of `FollowsInstrumentedTest`, `CommentsInstrumentedTest`, `FeedInstrumentedTest` and `TagFilterInstrumentedTest` on this tree were
  aborted twice by host load (other applications at 100% CPU starved the
  emulator into ANRs at login). Host verification is green; the four classes
  are to be re-run on a quiet machine.

### 15. Tier 2, item 3: bookmarks and drafts (2026-09-19)

- `feature.bookmarks`: `Bookmark` table (schema v8), `GET/POST/DELETE
  /bookmarks[/{postId}]` (saving what you cannot see is 404),
  `GET /posts?saved=true` as one more clause in the feed query; "Save for
  later" / "Remove from saved" in the card menu, a "Saved" chip in the feed
  filter. `BookmarkRoutesTest`. Follows and bookmarks share one small base,
  `IdSetViewModel` (a server-kept set of ids, toggled optimistically).
- `feature.drafts`: one `PostDraft` in `AppPreferences`, written when the
  post form closes without posting, restored into the next "Add", cleared on
  post and on sign-out. No image (the bytes live in memory). Several named
  drafts would need a table and a list. `DraftsInstrumentedTest` (not yet
  run on the emulator, see above).

### 16. Tier 2, item 4: offline outbox (2026-09-19)

`feature.offlineOutbox`: an add or edit that fails on the connection is
kept in an `Outbox` table (schema v9, device only) as JSON, shown under My
Posts with a "Not sent yet" pill, and sent at the start of the next feed
refresh (arrival, pull, periodic). An edit of a post the server has never
seen stays a single add. Refusals that are not the connection are not
queued (retrying cannot change them); posts with a new image are not
queued (the bytes are in memory). No connectivity listener: the refresh
cadence is the retry. `OutboxTest` covers queue, edit-while-offline, flush
and the non-connection refusal.

### 17. Tier 2, item 5: desktop (JVM) target (2026-09-19)

`feature.desktop` (off by default) adds `jvm("desktop")` to `composeApp`.
`composeApp/src/desktopMain` holds the actuals for the seven platform files
(the Material look copied from Android minus the Android-only bits, an AWT
image picker scaled with ImageIO, clipboard sharing, no notifications), a
`main()` that builds the same Koin graph as the phones, and file-backed
preferences and session under `~/.poster` (the JVM defaults are in-memory,
for the server's tests). The build refuses `desktop=true` with
`support=true`: RevenueCat has no desktop SDK. Verified: compiles with the
flags flipped; started once under an isolated `HOME`. Docs: `docs/Desktop.md`.

### 18. Tier 2, items 6 and 7: wide screens, Postgres seam (2026-09-19)

- From 840dp of width the post opens beside the list (`MainScreen`:
  `BoxWithConstraints`, `TwoPaneMinWidth`, a `detail_pane` with an empty
  state), and the readable cap grows from 640dp to 1280dp. Profile, groups,
  feedback and notifications keep the whole width. No flag: a phone never
  reaches the breakpoint. Verified by compiling and by the desktop run;
  not yet seen on a tablet emulator.
- Postgres is documented rather than built: `docs/Database.md` now lists the
  six repository interfaces and the nine concrete classes a port would give
  one, and the first step (one database handed out by `module()` instead of
  fourteen `DatabaseManager(DatabaseDriverFactory())` constructions). That
  is also a review item, see below.

## Verified

(Last full pass on 2026-09-18, after images and liquid design.)

- `scripts/check-architecture.sh` — 4/4 pass.
- `./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest` —
  green: server 364, shared 72, composeApp 58 tests.
- With `feature.images=false`, `feature.liquidDesign=true`, `feature.liquidNavBar=true`:
  shared tests, server test compile, `:composeApp:assembleE2eDebug` — green.
- `xcodebuild -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17' build`
  — BUILD SUCCEEDED with the PhotosUI picker interop and the glass layer.
- Android instrumented suite, **all 18 classes green on the final tree**
  (2026-09-18, AVD `sharepray_light`: Google APIs, API 31, `-gpu host`, English
  locale, local backend from `installDist`): Authentication, CoreIsolation,
  DailyReminderNotification, Favourites, Feed, FeedOffer, GroupAdded,
  GroupAdmin, InviteLink, Language, LoginScreen, MyPosts, PullToRefresh,
  Settings, Share, TagFilter, Tags, Verification. Test-only changes in the
  last pass: form and group controls are scrolled into view before tapping
  (the picture row made the form longer), the "leave group" tests open the
  row's overflow menu where *Leave* actually lives, one test reads the
  unmerged semantics tree.
- With `feature.liquidDesign=true` + `feature.liquidNavBar=true` on the same
  emulator: LoginScreen, Feed, MyPosts classes green with no idle timeouts
  (the first version of the glass layer wrote state on every draw and kept
  Compose busy; it is modifier-node based now). The bar and translucent cards
  were checked visually from screenshots; the blur under scrolling content was
  not, because the fixture feed is one card tall.
- `./gradlew :composeApp:assembleE2eDebug :composeApp:assembleRemoteDebug` —
  both APKs build.
- With **every feature flag set to false** (`sed` over `poster.properties`,
  then restored): `:shared:jvmTest`, `:server:compileTestKotlin` and
  `:composeApp:compileE2eDebugKotlinAndroid` — all green. The toggles hold.
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` — links.
- `xcodebuild -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17' build`
  — **BUILD SUCCEEDED** (Swift shell, SPM packages, Compose framework). Note:
  `generic/platform=iOS Simulator` fails with "Unknown iOS simulator arch:
  x86_64" because the project builds Apple-Silicon simulators only, as its
  parent did; target a named simulator.
- Leftover audit: no `pray`, `nongrate`, `sharepray`, personal emails, team ids
  or client ids remain outside test fixtures that use `somebody@gmail.com` for
  Gmail-normalisation tests.

## Not verified (needs a device session or accounts)

- The iOS XCUITest suite: renamed mechanically, fixture ids remapped, not executed.
- The photo picker itself (a system UI on both platforms) and an image
  round-trip through the app: covered by server tests and a compile of both
  platforms, not by an instrumented run.
- Screenshot scripts end to end (need an emulator/simulator run).
- Anything requiring real Google/Apple/RevenueCat/Resend accounts.
- Store-image composition scripts were only syntax-checked.

## Known rough edges

- A handful of comments still read slightly oddly after the pray→like rewrite
  (e.g. "the person liking"); harmless, grep `\bliking\b` to find them.
- `docs/` describes the intended contract; the admin panel's own labels
  ("answered" for a resolved post) were left as they were.
- The iOS `Package.resolved` still pins `purchases-ios-spm` and GoogleSignIn;
  with `feature.support=false` the RevenueCat package must be removed in Xcode
  by hand (documented).
