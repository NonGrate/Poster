#!/usr/bin/env bash
#
# Takes one backup of the server's database.
#
# Runs inside the running container as a scheduled task (cron, Coolify, ...) — see
# docs/Deployment.md. `sqlite3 .backup` is used rather than `cp` because the
# server is writing while this runs: a plain copy of a live database can be
# torn, and a torn backup is worse than none, because it looks like one.
#
# The backup lands on the same volume as the database. That covers a bad
# migration, an application bug, or a delete that should not have happened. It
# does not survive losing the volume or the machine — for that the copies have
# to leave the host, which is the OFF-HOST HOOK at the bottom of this file.
#
# Every setting is an environment variable so the test can point the whole
# thing at a temporary directory.

set -euo pipefail

DATABASE="${POSTER_DATABASE_PATH:-/data/post.db}"
BACKUP_DIR="${POSTER_BACKUP_DIR:-$(dirname "$DATABASE")/backups}"
KEEP="${POSTER_BACKUP_KEEP:-14}"

if [ ! -f "$DATABASE" ]; then
    echo "backup: no database at $DATABASE" >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"

stamp="${POSTER_BACKUP_STAMP:-$(date -u +%Y-%m-%dT%H-%M-%SZ)}"
target="$BACKUP_DIR/post-$stamp.db.gz"
working="$(mktemp "${TMPDIR:-/tmp}/poster-backup.XXXXXX")"
trap 'rm -f "$working" "$working.gz"' EXIT

# .backup takes a consistent snapshot through SQLite itself, so it is safe
# against whatever the server is doing at the time.
sqlite3 "$DATABASE" ".backup '$working'"

# A backup nobody can open is not a backup. Ask SQLite before keeping it.
integrity="$(sqlite3 "$working" 'PRAGMA integrity_check;')"
if [ "$integrity" != "ok" ]; then
    echo "backup: integrity check failed: $integrity" >&2
    exit 1
fi

gzip -c "$working" > "$working.gz"
# Move into place only once it is complete, so a backup that was interrupted
# never appears in the directory as though it had succeeded.
mv "$working.gz" "$target"
echo "backup: wrote $target ($(wc -c < "$target") bytes)"

# Retention. Newest first, everything past $KEEP goes.
removed=0
while IFS= read -r old; do
    rm -f "$old"
    removed=$((removed + 1))
done < <(ls -1t "$BACKUP_DIR"/post-*.db.gz 2>/dev/null | tail -n "+$((KEEP + 1))")
if [ "$removed" -gt 0 ]; then
    echo "backup: removed $removed old backup(s), keeping $KEEP"
fi

# POST IMAGES
# ───────────
# feature.images stores pictures as files beside the database. They are not
# in the .db, so a database backup alone would restore posts with holes where
# the pictures were. One tarball per run, pruned with the same KEEP.
UPLOADS="${POSTER_UPLOADS_DIR:-$(dirname "$DATABASE")/uploads}"
if [ -d "$UPLOADS" ] && [ -n "$(ls -A "$UPLOADS" 2>/dev/null)" ]; then
    uploads_target="$BACKUP_DIR/uploads-$stamp.tar.gz"
    tar -czf "$uploads_target" -C "$(dirname "$UPLOADS")" "$(basename "$UPLOADS")"
    echo "backup: wrote $uploads_target ($(du -h "$uploads_target" | cut -f1))"
    while IFS= read -r old; do rm -f "$old"; done < <(ls -1t "$BACKUP_DIR"/uploads-*.tar.gz 2>/dev/null | tail -n "+$((KEEP + 1))")
fi

# OFF-HOST HOOK
# ─────────────
# Everything above survives a bad migration but not a lost volume. To send the
# copies somewhere else, set POSTER_BACKUP_UPLOAD to a command that takes
# the file as its one argument, for example:
#
#   POSTER_BACKUP_UPLOAD="rclone copyto {} remote:poster/"
#
# {} is replaced with the path to the backup just written.
if [ -n "${POSTER_BACKUP_UPLOAD:-}" ]; then
    command="${POSTER_BACKUP_UPLOAD//\{\}/$target}"
    echo "backup: uploading with: $command"
    # A failed upload must fail the task — a backup that silently stopped
    # leaving the host is the thing this hook exists to prevent.
    eval "$command"
fi
