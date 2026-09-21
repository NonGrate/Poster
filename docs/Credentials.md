# Credentials and keys — the single list

Everything a forked Poster needs from *your* accounts, in one place: what each
key is for, where you get it, and where it goes. Nothing here is checked in.
The build and the server run without any of it — every row below is a feature
that stays off (or logs instead of sending) until its key is present, and the
server's startup log says which way each went.

Four destinations:

| Destination | What goes there | Loaded how |
|---|---|---|
| **Server environment** | everything the server needs at runtime | `docker run --env-file server/.env` or your host's env panel. Template: [`server/.env.example`](../server/.env.example) |
| **`gradle.properties`** (committed) | public ids the Android/desktop/web build bakes in | read by `composeApp/build.gradle.kts` |
| **`~/.gradle/gradle.properties`** (your machine) | private Android build values: RevenueCat key, signing | same, but never in the repo |
| **`iosApp/Configuration/Local.xcconfig`** (git-ignored) | the iOS equivalents; overrides `Config.xcconfig` | `#include? "Local.xcconfig"` at the bottom of `Config.xcconfig` |

Plus GitHub Actions **secrets** for the release and deploy workflows (last section).

Public means "ships inside the app anyway" (OAuth client ids, Firebase app
ids); those live in committed files. Anything that would let someone act as
you (API keys, JWT secret, signing keys, `.p8`/`.p12`/service-account files)
never goes into the repo, a Gradle file inside the repo, or a mobile build.

---

## 1. Server environment

### Always

| Variable | Where it comes from | Without it |
|---|---|---|
| `POSTER_JWT_SECRET` | `openssl rand -base64 48`. Keep it stable across deploys (rotating it signs everyone out) | server refuses to start outside development mode |
| `POSTER_BASE_URL` | your public origin, `https://your.domain` | email and admin links use `app.webOrigin` from `poster.properties` |
| `PORT`, `POSTER_DATABASE_PATH`, `POSTER_UPLOADS_DIR`, `POSTER_FIXTURES_ENABLED` | your choice; the Docker image sets `8080`, `/data/post.db`, `/data/uploads`, `false` | defaults; keep fixtures **off** in production — they wipe the database on request |
| `POSTER_ENFORCE_FOREIGN_KEYS` | leave alone (`main()` sets `true`) | — |

### Admin panel (`/admin`)

| Variable | Where it comes from | Without it |
|---|---|---|
| `POSTER_ADMIN_ENABLED` | `true` | no panel |
| `POSTER_ADMIN_EMAIL` | the account you registered in the app; promoted to admin on startup | nobody is admin |
| `POSTER_ADMIN_SESSION_KEY` | `openssl rand -base64 48` | random per start, so every restart signs the admin out |
| `POSTER_ADMIN_INSECURE_COOKIE` | `true` only for plain-HTTP local runs | cookie is `Secure` |

### Sign-in providers (`feature.googleSignIn`, `feature.appleSignIn`) — [`SignIn.md`](SignIn.md)

| Variable | Where it comes from | Without it |
|---|---|---|
| `POSTER_GOOGLE_CLIENT_ID` | Google Cloud console → APIs & Services → Credentials → the **Web** client id, plus the **iOS** client id, comma-separated. Must equal `posterGoogleClientId` / `POSTER_GOOGLE_CLIENT_ID` in the clients | Google sign-in answers 501 |
| `POSTER_APPLE_BUNDLE_ID` | your iOS bundle id (`BUNDLE_ID` in `Config.xcconfig`) | native Apple tokens are rejected |
| `POSTER_APPLE_SERVICE_ID` | developer.apple.com → Identifiers → **Services ID**; must equal `posterAppleServiceId` | Apple sign-in on Android/web fails |
| `POSTER_APPLE_DOMAIN_ASSOCIATION` | contents of Apple's `apple-developer-domain-association.txt` from the Services ID domain verification | Apple cannot verify your domain |

### Email (`feature.emailVerificationRequired`, magic links, invites) — [`Email.md`](Email.md)

| Variable | Where it comes from | Without it |
|---|---|---|
| `POSTER_RESEND_API_KEY` | resend.com → verify your domain → **API Keys → Create**, *Sending access*, restricted to the domain | emails are printed to the log |
| `POSTER_MAIL_FROM` | `Name <no-reply@your.domain>` on the verified domain | derived from `AppInfo` |

### Push notifications (`feature.pushNotifications`) — [`PushNotifications.md`](PushNotifications.md)

