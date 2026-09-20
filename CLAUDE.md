# Working on this repository with an AI assistant

Read this first; it saves a round trip. Two parts: how to work here day to day,
and the **adaptation playbook** to run when a developer says "make this template
into my app" (`/adapt-template` in Claude Code).

## What this is

A Kotlin Multiplatform template: `composeApp` (Compose UI, Android + iOS),
`shared` (models, API clients, SQLDelight cache, Koin), `server` (Ktor + SQLite),
`iosApp` (Xcode shell). `docs/Architecture.md` explains the layers;
`docs/Configuration.md` explains `poster.properties`, which generates the
`Features`, `BrandPalette` and `AppInfo` constants everything reads. The
vocabulary is **post** (the thing people write), **group** (an invite-only
circle), **like**.

## Rules the compiler does not enforce (the script does)

`scripts/check-architecture.sh` must pass:

1. No ViewModel takes another ViewModel.
2. `shared/` never references `com.example.poster.ui`.
3. User-visible text is a string resource in **both** `values/strings.xml` and
   `values-ru/strings.xml`, with positional placeholders (`%1$s`, never `%s`).

## Before saying a change is done

```bash
scripts/check-architecture.sh
./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest
```

`/quality-gates` runs these plus the compiles and the flags-off pass. Server
tests use `ServerTestSupport.kt` (`withServer`, `confirmed`, `postPost`,
`guids`); do not write private copies.

If the schema changed: add `shared/src/commonMain/sqldelight/<N>.sqm` where N is
the *current* version — the highest `databases/<N>.db` present (`1.sqm` took
1→2) — run `./gradlew :shared:generatePostDatabaseSchema`, commit the new
`databases/<N+1>.db`.
Never edit an existing `.sqm` or `.db`.

## Where things go

| Change | Files |
|---|---|
| A new user-visible string | `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-ru/` |
| A new screen | `composeApp/.../ui/screens/`, a branch in `ui/MainScreen.kt`, a ViewModel in `di/ViewModelModule.kt` if new |
| A new API endpoint | model in `shared/.../model/`, interface in `shared/.../network/`, Ktor impl in `shared/.../ktor/`, route in `server/.../Application.kt`, test in `server/src/test/` |
| A new field on Post | `.sq` + `.sqm`, `model/Post.kt`, mappers (`PostLocalStore`, server `PostsLocalRepository`), server validation, `PostFormDialog`, `PostCard`, strings, tests — recipe in `docs/Architecture.md` |
| A new feature flag | One entry in `buildSrc/src/main/kotlin/PosterFeatures.kt` (key, default, `requires`/`conflicts`, the two "off removes" texts) — that generates `Features.X`, validates `poster.properties` and rewrites `docs/Features.md`; then `Features.X` at the UI entry points, the Koin bindings and around the server routes |
| Colours | `poster.properties`: `color.primary`, `color.accent` (optional `color.tertiary`, `color.neutral`, or an explicit `color.light.<role>`); the palette, the web CSS, the iOS accent and the scripts' paper colour all derive from them (`buildSrc/PosterPalette.kt`). Never hardcode a hex in a page or a script. |
| Authors (names, avatars) | `feature.authors`; `Post.withAuthor` in `Application.kt`, `Avatar.kt`, the picture row in `ProfileScreen.kt` |
| Passwordless sign-in | `docs/SignIn.md` § magic link; `AuthService.signInWithMagicLink`, `AccountMail.sendMagicLink`, `AppLinkHandler.MagicDialog` |
| Push / activity | `docs/PushNotifications.md`; `server/.../push/`, `composeApp/.../notification/` (`PushRegistrar`, `push/{enabled,disabled}` for Firebase) |
| Comments | `docs/Comments.md`; `server/.../comments/`, `CommentsSection.kt`, `CommentsViewModel.kt` |
| Anything about post images | `docs/Images.md`; rules in `shared/.../domain/validation/ImageRules.kt`, files in `server/.../uploads/`, UI in `composeApp/.../ui/images/` |
| The liquid look / glass surfaces | `composeApp/.../ui/liquid/` (`liquidGlass`, `LiquidNavBar`), `theme/LiquidShapes.kt`; flags `feature.liquidDesign`, `feature.liquidNavBar` |
| Follows, bookmarks, public groups | `docs/Social.md`; `IdSetViewModel.kt` (Follows/Bookmarks view models), `KtorIdSetApi`, the `/follows`, `/bookmarks`, `/groups/public` routes |
| Offline outbox, drafts | `docs/Offline.md`; `PostLocalStore` (outbox queries), `PostRepository.flushOutbox`, `PostDraft` in `AppPreferences`, `MainScreen` (draft restore) |
| Wide screens | `MainScreen.kt`: `BoxWithConstraints`, `TwoPaneMinWidth`, the rail vs bottom bar choice; `ui/components/SideNavigation.kt`; `ReadableWidth.kt` |
| Web | `docs/Web.md`; `composeApp/src/wasmJsMain` (`Main.kt`, `resources/index.html`, actuals), `shared/src/wasmJsMain` (localStorage stores), server `POSTER_WEB_DIR` / `POSTER_WEB_ORIGINS` in `Application.kt` |
| Desktop | `docs/Desktop.md`; `composeApp/src/desktopMain` (`Main.kt`, `Adaptive.desktop.kt`, file-backed storage); `feature.desktop` in `composeApp/build.gradle.kts` |
| Platform-specific UI behaviour | `composeApp/.../ui/platform/Adaptive.kt` and its `.android.kt` / `.ios.kt` / `.desktop.kt` — a closed list, add deliberately |
| Server copy / emails | `server/.../*Page.kt`, `auth/AccountPages.kt`, `auth/GroupPages.kt`, `auth/AccountMail.kt`, `mail/GroupMail.kt` — English and Russian objects side by side |

