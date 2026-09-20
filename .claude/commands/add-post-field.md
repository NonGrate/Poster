---
description: Add a field to Post end to end (schema, model, server, form, card, tests)
---

Add this field to posts: $ARGUMENTS (name, type, required or optional, shown where).

Follow `docs/Architecture.md` → "Add a field to Post" exactly:

1. `shared/src/commonMain/sqldelight/com/example/poster/db/Post.sq`: the column
   with a default; `insertPost` / `insertMyPost` / `updatePost` as needed.
2. Migration `<N>.sqm` where N is the highest `databases/<N>.db`; then
   `./gradlew :shared:generatePostDatabaseSchema`; commit `databases/<N+1>.db`.
   Never edit an existing `.sqm` or `.db`.
3. `shared/.../model/Post.kt` (property with a default), mappers in
   `PostLocalStore.kt` and server `PostsLocalRepository.kt`.
4. A rule in `shared/.../domain/validation/` and the server check in
   `POST /posts` (`Application.kt`); a JVM test for the rule.
5. `PostFormDialog.kt` (input), `PostCard.kt` / `PostBadges.kt` (display),
   strings in `values/strings.xml` **and** `values-ru/strings.xml`.
6. A server route test (create with the field, read it back, refuse an invalid
   value) in `server/src/test/kotlin/com/example/poster/` using
   `ServerTestSupport.withServer` / `confirmed` / `postPost`.
7. `scripts/check-architecture.sh`, the three test tasks, commit.