| Variable | Where it comes from | Without it |
|---|---|---|
| `POSTER_FCM_PROJECT_ID` | Firebase console → Project settings → General → Project ID | Android pushes are logged |
| `POSTER_FCM_SERVICE_ACCOUNT` | path to the JSON from Firebase → Project settings → Service accounts → **Generate new private key**; mount it as a secret file | Android pushes are logged |
| `POSTER_APNS_KEY_PATH` | path to the `.p8` from developer.apple.com → Keys → **Apple Push Notifications service**; mount it as a secret file | iOS pushes are logged |
| `POSTER_APNS_KEY_ID` | the Key ID shown next to that key | iOS pushes are logged |
| `POSTER_APNS_TEAM_ID` | developer.apple.com → Membership → Team ID | iOS pushes are logged |
| `POSTER_APNS_TOPIC` | your iOS bundle id | iOS pushes are logged |
| `POSTER_APNS_SANDBOX` | `true` while testing development builds | production APNs host |

### Deep links (App Links / Universal Links) — [`Deployment.md`](Deployment.md)

| Variable | Where it comes from | Without it |
|---|---|---|
| `POSTER_ANDROID_FINGERPRINTS` | SHA-256 of your signing certificate(s), comma-separated: `keytool -list -v -keystore upload.jks`, and Play Console → Setup → App signing for the Play-managed key | `/.well-known/assetlinks.json` is 404, links open in the browser |
| `POSTER_ANDROID_PACKAGE` | your `applicationId` | the template's |
| `POSTER_APPLE_TEAM_ID` | developer.apple.com → Membership → Team ID | no `apple-app-site-association`, Universal Links stay off |

### Alerts, pages, purchases

