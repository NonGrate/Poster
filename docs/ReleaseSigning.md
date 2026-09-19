# Release builds: signing and versions

## Android

Release builds are signed with a key that lives on your machine or CI and
nowhere in the repository. Debug builds need none of this.

### Create the key once

```bash
keytool -genkeypair -v -keystore ~/keys/upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

With Play App Signing (the default for new apps) this is the **upload key**:
losing it is recoverable through Play support; the key that signs what users
install is held by Google. Back up the `.jks` and its password separately.

### Tell the build about it

The build looks, in order: environment variables → macOS Keychain → `keystore.properties`.

**Keychain (preferred on a Mac)** — the password is never on disk:

```bash
security add-generic-password -s poster-upload -a upload -w      # prompts
```
```properties
# ~/.gradle/gradle.properties (outside the repo; not secret)
posterKeystore=/Users/you/keys/upload.jks
posterKeyAlias=upload
# posterKeychainService=poster-upload   (default)
```

**A file** — simpler, readable by every process running as you:

```properties
# keystore.properties in the project root (git-ignored)
storeFile=/Users/you/keys/upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

**CI** — `POSTER_KEYSTORE`, `POSTER_KEYSTORE_PASSWORD`, `POSTER_KEY_ALIAS`,
`POSTER_KEY_PASSWORD` as secrets; write the keystore from a base64 secret to a
path first.

Then:

```bash
./gradlew :composeApp:bundleRemoteRelease     # .aab for Play
./gradlew :composeApp:assembleRemoteRelease   # .apk
```

An unsigned release does not build: the task graph check throws with the
message above rather than producing an APK that fails at upload. It also
refuses a RevenueCat key that starts with `test_`.

Verify: `$ANDROID_HOME/build-tools/*/apksigner verify --print-certs <apk>`.

### Version numbers

`versionCode` is **counted from git** (`git rev-list --count HEAD`) so it always
goes up and never has to be remembered. `./gradlew :composeApp:printVersion`
shows what a build would claim. Override when the count is wrong (shallow CI
clone, rewritten history): `-PposterVersionCode=250`. A fresh template with no
history builds debug with 1 and refuses to build a release until git can be
asked or a number is passed.

`versionName` is `-PposterVersionName=1.2` (default `1.0`).

### Server address in a release

The `remote` flavour bakes in `app.webOrigin`'s host over https/443. Override:
`-PposterRemoteScheme= -PposterRemoteHost= -PposterRemotePort=` (or
`ORG_GRADLE_PROJECT_posterRemoteHost=…` in the environment).

### R8

On for release (`isMinifyEnabled`, `isShrinkResources`). `composeApp/proguard-rules.pro`
keeps what is looked up by name: kotlinx.serialization serializers and the
`model` package (its field names *are* the wire format), Ktor, Credential
Manager, and source-file/line-number attributes so crash reports stay readable.
Upload the mapping file (`composeApp/build/outputs/mapping/remoteRelease/`) to Play.

## iOS

Signing is Xcode's. Set `TEAM_ID` in `iosApp/Configuration/Local.xcconfig`
(git-ignored) — the project reads `DEVELOPMENT_TEAM = ${TEAM_ID}` — and let
Xcode manage provisioning. Archive with Product → Archive or

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' archive -archivePath build/Poster.xcarchive
```

The release Kotlin/Native link is memory-hungry; `gradle.properties` already
gives the Kotlin daemon 4 GB for it.

Version and build number are `CFBundleShortVersionString` / `CFBundleVersion` in
`Info.plist` (or `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` if you move them
to the project settings).

## Automated releases (GitHub Actions)

Two workflows run on a `v*` tag (or by hand from the Actions tab):

| Workflow | Does | Secrets |
|---|---|---|
| `release-android.yml` | `bundleRemoteRelease`, keeps the `.aab`, uploads it to the **Play internal track** | `POSTER_KEYSTORE_BASE64`, `POSTER_KEYSTORE_PASSWORD`, `POSTER_KEY_ALIAS`, `POSTER_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON` (upload step is skipped without it) |
| `release-ios.yml` | archives on a macOS runner with a distribution certificate and an App Store profile fetched from App Store Connect, exports the `.ipa`, uploads it to **TestFlight** | `APPLE_TEAM_ID`, `APPSTORE_ISSUER_ID`, `APPSTORE_KEY_ID`, `APPSTORE_PRIVATE_KEY`, `IOS_DIST_CERT_P12_BASE64`, `IOS_DIST_CERT_PASSWORD` |

Setting them up once:

1. **Play**: Play Console → Setup → API access → create a service account,
   grant it *Release to testing tracks* on the app, download its JSON; the app
   must already exist in the console with one manual upload done.
2. **App Store Connect**: Users and Access → Integrations → App Store Connect
   API → generate a key (App Manager); note issuer id, key id, download the `.p8`.
3. **Certificate**: Xcode → Settings → Accounts → Manage Certificates → *Apple
   Distribution*, export as `.p12` with a password; `base64 -i cert.p12`.
4. **Keystore**: `base64 -i upload.jks` for the Android secret.

The `.aab` and `.ipa` are kept as workflow artifacts either way, so the
workflows are useful before the store accounts exist. Both use the version and
build number the repository produces (`versionCode` from the commit count,
`CFBundleVersion` from `Info.plist`); tag after bumping `CFBundleShortVersionString`
/ `versionName`. Neither workflow can be exercised from this repository without
the accounts; they follow the documented `xcodebuild` and Gradle commands above
and the actions' own documentation.

