---
description: Add a new feature.* flag wired the way the existing ones are
---

Add a feature flag for: $ARGUMENTS

1. Pick the key (`feature.<camelCase>`) and add one `PosterFeature(...)` entry to
   `buildSrc/src/main/kotlin/PosterFeatures.kt`: title, area, default, what it
   `requires` (flags it cannot work without) or `conflicts` with, and the two
   "off removes" texts. That single entry generates `Features.<KEY>`, validates
   `poster.properties` and rewrites `docs/Features.md` on the next build. Add a
   commented `feature.<key>=true|false` line to `poster.properties` too.
2. Gate the UI at its entry points with `Features.<UPPER_SNAKE>` — the screen
   branch in `MainScreen`, the button or row that opens it, the Koin binding of
   any view model only that feature uses (`di/ViewModelModule.kt`, and the API
   binding in `shared/.../di/KoinModule.kt`).
3. Gate the server: `if (Features.X) route(...)` in `Application.kt`, admin
   pages and nav entries in `admin/AdminRoutes.kt`.
4. Tests: every test of the feature starts with `if (!Features.X) return@withServer`
   (server) or `assumeTrue(Features.X)` in a `@Before` (androidTest).
5. Docs: a line in `README.md`'s table (`docs/Features.md` is generated).
6. Build with the flag on and off (`sed` the property, run
   `./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest :composeApp:compileE2eDebugKotlinAndroid`),
   restore the property, `scripts/check-architecture.sh`, commit.