## Things that look like bugs and are not

- `Post.likes` and `Post.comments` on the row are caches the device keeps; the
  server fills both from `UserPostFavorite` / `Comment` on the way out
  (`withLikeCount`) and ignores what a client sends.
- A flag gates the DI graph too: a view model or API bound only under
  `Features.X` must be injected only behind the same check (see how
  `SettingsScreen` injects `NotificationsViewModel`).
- The whole test suite passes with every flag off; CI runs that matrix. A
  test of a flagged feature returns early (server) or `assumeTrue`s (device).
- The feed omits the reader's own posts and merges them back from `myPosts`.
- Resolved posts leave the feed after 24 h but stay in My Posts and Liked.
- Foreign keys are enforced only on the server (`POSTER_ENFORCE_FOREIGN_KEYS`).
- `/debug/fixtures/*` exists only in development mode.
- `versionCode` comes from `git rev-list --count HEAD`; 1 when there is no history.
- The SQL table for groups is `Groups` (plural) because `GROUP` is an SQLite
  keyword; the Kotlin class is `Group`, the queries object `groupQueries`.
- `TagGroup` is a *tag category*, unrelated to `Group`.

## Do not

- Put secrets, personal emails, team ids or fingerprints in the repository.
- Add a dependency for something a few lines or the standard library covers.
- Rename the `POSTER_*` environment variables or `poster*` Gradle properties
  when adapting the template — they are its vocabulary; the rename script
  changes package, ids, name, scheme and domain, which is what matters.
- Reformat or "improve" files you were not asked to touch.

---

# Adaptation playbook

Run this when the developer wants to turn the template into their app. The
human-readable version is `docs/AdaptationChecklist.md`; this is the same list
as a procedure. Work in the order below; each phase ends in a green build so a
wrong answer is caught early. Report what you did and what needs the
developer's own accounts at the end of each phase.

### Phase 0 — interview (do not write code yet)

Ask, in one message, only what you cannot infer from the request:

1. App name; package (`com.acme.chirp`); URL scheme; public domain (or "none yet").
2. One sentence: what is a post, who are the groups, what does a like mean.
   Derive the three words to use in the UI (e.g. *note / class / star*).
3. Features to keep — read out `docs/Features.md`
   (the catalogue, with one line per feature) and ask which to turn off.
4. Languages: English only, English + Russian, other.
5. Monetisation: none / RevenueCat tips.
6. Colours (or "keep the defaults"): a primary, an accent, a background.
7. Extra fields on a post, if any (name, type, required?).

Defaults if the developer says "you decide": keep every feature on, English +
Russian, RevenueCat off, default colours, no extra fields.

### Phase 1 — identity

1. Require a clean git tree (`git status --porcelain` empty); commit or stash otherwise.
2. `scripts/rename-app.sh --package … --app-id … --name … --scheme … --domain …`
3. Check `poster.properties` (`app.*`) and `iosApp/Configuration/Config.xcconfig`
   (`BUNDLE_ID`, `APP_NAME`, `POSTER_URL_SCHEME`, `POSTER_SERVER_HOST`).
4. `LICENSE` holder if given. Delete `QUESTIONS.md` and `CONVERSION_REPORT.md`.
5. Rewrite the first paragraph of `README.md` for the developer's app; remove
   the "grew out of a shipped app" sentence.
6. Build: `./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest`. Commit: "Rename template to <Name>".

### Phase 2 — features

