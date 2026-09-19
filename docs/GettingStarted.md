# Getting started

From a fresh clone to the app running on an emulator, a simulator and a real
phone, against a server on your own machine.

## 0. What you need

| Tool | Why | Check |
|---|---|---|
| JDK 21 | Gradle, the server, the shared module | `java -version` |
| Android Studio (SDK platforms 24–37, an emulator image) | the Android app | `adb devices` |
| Xcode 16+ (macOS only) | the iOS app | `xcodebuild -version` |
| `librsvg`, `imagemagick` (optional) | icons and store graphics | `rsvg-convert --version` |
| `sqlite3` (optional) | inspecting/backing up the database | `sqlite3 --version` |

`scripts/doctor.sh` checks all of it and tells you what is missing. It never
installs anything.

Windows: the server, the shared module and the Android app build fine; the iOS
app needs a Mac. The scripts are bash — use Git Bash or WSL.

## 1. Make it yours (once)

```bash
scripts/rename-app.sh \
  --package com.acme.chirp   \   # Kotlin package / Android namespace
  --app-id  com.acme.chirp   \   # applicationId and iOS bundle id
  --name    "Chirp"          \   # display name
  --scheme  chirp            \   # deep links: chirp://join/CODE
  --domain  chirp.acme.com       # where the server will live
```

Every flag is optional. Run it on a clean tree so the result is one reviewable
commit. Details and the things a script cannot decide (the word "Post", icons,
store names): [`Renaming.md`](Renaming.md).

Then open [`poster.properties`](../poster.properties) and switch features off that
you do not want, and set your colours. [`Configuration.md`](Configuration.md)
explains every key.

## 2. Run the server locally

```bash
scripts/run-local-backend.sh
```

This starts Ktor on `0.0.0.0:8080` in **development mode**, with its own SQLite
file at `server/build/local/post-integration.db` (so it never touches a database
you care about). Development mode means:

- no `POSTER_JWT_SECRET` needed (a fixed development secret is used),
- the fixture routes under `/debug/fixtures/*` are on — the tests and the
  screenshot scripts use them to reset and seed the database,
- mail is printed to the console instead of sent (there is no Resend key),
- alerts are printed instead of sent to Telegram.

Check it: `curl localhost:8080/health` → `OK`. The landing page is at
`http://localhost:8080/`.

Fill it with something to look at:

```bash
scripts/seed-demo-data.sh --host localhost:8080 --reset
```

That creates two accounts per language (`demo.author.en@example.com`,
`demo.reader.en@example.com`, password `demo123456`), three groups, and a
handful of posts, likes and a resolved post. Sign in as the *reader* to see the
author's posts in the feed.

The admin panel is off by default. To try it locally:

```bash
POSTER_ADMIN_ENABLED=true POSTER_ADMIN_EMAIL=demo.author.en@example.com \
POSTER_ADMIN_INSECURE_COOKIE=true scripts/run-local-backend.sh
```

and open `http://localhost:8080/admin` (sign in with that account's password).
[`Server.md`](Server.md) lists every environment variable.

### Running the server from the distribution instead of Gradle

`:server:run` keeps the server inside a Gradle daemon, and a later
`./gradlew --stop` (or Android Studio's sync) kills it. For anything longer than
a quick check:

```bash
./gradlew :server:installDist
JAVA_OPTS="-Dio.ktor.development=true -Dposter.database=$PWD/server/build/local/post.db" \
  server/build/install/server/bin/server
```

## 3. Android

Two product flavours, one dimension (`server`):

| Flavour | Talks to | Package suffix | Use |
|---|---|---|---|
| `e2e` | `http://10.0.2.2:8080` — the host machine as the **emulator** sees it | `.test` | development, instrumented tests |
| `remote` | `https://<app.webOrigin>` (override with `-PposterRemote*`) | none | what ships |

```bash
./gradlew :composeApp:installE2eDebug        # emulator running, local server running
```

Sign in with a seeded account. Both flavours install side by side.

**A real Android phone over USB**: the emulator address does not exist there.
Either forward the port —

```bash
scripts/dev-tunnel.sh android          # adb reverse tcp:8080 tcp:8080
./gradlew :composeApp:installRemoteDebug -PposterRemoteScheme=http \
    -PposterRemoteHost=127.0.0.1 -PposterRemotePort=8080
```

— or point the build at your Mac's LAN address (`scripts/dev-tunnel.sh lan` prints
the flags). Cleartext HTTP is allowed by the manifest for exactly this.

## 4. iOS

```bash
open iosApp/iosApp.xcodeproj
```

Pick an iPhone simulator, run. The simulator reaches the Mac at `127.0.0.1`,
and the server address is a build setting in
[`iosApp/Configuration/Config.xcconfig`](../iosApp/Configuration/Config.xcconfig):

```
POSTER_SERVER_SCHEME=https
POSTER_SERVER_HOST=poster.example.com
POSTER_SERVER_PORT=443
```

For local work, override without editing the tracked file — create
`iosApp/Configuration/Local.xcconfig` (git-ignored, included at the bottom of
`Config.xcconfig`):

```
POSTER_SERVER_SCHEME=http
POSTER_SERVER_HOST=127.0.0.1
POSTER_SERVER_PORT=8080
```

or pass them to `xcodebuild` as the scripts do. `Info.plist` already allows
plain-HTTP to local networks (`NSAllowsLocalNetworking`).

The first build compiles the Kotlin framework (`:composeApp:linkDebugFramework…`)
through the Xcode run-script phase; it takes a few minutes the first time and
seconds after.

**A real iPhone**: use the LAN address (`scripts/dev-tunnel.sh lan`), or a public
tunnel (`scripts/dev-tunnel.sh public` with `cloudflared` or `ngrok`) when the
phone is not on your Wi-Fi. Signing needs your Apple team: set `TEAM_ID` in
`Local.xcconfig`.

## 5. A public URL for the local server

Some flows need the server to be reachable from the internet even in
development: Apple sign-in on Android (Apple posts back to `https://…/auth/apple/callback`),
links in emails opened on another device, testing App Links.

```bash
scripts/dev-tunnel.sh public      # cloudflared quick tunnel or ngrok
```

Copy the `https://….trycloudflare.com` host it prints into the Android build
flags (`-PposterRemoteScheme=https -PposterRemoteHost=<host> -PposterRemotePort=443`)
and, on the server side, `POSTER_BASE_URL=https://<host>` so the links it writes
into emails point at the tunnel.

## 6. Run the tests

```bash
./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest   # no devices needed
scripts/check-architecture.sh                                                # layer rules, string placeholders
./gradlew :composeApp:connectedE2eDebugAndroidTest                           # emulator + local server
scripts/run-ios-integration-tests.sh                                         # simulator + local server
```

[`Testing.md`](Testing.md) has the details and the known traps.

## 7. Next

- Wire up sign-in providers: [`SignIn.md`](SignIn.md)
- Turn on purchases: [`InAppPurchases.md`](InAppPurchases.md)
- Send real email: [`Email.md`](Email.md)
- Put the server somewhere: [`Deployment.md`](Deployment.md)
- Ship: [`ReleaseSigning.md`](ReleaseSigning.md), [`StoreListing.md`](StoreListing.md)
