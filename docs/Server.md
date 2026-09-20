# The server

One Ktor process, one SQLite file, one Docker image. It serves the JSON API the
apps talk to, the web pages people reach from emails and shared links, and the
admin panel.

```
server/src/main/kotlin/com/example/poster/
  Application.kt        wiring: plugins, repositories, throttles, the notifier, and a routing
                        block that mounts the route packages below
  RouteSupport.kt       the handful of helpers more than one route package needs
  posts/                /posts: the feed, writing, completing, sharing, reporting
  accounts/             /accounts: your own account, editing it, deleting it
  groups/               /groups: rooms, members, roles, invites, joining and leaving (feature.groups)
  favorites/            /favorites: liking a post (feature.likes)
  social/               /bookmarks and /follows (feature.bookmarks, feature.follows)
  tags/                 /tags (feature.tags)
  feedback/             /feedback (feature.feedback)
  diagnostics/          /crashes, /events, /config, /support/interest — the unauthenticated ones
  comments/             /posts/{id}/comments and the comments repository (feature.comments)
  push/                 device registration, the activity list, FCM/APNs senders (feature.pushNotifications)
  uploads/              /uploads: the image store and routes (feature.images)
  auth/                 JWT config, register/login/refresh, verification, reset, social sign-in,
                        account pages (verify, reset, delete), group invitation page
  admin/                the HTMX admin panel and its session/CSRF handling
  model/                repositories over the SQLDelight schema (shared module)
  mail/                 Mailer interface, Resend implementation, invitation emails
  notify/               Telegram alerts (crashes, feedback, reports)
  LandingPage.kt, PrivacyPage.kt, TermsPage.kt, ChildSafetyPage.kt, PostSharePage.kt
  SiteChrome.kt, SiteLanguage.kt   shared page frame and language pick for the web pages
  WellKnownRoutes.kt    assetlinks.json, apple-app-site-association, Apple domain association
  DebugFixtures.kt      /debug/fixtures/* — reset and seed, development mode only
  HealthRoutes.kt       GET /health
```

## Running it

| How | Command | Notes |
|---|---|---|
| development | `scripts/run-local-backend.sh` | dev mode, own DB under `server/build/local/`, fixtures on |
| from the distribution | `./gradlew :server:installDist && server/build/install/server/bin/server` | needs `POSTER_JWT_SECRET` unless `JAVA_OPTS=-Dio.ktor.development=true` |
| Docker | `docker build -f Dockerfile.runtime -t poster-server server/build/install/server && docker run -p 8080:8080 -v poster-data:/data -e POSTER_JWT_SECRET=… poster-server` | what production runs |

The database path: `POSTER_DATABASE_PATH` (env) or `-Dposter.database=` (system
property), else `post.db` in the working directory. The Docker image sets it to
`/data/post.db`; mount a volume there.

## Environment variables

Nothing is required in development mode. Outside it, only `POSTER_JWT_SECRET`.
Everything else is off until set, and the startup log says which way each went.

### Core

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | listen port |
| `POSTER_DATABASE_PATH` | `post.db` (`/data/post.db` in Docker) | the SQLite file |
| `POSTER_JWT_SECRET` | *(required outside dev)* | ≥ 32 chars; signs access tokens. Changing it signs everyone out. `openssl rand -base64 48` |
| `POSTER_BASE_URL` | `app.webOrigin` from poster.properties | the public origin used in email links and admin alert links |
| `POSTER_FIXTURES_ENABLED` | `true` in dev mode, else `false` | registers `/debug/fixtures/*`. **Never on in production**: it wipes the database on request |
| `POSTER_ENFORCE_FOREIGN_KEYS` | `true` (server `main()` sets it) | SQLite cascades on; leave alone |
| `POSTER_UPLOADS_DIR` | `uploads` (`/data/uploads` in Docker) | where post images are stored when `feature.images` is on; back it up with the database. `-Dposter.uploads=` for a local run. [`Images.md`](Images.md) |

### Admin panel (`/admin`)

| Variable | Default | Purpose |
|---|---|---|
| `POSTER_ADMIN_ENABLED` | `false` | serve the panel at all |
| `POSTER_ADMIN_EMAIL` | — | the one account promoted to admin at startup. Must already exist (register in the app first, then restart). The only way an admin is created |
| `POSTER_ADMIN_SESSION_KEY` | random per start | signs the session cookie; set it so admin sessions survive restarts |
| `POSTER_ADMIN_INSECURE_COOKIE` | `false` | allow the cookie over plain HTTP (local use only) |

