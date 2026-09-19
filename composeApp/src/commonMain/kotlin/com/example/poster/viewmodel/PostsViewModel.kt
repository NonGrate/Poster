package com.example.poster.viewmodel

import com.example.poster.ui.screens.matching
import com.example.poster.model.AppEventName
import com.example.poster.model.AppEventSeverity
import com.example.poster.model.Post
import com.example.poster.network.EmailNotVerified
import com.example.poster.telemetry.EventReporter
import com.example.poster.repository.PAGE_SIZE
import com.example.poster.repository.PostRepository
import com.example.poster.repository.SessionRepository
import com.example.poster.domain.validation.TagRules
import com.example.poster.ui.screens.PostFilter
import com.example.poster.ui.screens.asFeedShows
import com.example.poster.ui.screens.fromGroups
import com.example.poster.ui.screens.withTags
import com.example.poster.util.DispatcherProvider
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.io.IOException

/**
 * ViewModel for managing posts.
 *
 * ## Why the list does not change under you
 *
 * The database is the truth and it can change at any moment — a background
 * refresh, someone else's post arriving. Binding the feed straight to it means
 * the list reorders while somebody is reading it, which is how you lose your
 * place mid-sentence.
 *
 * So [feed] is what the Home screen shows, and it only ever changes when the
 * reader asks. Fresh content that arrives meanwhile waits in [pendingCount], the
 * feed offers a button, and [showPending] is what accepting that offer does.
 *
 * The exception is your own actions. Adding, editing or completing a post
 * applies at once: you already know the list changed, because you changed it.
 *
 * [posts] is the truth, ungated, for every other screen. My Posts hid a
 * person's own posts behind the feed's button for a day — a button that is not
 * on that screen, so there was no way to ask for them.
 */
