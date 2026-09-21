import java.util.Properties

/**
 * One feature, declared once. Everything else derives from this list:
 *
 * - the generated `Features` object (`const val`s the app and the server read),
 * - the validation of `poster.properties` (unknown keys, dependencies, conflicts),
 * - `docs/Features.md`, the catalogue developers read to decide what to keep.
 *
 * Adding a feature is adding an entry here and gating the code on
 * `Features.<KEY>`; nothing else has to be kept in step by hand.
 */
data class PosterFeature(
    val key: String,
    val title: String,
    val area: String,
    val default: Boolean = true,
    /** Flags that must be on for this one to mean anything; the build refuses the combination. */
    val requires: List<String> = emptyList(),
    /** Flags that cannot be on at the same time; the build refuses the combination. */
    val conflicts: List<String> = emptyList(),
    /** What turning it off removes from the app. */
    val app: String,
    /** What turning it off removes from the server or the build. */
    val server: String,
    val notes: String = "",
) {
    val constName: String get() = key.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
}

object PosterFeatures {
    val all: List<PosterFeature> = listOf(
    PosterFeature("groups", "Groups", "Posts and groups", default = true, requires = listOf(), conflicts = listOf(),
        app = "Groups screen and settings section, group picker in the post form, group filter, invite-code field at registration, invite deep links, \"joined\" announcements",
        server = "`/groups/*`, `/join/{code}` page",
        notes = "Invite-only circles a post can be shared with. `publicGroups` and the group option of `postVisibility` build on it."),
    PosterFeature("publicGroups", "Public groups", "Posts and groups", default = true, requires = listOf("groups"), conflicts = listOf(),
        app = "\"Public groups\" list and \"Anyone can find and join\" switch in the groups sheet, a visibility toggle for owners",
        server = "`GET /groups/public`, `POST /groups/{id}/visibility`, joining by id allowed only for public groups. Off = every group is invite-only.",
        notes = ""),
    PosterFeature("postVisibility", "Post visibility", "Posts and groups", default = true, requires = listOf(), conflicts = listOf(),
        app = "The Everyone / Group / Only me picker (every post is public), the default-visibility setting",
        server = "nothing \u2014 visibility is still enforced for stored data",
        notes = "The Group option needs `groups`; without it the picker offers Everyone and Only me."),
    PosterFeature("postCompletion", "Mark as resolved", "Posts and groups", default = true, requires = listOf(), conflicts = listOf(),
        app = "\"Mark as resolved\" / \"Reopen\", the Resolved badge",
        server = "`POST /posts/{id}/complete`, `/reopen`",
        notes = ""),
    PosterFeature("tags", "Tags", "Posts and groups", default = true, requires = listOf(), conflicts = listOf(),
        app = "Tag picker in the post form (and the \"at least one tag\" rule), tag chips on cards, tag filter",
        server = "`/tags/*` (tags on posts are still stored and returned)",
        notes = ""),
    PosterFeature("images", "Images on posts", "Posts and groups", default = true, requires = listOf(), conflicts = listOf(),
        app = "The picture row in the form, pictures on cards and details, the image loader",
        server = "`POST/GET /uploads`, the `image` field on incoming posts (dropped), the share page's image, the upload directory. The privacy and child-safety pages switch wording. [`Images.md`](Images.md)",
        notes = "Avatars (`authors`) need this for the upload; without it profiles have no picture."),
    PosterFeature("sharing", "Share links", "Posts and groups", default = true, requires = listOf(), conflicts = listOf(),
        app = "Share action, opening `\u2026://post/TOKEN` links",
        server = "`POST /posts/{id}/share`, `GET /shared/{token}`, the `/p/{token}` web page",
        notes = ""),
    PosterFeature("reports", "Report a post", "Posts and groups", default = true, requires = listOf(), conflicts = listOf(),
        app = "\"Report this post\"",
        server = "`POST /posts/{id}/report`",
        notes = ""),
    PosterFeature("likes", "Likes", "Social", default = true, requires = listOf(), conflicts = listOf(),
        app = "Like button and counts, the \"Liked\" tab, who-liked roster, \"show my name\" setting",
        server = "`/favorites/*`",
        notes = ""),
    PosterFeature("comments", "Comments", "Social", default = true, requires = listOf(), conflicts = listOf(),
        app = "The thread on Details, the count on cards",
        server = "`/posts/{id}/comments`, `/admin/comments`, the `comments` count on posts. [`Comments.md`](Comments.md)",
        notes = ""),
    PosterFeature("authors", "Authors (names and avatars)", "Social", default = true, requires = listOf(), conflicts = listOf(),
        app = "Name and avatar on every post and comment, a picture on the profile; hides the \"show my name\" opt-in",
        server = "Posts and comments carry `authorName` / `authorPhoto`; avatars are uploads any signed-in reader may fetch; the liked-by roster names everybody. Off = the anonymous default. Child-safety page wording follows.",
        notes = "Off keeps the anonymous default: a name shows only where the person opted in."),
    PosterFeature("follows", "Follows", "Social", default = true, requires = listOf("authors"), conflicts = listOf(),
        app = "\"Following\" chip in the feed filter, Follow/Unfollow by tapping a post's author (needs `feature.authors`)",
        server = "`Follow` table, `GET/POST/DELETE /follows[/{userId}]`, `GET /posts?following=true`. Following never widens what a reader may see.",
        notes = ""),
    PosterFeature("bookmarks", "Bookmarks", "Social", default = true, requires = listOf(), conflicts = listOf(),
        app = "\"Save for later\" in a post's menu, a \"Saved\" chip in the feed filter",
        server = "`Bookmark` table, `GET/POST/DELETE /bookmarks[/{postId}]`, `GET /posts?saved=true`. Private, unlike likes.",
        notes = ""),
    PosterFeature("feedback", "Feedback", "Social", default = true, requires = listOf(), conflicts = listOf(),
        app = "The Feedback section and screen",
        server = "`/feedback`",
        notes = ""),
    PosterFeature("magicLink", "Passwordless sign-in", "Accounts and sign-in", default = true, requires = listOf(), conflicts = listOf(),
        app = "\"Sign in with an emailed link\" on the login screen and the link dialog",
        server = "`POST /auth/magic/request`, `POST /auth/magic`, the `/magic` landing page. Needs the mailer. [`SignIn.md`](SignIn.md#passwordless-sign-in-magic-link)",
        notes = ""),
    PosterFeature("googleSignIn", "Google sign-in", "Accounts and sign-in", default = true, requires = listOf(), conflicts = listOf(),
        app = "The button, even if a client id is configured",
        server = "\u2014 (the server enables a verifier when its env var is set; see `SignIn.md`)",
        notes = ""),
    PosterFeature("appleSignIn", "Apple sign-in", "Accounts and sign-in", default = true, requires = listOf(), conflicts = listOf(),
        app = "The Apple button, even if a client id is configured",
        server = "\u2014 (the server enables a verifier when its env var is set; see `SignIn.md`)",
        notes = ""),
    PosterFeature("emailVerificationRequired", "Email verification before writing", "Accounts and sign-in", default = true, requires = listOf(), conflicts = listOf(),
        app = "\u2014",
        server = "The \"confirm your email first\" check on writing a post or creating a group. Verification emails are still sent.",
        notes = ""),
    PosterFeature("multiLanguage", "Per-post language", "Accounts and sign-in", default = true, requires = listOf(), conflicts = listOf(),
        app = "Per-post language field, reading-languages profile fields",
        server = "\u2014 (language is still stored; defaults to `en`)",
        notes = ""),
    PosterFeature("pushNotifications", "Activity and push", "Notifications", default = true, requires = listOf(), conflicts = listOf(),
        app = "The Activity screen, the bell on Home, device registration",
        server = "`/notifications*`, `/devices`, the notifier behind likes, comments and group changes, the FCM/APNs senders. [`PushNotifications.md`](PushNotifications.md)",
        notes = ""),
    PosterFeature("pushPostTitles", "Post titles in pushes", "Notifications", default = true, requires = listOf("pushNotifications"), conflicts = listOf(),
        app = "Nothing: the app reads the text the server sent",
        server = "The post's title inside a push body — off, a push says \"Somebody liked your post\" with no quote",
        notes = "A push reaches the phone through Apple or Google, who see its text, and it lands on a lock screen where anybody holding the phone can read it. The title is the author's own words going to the author's own device, so nothing leaks to other people — but on an app about difficult things, a title on a lock screen in front of somebody else is a real moment. Turn it off for a quieter notification that says what happened and nothing about what it was about."),
    PosterFeature("dailyReminder", "Daily reminder", "Notifications", default = true, requires = listOf(), conflicts = listOf(),
        app = "The reminder rows in Settings",
        server = "\u2014",
        notes = ""),
    PosterFeature("drafts", "Drafts", "Offline", default = true, requires = listOf(), conflicts = listOf(),
        app = "Closing the post form without posting keeps the words; the next \"Add\" starts from them",
        server = "On the device only (`AppPreferences.postDraft`, one draft, no image). Cleared on post and on sign-out.",
        notes = ""),
    PosterFeature("offlineOutbox", "Offline outbox", "Offline", default = true, requires = listOf(), conflicts = listOf(),
        app = "A post written or edited with no connection is kept, shown under My Posts as \"Not sent yet\", and sent on the next refresh",
        server = "Device only: `Outbox` table, `PostRepository.flushOutbox()`. Posts with a new image are not queued (the bytes live in memory). Likes already roll back on failure and are not queued.",
        notes = ""),
    PosterFeature("liquidDesign", "Liquid look", "Look", default = false, requires = listOf(), conflicts = listOf(),
        app = "Rounder shapes and translucent cards drawn by Compose ([LiquidDesign.md](LiquidDesign.md))",
        server = "\u2014",
        notes = ""),
    PosterFeature("liquidNavBar", "Floating glass tab bar", "Look", default = false, requires = listOf(), conflicts = listOf(),
        app = "A floating glass tab bar over the content; on iOS the native SwiftUI tab bar (Liquid Glass on iOS 26) ([LiquidDesign.md](LiquidDesign.md))",
        server = "\u2014",
        notes = ""),
    PosterFeature("support", "In-app purchases (RevenueCat)", "Monetisation", default = true, requires = listOf(), conflicts = listOf(),
        app = "The whole Support section; also **drops the RevenueCat SDK from the Android build** (see below)",
        server = "`/config`, `/support/interest`",
        notes = ""),
    PosterFeature("crashReports", "Crash reports", "Diagnostics", default = true, requires = listOf(), conflicts = listOf(),
        app = "Installing the crash handler and uploading stored crashes",
        server = "`POST /crashes`",
        notes = ""),
    PosterFeature("telemetry", "Diagnostic events", "Diagnostics", default = true, requires = listOf(), conflicts = listOf(),
        app = "Diagnostic events (`EventReporter` becomes a no-op)",
        server = "`POST /events`",
        notes = ""),
    PosterFeature("desktop", "Desktop (JVM) target", "Platforms", default = false, requires = listOf(), conflicts = listOf("support"),
        app = "A JVM desktop app: `./gradlew :composeApp:run`",
        server = "Adds `jvm(\"desktop\")` to `composeApp`; needs `feature.support=false`. Off by default. See [Desktop.md](Desktop.md).",
        notes = "Off by default: it changes dependency resolution for every build."),
    PosterFeature("web", "Web (Kotlin/Wasm) target", "Platforms", default = false, requires = listOf(), conflicts = listOf("support"),
        app = "The app in a browser (Kotlin/Wasm): `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`",
        server = "Adds `wasmJs()` to `shared` and `composeApp`; online only, no SQLite cache; needs `feature.support=false`. Off by default. See [Web.md](Web.md).",
        notes = "Off by default, online only (no SQLite in the browser)."),
    )

