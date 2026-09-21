# Contributing

Thanks for looking. This is a template, so the bar for what goes in is
"would most forks want this by default". Product features that only some apps
need belong behind a `feature.*` flag or in your fork.

## Before you open a pull request

```bash
scripts/check-architecture.sh
./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest
```

Both must pass; CI runs the same. If you changed the schema, `verifyMigrations`
will insist on a `.sqm` and a regenerated `databases/<N>.db` — see
[`docs/Database.md`](docs/Database.md).

If you touched the UI, run the instrumented suite for the screens you changed
([`docs/Testing.md`](docs/Testing.md)), class by class.

## Conventions

- Every user-visible string is a resource, in both `values/` and `values-ru/`,
  with positional placeholders. The architecture check enforces it.
- No ViewModel depends on another ViewModel. `shared/` never imports the UI.
- Layer rules and how to add a screen, a field or an endpoint:
  [`docs/Architecture.md`](docs/Architecture.md).
- A bug fix comes with the test that would have caught it and a comment
  saying what broke. Comments explain *why*, not what.
- Match the existing style. Do not reformat files you did not otherwise change.
- Nothing personal or secret in the repository: no client secrets, keys,
  fingerprints, emails, team ids. Public client ids go in `gradle.properties`
  / `Config.xcconfig`; everything else in `~/.gradle/gradle.properties`,
  `Local.xcconfig` or server environment variables.

## Reporting a problem

Open an issue with: what you did, what you expected, what happened, and the
relevant startup log lines from the server (they say which subsystems were
enabled). For a security issue, please do not open a public issue — see
`SECURITY.md`.
