# Database

SQLite everywhere, through [SQLDelight](https://sqldelight.github.io/sqldelight/):
one schema, three drivers (Android, native iOS, JDBC on the server). The device
uses it as an offline cache; the server uses it as the source of truth.

## Schema

`shared/src/commonMain/sqldelight/com/example/poster/db/*.sq` — one file per
table, each with its `CREATE TABLE` and its named queries. SQLDelight generates
`PostDatabase`, one `XQueries` object per file, and a data class per table.

| Table | Holds |
|---|---|
| `User` | accounts: name, surname, email (unique), Argon2 hash, role (`user`/`admin`), status (`active`/`banned`), languages, verified_at, show_name |
| `Post` | guid, title, message, author → User, group, date, visibility (`public`/`group`/`private`), language, completed_at + completion_message, deleted_at (soft delete), shareToken, in_feed (device cache only) |
| `Tag`, `PostTag` | the curated catalogue (id, labels en/ru, group) and the many-to-many |
| `UserPostFavorite` | likes: (user, post, created_at) |
| `Group`, `UserGroup`, `GroupInvite` | groups, memberships with role (`member`/`admin`), single-use invites (created_by, used_by, revoked_at, sent_to) |
| `RefreshToken` | hashed refresh tokens with expiry |
| `SocialIdentity` | (provider, subject) → user; what makes Apple's hidden email safe |
| `PostReport`, `ModerationAudit`, `Feedback` | moderation |

Crash reports and diagnostic events are stored by the server in tables it
creates itself (`CrashRepository`, `EventRepository`) — they are server-only and
capped, so they stay out of the shared schema.

Every `ON DELETE CASCADE` only fires on the server (`PRAGMA foreign_keys` is a
per-connection setting the JDBC driver turns on); the device stores posts
without their authors and must not cascade.

## Changing the schema

The template ships at **version 2**: `databases/1.db` is the original baseline,
`1.sqm` adds `Post.image` (the worked example of a migration, for
`feature.images`), `databases/2.db` is the result. Every further change is
another migration.

1. Edit the `.sq` file (the `CREATE TABLE` is always the *current* shape).
2. Add `shared/src/commonMain/sqldelight/<N>.sqm` where **`N` is the version
   being migrated *from*** — SQLDelight's convention: `1.sqm` takes version 1 to
   2, `2.sqm` takes 2 to 3. So the next file is `<current version>.sqm`.
3. `./gradlew :shared:generatePostDatabaseSchema` records `databases/<N+1>.db`.
4. Commit the `.sq`, the `.sqm` and the new `.db` together.

`verifyMigrations = true` in `shared/build.gradle.kts` fails the build if
applying the migrations to the previous baseline does not produce exactly the
schema the `.sq` files describe, so the two cannot drift. `SchemaMigrationTest`
(jvm) additionally opens every recorded older baseline, runs the real driver on
it and checks it lands on the current version with a snapshot beside it.

What runs the migrations:

- **Android / iOS**: the drivers use `PRAGMA user_version` and
  `PostDatabase.Schema.migrate`.
- **Server**: `shared/src/jvmMain/.../DatabaseDriverFactory.kt` does the same by
  hand, one step at a time, writing the version after each step (SQLite commits
  DDL as it goes, so "all or nothing" was never available) — and before the
  first step it writes `backups/pre-migration-v<N>-<stamp>.db` with
  `VACUUM INTO`. If that snapshot cannot be written the server refuses to start:
  visible and recoverable beats migrated with no way back.

Rules that save a bad afternoon:

- Never edit a committed `.sqm` or `.db`. Add the next one.
- SQLite cannot drop or rename columns freely; a table rebuild is
  `CREATE new → INSERT SELECT → DROP old → ALTER RENAME`, and it must run with
  foreign keys off — which the server's migration connection already is.
- Columns added in a migration need a `DEFAULT` or `NULL`, because the device
  has rows the migration does not know about.
- The `.sq` `CREATE TABLE` and the sum of the migrations must agree — that is
  the whole point of `verifyMigrations`.

## Inspecting a database

```bash
sqlite3 server/build/local/post-integration.db
.tables
.schema Post
SELECT COUNT(*) FROM Post WHERE deleted_at IS NULL;
```

The Android device database is `post.db` in the app's databases directory;
`adb shell run-as com.example.poster ls databases/` on a debug build.

## Why SQLite and not Postgres

If you do port: `PostsRepository`, `AccountRepository`, `GroupRepository`,
`UserGroupRepository`, `FavoritesRepository` and `TagRepository` are
interfaces with one SQLite implementation each. `CommentsRepository`,
`NotificationsRepository`, `FollowsLocalRepository`, `BookmarksLocalRepository`,
`ModerationRepository`, `ReportsRepository`, `FeedbackRepository`,
`CrashRepository` and `EventRepository` are concrete classes over the
generated queries and would get an interface first. Each builds its own
`DatabaseManager(DatabaseDriverFactory())`; giving `Application.module()` one
database to hand out is the first step of any such port.


One file, no service to run, backups are a copy, and a single Ktor instance
handles far more than a small group app will see. When you outgrow it —
several server instances, or write volume that makes a single writer a
bottleneck — the repositories in `server/.../model/` are the seam: they are the
only code that touches `PostDatabase`. The shared schema would stay for the
device cache.
