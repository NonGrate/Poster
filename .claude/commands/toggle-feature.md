---
description: Turn feature flags on or off and prove the build still holds
---

Flags to change: $ARGUMENTS (names and on/off).

1. Edit the `feature.*` lines in `poster.properties`. Remember the constraints:
   `feature.desktop=true` needs `feature.support=false`; `feature.follows`
   needs `feature.authors` to show anything; `feature.publicGroups` needs
   `feature.groups`.
2. Run `scripts/check-architecture.sh` and
   `./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest :composeApp:assembleE2eDebug`.
   The suite is flag-aware; a failure means a gate is missing — fix the gate,
   not the test.
3. If `feature.support` went off, tell the developer to remove
   `purchases-ios-spm` in Xcode (you cannot). If `feature.images` went off,
   `POSTER_UPLOADS_DIR` leaves the deployment env.
4. Update `docs/Configuration.md` only if a row's wording is now wrong. Commit
   "Configure features: <list>".
