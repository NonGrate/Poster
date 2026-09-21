Read `CLAUDE.md` at the repository root first: the rules the architecture
script enforces (no ViewModel depends on a ViewModel; `shared/` never imports
the UI; every user-visible string exists in `values/strings.xml` and
`values-ru/strings.xml` with positional placeholders), where each kind of
change goes, and the adaptation playbook. Before calling a change done run
`scripts/check-architecture.sh` and
`./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest`.
