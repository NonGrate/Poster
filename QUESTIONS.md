# Open questions for the maintainer

Decisions I made while turning Share-Pray into this template that you may want
to revisit, plus things only you can answer. Delete this file (and
`CONVERSION_REPORT.md`) before publishing, or keep them as history.

## Decisions to confirm

1. **Location and name.** The copy is at `/Users/alisunov/Code/poster-template`
   (a sibling of the Share-Pray checkout, which I did not touch). The template is
   called **Poster**, after the directory you were working in; package
   `com.example.poster`, app id `com.example.poster`, scheme `poster://`, domain
   `poster.example.com`. All four are changed by `scripts/rename-app.sh`.
   *Do you want a different default name for the public repo?*

2. **No git history.** I did not `git init` or commit — the copy has no history,
   which is the point (Share-Pray's history contains real user data, personal
   emails, a RevenueCat key and an Apple team id). When you are happy:
   ```bash
   cd /Users/alisunov/Code/poster-template && git init -b main && git add -A && git commit -m "Poster: KMP app template"
   ```
   Note `versionCode` is counted from commits, so the first release build wants
   at least one.

3. **Vocabulary.** Prayer → **Post**, Pray (verb) → **Like**, Praying tab →
   **Liked**, "answered" → **resolved**, PrayingPerson → **Liker**, and (as you
   asked) Community → **Group** everywhere: Kotlin `Group`, routes `/groups`,
   flag `feature.groups`, strings `group_*`, visibility value `group`. The SQL
   table is `Groups` (plural) because `GROUP` is an SQLite keyword; the Post
   column is `groupId`. `TagGroup` (tag categories) is unrelated and unchanged.

4. **Tags.** The curated set is now seven generic categories (Health and wellbeing, Family and
   people, Work and study, Ideas, Hobbies, Places, Tech) × ten tags, en + ru. Tests were remapped to the
   new ids. *Keep, trim, or ship with an empty catalogue?*

5. **Licence.** `LICENSE` is MIT with "Poster template contributors" as the
   holder and a note on the bundled Roboto/Apple/Google assets. *Your name
   instead? Different licence?*

6. **Feature flags are compile-time** (`poster.properties` → `const val`), not
   Gradle sub-modules. R8 strips disabled code; only RevenueCat is a separately
   swappable source set (`composeApp/src/billing/{enabled,disabled}`) because it
   is the one dependency that matters for size. Splitting into real Gradle
   modules would have meant untangling ViewModels and Koin wiring across ~40
   files for little gain at this size. *Good enough, or do you want the module
   split?*

7. **Comments** (your example of a toggle) do not exist in Share-Pray, so there
   is no `feature.comments`. Listed in `docs/Roadmap.md` with where it would go.
   *Should the template ship a comments feature?*

8. **Migrations collapsed to v1.** The 15 historical `.sqm` files and baseline
   `.db` files are gone; the schema is the current `.sq` files, `databases/1.db`
   is regenerated. The server's legacy "pre-versioning" column patch-up code is
   removed. `SchemaMigrationTest` is now data-driven and will exercise real
   migrations as soon as a `2.sqm` exists.

9. **Legal pages** (privacy, terms, child safety) were rewritten generically and
   carry "Operator:" comments for the hotline, hosting location and dates. They
   still name Resend and RevenueCat as processors, which is true for the default
   build. *Fine for a template, or would you rather ship them as obvious
   placeholders?*

10. **Russian stays.** The template is bilingual (en/ru) end to end, with
    `feature.multiLanguage` to hide the pickers. Removing Russian is documented
    in `docs/Localization.md`. *Keep it as the worked example of a second
    language?*

11. **Two workflows.** `ci.yml` (tests + APK on every push) and
    `deploy-backend.yml` (GHCR image + generic webhook, Coolify-compatible). The
    Proxmox `deployServer` Gradle task was deleted.

12. **The placeholder mark** is a card-with-lines drawing in the default warm
    palette (`assets/poster-mark*.svg`, launcher icons, empty states regenerated
    from it). Deliberately plain so nobody ships it by accident.

13. **Images** (your request; local file storage confirmed 2026-09-19). One picture per post, stored as files on the
    server's volume (`POSTER_UPLOADS_DIR`), served under the post's own
    visibility rules, deleted with the post. Chosen over object storage (S3)
    because the template has one server and one volume; the store class is the
    seam if you outgrow that. The device downscales to 1600 px JPEG, so a
    picture is a few hundred KB. *Fine, or do you want S3/R2 support, several
    pictures per post, or thumbnails now?* The admin panel does not preview
    images (reports → hide → restore still works).

14. **Liquid design** (your request; native iOS bar chosen 2026-09-19 — see `docs/LiquidDesign.md`, the iOS app now uses a SwiftUI `TabView` with one Compose view controller per tab when `feature.liquidNavBar` is on, which is the system's Liquid Glass bar on iOS 26). Two flags, off by default:
    `feature.liquidDesign` (rounder shapes, translucent cards) and
    `feature.liquidNavBar` (floating glass capsule over the content). The glass
    is drawn by Compose — a recorded layer, blurred and tinted — so it is the
    same on Android 12+ and iOS; Android 8–11 gets the tint without the blur.
    It is **not** the native iOS 26 `UIGlassEffect` tab bar: that needs SwiftUI
    to own the tabs, which is a navigation rewrite (noted in `Roadmap.md`).
    *Is the Compose version what you meant, or do you want the native iOS bar
    even at that cost?*

15. **Tier 1 defaults** (authors on confirmed 2026-09-19). Comments, push/activity, magic link and authors are
    all **on** by default, so a fresh fork looks like a modern posts app;
    `feature.authors=false` restores Share-Pray's anonymity (and the liked-by
    opt-in). *Agree with on-by-default, or should authors stay off?*

16. **Magic link is sign-in only** (your call, 2026-09-19): an unknown address
    gets no mail and the same 204. Registration stays the first step.

17. **Push senders are plain HTTP**, no Firebase Admin SDK, no fastlane: an
    OAuth JWT for FCM v1 and an ES256 provider token for APNs, ~250 lines,
    tested against fakes. Real delivery needs your keys — I could not send one.

18. **Release workflows** use `upload-google-play` and `apple-actions` rather
    than fastlane or Gradle Play Publisher (fewer moving parts, no Ruby). They
    are unverified until the store accounts exist; the `.aab`/`.ipa` artifacts
    are useful before that.

## Things only you can do

- Replace `poster.example.com` with a real domain when you have one (or leave
  it as the template default — it is clearly fake).
- Decide whether to credit Share-Pray in the README ("grew out of a shipped
  app" is there without naming it).
- The instrumented suite has now been run class by class (see
  `CONVERSION_REPORT.md` §9–10). It needs an English-language emulator without
  Play Services and with hardware GPU; `docs/Testing.md` lists the symptoms
  when that is not the case. The image picker itself is not driven by any test.
- App Store / Play listing copy is not part of the repo any more (the `store/`
  folder was Share-Pray-specific); `docs/StoreListing.md` says what is needed.

## Small things I noticed and left alone

- `AGP 9` escape hatches (`android.builtInKotlin=false`, `android.newDsl=false`)
  in `gradle.properties` — still needed with the KMP plugin; the comment says so.
- `compose.materialIconsExtended` is deprecated upstream (pinned to 1.7.3);
  the build warns. Migrating to Material Symbols is a chore for later.
- The admin panel is not feature-flagged; it shows whatever tables have data.
- `login_google_soon` string and `googleCredentials.comingSoon` path are dead
  on both platforms now that native Google sign-in exists on iOS; harmless.
