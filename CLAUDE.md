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
| A new feature flag | `posterFeatureKeys` in `shared/build.gradle.kts`, a line in `poster.properties`, `Features.X` at the UI entry points and around the server routes |
| A new colour | `poster.properties` (`color.light.*` / `color.dark.*`), nothing else |
| Authors (names, avatars) | `feature.authors`; `Post.withAuthor` in `Application.kt`, `Avatar.kt`, the picture row in `ProfileScreen.kt` |
| Passwordless sign-in | `docs/SignIn.md` § magic link; `AuthService.signInWithMagicLink`, `AccountMail.sendMagicLink`, `AppLinkHandler.MagicDialog` |
| Push / activity | `docs/PushNotifications.md`; `server/.../push/`, `composeApp/.../notification/` (`PushRegistrar`, `push/{enabled,disabled}` for Firebase) |
| Comments | `docs/Comments.md`; `server/.../comments/`, `CommentsSection.kt`, `CommentsViewModel.kt` |
| Anything about post images | `docs/Images.md`; rules in `shared/.../domain/validation/ImageRules.kt`, files in `server/.../uploads/`, UI in `composeApp/.../ui/images/` |
| The liquid look / glass surfaces | `composeApp/.../ui/liquid/` (`liquidGlass`, `LiquidNavBar`), `theme/LiquidShapes.kt`; flags `feature.liquidDesign`, `feature.liquidNavBar` |
| Platform-specific UI behaviour | `composeApp/.../ui/platform/Adaptive.kt` and its `.android.kt` / `.ios.kt` — a closed list, add deliberately |
| Server copy / emails | `server/.../*Page.kt`, `auth/AccountPages.kt`, `auth/GroupPages.kt`, `auth/AccountMail.kt`, `mail/GroupMail.kt` — English and Russian objects side by side |

## Things that look like bugs and are not

- `Post.likes` is not stored on the row; it is counted from `UserPostFavorite`.
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
3. Features to keep — read out the `feature.*` list from `poster.properties`
   with one-line descriptions and ask which to turn off.
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
1. `poster.properties` colours. Keep body-text contrast ≥ 4.5:1; say so if the
   developer's choice does not.
2. Match the primary in `SITE_STYLE` (`LandingPage.kt`) and `simplePage`
   (`AccountPages.kt`) CSS.
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
