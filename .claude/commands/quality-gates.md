---
description: Run every check a change must pass before it is called done
---

Run, in this order, and report each result line:

```bash
scripts/check-architecture.sh
./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest --no-daemon
./gradlew :composeApp:compileE2eDebugKotlinAndroid :composeApp:compileE2eDebugAndroidTestKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 --no-daemon
```

Then the flags-off pass (the suite must stay green with every feature off):

```bash
cp poster.properties /tmp/poster.properties.bak
sed -i '' 's/^\(feature\.[A-Za-z]*\)=true/\1=false/' poster.properties
./gradlew :shared:jvmTest :server:test :composeApp:compileE2eDebugKotlinAndroid --no-daemon
cp /tmp/poster.properties.bak poster.properties
```

If the schema changed, `SchemaMigrationTest` (in `:shared:jvmTest`) already
covered the migration; check `databases/<N+1>.db` is committed. Instrumented
and XCUITest suites need a device session (`docs/Testing.md`) — offer, do not
assume. $ARGUMENTS
