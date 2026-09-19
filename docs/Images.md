# Images on posts

`feature.images` (on by default). One still picture per post: picked with the
system photo picker, downscaled on the device, uploaded to the server, shown on
the card, the details screen and the public share page, and deleted with the
post.

## What the person sees

- **Form** — a *Add a picture* button under the message. Picking one shows it
  with *Change picture* / *Remove picture*. Nothing is uploaded until *Save*;
  the post is only saved once the upload succeeded, so there is never a post
  whose picture silently went missing.
- **Cards** — the picture below the text, full width, cropped to a comfortable
  band (between 4:5 and 2.2:1). On the details screen it keeps its own
  proportions.
- **Share page** (`/p/TOKEN`) — the picture, for public posts only.
- **Permissions** — none. Android uses the Photo Picker, iOS `PHPicker`; both
  hand over just the chosen file.

## How it works

```
form ──pick──▶ device: decode, downscale to ≤1600 px, JPEG q82 (ImageRules)
     ──save──▶ POST /uploads (multipart "file")  ──▶ { "id": "<32 hex>.jpg" }
              POST /posts { …, "image": "<id>" }
cards ───────▶ GET /uploads/<id>  (bearer token; served under the post's visibility)
share page ──▶ GET /p/<token>/image (public posts only)
```

| Where | What |
|---|---|
| `shared/.../domain/validation/ImageRules.kt` | 5 MB cap, 1600 px longest side, JPEG quality, format sniffing by magic bytes (JPEG/PNG/WebP). Used by the form *and* the server. |
| `shared/.../model/Post.kt` → `image: String?` | The id. `null` for no picture. Column `Post.image` (migration `1.sqm`). |
| `shared/.../network/PostApi.uploadImage / fetchImage` | Multipart upload; authenticated fetch. |
| `shared/.../repository/PostRepository.addPost/updatePost(post, newImage)` | Upload first, then save the post pointing at the id. |
| `composeApp/.../ui/platform/ImagePicker.kt` (+ `.android.kt`, `.ios.kt`) | `rememberImagePicker { bytes -> }`: the platform picker plus the downscale. |
| `composeApp/.../ui/images/` | `PostImageLoader` (authenticated fetch + small in-memory cache), `PostImage` (the card picture), `PostPhotoRow` (the form section), `PostImageChange` (Keep / Remove / New). |
| `server/.../uploads/UploadStore.kt` | Files on disk under `POSTER_UPLOADS_DIR`, random ids, orphan sweep. |
| `server/.../uploads/UploadRoutes.kt` | `POST /uploads`, `GET /uploads/{id}`. |
| `server/.../Application.kt` | `POST /posts` refuses an id the server did not store; replacing/removing a picture or deleting the post (or the account) deletes the file. |

### Who can see an image

The same rule as the post: `GET /uploads/{id}` looks up the post carrying the
id and serves the file only if `visiblePostById(viewer, post)` would return the
post — public to any signed-in account, group to members, private to the
author, hidden (moderated) to nobody. An image no post carries yet is
readable by any signed-in account for 24 hours (`UploadStore.PENDING_GRACE`);
that is the window between upload and save, and the id is 128 random bits.
Nothing is served without a bearer token except `/p/TOKEN/image` for a public,
shared post.

### Storage and cleanup

- Directory: `POSTER_UPLOADS_DIR`, default `uploads` under the working
  directory; `/data/uploads` in the Docker image, so the one volume holds the
  database and the pictures. **Back it up with the database** —
  `scripts/backup-database.sh` tars it alongside the `.db.gz` when it exists.
- Deleting a post deletes its file; replacing the picture deletes the old one;
  deleting an account deletes the pictures of its posts. Hiding a post in the
  admin panel keeps the file (restore brings it back).
- Orphans (uploaded, never attached) older than 24 h are removed **at server
  start**. That is deliberate — a restart is the one thing every deployment
  does; if yours runs for months without one, add a timer around
  `UploadStore.sweepOrphans`.
- Size: 5 MB per file on the wire, but the device sends ~150–400 KB JPEGs.
  A thousand posts with pictures is well under half a gigabyte.

### Turning it off

`feature.images=false`: the form row, the card picture and the loader are
compiled out; the server drops the `image` field from incoming posts, mounts no
`/uploads` routes and creates no directory. Existing rows keep their ids
(harmless). The privacy and child-safety pages switch back to their
"no images" wording automatically (`Features.IMAGES` in `PrivacyPage.kt`,
`ChildSafetyPage.kt`).

### Not here (yet)

- Several pictures per post, video, files: the model is one `image` column on
  purpose; a second would want a `PostImage` table.
- Thumbnails / a CDN: the device already downscales, and the server sets
  `Cache-Control: private, max-age=86400`. Add resizing on the server when a
  feed of full-size pictures gets slow on cellular.
- Image moderation in the admin panel: the post list shows text only. The
  report → hide → file stays → restore flow already covers the removal side.
- EXIF orientation on Android 24–27 (the `BitmapFactory` path); 28+ and iOS
  rotate correctly.

### Tests

`server/src/test/.../UploadRoutesTest.kt` (upload, refusal of non-images and
oversized files, visibility of a private/public post's picture, cleanup on
delete and replace), `UploadStoreTest.kt` (ids, path safety, sweep). The
instrumented suite does not pick a photo (the picker is a system UI); the
form row has test tags (`post_photo_pick`, `post_photo_remove`,
`post_photo_preview`) if you want to drive it with a fake picker.