The panel: users (ban/unban, force verify, temporary password), posts (hide,
restore, fix language), reports queue, feedback with replies, tags (labels,
groups, delete), groups (create, rename, invites, add/remove members),
crashes, diagnostic events, audit log. Every action writes an audit row.

### Sign-in providers — see [`SignIn.md`](SignIn.md)

| Variable | Purpose |
|---|---|
| `POSTER_GOOGLE_CLIENT_ID` | Google OAuth client ids the server accepts tokens for, comma-separated (Web + iOS). Unset = Google sign-in answers 501 |
| `POSTER_APPLE_BUNDLE_ID` | the iOS bundle id, for native Apple sign-in tokens |
| `POSTER_APPLE_SERVICE_ID` | the Services ID, for the Android/web Apple flow |
| `POSTER_APPLE_DOMAIN_ASSOCIATION` | contents of Apple's `apple-developer-domain-association.txt`, served at `/.well-known/` |

### Deep links — see [`Deployment.md`](Deployment.md)

| Variable | Purpose |
|---|---|
| `POSTER_ANDROID_FINGERPRINTS` | SHA-256 certificate fingerprints (comma-separated) for `/.well-known/assetlinks.json`; unset = 404 (deliberately: Android caches an empty file, not a 404) |
| `POSTER_ANDROID_PACKAGE` | the applicationId in that file; defaults to the template's |
| `POSTER_APPLE_TEAM_ID` | enables `/.well-known/apple-app-site-association` for Universal Links |

### Email — see [`Email.md`](Email.md)

| Variable | Purpose |
|---|---|
| `POSTER_RESEND_API_KEY` | Resend API key. Unset = emails are printed to the log, not sent |
| `POSTER_MAIL_FROM` | `Name <no-reply@your.domain>`; defaults from `AppInfo` |

### Alerts, pages, misc

| Variable | Purpose |
|---|---|
| `POSTER_TELEGRAM_BOT_TOKEN`, `POSTER_TELEGRAM_CHAT_ID` | crash, feedback and report alerts to a Telegram chat. Unset = logged |
| `POSTER_FCM_PROJECT_ID`, `POSTER_FCM_SERVICE_ACCOUNT` | Android pushes (`feature.pushNotifications`): Firebase project id and the path to a service-account JSON. Unset = logged. [`PushNotifications.md`](PushNotifications.md) |
| `POSTER_APNS_KEY_PATH`, `POSTER_APNS_KEY_ID`, `POSTER_APNS_TEAM_ID`, `POSTER_APNS_TOPIC`, `POSTER_APNS_SANDBOX` | iOS pushes: the `.p8` key, its id, your team id, the bundle id; `true` for development builds. Unset = logged |
| `POSTER_CONTACT_EMAIL` | the address on the privacy and terms pages (default `privacy@<domain>`) |
| `POSTER_SAFETY_CONTACT` | the address on the child-safety page |
| `POSTER_OWNER_NAME` | the name after © on the landing page |
| `POSTER_PLAY_URL`, `POSTER_APPSTORE_URL` | store links on the landing page; unset shows "coming soon" |
| `POSTER_SITE_VERIFICATION` | Google Search Console HTML-tag token, emitted on every page |
| `POSTER_PAYMENTS_ENABLED` | `true` lets the app offer purchases (`GET /config`); off until you may legally take money |
| `POSTER_BACKUP_DIR`, `POSTER_BACKUP_KEEP`, `POSTER_BACKUP_UPLOAD` | for `scripts/backup-database.sh` and the pre-migration snapshot location |

## The API, briefly

The web app (`feature.web`): `POSTER_WEB_DIR` serves its bundle under `/app`; `POSTER_WEB_ORIGINS` (comma separated) allows other origins to call the API (CORS; development mode allows `http://localhost:8081`). See `docs/Web.md`.

Everything except the auth and public routes wants `Authorization: Bearer <access token>`.
Access tokens live 15 minutes; `POST /auth/refresh` with the refresh token (30 days,
rotated on use) gets a new pair. The Ktor client in `shared` does this automatically.