| Variable | Where it comes from | Without it |
|---|---|---|
| `POSTER_TELEGRAM_BOT_TOKEN` | Telegram → @BotFather → `/newbot` | crash, feedback and report alerts are logged |
| `POSTER_TELEGRAM_CHAT_ID` | start a chat with the bot, then `curl https://api.telegram.org/bot<token>/getUpdates` → `chat.id` | same |
| `POSTER_PAYMENTS_ENABLED` | `true` once you may legally take money (`feature.support`; see [RevenueCat](#revenuecat-featuresupport) below) | the app shows no purchase buttons |
| `POSTER_CONTACT_EMAIL`, `POSTER_SAFETY_CONTACT`, `POSTER_OWNER_NAME` | your addresses / name for the privacy, terms, child-safety and landing pages | `privacy@<domain>`, none, app name |
| `POSTER_PLAY_URL`, `POSTER_APPSTORE_URL` | the store listing URLs after publishing | landing page says "coming soon" |
| `POSTER_SITE_VERIFICATION` | Google Search Console → HTML tag → the `content` value | no tag |
| `POSTER_WEB_ORIGINS` | the origin(s) a separately hosted web app runs on, comma-separated (`feature.web`) | CORS only for `POSTER_BASE_URL` |
| `POSTER_WEB_DIR` | directory with the built Wasm bundle, if the server should serve it at `/app` | not served |
| `POSTER_BACKUP_DIR`, `POSTER_BACKUP_KEEP`, `POSTER_BACKUP_UPLOAD` | your paths / a copy-off command for `scripts/backup-database.sh` | `<db dir>/backups`, 14, no upload |

Defaults and behaviour in more detail: [`Server.md`](Server.md#environment-variables).

---

## 2. Android, desktop and web build

### `gradle.properties` (committed — public ids only)

| Property | Where it comes from | Without it |
|---|---|---|
| `posterGoogleClientId` | the same **Web** client id as `POSTER_GOOGLE_CLIENT_ID` | no Google button |
| `posterAppleServiceId` | the same Services ID as `POSTER_APPLE_SERVICE_ID` | no Apple button on Android |
| `posterFirebaseProjectId`, `posterFirebaseAppId`, `posterFirebaseApiKey`, `posterFirebaseSenderId` | Firebase console → Project settings → Your apps → the **Android** app (all four or none). No `google-services.json` needed | Android pushes cannot register |

### `~/.gradle/gradle.properties` (your machine — never in the repo)

| Property | Where it comes from | Without it |
|---|---|---|
| `posterRevenueCatKey` | RevenueCat → Project → API keys → the **Google Play** public key (`goog_…`); `test_…` for the Test Store while developing. Release builds refuse a `test_` key | Settings shows no support section |
| `posterKeystore`, `posterKeyAlias` | path to your upload keystore and its alias; passwords from the macOS Keychain (`posterKeychainService`, default `poster-upload`) or `keystore.properties` | release build refuses to sign — [`ReleaseSigning.md`](ReleaseSigning.md) |
| `posterRemoteHost`, `posterRemotePort`, `posterRemoteScheme` | your server, for the `remote` flavour; normally derived from `app.domain` in `poster.properties` | derived |
| `posterDemoPaywall` | `true` to show the support screen with fake products, for screenshots | off |

---

## 3. iOS build — `iosApp/Configuration/Local.xcconfig`

Create the file next to `Config.xcconfig`; it is git-ignored and overrides it.

| Key | Where it comes from | Without it |
|---|---|---|
| `TEAM_ID` | developer.apple.com → Membership → Team ID | Xcode cannot sign a device build |
| `POSTER_GOOGLE_CLIENT_ID` | Google Cloud console → the **iOS** client id (also added to the server's list) | no Google button |
| `POSTER_GOOGLE_REVERSED_CLIENT_ID` | the same id with its parts reversed: `com.googleusercontent.apps.<id>` | Google redirect cannot return to the app |
| `POSTER_REVENUECAT_KEY` | RevenueCat → API keys → the **App Store** public key (`appl_…`) | no support section |
| `POSTER_SERVER_HOST`, `POSTER_SERVER_PORT`, `POSTER_SERVER_SCHEME` | your server, if different from `Config.xcconfig` | `Config.xcconfig` values |

`BUNDLE_ID`, `APP_NAME`, `POSTER_URL_SCHEME` are identity, not credentials; `scripts/rename-app.sh` sets them in `Config.xcconfig`.

---

## 4. GitHub Actions secrets

Repository → Settings → Secrets and variables → Actions. Each workflow skips
its upload step when its secrets are missing, so the build artefacts are still
produced. Setup steps: [`ReleaseSigning.md`](ReleaseSigning.md#automated-releases-github-actions),
[`Deployment.md`](Deployment.md#github-actions--ghcr--your-host).

| Secret | Workflow | Where it comes from |
|---|---|---|
| `DEPLOY_WEBHOOK` | `deploy-backend.yml` | your host's redeploy webhook URL (Coolify: Webhooks page) |
| `DEPLOY_TOKEN` | `deploy-backend.yml` | bearer token for that webhook (Coolify: API token with `deploy`) |
| `POSTER_KEYSTORE_BASE64` | `release-android.yml` | `base64 -i upload.jks` |
| `POSTER_KEYSTORE_PASSWORD`, `POSTER_KEY_ALIAS`, `POSTER_KEY_PASSWORD` | `release-android.yml` | the keystore you created |
| `PLAY_SERVICE_ACCOUNT_JSON` | `release-android.yml` | Play Console → Setup → API access → service account with *Release to testing tracks*; the JSON file's contents |
| `APPLE_TEAM_ID` | `release-ios.yml` | developer.apple.com → Membership |
| `APPSTORE_ISSUER_ID`, `APPSTORE_KEY_ID`, `APPSTORE_PRIVATE_KEY` | `release-ios.yml` | App Store Connect → Users and Access → Integrations → App Store Connect API → key (App Manager); the `.p8` contents |
| `IOS_DIST_CERT_P12_BASE64`, `IOS_DIST_CERT_PASSWORD` | `release-ios.yml` | Xcode → Settings → Accounts → Manage Certificates → *Apple Distribution* → export `.p12`; `base64 -i cert.p12` |

`GITHUB_TOKEN` is provided by GitHub; nothing to create.

---

## RevenueCat (`feature.support`)

Keep or drop it in `poster.properties`:

- `feature.support=false` — the support screen is gone and the RevenueCat SDK is out of the Android build (on iOS also drop the `purchases-ios-spm` package from the Xcode project). No keys needed anywhere. (Required for `feature.desktop` / `feature.web`.)
- `feature.support=true` — the code is in, but the screen only appears once a key is present: `posterRevenueCatKey` (Android) and `POSTER_REVENUECAT_KEY` (iOS). Purchases are offered only when the server says `POSTER_PAYMENTS_ENABLED=true`. Products, offering and the entitlement named `SupportRepository.SUPPORTER_ENTITLEMENT` are set up in the RevenueCat dashboard — [`InAppPurchases.md`](InAppPurchases.md).

---

## Which rows apply to you

Only the sections whose feature is on. `docs/Features.md` lists the flags;
a minimal email-and-password app on one server needs exactly `POSTER_JWT_SECRET`,
`POSTER_BASE_URL`, the Resend pair, and (when you deploy from GitHub) the two
`DEPLOY_*` secrets.
