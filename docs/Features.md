# Features

<!-- GENERATED from buildSrc/src/main/kotlin/PosterFeatures.kt by ./gradlew :shared:generatePosterConfig — edit there, not here. -->

Every feature is a `feature.<key>=true|false` line in `poster.properties`. Off means
compiled out: the app loses the screens and the Koin bindings, the server does not
register the routes (a stale client gets 404), and the tests of that feature skip
themselves. The build refuses a combination that cannot work (a flag whose
dependency is off, or two that conflict) and names the pair.

Change a flag, then `./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest`
(or `/toggle-feature` with an AI assistant). `docs/Configuration.md` explains the
mechanism; the per-feature guides are linked from the rows.

| Area | Feature | Flag | Default | Needs | Not with |
|---|---|---|---|---|---|
| Posts and groups | Groups | `feature.groups` | on |  |  |
| Posts and groups | Public groups | `feature.publicGroups` | on | `groups` |  |
| Posts and groups | Post visibility | `feature.postVisibility` | on |  |  |
| Posts and groups | Mark as resolved | `feature.postCompletion` | on |  |  |
| Posts and groups | Tags | `feature.tags` | on |  |  |
| Posts and groups | Images on posts | `feature.images` | on |  |  |
| Posts and groups | Share links | `feature.sharing` | on |  |  |
| Posts and groups | Report a post | `feature.reports` | on |  |  |
| Social | Likes | `feature.likes` | on |  |  |
| Social | Comments | `feature.comments` | on |  |  |
| Social | Authors (names and avatars) | `feature.authors` | on |  |  |
| Social | Follows | `feature.follows` | on | `authors` |  |
| Social | Bookmarks | `feature.bookmarks` | on |  |  |
| Social | Feedback | `feature.feedback` | on |  |  |
| Accounts and sign-in | Passwordless sign-in | `feature.magicLink` | on |  |  |
| Accounts and sign-in | Google sign-in | `feature.googleSignIn` | on |  |  |
| Accounts and sign-in | Apple sign-in | `feature.appleSignIn` | on |  |  |
| Accounts and sign-in | Email verification before writing | `feature.emailVerificationRequired` | on |  |  |
| Accounts and sign-in | Per-post language | `feature.multiLanguage` | on |  |  |
| Notifications | Activity and push | `feature.pushNotifications` | on |  |  |
| Notifications | Post titles in pushes | `feature.pushPostTitles` | on | `pushNotifications` |  |
| Notifications | Daily reminder | `feature.dailyReminder` | on |  |  |
| Offline | Drafts | `feature.drafts` | on |  |  |
| Offline | Offline outbox | `feature.offlineOutbox` | on |  |  |
| Look | Liquid look | `feature.liquidDesign` | off |  |  |
| Look | Native iOS tab bar | `feature.liquidNavBar` | on |  |  |
| Monetisation | In-app purchases (RevenueCat) | `feature.support` | on |  |  |
| Diagnostics | Crash reports | `feature.crashReports` | on |  |  |
| Diagnostics | Diagnostic events | `feature.telemetry` | on |  |  |
| Platforms | Desktop (JVM) target | `feature.desktop` | off |  | `support` |
| Platforms | Web (Kotlin/Wasm) target | `feature.web` | off |  | `support` |

## Posts and groups

| Flag | Off removes from the app | Off removes from the server / build |
|---|---|---|
| `feature.groups` (on by default) | Groups screen and settings section, group picker in the post form, group filter, invite-code field at registration, invite deep links, "joined" announcements | `/groups/*`, `/join/{code}` page Invite-only circles a post can be shared with. `publicGroups` and the group option of `postVisibility` build on it. |
| `feature.publicGroups` (on by default) | "Public groups" list and "Anyone can find and join" switch in the groups sheet, a visibility toggle for owners | `GET /groups/public`, `POST /groups/{id}/visibility`, joining by id allowed only for public groups. Off = every group is invite-only. |
| `feature.postVisibility` (on by default) | The Everyone / Group / Only me picker (every post is public), the default-visibility setting | nothing — visibility is still enforced for stored data The Group option needs `groups`; without it the picker offers Everyone and Only me. |
| `feature.postCompletion` (on by default) | "Mark as resolved" / "Reopen", the Resolved badge | `POST /posts/{id}/complete`, `/reopen` |
| `feature.tags` (on by default) | Tag picker in the post form (and the "at least one tag" rule), tag chips on cards, tag filter | `/tags/*` (tags on posts are still stored and returned) |
| `feature.images` (on by default) | The picture row in the form, pictures on cards and details, the image loader | `POST/GET /uploads`, the `image` field on incoming posts (dropped), the share page's image, the upload directory. The privacy and child-safety pages switch wording. [`Images.md`](Images.md) Avatars (`authors`) need this for the upload; without it profiles have no picture. |
| `feature.sharing` (on by default) | Share action, opening `…://post/TOKEN` links | `POST /posts/{id}/share`, `GET /shared/{token}`, the `/p/{token}` web page |
| `feature.reports` (on by default) | "Report this post" | `POST /posts/{id}/report` |

## Social

