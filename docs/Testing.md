# Testing

| Suite | Command | Needs | Roughly |
|---|---|---|---|
| Shared unit (jvm) | `./gradlew :shared:jvmTest` | nothing | validation rules, invite-link parsing, session restore, local store, migrations |
| Server routes | `./gradlew :server:test` | nothing (temp SQLite per test) | ~400 tests: auth, posts, feed paging/filters, groups, invites, admin panel, mail, sign-in verifiers, backups |
| App unit | `./gradlew :composeApp:testRemoteDebugUnitTest` | nothing | ViewModel and feed-rule tests from `commonTest` (`PeriodicRefreshTest` is excluded here and runs on the iOS simulator) |
| Architecture | `scripts/check-architecture.sh` | python3 | layer rules, hardcoded text, string placeholders in both languages |
| Android instrumented | `./gradlew :composeApp:connectedE2eDebugAndroidTest` | emulator + `scripts/run-local-backend.sh` | 25 classes against the real server |
| iOS UI | `scripts/run-ios-integration-tests.sh` | simulator + local server | 10 scenarios (XCUITest) |

CI (`.github/workflows/ci.yml`) runs the first four on every push, then the
same tests again with **every feature flag off** (flag-dependent tests skip
themselves; a failure there is a missing gate), compiles the desktop target,
and builds the iOS shell on macOS for pushes to `main`.

Server tests share `ServerTestSupport.kt`: `withServer { }` (a fresh SQLite
file per class, a recording mailer, a temp upload dir; optional social
verifiers and push senders), `confirmed(email)`, `postPost(...)` and
`HttpResponse.guids()`. Isolation rests on the `poster.database` system
property, so `maxParallelForks` stays 1. Shared unit tests have `NoopPostApi`,
`NoopUserApi` and `withTempDatabase { }`; instrumented tests pass what they
seed to `runWrapped(seeded = …)` and it is deleted afterwards.

## Server tests

Each test starts `testApplication { application { module(mailer = …, verifiers = …) } }`
with `poster.database` pointing at a temp file, so they are hermetic and can run
in parallel. Helpers in `server/src/test/.../ConfirmedAccounts.kt` register and
verify accounts through the real routes. The `Mailer` is a fake that records what
would have been sent; social verifiers are given a locally generated RSA key.

## Instrumented Android suite

Runs against the `e2e` flavour, which talks to `10.0.2.2:8080`. Before each
class the runner posts `/debug/fixtures/integration`, which wipes the server's
database and seeds two accounts (`test@example.com`, `user2@example.com`,
password `password123`), two groups (`group-a`, `book-club`) and the tag
catalogue. `TestUtils` waits for test tags rather than asserting immediately —
screens arrive after a network round trip.

Run it **class by class** rather than in one invocation. A process crash ends
the whole run with `Expected N tests, received M`, which says nothing about the
code; per-class runs contain it.

When it fails and the code is fine (it happens):

- `INSTRUMENTATION_ABORTED: System has crashed` / `Process crashed` with no
  `FATAL EXCEPTION` in logcat is the emulator killing the app under memory
  pressure, not a bug. Use a system image **without** Play Services
  (`google_apis`, not `google_apis_playstore`); windowed, default RAM.
- `runTest did not complete within the testTimeout of 1m` on every test, or
  request gaps of minutes in the server log, means the emulator is starved.
  Two causes seen: Play Services churning after boot (see above), and the
  emulator falling back to *software* rendering when the host is low on
  memory (`Software GL rendering will be used` in its log) — start it with
  `-gpu host`. A healthy emulator shows a load average under 2 in
  `adb shell cat /proc/loadavg`.
- The suite asserts English strings. The device language must be English:
  `adb shell settings get system system_locales` → `en-US` (on a rooted
  emulator, `settings put system system_locales en-US` then restart zygote).
- With a phone attached as well, pin Gradle to the emulator:
  `ANDROID_SERIAL=emulator-5554 ./gradlew …`. Otherwise it installs on both.
- Running the suite from a scratch copy of the tree (`rsync -a --exclude build
  --exclude .gradle`) lets you keep editing while it runs; the copy compiles
  in its own build directories.
- A backend left running while `:shared` is rebuilt serves stale classes: every
  sign-in fails at once. Restart it after any `:shared` change.
- Run the backend from the distribution (`:server:installDist`, then
  `server/build/install/server/bin/server` with `JAVA_OPTS="-Dio.ktor.development=true -Dposter.database=…"`)
  rather than `:server:run`, or a later `gradle --stop` takes it down mid-suite.
- Cold-boot a wedged emulator with `-no-snapshot-load`, and wait for
  `pm list packages` to answer before installing.

## iOS UI suite

`iosApp/iosAppUITests/PosterUITests.swift`. The script applies the same fixtures,
builds with `POSTER_SERVER_HOST=127.0.0.1` etc. as `xcodebuild` arguments (so the
tracked `Config.xcconfig` is untouched), and runs `PosterUITests`. The
`POSTER_UI_TEST=1` launch environment makes the app clear any cached session
first. Override the simulator with `IOS_SIMULATOR_NAME="iPhone 16 Pro"`.

The same file holds `PosterScreenshotTests`, which the screenshot script drives.

## Conventions

- Tests mirror production packages. Names say the behaviour
  (`aPostCarryingAnUnknownTagDoesNotCreateIt`).
- Anything a test touches has a `testTag`; tests wait for tags with a timeout.
- Fixtures are seeded over HTTP through `/debug/fixtures/*`, never by reaching
  into the database, so they exercise the same code paths users do.
- A bug fix comes with the test that would have caught it, and a comment saying
  what broke.
