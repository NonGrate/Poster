## What and why

<!-- One paragraph. Link the issue if there is one. -->

## Checklist

- [ ] `scripts/check-architecture.sh` passes
- [ ] `./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest` passes
- [ ] Strings added in both `values/` and `values-ru/` (or none added)
- [ ] Schema change comes with a `.sqm` and a regenerated `databases/<N>.db` (or no schema change)
- [ ] Behind a `feature.*` flag if it is a product feature most forks would not want by default
- [ ] No secrets, personal emails, team ids or fingerprints
