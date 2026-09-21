# Architecture

Three Gradle modules and an Xcode project.

```
composeApp   Compose Multiplatform UI + ViewModels      (androidTarget, iosArm64, iosSimulatorArm64)
shared       models, validation, API clients, cache, DI  (+ jvm, so the server can use it)
server       Ktor backend                                 (jvm)
iosApp       Swift shell that hosts the Compose framework and bridges native SDKs
```

`server` depends on `shared`, so request/response classes, validation rules,
the SQLDelight schema and the generated `Features`/`AppInfo` constants are
written once and used on both ends. That is the single most useful property of
the layout: change a field in `model/Post.kt` and the compiler tells you every
place on the client *and* the server that has to follow.

## Layers in the app

```
platform entry point  →  Compose UI  →  ViewModel  →  repository  →  API client / local DB
PosterApplication.kt     ui/screens/    viewmodel/     shared/repository/  shared/ktor/, shared/db/
MainViewController.kt    ui/components/
```

Each layer talks only to the next. Three rules are enforced by
`scripts/check-architecture.sh` because the compiler will not enforce them and
each was broken once:

1. No ViewModel takes another ViewModel as a dependency.
2. `shared/` never references `com.example.poster.ui`.
3. User-visible text is never a string literal — it comes from
   `composeResources/values/strings.xml` (and every placeholder is positional
   and matches across languages).

### Dependency injection

Koin. `shared/di/KoinModule.kt` builds the shared graph from an `AppConfig`
(server host/port/scheme, provider ids, RevenueCat key); `composeApp/di/ViewModelModule.kt`
adds the ViewModels; each platform adds its own module (token storage,
notifications, credential launchers) in `PosterApplication.kt` / `MainViewController.kt`.
Screens take their ViewModels as parameters with `koinInject()` defaults, which
is what lets previews (`preview/PreviewGraph.kt`) hand in fakes.

### State

One `StateFlow` per fact, collected with `collectAsState()`. Where a screen has
several interdependent facts they are one immutable state object
(`FavoritesUiState`), and derived values are derived, not stored. ViewModels
extend `ScopedViewModel`, which owns a coroutine scope on the injected
`DispatcherProvider` (tests pass an unconfined one).

### Offline

The feed, the reader's own posts and their likes are written to the local
SQLDelight database (`PostLocalStore`) after every successful fetch and read from
it first, so the app opens on what it last saw. The cache keeps liked posts that
have fallen out of the feed page (`in_feed = 0`). Mutations (like, post) are
applied optimistically and rolled back on a refused response
(`RefusedFavoriteRollbackTest`).

The same schema serves the server and the device, but the server is the only
process that turns SQLite foreign keys on — the phone stores posts without their
authors, so cascades would break its cache. `DatabaseDriverFactory` (jvm) reads
`POSTER_ENFORCE_FOREIGN_KEYS`.

### Platform divergence

Layouts and hierarchy are shared. Only controls a user would notice as foreign
differ, and they go through `ui/platform/Adaptive.kt` — a closed list of
`expect` composables (switch, back button, nav bar, time picker, confirm dialog,
text field, settings section, card elevation, edge-swipe back, and a few
more). Adding one more divergence is a decision to argue for, not a convenience.

### Deep links

`shared/invite/InviteLink.kt` and `PendingAppLink.kt` hold whatever URL the OS
handed the app until someone is signed in, then act once. Android receives them
in `MainActivity.handleLink`, iOS in `iOSApp.onOpenURL`. Handled kinds:
`join/CODE`, `post/TOKEN`, `verify?token`, `verified`, `reset?token`, `auth/apple`.

## Layers on the server

```
Application.kt  builds repositories and services, installs plugins, and registers the route files (posts/, groups/, accounts/, favorites/, social/, feedback/, tags/, diagnostics/, comments/, uploads/, push/)
auth/           AuthService (register/login/refresh/social/merge), TokenService (JWT),
                AccountTokens (single-use email tokens), AttemptThrottle, verifiers
model/          *LocalRepository over PostDatabase queries; interfaces for tests
admin/          HTMX pages; AdminSecurity for session + CSRF
```

