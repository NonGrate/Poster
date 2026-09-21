# Deploying the server

The server is a single JVM process with a SQLite file. Anything that can run a
Docker image with a persistent volume and an https reverse proxy will do: a
€5 VPS with Coolify/Dokploy/CapRover, Fly.io, Railway, Render, a plain
`docker compose` on a box you own.

## The image

`Dockerfile.runtime` is the real image: Temurin 21 JRE, `curl` and `sqlite3`
(for the health check and backups), a non-root user, the Gradle `installDist`
output copied to `/app`, `ENTRYPOINT /app/bin/server`, `HEALTHCHECK` on
`GET /health`. It expects a volume at `/data`.

```bash
./gradlew :server:installDist
docker build -f Dockerfile.runtime -t poster-server server/build/install/server
docker run --rm -p 8080:8080 -v poster-data:/data \
  -e POSTER_JWT_SECRET="$(openssl rand -base64 48)" poster-server
```

The root `Dockerfile` is for hosts that insist on building from the repository:
it only `FROM`s the image CI already published, so the host never needs Gradle
or the Android SDK. Set its `SOURCE_IMAGE` build arg (or edit the default) to
your registry path.

## GitHub Actions → GHCR → your host

`.github/workflows/deploy-backend.yml` runs on every push to `main`:

1. `./gradlew :shared:jvmTest :server:test :server:installDist`
2. builds `Dockerfile.runtime` and pushes `ghcr.io/<owner>/<repo>-server:main`
   and `:<sha>`
3. if the `DEPLOY_WEBHOOK` secret is set, calls it (with `DEPLOY_TOKEN` as a
   bearer token when set) so the host pulls and restarts.

Make the GHCR package public (GitHub → your profile → Packages → package →
settings) or give the host a read-only PAT (`docker login ghcr.io` on the host).

## Environment on the host

Runtime variables, not build variables. Minimum:

```dotenv
PORT=8080
POSTER_DATABASE_PATH=/data/post.db
POSTER_FIXTURES_ENABLED=false
POSTER_JWT_SECRET=<openssl rand -base64 48>
POSTER_BASE_URL=https://your.domain
POSTER_UPLOADS_DIR=/data/uploads   # feature.images: same volume as the database
# feature.pushNotifications, when you have the keys (mount them as secret files):
# POSTER_FCM_PROJECT_ID=… POSTER_FCM_SERVICE_ACCOUNT=/run/secrets/fcm.json
# POSTER_APNS_KEY_PATH=/run/secrets/apns.p8 POSTER_APNS_KEY_ID=… POSTER_APNS_TEAM_ID=… POSTER_APNS_TOPIC=com.example.poster
```

Then whatever you turn on: admin panel, Google/Apple ids, Resend, Telegram,
fingerprints — where to obtain each is in [`Credentials.md`](Credentials.md),
defaults in [`Server.md`](Server.md); [`server/.env.example`](../server/.env.example)
has every variable ready to uncomment (`docker run --env-file`). Keep `POSTER_JWT_SECRET`
stable across deploys (rotating it signs everyone out) and out of the repo and
the mobile builds.

Health check: `GET http://127.0.0.1:8080/health` → 200 (interval 10 s, start
period 30 s). One replica only — SQLite has one writer.

**Do not put HTTP Basic Auth in front of the API.** It uses the `Authorization`
header, and so do the app's bearer tokens; a client cannot send both.

## Coolify, concretely

- New resource → Docker image (`ghcr.io/<owner>/<repo>-server:main`) or
  Dockerfile build pack from this repo (uses the root `Dockerfile`).
- Ports exposed: `8080`. Domain: `https://your.domain`. Force HTTPS on.
- Persistent storage: volume → `/data`.
- Environment variables: the block above, as runtime variables.
- Health check: method GET, path `/health`, port 8080.
- Rolling updates off (single SQLite volume), auto-deploy off (CI triggers it).
- Webhooks page → copy the deploy webhook URL → GitHub secret `DEPLOY_WEBHOOK`;
  Coolify API token with `deploy` permission → `DEPLOY_TOKEN`.
- Scheduled task for backups: command `/app/scripts/backup-database.sh`,
  `0 3 * * *`, in the server container. Set `POSTER_BACKUP_UPLOAD` to copy off host.

Other hosts are the same five facts: image, port, volume, env, webhook.

## Backups

`scripts/backup-database.sh` is packaged into the image at
`/app/scripts/backup-database.sh`. It takes a consistent copy with
`sqlite3 .backup`, verifies it with `PRAGMA integrity_check`, gzips it into
`$POSTER_BACKUP_DIR` (default `/data/backups`), keeps the newest 14, and runs
`$POSTER_BACKUP_UPLOAD` with `{}` replaced by the file (e.g.
`rclone copyto {} remote:poster/`) — failing loudly if the upload fails.

The server also snapshots the database by itself before any schema migration.
On-volume copies cover a bad migration or a bad delete; only the upload hook
covers losing the volume.

## Deep links that open the app

`poster://…` links work on any build with no setup. For **https** links
(`https://your.domain/join/CODE`, `/verify`, `/reset`, `/p/TOKEN`) to open the
app directly, the domain has to vouch for the app:

- **Android App Links** — `POSTER_ANDROID_FINGERPRINTS=<SHA-256 of the signing cert>`
  (comma-separated for debug + release; with Play App Signing it is *Google's*
  app-signing certificate, from Play Console). The server then serves
  `/.well-known/assetlinks.json`. Unset, it serves 404 on purpose: Android caches
  an empty file as "no app", but asks again after a 404.
- **iOS Universal Links** — `POSTER_APPLE_TEAM_ID=<team id>`; the server serves
  `/.well-known/apple-app-site-association` for `/verify*`, `/reset*`, `/join*`.
  Add the Associated Domains capability (`applinks:your.domain`) in Xcode.

Check: `curl -s https://your.domain/.well-known/assetlinks.json`.

## Web pages

The server is also the website: `/` (landing, with store links from
`POSTER_PLAY_URL` / `POSTER_APPSTORE_URL`), `/privacy`, `/terms`,
`/child-safety`, `/delete-account`. Store listings need those URLs — see
[`StoreListing.md`](StoreListing.md). Google's OAuth consent screen needs to
verify you own the domain: `POSTER_SITE_VERIFICATION=<Search Console token>` adds
the meta tag to every page.

## Upgrading

Push to `main`; CI publishes and the host restarts on the new image. Schema
migrations run at startup with a snapshot first ([`Database.md`](Database.md)).
Roll back by redeploying the previous `:<sha>` tag — but a database that has
already migrated forward stays migrated; that is what the snapshot is for.
