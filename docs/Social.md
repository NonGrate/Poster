# Follows, bookmarks, public groups

Three flags, all on by default, all server-backed and all bound by one rule:
**none of them widens what a reader may see.** The feed query decides
visibility first; these only narrow it.

## Follows — `feature.follows` (needs `feature.authors`)

- Tap the author line on a card (feed or details) → Follow / Unfollow.
- "Following" chip in the feed filter sheet; the applied-filter row shows it.
- Server: `Follow(follower, followed)`, `GET /follows` (ids),
  `POST|DELETE /follows/{userId}` (400 for yourself, 404 for nobody),
  `GET /posts?following=true` adds one clause to the feed query.
- App: `FollowsViewModel` (an `IdSetViewModel`: a server-kept set of ids,
  toggled optimistically, put back if the server refuses), refreshed when the
  signed-in user changes.
- Not built: follower counts, "X followed you" notifications. `Notifier` is the
  seam for the second.

## Bookmarks — `feature.bookmarks`

- "Save for later" / "Remove from saved" in a card's menu (feed and details);
  "Saved" chip in the feed filter. Private: nobody else sees what you saved,
  unlike likes.
- Server: `Bookmark(user_id, post_id)`, `GET /bookmarks`,
  `POST|DELETE /bookmarks/{postId}` (saving a post you cannot see is 404),
  `GET /posts?saved=true`.
- App: `BookmarksViewModel`, same shape as follows.

## Public groups — `feature.publicGroups`

- A group is `private` (invite only, the default) or `public` (listed, anybody
  may join). Owners flip it in the manage panel; creating offers the switch.
- "Public groups" section in the join/create sheet with member counts and a
  Join button.
- Server: `Groups.visibility`, `GET /groups/public`, `POST /groups/{id}/visibility`
  (owner only), `POST /groups/join` by id succeeds only for public groups.
- Off: every group is invite only, exactly as before the flag existed.

## Tests

`FollowRoutesTest`, `BookmarkRoutesTest`, `PublicGroupsTest` on the server;
`FollowsInstrumentedTest` on the device (follow → filter → unfollow).