Routes are thin: parse, check the caller, call a repository, respond.
Authorization is always derived from the JWT subject (`call.authenticatedUserId()`),
never from ids in the body.

## Cookbook

### Add a field to Post (end to end)

1. `shared/src/commonMain/sqldelight/com/example/poster/db/Post.sq` — add the
   column to `CREATE TABLE`, and to `insertPost` / `insertMyPost` / `updatePost`
   / `SELECT *` mappers as needed.
2. Write the migration: `shared/src/commonMain/sqldelight/<current version>.sqm`
   (`N.sqm` migrates *from* version N; `1.sqm` took 1→2 for `Post.image`, and
   the highest `.sqm` present tells you the current N) with the
   `ALTER TABLE`, then `./gradlew :shared:generatePostDatabaseSchema` and commit
   the new `databases/<N+1>.db`. [`Database.md`](Database.md) has the rules;
   `Post.image` is the worked example across every layer below.
3. `shared/.../model/Post.kt` — add the property with a default (older clients
   send JSON without it).
4. Mappers: `shared/.../repository/PostLocalStore.kt` (`toPost`), server
   `model/PostsLocalRepository.kt`.
5. Server: validate it in `posts/PostRoutes.kt`'s `POST /posts` if it needs a rule
   (`domain/validation/PostRules.kt` is where limits live, shared with the form).
6. UI: `ui/components/PostFormDialog.kt` to edit it, `PostCard.kt` to show it.
7. Strings for any new label, in both `values/` and `values-ru/`.
8. Tests: a server route test in `server/src/test`, and a ViewModel or rules
   test in `composeApp/src/commonTest` if there is logic.

### Add a screen

1. `composeApp/src/commonMain/kotlin/com/example/poster/ui/screens/YourScreen.kt`;
   it reads state with `collectAsState()` and calls ViewModel functions. No
   network calls, no state that outlives composition.
2. A ViewModel if none fits: extends `ScopedViewModel`, takes repositories.
   Register it in `di/ViewModelModule.kt`.
3. Navigation is a `when` in `ui/MainScreen.kt` over a `selectedTab` route plus
   overlay flags (`showProfile`, `detailsPostId`…). Add a flag and a branch;
   add it to the `AdaptiveBackHandler` condition so Back closes it.
4. Test tags on anything a test will touch, and tests that *wait* for them
   (`TestUtils.awaitTag`) rather than asserting on arrival.
5. Strings in both languages.

### Add an endpoint

1. Request/response `@Serializable` classes in `shared/.../model/`.
2. `shared/.../network/XApi.kt` interface + `shared/.../ktor/KtorXApi.kt` implementation; bind in `KoinModule.kt`.
3. Server route in the area's `XxxRoutes.kt` (or a new `Route.xRoutes()` file, registered in `Application.kt`'s `routing { }`), inside `authenticate("auth-jwt")` unless it is public on purpose. Wrap it in `if (Features.X)` if it belongs to a flag.
4. A route test: `server/src/test/kotlin/.../XRoutesTest.kt` — the existing ones use `testApplication` with a temp database and `ConfirmedAccounts` helpers.

### Add a feature flag

[`Configuration.md`](Configuration.md#how-a-flag-is-wired-and-how-to-add-one).

## Things that look odd and are on purpose

- `Post.likes` is never written on the post row; it is counted from the
  favorites table on the way out (`withLikeCount()`), and the device adjusts its
  cached copy on a tap.
- The feed hides the reader's own posts and shows them back merged from a
  separate `myPosts` list, so "N new posts" never counts what you just wrote.
- Resolved posts leave the feed after 24 h (`FeedRules.kt`) but stay in My Posts
  and Liked.
- Language is a comma-separated column, not a join table: two languages, fixed list.
- Email verification is enforced by the server refusing the write (403 with
  `code: email_not_verified`), which the app turns into a dialog. A client
  cannot skip it by ignoring a flag.
- `versionCode` is counted from git history, so a fresh template builds with 1
  and a release refuses to build if git cannot be asked.
