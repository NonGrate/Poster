# Adaptation checklist

Everything a developer changes, adds or removes to turn this template into
their own app, in the order that avoids redoing work. Tick as you go. Each item
says *where* and links the doc that says *how*. The same list, written for an
AI assistant to execute, is in [`CLAUDE.md`](../CLAUDE.md) (`/adapt-template`).

Legend: **must** — every app needs it · *should* — most apps · optional.

## 0. Decide before touching code

- [ ] **must** The one-line description of your app. What is a "post" in it (a note, a
  request, a listing, a question)? Who are the "groups" (families, classes, teams)?
  What does "like" mean? Write it at the top of `README.md`.
- [ ] **must** Name, package (`com.acme.chirp`), application/bundle id, URL scheme
  (`chirp`), public domain (`chirp.acme.com`).
- [ ] **must** Which features stay on. Go down the `feature.*` list in
  `poster.properties` with the description of each in [`Configuration.md`](Configuration.md).
- [ ] *should* Languages: keep English only, keep English + Russian, or add another
  ([`Localization.md`](Localization.md)).
- [ ] *should* Monetisation: none, tips/subscription via RevenueCat, or something else.
- [ ] optional Brand colours (a primary and an accent; a tertiary if you have one)
  and a mark.

## 1. Identity (one script, one commit)

- [ ] **must** `scripts/rename-app.sh --package … --app-id … --name … --scheme … --domain …`
  on a clean tree, then `git diff --stat`, build, commit. [`Renaming.md`](Renaming.md)
- [ ] **must** `poster.properties` → `app.name`, `app.scheme`, `app.webOrigin` (the
  script sets them; check).
- [ ] **must** `iosApp/Configuration/Config.xcconfig` → `BUNDLE_ID`, `APP_NAME`,
  `POSTER_URL_SCHEME`, server host/port (the script sets the first three; set
  the server host to your domain).
- [ ] *should* `LICENSE` copyright holder; `POSTER_OWNER_NAME` on the server for the © line.
- [ ] *should* `README.md`: replace the template's description with yours, delete
  the "grew out of" paragraph, keep the docs index.

## 2. Features and configuration

- [ ] **must** `poster.properties` → set every `feature.*` you decided on. Rebuild;
  disabled features are compiled out of app and server.
- [ ] *should* If `feature.support=false`: also remove the `purchases-ios-spm`
  package in Xcode (Gradle cannot). [`InAppPurchases.md`](InAppPurchases.md)
- [ ] *should* `feature.images`: keep one picture per post, or off. On: decide
  where `POSTER_UPLOADS_DIR` lives and that it is backed up. [`Images.md`](Images.md)
- [ ] *should* Limits that are product decisions, in `shared/.../domain/validation/`:
  `PostRules` (title 120 / message 2000 chars), `TagRules` (5 tags per post,
  filter limit), `GroupRules` (name length), `AccountRules` (name/password
  rules); `GROUPS_PER_PERSON` (5) and `DEFAULT_FEED_LIMIT` in server
  `Application.kt`; `RESOLVED_VISIBLE_FOR` (24 h) in `ui/screens/FeedRules.kt`;
  `AppPreferences.DEFAULT_REMINDER_MINUTES` (21:00).
- [ ] optional Access/refresh token lifetimes in `server/.../auth/AuthConfig.kt`.

## 3. Vocabulary and text

- [ ] **must** `composeApp/src/commonMain/composeResources/values/strings.xml`
  (+ `values-ru/`): read every string once. Rename "post", "group", "like"
  to your words; delete the entries for features you turned off if you like
  (unused strings are harmless). Run `scripts/check-architecture.sh`.
- [ ] **must** `composeApp/src/androidMain/res/values*/strings.xml` — app name and
  the reminder notification text.
- [ ] *should* Empty states, onboarding tone: `empty_*`, `visibility_*_hint`,
  `settings_support_body`, `support_paywall_body`, `report_body`.