1. Set every `feature.*` in `poster.properties` as answered.
2. `feature.images` off means no upload directory and the legal pages say so;
   on means `POSTER_UPLOADS_DIR` belongs in the deployment's env and backups.
3. If `feature.support=false`, tell the developer to remove `purchases-ios-spm`
   in Xcode (you cannot); if `feature.groups=false`, also remove the group
   invite intent-filter hosts from `AndroidManifest.xml` only if asked — they
   are harmless. `feature.desktop=true` needs `feature.support=false`.
4. Rebuild. Commit: "Configure features".

### Phase 3 — vocabulary

If the developer's words differ from post / group / like:

1. `values/strings.xml` and `values-ru/strings.xml`: change the *values*, keep
   the *keys*. Do all of them; grep `post`, `group`, `like`, `пост`, `групп`,
   `нравится`. Mind Russian gender and case agreement.
2. `androidMain/res/values*/strings.xml` (reminder texts).
3. Server copy: `LandingPage.kt`, `PrivacyPage.kt`, `TermsPage.kt`,
   `ChildSafetyPage.kt`, `PostSharePage.kt`, `auth/AccountPages.kt`,
   `auth/GroupPages.kt`, `auth/AccountMail.kt`, `mail/GroupMail.kt` — English
   and Russian; resolve every `// Operator:` marker you can, list the rest.
4. Store captions and demo content: `scripts/build-*-screenshots.sh`,
   `build-feature-graphic.sh`, `build-thumbnail.sh`, `seed-demo-data.sh`.
5. Do **not** rename Kotlin identifiers or SQL tables for this; it buys nothing
   and costs a schema migration. Only if the developer explicitly asks,
   proceed as `docs/Renaming.md` describes (script over the tree, regenerate
   the current `databases/<N>.db` baseline, run everything).
6. `scripts/check-architecture.sh`, tests. Commit: "Vocabulary: <words>".

### Phase 4 — tags

1. Replace `server/.../model/CuratedTags.kt` and `shared/.../model/TagGroup.kt`
   with the developer's topics — ten tags per category, both languages, ids
   lowercase snake_case, category ids distinct from tag ids.
2. Grep tests and `seed-demo-data.sh` for tag ids you removed (`health`,
   `wellbeing`, `sleep`, `family`, `work`, `debt`, `books`, …) and remap them.
3. `./gradlew :server:test`. Commit.

### Phase 5 — data model

For each extra field on Post (or User/Group), follow
`docs/Architecture.md` → "Add a field to Post" exactly: `.sq` column with a
default, `<N>.sqm` migration, `generatePostDatabaseSchema`, model property with
default, both mappers, server validation rule in `domain/validation/`, form
field in `PostFormDialog.kt`, display in `PostCard.kt`, strings in both
languages, a server route test and a rules test. Build and commit per field.

### Phase 6 — look

0. Ask whether they want the liquid look (`feature.liquidDesign`) and/or the
   floating glass tab bar (`feature.liquidNavBar`); both default off. Set, rebuild.
1. `poster.properties`: `color.primary` and `color.accent` (and `color.tertiary`
   if they named a third). The build derives the rest and fails on any text
   pair under 4.5:1 — if it does, show the developer the pair and offer a
   nearby seed or an explicit `color.light.<role>` override.
2. The web pages and the store scripts follow the palette on their own;
   nothing to edit there.
3. If artwork was provided: drop SVGs into `assets/`, run
   `scripts/build-brand-assets.sh` and `scripts/build-icons.sh --install`.
   Otherwise tell the developer the placeholder mark is still in place.
4. Commit.

### Phase 7 — accounts, server, stores (mostly the developer's own accounts)

You can prepare; you cannot finish. Produce a short list for the developer:

- Google/Apple ids to obtain and where to paste them (`docs/SignIn.md`).
- Server environment variables to set, from `docs/Server.md`, filtered to the
  features left on.
- GitHub secrets for `deploy-backend.yml`.
- Resend domain, RevenueCat products, store listings.

Where you can verify locally, do: `scripts/run-local-backend.sh`,
`scripts/seed-demo-data.sh --host localhost:8080 --reset`, `curl localhost:8080/health`.

### Phase 8 — quality gates

`scripts/check-architecture.sh`; the three test tasks; `./gradlew
:composeApp:assembleE2eDebug`; if a Mac with a simulator, `./gradlew
:composeApp:linkDebugFrameworkIosSimulatorArm64` and `xcodebuild … -destination
'platform=iOS Simulator,name=<an iPhone>' build`. Instrumented and XCUITest suites
need a device session — offer, do not assume.

### Reporting

End with: what changed (by phase), what is verified (commands + results), what
the developer must do in their own accounts, and any answer from Phase 0 you
had to assume.
