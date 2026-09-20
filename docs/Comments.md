# Comments

`feature.comments` (on by default). A text-only thread under each post.

- **Details screen**: the comments oldest first, a composer at the bottom.
  With `feature.authors` on (the default) each comment carries the writer's
  name and avatar; off, comments are anonymous like the posts. The reader's own
  are marked *You*.
- **Cards**: a comment count chip next to the like control when there are any.
- **Who may**: read — whoever may see the post (`visiblePostById`, the same
  rule as the post); write — anybody who can see it, with a confirmed email
  when `feature.emailVerificationRequired` is on; remove — the comment's author
  and the post's author. Deleting the post deletes its thread (`ON DELETE
  CASCADE`, enforced on the server).
- **Admin panel** `/admin/comments`: the newest 500 with hide / restore, each
  audited. Hidden comments disappear from the thread and the count.
- **Limits**: `CommentRules.TEXT_LIMIT` (1000 characters), checked in the
  composer and on the server.

| Where | What |
|---|---|
| `shared/.../db/Comment.sq`, `2.sqm` | Table and the migration that added it (version 3). |
| `shared/.../model/Comment.kt`, `Post.comments` | Wire model; the post carries its visible count. |
| `shared/.../network/CommentApi.kt`, `ktor/KtorCommentApi.kt`, `repository/CommentRepository.kt` | Client. Nothing is cached on the device; a thread is fetched when a post is opened. |
| `server/.../comments/` | `CommentsRepository`, `commentRoutes` (`/posts/{id}/comments`). |
| `composeApp/.../viewmodel/CommentsViewModel.kt`, `ui/components/CommentsSection.kt` | The thread and composer; `CommentCountBadge` in `PostCard.kt`. |

Not here: replies to comments (one level is enough for a small app; a
`parent_guid` column would add threading), reactions on comments, editing a
comment (remove and rewrite), reporting a single comment (report the post; the
admin page shows the thread).

Tests: `server/src/test/.../CommentRoutesTest.kt`; instrumented
`CommentsInstrumentedTest` (write, see, remove).