class PostsViewModel(
    private val repository: PostRepository,
    private val session: SessionRepository,
    dispatchers: DispatcherProvider,
    // Optional so previews and tests need not supply one; the app injects it.
    private val events: EventReporter? = null,
) : ScopedViewModel(dispatchers) {

    /** Everything this device knows, as soon as it knows it. */
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    /**
     * Everything this person wrote, from its own request and its own rows.
     *
     * Not a filter over [posts]: that is the feed, the feed is a page, and a
     * post of yours older than the page would simply not be in it. My Posts
     * showing fewer posts than somebody wrote reads as the app having lost
     * one, which is the worst thing this app could appear to do.
     */
    private val _myPosts = MutableStateFlow<List<Post>>(emptyList())
    val myPosts: StateFlow<List<Post>> = _myPosts.asStateFlow()

    /**
     * Posts opened from a shared link that are in neither the feed nor My
     * Posts — a stranger's public post. Kept in their own list so the feed's
     * disk flow cannot evict them mid-view, and so Details can find them by guid.
     */
    private val _sharedPosts = MutableStateFlow<List<Post>>(emptyList())
    val sharedPosts: StateFlow<List<Post>> = _sharedPosts.asStateFlow()

    fun cacheSharedPost(post: Post) {
        if (_sharedPosts.value.none { it.guid == post.guid }) {
            _sharedPosts.value = _sharedPosts.value + post
        }
    }

    /** What Home shows: the same posts, held still while they are being read. */
    private val _feed = MutableStateFlow<List<Post>>(emptyList())
    val feed: StateFlow<List<Post>> = _feed.asStateFlow()

    /** Newer than what is on screen, waiting to be asked for. */
    private val _pending = MutableStateFlow<List<Post>?>(null)

    private val _error = MutableStateFlow<UiError?>(null)
    val error: StateFlow<UiError?> = _error.asStateFlow()

    /** True while a fetch a person asked for is in flight, for the pull indicator. */
    /**
     * The button protects a reader from the list moving. Until the first full
     * refresh lands there is no reader to protect and nothing settled to move:
     * the store fills in pieces — favorites first — and holding those back left
     * a cold start showing two posts and "5 new posts" over the rest.
     */
    private var settled = false

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * What the feed is narrowed to: a group, and optionally tags inside it.
     *
     * Deliberately not remembered anywhere: a filter is something you set to
     * look for one thing, and finding the feed still narrowed days later — with
     * the reason a chip you scrolled past — reads as posts having gone
     * missing. It is cleared by signing out with the rest of this state.
     */
    private val _tagFilter = MutableStateFlow(PostFilter())
    val tagFilter: StateFlow<PostFilter> = _tagFilter.asStateFlow()

    /**
     * How many pending posts are not already on screen — derived, not stored.
     *
     * A stored count went stale: the feed is written several times in a refresh
     * burst, and a count frozen during one of those transient states stayed at 3
     * even once the feed had adopted those very posts — so the button offered
     * posts that were already there and did nothing when tapped. Deriving it
     * from the live feed vs pending means the instant the feed shows them the
     * count is zero and the button goes. Compared through [asFeedShows], the same
     * rule the screen uses, so it counts only what the reader would actually see.
     */
    val pendingCount: StateFlow<Int> =
        combine(_feed, _pending, _tagFilter) { feed, pending, filter ->
            if (pending == null) {
                0
            } else {
                val viewer = session.user.value?.guid
                val now = Clock.System.now()
                val onScreen = feed.asFeedShows(viewer, now, filter).map { it.guid }.toSet()
                pending.asFeedShows(viewer, now, filter).count { it.guid !in onScreen }
            }
        }.stateIn(scope, SharingStarted.Eagerly, 0)

    /**
     * Opening a group, or shutting the one already open.
     *
     * [tagsInGroup] travels with it so the filter can answer "the whole group"
     * on its own — see [PostFilter]. Switching group drops the tags ticked
     * inside the last one: they answered a question nobody is asking now.
     */
    fun filterByGroup(groupId: String?, tagsInGroup: List<String>) {
        val chosen = if (groupId == _tagFilter.value.group) null else groupId
        _tagFilter.value =
            // Only the About half is rebuilt: which rooms the reader is
            // looking at is a different question and they did not touch it.
            if (chosen == null) _tagFilter.value.copy(group = null, groupTags = emptyList(), tags = emptySet())
            else _tagFilter.value.copy(group = chosen, groupTags = tagsInGroup, tags = emptySet())
        forgetTheBottom()
    }

    /**
     * Ticking a tag inside the open group, or unticking it.
     *
     * Capped at [TagRules.MAX_IN_FILTER]. Past the cap a tap does nothing
     * rather than dropping the oldest choice to make room: a filter that
     * changes something you did not touch is one you stop trusting.
     */
    fun toggleFilterTag(tagId: String) {
        val current = _tagFilter.value
        val next = when {
            tagId in current.tags -> current.tags - tagId
            current.tags.size >= TagRules.MAX_IN_FILTER -> return
            else -> current.tags + tagId
        }
        _tagFilter.value = current.copy(tags = next)
        forgetTheBottom()
    }

    /**
     * Which rooms the feed is showing, or all of them.
     *
     * Multi-select and an OR: two groups means anything either is
     * carrying. Unticking the last one is "Everyone" again — there is no
     * separate state for it, because an empty set already means the question
     * is not being asked.
     */
    fun toggleGroup(groupId: String) {
        val current = _tagFilter.value
        _tagFilter.value = current.copy(
            groups = if (groupId in current.groups) {
                current.groups - groupId
            } else {
                current.groups + groupId
            },
        )
        forgetTheBottom()
    }

    /** Everyone: the From half cleared, the About half left alone. */
    fun showEveryGroup() {
        if (_tagFilter.value.groups.isEmpty()) return
        _tagFilter.value = _tagFilter.value.copy(groups = emptySet())
        forgetTheBottom()
    }

    /** Back to the whole feed. */
    /** Free text over the feed; the server gets it too when older pages are fetched. */
    fun search(query: String) {
        if (_tagFilter.value.query == query) return
        _tagFilter.value = _tagFilter.value.copy(query = query)
        forgetTheBottom()
    }

    fun clearFilter() {
        _tagFilter.value = PostFilter()
        forgetTheBottom()
    }

    /**
     * A filter is a different question, so what was learned about the bottom of
     * the last one says nothing about this one. What still holds is the whole
     * feed: if its last page came back short there is nothing older to fetch
     * under any filter.
     */
    private fun forgetTheBottom() {
        _moreToLoad.value = feedPageWasFull
    }

    /**
     * Whether anybody has been signed in since this screen's state was built.
     * What tells a sign-out apart from a session that was never restored.
     */
    private var hadUser = false

    private var myPostsJob: Job? = null

    init {
        // Whatever this device stored last, on screen before any request is made.
        repository.posts?.let { stored ->
            scope.launch {
                stored.collect { fromDisk ->
                    // Every other screen sees it immediately.
                    _posts.value = fromDisk
                    if (_feed.value.isEmpty() || !settled) {
                        // Nothing on screen to disturb: the first paint, or a
                        // cold start with no network yet.
                        _feed.value = fromDisk
                        clearPending()
                    } else if (fromDisk != _feed.value) {
                        adoptOrOffer(fromDisk)
                    }
                }
            }
        }
        scope.launch {
            // Keyed on identity, not the whole User: a profile edit (e.g. the
            // name-visibility toggle) emits a new User with the same guid, and
            // this block used to treat that as a fresh sign-in — re-subscribing
            // and re-fetching, which flashed the lists empty. Only a real
            // sign-in/out/switch (guid change) should reload.
            session.user.distinctUntilChangedBy { it?.guid }.collectLatest { user ->
                if (user != null) {
                    // One collector at a time. Signing in again would otherwise
                    // leave the previous one running against the same rows.
                    myPostsJob?.cancel()
                    myPostsJob = repository.myPosts(user.guid)?.let { stored ->
                        scope.launch { stored.collect { _myPosts.value = it } }
                    }
                }
                if (user == null) {
                    // The cached feed belonged to whoever was signed in.
                    _posts.value = emptyList()
                    _feed.value = emptyList()
                    _myPosts.value = emptyList()
                    settled = false
                    _tagFilter.value = PostFilter()
                    clearPending()
                    // Emptying the device is what signing out means. It is not
                    // what "no user yet" means, and those look identical from
                    // here: a session still being restored reads as no user,
                    // and so does one the server could not be reached to
                    // confirm. Clearing on either threw away everything
                    // somebody had — offline, at the one moment nothing could
                    // be fetched back.
                    //
                    // Nobody can see those rows in the meantime: the app shows
                    // the login screen until somebody is signed in.
                    if (hadUser) repository.clearLocalPosts()
                } else {
                    hadUser = true
                    loadPosts()
                }
            }
        }
    }

    /** Fetches in the background. What it finds is offered, not imposed. */
    fun loadPosts() {
        scope.launch {
            if (repository.posts != null) {
                // Your own posts come from their own request. The feed is a
                // page and yours can sit outside it — My Posts reads these
                // rows, and a post missing from there reads as lost.
                val viewer = session.user.value?.guid
                if (viewer != null) repository.refreshMyPosts(viewer)
                repository.refreshPosts(viewer.orEmpty())
                    .onSuccess { fresh ->
                        notePage(fresh, filtered = false)
                        if (!settled) { settled = true; show(fresh) }
                    }
                    .onFailure {
                        // Offline is not worth interrupting anyone for — what is
                        // on screen is still true, only older. A refresh that
                        // fails for any other reason is a bug, and staying quiet
                        // about it is how the feed froze for a day without
                        // anyone being able to tell.
                        if (_feed.value.isEmpty() || it !is IOException) {
                            _error.value = UiError("Unable to load posts", it)
                        }
                    }
            } else {
                repository.getAllPosts()
                    .onSuccess { _moreToLoad.value = false; show(it) }
                    .onFailure { _error.value = UiError("Unable to load posts", it) }
            }
        }
    }

    /**
     * A pull, or any deliberate act that changes what a person should see —
     * joining or leaving a group. Fetches and shows what comes back: being
     * asked to tap a button after pulling for fresh content would be absurd,
     * and posts from a group you just left should go without being
     * offered.
     */
    fun refresh() {
        scope.launch {
            _isRefreshing.value = true
            try {
                if (repository.posts != null) {
                    val viewer = session.user.value?.guid
                    if (viewer != null) repository.refreshMyPosts(viewer)
                    // Shows what the fetch returned rather than waiting for the
                    // database to notify: showPending() before the flow emits
                    // has nothing to show, and the change then arrives as the
                    // very button this pull was meant to skip.
                    repository.refreshPosts(viewer.orEmpty())
                        .onSuccess { fresh ->
                            settled = true
                            // A new page from the top means whatever was known
                            // about the bottom no longer holds.
                            notePage(fresh, filtered = false)
                            show(fresh)
                        }
                        .onFailure {
                            events?.report(AppEventName.FEED_LOAD_FAILED, AppEventSeverity.WARN)
                            _error.value = UiError("Unable to load posts", it)
                        }
                } else {
                    repository.getAllPosts()
                        .onSuccess { _moreToLoad.value = false; show(it) }
                        .onFailure {
                            events?.report(AppEventName.FEED_LOAD_FAILED, AppEventSeverity.WARN)
                            _error.value = UiError("Unable to load posts", it)
                        }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** The reader asked for the newer list. */
    fun showPending() {
        _pending.value?.let { _feed.value = it }
        clearPending()
    }

    /**
     * True when the last attempt to write was refused for want of a confirmed
     * address. Its own state rather than an error, because it is the one
     * failure with something to do about it.
     */
    private val _needsVerifiedEmail = MutableStateFlow(false)
    val needsVerifiedEmail: StateFlow<Boolean> = _needsVerifiedEmail.asStateFlow()

    /**
     * Whether asking for more is worth offering.
     *
     * False once a page comes back with nothing in it: the end of the feed, and
     * a button that fetches nothing is worse than no button.
     */
    private val _moreToLoad = MutableStateFlow(true)
    val moreToLoad: StateFlow<Boolean> = _moreToLoad.asStateFlow()

    /** Whether the last unfiltered page filled up. See [filterByGroup]. */
    private var feedPageWasFull = true

    /**
     * A page the server did not fill is the bottom of the feed.
     *
     * Waiting for an empty one instead meant the button was offered on every
     * feed shorter than a page, and pressing it fetched nothing: the first
     * honest answer about the bottom arrived only after somebody had asked.
     */
    private fun notePage(page: List<Post>, filtered: Boolean) {
        val full = page.size >= PAGE_SIZE
        if (!filtered) feedPageWasFull = full
        _moreToLoad.value = full
    }

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    /**
     * Fetches the page below what is on screen.
     *
     * Applied at once rather than offered behind the "new posts" button: the
     * reader asked for these, they arrive underneath rather than on top, and
     * nothing they are looking at moves.
     */
    fun loadOlder() {
        val filter = _tagFilter.value
        val tags = filter.effectiveTags
        // Resume from the oldest post being shown, which under a filter is the
        // oldest one that survives it. Asking from the oldest of the whole feed
        // would fetch the same posts again and look like nothing happened.
        val shown = _feed.value.fromGroups(filter.groups).withTags(tags).matching(filter.query)
        val oldest = shown.minByOrNull { it.date } ?: return
        if (_loadingMore.value) return
        scope.launch {
            _loadingMore.value = true
            try {
                repository.loadOlderPosts(
                    oldest.date.toString(),
                    oldest.guid,
                    tags.toList(),
                    filter.groups.toList(),
                    filter.query,
                )
                    .onSuccess { older ->
                        notePage(older, filtered = !filter.isEmpty)
                        // What arrived is in the store, and the store's flow
                        // brings it here. Applying it directly as well would
                        // show the same posts twice for an instant.
                    }
                    .onFailure { _error.value = UiError("Unable to load older posts", it) }
            } finally {
                _loadingMore.value = false
            }
        }
    }

    fun acknowledgeVerificationNeeded() {
        _needsVerifiedEmail.value = false
    }

    /** [newImage]: a picked picture to upload first, or null to leave [Post.image] as it is. */
    fun addPost(post: Post, newImage: ByteArray? = null) {
        scope.launch {
            repository.addPost(post, newImage)
                .onSuccess { show(it) }
                .onFailure { cause ->
                    if (cause is EmailNotVerified) _needsVerifiedEmail.value = true
                    else _error.value = UiError("Unable to add post", cause)
                }
        }
    }

    fun updatePost(post: Post, newImage: ByteArray? = null) {
        scope.launch {
            repository.updatePost(post, newImage)
                .onSuccess { show(it) }
                .onFailure { _error.value = UiError("Unable to update post", it) }
        }
    }

    fun completePost(post: Post, message: String?): Job = scope.launch {
        repository.completePost(post.guid, message)
            .onSuccess { show(it) }
            .onFailure { _error.value = UiError("Unable to complete post", it) }
    }

    fun reopenPost(post: Post): Job = scope.launch {
        repository.reopenPost(post.guid)
            .onSuccess { show(it) }
            .onFailure { _error.value = UiError("Unable to reopen post", it) }
    }

    fun deletePost(post: Post): Job = scope.launch {
        repository.deletePost(post)
            .onSuccess { show(it) }
            .onFailure { _error.value = UiError("Unable to delete post", it) }
    }

    /** Reporting is fire and forget from here: the screen says thank you either way. */
    fun reportPost(postId: String, reason: String? = null): Job = scope.launch {
        repository.reportPost(postId, reason)
            .onFailure { _error.value = UiError("Unable to send that report", it) }
    }

    fun acknowledgeError() {
        _error.value = null
    }

    /** Your own change: on screen straight away, with nothing left waiting. */
    private fun show(posts: List<Post>) {
        _posts.value = posts
        _feed.value = posts
        clearPending()
    }

    private fun adoptOrOffer(fresh: List<Post>) {
        // Decided on what Home will render, not on the page the server sent.
        // The feed itself keeps everything; the screen does the hiding, so the
        // count has to look through the same rule or it announces posts that
        // are never shown. See asFeedShows.
        val update = decideFeedUpdate(
            onScreen = _feed.value,
            fresh = fresh,
            shown = {
                it.asFeedShows(
                    viewer = session.user.value?.guid,
                    now = Clock.System.now(),
                    filter = _tagFilter.value,
                )
            },
        )
        when (update) {
            // Adopting fresh content settles the feed, so any older offer is
            // spent — clear it, or its stale count outlives the posts it named.
            is FeedUpdate.Apply -> {
                _feed.value = update.posts
                _pending.value = null
            }
            // The count is derived from this against the live feed (see
            // [pendingCount]); the arrived number the rule computed is not stored.
            is FeedUpdate.Hold -> _pending.value = update.posts
        }
    }

    private fun clearPending() {
        _pending.value = null
    }
}