- [ ] *should* Server web pages and emails — `server/.../LandingPage.kt`
  (tagline, "how it works"), `PrivacyPage.kt`, `TermsPage.kt`, `ChildSafetyPage.kt`
  (**read these; they describe the template's behaviour and must stay true**),
  `PostSharePage.kt`, `auth/AccountPages.kt`, `auth/GroupPages.kt`,
  `auth/AccountMail.kt`, `mail/GroupMail.kt`. Fill every `// Operator:` marker.
- [ ] *should* Tag catalogue: `server/.../model/CuratedTags.kt` and
  `shared/.../model/TagGroup.kt` — your topics, ten per category, both languages.
  Instrumented tests and `scripts/seed-demo-data.sh` reference `health`,
  `wellbeing`, `sleep`, `family`, `work`, `debt`, `books` — grep before removing those.
- [ ] optional Demo content in `scripts/seed-demo-data.sh` (names, groups, posts)
  and store captions in `scripts/build-store-screenshots.sh`,
  `build-ios-store-screenshots.sh`, taglines in `build-feature-graphic.sh`,
  `build-thumbnail.sh`.

## 4. Data model — what a post *is* in your app

- [ ] *should* Add/remove fields on `Post` (`shared/.../model/Post.kt`) following the
  end-to-end recipe in [`Architecture.md`](Architecture.md#add-a-field-to-post-end-to-end):
  `.sq` + migration, model, mappers, server validation, form, card, strings, tests.
- [ ] optional Same for `User` (e.g. a display name, avatar URL) and `Group`.
- [ ] optional Visibility model: keep everyone/group/only-me, or simplify with
  `feature.postVisibility=false`.
- [ ] optional Anything you removed from the model: delete its column via a
  migration rather than leaving it dead. [`Database.md`](Database.md)

## 5. Look

- [ ] optional The liquid look: `feature.liquidDesign` (rounder shapes, translucent
  cards) and/or `feature.liquidNavBar` (floating glass tab bar). [`LiquidDesign.md`](LiquidDesign.md)
- [ ] *should* `poster.properties` → `color.primary` and `color.accent` (optional
  `color.tertiary`, `color.neutral`). The build derives the light and dark
  palette, the web pages, the iOS accent and the store graphics from them and
  fails on any text pair under 4.5:1 (`docs/Configuration.md#colours`).
- [ ] *should* Mark: replace `assets/poster-mark.svg` + `poster-mark-dark.svg`, run
  `scripts/build-brand-assets.sh`. Launcher: replace `assets/icon-*.svg`, run
  `scripts/build-icons.sh --install`. iOS icon: drop a 1024² PNG into
  `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/`.
- [ ] optional Empty-state illustrations (560×420) over the generated
  `composeResources/drawable/empty_*.png`.
- [ ] optional Typography and shapes: `theme/AppTheme.android.kt`, `AppTheme.ios.kt`
  (`platformTypography`, `platformShapes`); Roboto in `assets/fonts` is only
  used by the store-graphic scripts.
- [ ] optional Platform-adaptive controls: `ui/platform/Adaptive.kt` is the closed list.

## 6. Accounts and sign-in

- [ ] **must** Decide: email/password only, or also Google and/or Apple. Flags
  `feature.googleSignIn`, `feature.appleSignIn`.
- [ ] *should* Google: Cloud project, consent screen, Web + Android (+ iOS) client ids →
  `gradle.properties` (`posterGoogleClientId`), `Config.xcconfig`, server
  `POSTER_GOOGLE_CLIENT_ID`. [`SignIn.md`](SignIn.md)
- [ ] *should* Apple: App ID capability, Xcode capability, `POSTER_APPLE_BUNDLE_ID`;
  for Android also a Services ID + domain verification. [`SignIn.md`](SignIn.md)
- [ ] optional Require email verification or not: `feature.emailVerificationRequired`.

## 7. Server and operations

- [ ] **must** Work through [`Credentials.md`](Credentials.md): every key the
  sections below mention, where to get it and where it goes; copy
  `server/.env.example` to `server/.env` and fill in what you turned on.
- [ ] **must** Run locally once: `scripts/run-local-backend.sh`,
  `scripts/seed-demo-data.sh --host localhost:8080 --reset`, sign in on the
  emulator. [`GettingStarted.md`](GettingStarted.md)
- [ ] **must** Pick a host, set the environment variables (`POSTER_JWT_SECRET`,
  `POSTER_BASE_URL`, database path, uploads directory, fixtures off). [`Deployment.md`](Deployment.md),
  [`Server.md`](Server.md)
- [ ] **must** GitHub Actions: `.github/workflows/deploy-backend.yml` — set
  `DEPLOY_WEBHOOK` (+ `DEPLOY_TOKEN`) secrets or delete the trigger step; make the
  GHCR package readable by your host.
- [ ] *should* Email: Resend domain + `POSTER_RESEND_API_KEY`, `POSTER_MAIL_FROM`. [`Email.md`](Email.md)
- [ ] *should* Admin: `POSTER_ADMIN_ENABLED`, `POSTER_ADMIN_EMAIL`, `POSTER_ADMIN_SESSION_KEY`.
- [ ] *should* Backups: schedule `/app/scripts/backup-database.sh`; set
  `POSTER_BACKUP_UPLOAD` to get copies off the host.
- [ ] *should* Pushes (`feature.pushNotifications`): Firebase values in `gradle.properties`,
  Push capability in Xcode, `POSTER_FCM_*` / `POSTER_APNS_*` on the server. [`PushNotifications.md`](PushNotifications.md)
- [ ] optional Telegram alerts (`POSTER_TELEGRAM_*`), Search Console
  (`POSTER_SITE_VERIFICATION`), App Links / Universal Links
  (`POSTER_ANDROID_FINGERPRINTS`, `POSTER_APPLE_TEAM_ID`).

## 8. Monetisation

- [ ] optional RevenueCat project, products, offering, entitlement named as
  `SupportRepository.SUPPORTER_ENTITLEMENT`; keys in `~/.gradle/gradle.properties`
  and `Local.xcconfig`; `POSTER_PAYMENTS_ENABLED=true` on the server when you may
  take money. [`InAppPurchases.md`](InAppPurchases.md)

## 9. Quality gates before the first release

- [ ] **must** `scripts/check-architecture.sh` and
  `./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest` green.
- [ ] *should* Instrumented suite class by class against the local server; iOS UI
  suite. Update the tests you invalidated (renamed strings, removed features). [`Testing.md`](Testing.md)
- [ ] *should* Screenshots: `scripts/store-screenshots.sh`, `scripts/ios-screenshots.sh`. [`Screenshots.md`](Screenshots.md)
- [ ] **must** Signing key, `printVersion`, release build refuses without a key. [`ReleaseSigning.md`](ReleaseSigning.md)
- [ ] **must** Store requirements: privacy/terms/deletion/child-safety URLs live,
  data-safety answers match `Database.md`. [`StoreListing.md`](StoreListing.md)
- [ ] *should* Set `POSTER_PLAY_URL` / `POSTER_APPSTORE_URL` after publishing.

## 10. Things people forget

- [ ] The `e2e` Android flavour talks to `10.0.2.2:8080` — fine for emulators,
  useless on a phone; use `scripts/dev-tunnel.sh`.
- [ ] `verifyMigrations` will stop your build the first time you change a `.sq`
  without a `.sqm`. That is the feature working.
- [ ] The privacy page says "no analytics, no images, no messaging". Adding any
  of them means rewriting it.
- [ ] `versionCode` is the git commit count: squash-merging or rewriting history
  can make it go down. Pass `-PposterVersionCode=N` if it does.
- [ ] Every string in `values/` needs a twin in `values-ru/` (or delete `values-ru/`).