| Area | Routes |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/verify`, `/auth/verify/resend`, `/auth/password/forgot`, `/auth/password/reset`, `/auth/magic/request` + `/auth/magic` (feature.magicLink), `/auth/social` (Google/Apple id token), `/auth/apple/callback` (Apple form_post), `/auth/social/link`, `/auth/social/merge`, `GET /auth/me` |
| Posts | `GET /posts?limit&beforeDate&beforeGuid&tags&groups&q&following&saved`, `GET /posts/mine`, `GET /posts/byId/{id}`, `POST /posts` (create or update, author-gated), `DELETE /posts/{id}`, `POST /posts/{id}/complete`, `/reopen`, `/share`, `/report`, `GET /shared/{token}` |
| Comments | `GET|POST /posts/{id}/comments`, `DELETE /posts/{id}/comments/{commentId}` (feature.comments) |
| Images | `POST /uploads` (multipart, the caller's own), `GET /uploads/{id}` (under the post's visibility; avatars readable by any signed-in user) (feature.images) |
| Bookmarks | `GET /bookmarks`, `POST|DELETE /bookmarks/{postId}` (feature.bookmarks) |
| Follows | `GET /follows`, `POST|DELETE /follows/{userId}` (feature.follows) |
| Activity & push | `POST|DELETE /devices` (push tokens), `GET /notifications`, `GET /notifications/unread`, `POST /notifications/read` (feature.pushNotifications) |
| Likes | `GET /favorites/me`, `POST|DELETE /favorites/{postId}`, `GET /favorites/check/{postId}`, `GET /favorites/count/{postId}`, `GET /favorites/post/{postId}/people` — the liker is always the bearer token's subject, never a path segment |
| Groups | `GET /groups/public` (feature.publicGroups), `/byId/{id}`, `/byInvite/{code}`, `/user/{userId}`, `POST /groups/create`, `/join`, `POST /groups/{id}/visibility`, `DELETE /groups/leave`, `/{id}`, members: `GET /{id}/members`, `DELETE /{id}/members/{memberId}`, `POST /{id}/members/{memberId}/role`; invites: `GET|POST /{id}/invites`, `POST /{id}/invites/email`, `/{id}/invites/{code}/revoke` |
| Tags | `GET /tags`, `/tags/byName/{q}`, `/tags/forPost/{id}` |
| Accounts | `GET /accounts/byId/{id}` (own only), `POST /accounts` (own profile; role/status are never client-writable), `DELETE /accounts/{id}` |
| Feedback | `GET|POST /feedback` |
| Telemetry | `POST /crashes` (unauthenticated, size-capped, last 500 kept), `POST /events` (optional auth, fixed vocabulary) |
| Config | `GET /config` → `{ "paymentsEnabled": bool }` |
| Web | `/`, `/privacy`, `/terms`, `/child-safety`, `/verify`, `/reset`, `/delete-account`, `/join/{code}`, `/p/{token}`, `/.well-known/*`, `/health`, `/admin/*` |

Request/response shapes are the `@Serializable` classes in
`shared/src/commonMain/kotlin/com/example/poster/model/` — the same classes the
apps use, so the wire format cannot drift.

### Security decisions worth knowing before you change things

- Every write is gated on the bearer token's subject, not on ids in the body.
  `POST /accounts` applies an allow-list of fields; `role` and `status` cannot be
  set by a client.
- Reading a post by id obeys the same visibility rules as the feed, and a
  not-visible post is a 404 (whether it exists is itself private).
- Share tokens are random and unrelated to ids; only *public* posts can be shared.
- Invite codes are `SecureRandom`, 8 chars, no vowels or look-alikes, single use,
  optionally bound to an email address.
- Passwords: Argon2id. Login and reset attempts are throttled per address.
- Fixtures are dev-only. The image sets `POSTER_FIXTURES_ENABLED=false`.

## Data on disk

- `post.db` — everything. Schema: [`Database.md`](Database.md).
- `backups/pre-migration-v<N>-<stamp>.db` — a `VACUUM INTO` snapshot the server
  takes before running a schema migration. If it cannot write one, it refuses
  to start.
- `backups/post-<stamp>.db.gz` — nightly, from `scripts/backup-database.sh`
  (run it as a cron/scheduled task inside the container; it uses `sqlite3 .backup`
  so a live database copies cleanly, keeps the newest `POSTER_BACKUP_KEEP` = 14,
  and runs `POSTER_BACKUP_UPLOAD` — e.g. `rclone copyto {} remote:poster/` — to get
  copies off the host).

Restore: stop the server, `gunzip -c backup.db.gz > /data/restored.db`,
`sqlite3 /data/restored.db 'PRAGMA integrity_check;'`, move it over `post.db`, start.

## Logs

`CallLogging` prints `METHOD /path -> status` for every request. Startup prints
one line per optional subsystem: `google sign-in: enabled`, `admin panel:
disabled (set POSTER_ADMIN_ENABLED=true to enable)`, `mail: no
POSTER_RESEND_API_KEY, so mail is printed rather than sent`, and so on. When
something "does nothing", read those lines first.