    val keys: List<String> get() = all.map { it.key }
    fun knows(key: String): Boolean = key.startsWith("feature.") && key.removePrefix("feature.") in keys

    /** Every flag with its value from the properties (or its default), after checking the combination. */
    fun resolve(props: Properties): Map<String, Boolean> {
        val enabled = all.associate { f -> f.key to (props.getProperty("feature.${f.key}")?.trim()?.toBoolean() ?: f.default) }
        for (f in all) {
            if (enabled.getValue(f.key)) {
                val missing = f.requires.filterNot { enabled.getValue(it) }
                require(missing.isEmpty()) {
                    "poster.properties: feature.${f.key}=true needs ${missing.joinToString(" and ") { "feature.$it=true" }} (docs/Features.md)"
                }
                val clash = f.conflicts.filter { enabled.getValue(it) }
                require(clash.isEmpty()) {
                    "poster.properties: feature.${f.key}=true needs ${clash.joinToString(" and ") { "feature.$it=false" }}: the billing SDK has no ${f.title.lowercase()} build (docs/Features.md)"
                }
            }
        }
        return enabled
    }

    /** docs/Features.md: the catalogue, generated so it cannot drift from this list. */
    fun markdown(): String = buildString {
        appendLine("# Features")
        appendLine()
        appendLine("<!-- GENERATED from buildSrc/src/main/kotlin/PosterFeatures.kt by ./gradlew :shared:generatePosterConfig — edit there, not here. -->")
        appendLine()
        appendLine("Every feature is a `feature.<key>=true|false` line in `poster.properties`. Off means")
        appendLine("compiled out: the app loses the screens and the Koin bindings, the server does not")
        appendLine("register the routes (a stale client gets 404), and the tests of that feature skip")
        appendLine("themselves. The build refuses a combination that cannot work (a flag whose")
        appendLine("dependency is off, or two that conflict) and names the pair.")
        appendLine()
        appendLine("Change a flag, then `./gradlew :shared:jvmTest :server:test :composeApp:testRemoteDebugUnitTest`")
        appendLine("(or `/toggle-feature` with an AI assistant). `docs/Configuration.md` explains the")
        appendLine("mechanism; the per-feature guides are linked from the rows.")
        appendLine()
        appendLine("| Area | Feature | Flag | Default | Needs | Not with |")
        appendLine("|---|---|---|---|---|---|")
        for (f in all) {
            appendLine("| ${f.area} | ${f.title} | `feature.${f.key}` | ${if (f.default) "on" else "off"} | ${f.requires.joinToString(", ") { "`$it`" }} | ${f.conflicts.joinToString(", ") { "`$it`" }} |")
        }
        for (area in all.map { it.area }.distinct()) {
            appendLine()
            appendLine("## $area")
            appendLine()
            appendLine("| Flag | Off removes from the app | Off removes from the server / build |")
            appendLine("|---|---|---|")
            for (f in all.filter { it.area == area }) {
                val notes = if (f.notes.isBlank()) "" else " ${f.notes}"
                appendLine("| `feature.${f.key}` (${if (f.default) "on" else "off"} by default) | ${f.app} | ${f.server}$notes |")
            }
        }
    }
}