| Flag | Off removes from the app | Off removes from the server / build |
|---|---|---|
| `feature.likes` (on by default) | Like button and counts, the "Liked" tab, who-liked roster, "show my name" setting | `/favorites/*` |
| `feature.comments` (on by default) | The thread on Details, the count on cards | `/posts/{id}/comments`, `/admin/comments`, the `comments` count on posts. [`Comments.md`](Comments.md) |
| `feature.authors` (on by default) | Name and avatar on every post and comment, a picture on the profile; hides the "show my name" opt-in | Posts and comments carry `authorName` / `authorPhoto`; avatars are uploads any signed-in reader may fetch; the liked-by roster names everybody. Off = the anonymous default. Child-safety page wording follows. Off keeps the anonymous default: a name shows only where the person opted in. |
| `feature.follows` (on by default) | "Following" chip in the feed filter, Follow/Unfollow by tapping a post's author (needs `feature.authors`) | `Follow` table, `GET/POST/DELETE /follows[/{userId}]`, `GET /posts?following=true`. Following never widens what a reader may see. |
| `feature.bookmarks` (on by default) | "Save for later" in a post's menu, a "Saved" chip in the feed filter | `Bookmark` table, `GET/POST/DELETE /bookmarks[/{postId}]`, `GET /posts?saved=true`. Private, unlike likes. |
| `feature.feedback` (on by default) | The Feedback section and screen | `/feedback` |

## Accounts and sign-in

| Flag | Off removes from the app | Off removes from the server / build |
|---|---|---|
| `feature.magicLink` (on by default) | "Sign in with an emailed link" on the login screen and the link dialog | `POST /auth/magic/request`, `POST /auth/magic`, the `/magic` landing page. Needs the mailer. [`SignIn.md`](SignIn.md#passwordless-sign-in-magic-link) |
| `feature.googleSignIn` (on by default) | The button, even if a client id is configured | — (the server enables a verifier when its env var is set; see `SignIn.md`) |
| `feature.appleSignIn` (on by default) | The Apple button, even if a client id is configured | — (the server enables a verifier when its env var is set; see `SignIn.md`) |
| `feature.emailVerificationRequired` (on by default) | — | The "confirm your email first" check on writing a post or creating a group. Verification emails are still sent. |
| `feature.multiLanguage` (on by default) | Per-post language field, reading-languages profile fields | — (language is still stored; defaults to `en`) |

## Notifications

| Flag | Off removes from the app | Off removes from the server / build |
|---|---|---|
| `feature.pushNotifications` (on by default) | The Activity screen, the bell on Home, device registration | `/notifications*`, `/devices`, the notifier behind likes, comments and group changes, the FCM/APNs senders. [`PushNotifications.md`](PushNotifications.md) |
| `feature.pushPostTitles` (on by default) | Nothing: the app reads the text the server sent | The post's title inside a push body — off, a push says "Somebody liked your post" with no quote A push reaches the phone through Apple or Google, who see its text, and it lands on a lock screen where anybody holding the phone can read it. The title is the author's own words going to the author's own device, so nothing leaks to other people — but on an app about difficult things, a title on a lock screen in front of somebody else is a real moment. Turn it off for a quieter notification that says what happened and nothing about what it was about. |
| `feature.dailyReminder` (on by default) | The reminder rows in Settings | — |

## Offline

| Flag | Off removes from the app | Off removes from the server / build |
|---|---|---|
| `feature.drafts` (on by default) | Closing the post form without posting keeps the words; the next "Add" starts from them | On the device only (`AppPreferences.postDraft`, one draft, no image). Cleared on post and on sign-out. |
| `feature.offlineOutbox` (on by default) | A post written or edited with no connection is kept, shown under My Posts as "Not sent yet", and sent on the next refresh | Device only: `Outbox` table, `PostRepository.flushOutbox()`. Posts with a new image are not queued (the bytes live in memory). Likes already roll back on failure and are not queued. |

## Look

| Flag | Off removes from the app | Off removes from the server / build |
|---|---|---|
| `feature.liquidDesign` (off by default) | Rounder shapes, translucent cards, and a floating capsule tab bar instead of the docked one, all drawn by Compose ([LiquidDesign.md](LiquidDesign.md)) | — |
| `feature.liquidNavBar` (on by default) | iOS uses the system's own tab bar — Liquid Glass on iOS 26, the ordinary bar before it — and the content scrolls behind it. Off, iOS draws the same docked bar as everywhere else ([LiquidDesign.md](LiquidDesign.md)) | — iOS only. It used to give Android, desktop and the browser a floating capsule as well, which is not what those platforms' people expect; that capsule now belongs to `liquidDesign`. |

## Monetisation

| Flag | Off removes from the app | Off removes from the server / build |
|---|---|---|
| `feature.support` (on by default) | The whole Support section; also **drops the RevenueCat SDK from the Android build** (see below) | `/config`, `/support/interest` |

## Diagnostics

| Flag | Off removes from the app | Off removes from the server / build |
|---|---|---|
| `feature.crashReports` (on by default) | Installing the crash handler and uploading stored crashes | `POST /crashes` |
| `feature.telemetry` (on by default) | Diagnostic events (`EventReporter` becomes a no-op) | `POST /events` |

## Platforms

| Flag | Off removes from the app | Off removes from the server / build |
|---|---|---|
| `feature.desktop` (off by default) | A JVM desktop app: `./gradlew :composeApp:run` | Adds `jvm("desktop")` to `composeApp`; needs `feature.support=false`. Off by default. See [Desktop.md](Desktop.md). Off by default: it changes dependency resolution for every build. |
| `feature.web` (off by default) | The app in a browser (Kotlin/Wasm): `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` | Adds `wasmJs()` to `shared` and `composeApp`; online only, no SQLite cache; needs `feature.support=false`. Off by default. See [Web.md](Web.md). Off by default, online only (no SQLite in the browser). |
