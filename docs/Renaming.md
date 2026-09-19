# Renaming the template

Two layers: what a script can do mechanically, and what needs a decision.

## 1. The script

```bash
scripts/rename-app.sh \
  --package com.acme.chirp \
  --app-id  com.acme.chirp \
  --name    "Chirp" \
  --scheme  chirp \
  --domain  chirp.acme.com
```

Run it on a clean working tree. It rewrites every text file in the repository
(Kotlin, Swift, XML, Gradle, the Xcode project, scripts, docs) and moves the
Kotlin source directories to the new package path. Then:

```bash
git diff --stat
./gradlew :shared:jvmTest :server:test          # still builds and passes
open iosApp/iosApp.xcodeproj                    # let Xcode pick up the new bundle id
git commit -am "Rename template to Chirp"
```

| Flag | Changes |
|---|---|
| `--package` | Kotlin package `com.example.poster` → yours, in every source file and directory; Android `namespace`; SQLDelight package; ProGuard keep rules |
| `--app-id` | Android `applicationId`, iOS `PRODUCT_BUNDLE_IDENTIFIER`, `BUNDLE_ID` in `Config.xcconfig`, App Links / assetlinks defaults |
| `--name` | `app.name` in `poster.properties`, `app_name` string resources, `CFBundleDisplayName`, `rootProject.name` (and with it the generated Compose resources package `poster.composeapp.generated.resources`) |
| `--scheme` | `app.scheme`, `POSTER_URL_SCHEME`, every `poster://` literal |
| `--domain` | `app.webOrigin` and every `poster.example.com` literal (server defaults, Config.xcconfig, docs) |

Tests assert `AppInfo.NAME` / `AppInfo.SCHEME` rather than literals, so they
stay green after a rename; the dry run in the template's own review did exactly
this (`--name Chirp …` then the full test suite).

What stays: environment variable names (`POSTER_JWT_SECRET`, …), Gradle property
names (`posterGoogleClientId`, …), the `poster.properties` file name, and the
word "Post". Those are the template's vocabulary and renaming them buys nothing
except merge conflicts when you pull template updates.

## 2. The word "Post"

The template calls the thing people write a *post*. If yours are *notes*,
*requests*, *entries*, *questions*, decide early and change:

- **User-visible text**: `composeApp/src/commonMain/composeResources/values/strings.xml`
  and `values-ru/strings.xml`. Search "post" / "пост". Around a hundred strings;
  an hour with a translator for each extra language.
- **Web pages and emails**: `server/src/main/kotlin/com/example/poster/{LandingPage,PrivacyPage,TermsPage,ChildSafetyPage,PostSharePage}.kt`,
  `auth/{AccountPages,GroupPages,AccountMail}.kt`, `mail/GroupMail.kt`.
- **Store copy**: captions in `scripts/build-store-screenshots.sh` and
  `scripts/build-ios-store-screenshots.sh`, taglines in `scripts/build-feature-graphic.sh`
  and `scripts/build-thumbnail.sh`, demo content in `scripts/seed-demo-data.sh`.

Renaming the *code* (`Post`, `PostsViewModel`, `PostCard`, the `Post` table) is
optional and mostly cosmetic. If you do it, do it the way this template was made
from its parent: a script over the whole tree with ordered case-aware
replacements, then delete `shared/src/commonMain/sqldelight/databases/*.db`,
regenerate with `./gradlew :shared:generatePostDatabaseSchema`, and run every
test. Do not rename a table in a deployed database without a migration.

## 3. Artwork

- `assets/poster-mark.svg`, `assets/poster-mark-dark.svg` — the mark on the login
  screen, empty states and the landing page. Replace, keep the file names, then
  `scripts/build-brand-assets.sh` regenerates the PNGs the app packages.
- `assets/icon-foreground.svg`, `icon-foreground-dense.svg`, `icon-background.svg`
  — the adaptive launcher icon. `scripts/build-icons.sh --install` writes every
  density plus `build/icons/play-icon-512.png` for the store.
- iOS icon: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` — drop in a
  1024×1024 PNG (Xcode 15+ needs only that one).
- `composeApp/src/commonMain/composeResources/drawable/empty_*.png` — the empty
  state illustrations (560×420). Generated from the mark by default.

## 4. Names that live in other people's systems

These are not in the repository and no script reaches them:

- Google Cloud OAuth client ids (Web, Android with your signing fingerprints, iOS) — [`SignIn.md`](SignIn.md)
- Apple: App ID with Sign in with Apple, Services ID, Team ID — [`SignIn.md`](SignIn.md)
- RevenueCat project, products, entitlement `Poster supporter` (the app reads the name from `SupportRepository.SUPPORTER_ENTITLEMENT`) — [`InAppPurchases.md`](InAppPurchases.md)
- Resend domain and API key — [`Email.md`](Email.md)
- Play Console and App Store Connect listings — [`StoreListing.md`](StoreListing.md)
- GitHub Actions secrets and the container registry path in `.github/workflows/deploy-backend.yml` — [`Deployment.md`](Deployment.md)

## 5. Legal pages

`PrivacyPage.kt`, `TermsPage.kt` and `ChildSafetyPage.kt` describe *this*
template's behaviour truthfully (no analytics, no images, no messaging). They are
a good starting point and a bad thing to leave unread: if you add analytics,
media or DMs, the pages become false. Each has an "Operator:" comment where a
value is yours to fill (contact email via `POSTER_CONTACT_EMAIL`, hosting
location, the child-safety hotline for your country, the "last updated" date).
