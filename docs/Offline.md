# Offline: cache, outbox, drafts

## The cache (always on)

The feed, My Posts and Liked are read from the device's SQLite database
(`PostLocalStore`, the same SQLDelight schema the server uses). A cold start
shows what was there last time; a refresh replaces it. The author line and the
comment count are cached with the row (`Post.comments`, a stub `User` row) so a
card reads the same from disk as it did fresh.

## Outbox — `feature.offlineOutbox`

An add or edit whose request fails on the connection is kept:

- `Outbox(guid, kind, payload, created_at)`, device only. An edit of a post the
  server has never seen stays a single `add` with the newer words.
- The post shows under My Posts with a "Not sent yet" pill (`post_unsent_label`).
- `PostRepository.flushOutbox()` runs at the start of every feed refresh
  (arrival, pull, the periodic refresh), oldest first; it stops at the first
  connection failure and drops anything the server refuses for another reason
  (retrying cannot change a 400).
- Not queued: posts with a new image (the bytes live in memory, not on disk),
  likes (they already roll back on failure), deletes.
- No connectivity listener on purpose: the refresh cadence is the retry.

`OutboxTest` (shared, JVM) covers queue, edit-while-offline, flush order and
the non-connection refusal.

## Drafts — `feature.drafts`

Closing the post form without posting keeps title, text, tags, group,
visibility and language as one `PostDraft` in `AppPreferences`; the next "Add"
starts from it. Cleared when the post is sent and on sign-out. One draft, no
image. Several named drafts would need a table and a list; `PostDraft` is the
seam. `DraftsInstrumentedTest` covers close → reopen → clear.
