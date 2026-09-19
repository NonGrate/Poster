package com.example.poster.repository

import com.example.poster.cache.PostCache
import com.example.poster.domain.validation.ImageRules
import com.example.poster.model.Post
import com.example.poster.model.User
import com.example.poster.network.PostApi
import com.example.poster.network.UserApi
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Repository layer for posts, favorites, and users.
 * Holds in-memory cache and synchronizes with API.
 */
/**
 * How many posts arrive at once.
 *
 * Enough that a family never asks for a second page, few enough that a
 * neighbourhood's first screen is not a download.
 */
const val PAGE_SIZE = 50

class PostRepository(
    private val postApi: PostApi,
    private val userApi: UserApi,
    private val cache: PostCache,
    private val dispatchers: DispatcherProvider,
    private val localStore: PostLocalStore? = null,
) {

    /**
     * The feed, from disk. Emits what this device last stored before any request
     * is made, which is what a cold start and a bad connection both need.
     *
     * Null [localStore] means no database — previews and unit tests — and then
     * there is nothing to read from, so callers fall back to [getAllPosts].
     */
    val posts: Flow<List<Post>>? get() = localStore?.posts()

    /**
     * Ask the server and write what it says to disk. Returns whether the request
     * worked, not the data: the data arrives through [posts].
     */
    /** Everything this person wrote, from disk. Null without a database. */
    fun myPosts(userId: String): Flow<List<Post>>? = localStore?.mine(userId)

    /** What this person has liked, from disk. Null without a database. */
    fun favorites(userId: String): Flow<List<Post>>? = localStore?.favorites(userId)

    /** Asks the server what this person has liked and writes it to disk. */
    suspend fun refreshFavorites(userId: String): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val fresh = postApi.getFavoritePosts()
            localStore?.replaceFavorites(userId, fresh)
            cache.setFavoritePosts(fresh)
        }
    }

    /** Passed straight through: nothing local changes when somebody reports. */
    suspend fun reportPost(postId: String, reason: String?): Result<Unit> =
        withContext(dispatchers.io) { runCatching { postApi.reportPost(postId, reason) } }

    /** Empties the device's copy of the feed. See [PostLocalStore.clear]. */
    suspend fun clearLocalPosts() {
        localStore?.clear()
        cache.clear()
    }

    suspend fun refreshPosts(viewer: String = ""): Result<List<Post>> = withContext(dispatchers.io) {
        runCatching {
            val fresh = postApi.getPostPage(limit = PAGE_SIZE)
            // The feed aggregates everyone's posts, so an empty page means the
            // request came back wrong, not that there is genuinely nothing to
            // show — blanking a feed that had posts is worse than showing them
            // a little stale. Keep what is on disk. (My Posts and Favourites
            // may legitimately be empty and are refreshed as-is.)
            if (fresh.isEmpty()) {
                return@runCatching localStore?.posts()?.first() ?: emptyList()
            }
            localStore?.replaceAll(fresh, viewer)
            cache.setAllPosts(fresh)
            fresh
        }
    }

    /**
     * Asks the server for everything this person wrote and writes it to disk.
     *
     * Separate from [refreshPosts] because the feed is a page: a post of
     * yours older than that page is not gone, and My Posts reads the same
     * rows the feed does. Returns whether it worked; the posts arrive through
     * [posts].
     */
    suspend fun refreshMyPosts(userId: String): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            localStore?.replaceMine(userId, postApi.getMyPosts())
            Unit
        }
    }

    /**
     * The page of posts older than [beforeDate]/[beforeGuid].
     *
     * Added to what is on the device rather than replacing it, so reaching the
     * bottom of the feed does not throw away the top of it. Returns what
     * arrived, so the caller can tell an empty page — the end of the feed —
     * from a full one.
     *
     * [tags] and [groups] ask the server for the next page that survives
     * the filter — narrowing here rather than on the device is what makes an
     * older post reachable at all, since the device only holds the pages it
     * has already fetched. Appending is what makes a filter cheap to change
     * your mind about: clearing it refetches nothing, because nothing was
     * thrown away to apply it.
     */
    suspend fun loadOlderPosts(
        beforeDate: String,
        beforeGuid: String,
        tags: List<String> = emptyList(),
        groups: List<String> = emptyList(),
        query: String = "",
        following: Boolean = false,
        saved: Boolean = false,
    ): Result<List<Post>> = withContext(dispatchers.io) {
        runCatching {
            val older = postApi.getPostPage(
                limit = PAGE_SIZE,
                beforeDate = beforeDate,
                beforeGuid = beforeGuid,
                tags = tags,
                groups = groups,
                query = query,
                following = following,
                saved = saved,
            )
            localStore?.appendPage(older)
            // The cache is what answers "how many people have liked this",
            // and it only knew the pages fetched so far. A post it has never
            // seen cannot be counted: liking one from an older page left
            // the number on screen at whatever it was before.
            //
            // Added rather than replaced — the top of the feed is still on
            // screen — and deduplicated, because the same page arriving twice
            // would otherwise count everything on it twice. What is already
            // cached wins: it may carry a toggle this device has made and the
            // server has not yet been asked about.
            cache.setAllPosts((cache.getAllPosts() + older).distinctBy { it.guid })
            older
        }
    }

    data class FavoritesSnapshot(
        val favoritePosts: List<Post>,
        val favoriteIds: Set<String>,
        val favoriteCounts: Map<String, Int>
    )

    suspend fun getAllPosts(): Result<List<Post>> = withContext(dispatchers.io) {
        runCatching {
            val cached = cache.getAllPosts()
            if (cached.isNotEmpty()) {
                cached
            } else {
                val posts = postApi.getAllPosts()
                cache.setAllPosts(posts)
                posts
            }
        }
    }

    suspend fun getPostsByGroup(groupId: String): Result<List<Post>> =
        getAllPosts().mapCatching { posts ->
            posts.filter { it.group == groupId }
        }

    suspend fun getFavoritePosts(): Result<FavoritesSnapshot> = withContext(dispatchers.io) {
        runCatching {
            val cached = cache.getFavoritePosts()
            if (cached.isNotEmpty()) {
                FavoritesSnapshot(
                    favoritePosts = cached,
                    favoriteIds = cache.getFavoritePostIds(),
                    favoriteCounts = cache.getPostLikeCounts()
                )
            } else {
                val favorites = postApi.getFavoritePosts()
                cache.setFavoritePosts(favorites)
                FavoritesSnapshot(
                    favoritePosts = favorites,
                    favoriteIds = cache.getFavoritePostIds(),
                    favoriteCounts = cache.getPostLikeCounts()
                )
            }
        }
    }

    /**
     * After a write, the server is re-read for the truth and both stores updated.
     *
     * They are not the same size on purpose. The cache keeps everything the
     * refetch returned — group screens and like counts see past the feed's
     * page — but the feed table, exactly like [refreshPosts], holds only the top
     * [PAGE_SIZE]. `getAllPosts` returns up to the server's larger cap, so
     * writing all of it into the feed put posts from beyond the page into it
     * (`in_feed = 1`); the "new posts" button then offered those older posts
     * as new, and a paginated refresh kept dropping and re-adding them. Returns
     * the page, so the feed on screen and the feed on disk are the same list.
     */
    private suspend fun adoptRefetched(all: List<Post>): List<Post> {
        cache.setAllPosts(all)
        val page = all.take(PAGE_SIZE)
        localStore?.replaceAll(page)
        return page
    }

    /**
     * [newImage] is uploaded first and the post saved pointing at it; a post
     * whose image failed to upload is not saved at all, rather than saved
     * without the picture the person chose.
     */
    suspend fun addPost(post: Post, newImage: ByteArray? = null): Result<List<Post>> = withContext(dispatchers.io) {
        val previous = cache.getAllPosts()
        cache.setAllPosts(previous + post)
        runCatching {
            postApi.addPost(post.withUploaded(newImage))
            adoptRefetched(postApi.getAllPosts())
        }.onFailure {
            cache.setAllPosts(previous)
        }
    }

    suspend fun updatePost(post: Post, newImage: ByteArray? = null): Result<List<Post>> = withContext(dispatchers.io) {
        val previous = cache.getAllPosts()
        cache.updatePost(post)
        runCatching {
            postApi.updatePost(post.withUploaded(newImage))
            adoptRefetched(postApi.getAllPosts())
        }.onFailure {
            cache.setAllPosts(previous)
        }
    }

    suspend fun addFavorite(userId: String, postId: String): Result<FavoritesSnapshot> =
        withContext(dispatchers.io) {
            val previousAll = cache.getAllPosts()
            val previousFavorites = cache.getFavoritePosts()
            val previousIds = cache.getFavoritePostIds()
            val previousCounts = cache.getPostLikeCounts()

            cache.addFavorite(postId)
            localStore?.addFavorite(userId, postId)
            runCatching {
                postApi.addFavorite(userId, postId)
                FavoritesSnapshot(
                    favoritePosts = cache.getFavoritePosts(),
                    favoriteIds = cache.getFavoritePostIds(),
                    favoriteCounts = cache.getPostLikeCounts()
                )
            }.onFailure {
                cache.setAllPosts(previousAll)
                cache.setFavoritePosts(previousFavorites)
                cache.setFavoritePostIds(previousIds)
                cache.setPostLikeCounts(previousCounts)
                // Undo exactly what was done, rather than rewriting the table
                // from the cache. The count has to come back off with the row —
                // it was put on the moment the tap happened — and a device that
                // reads from disk may have a cache that was never filled, in
                // which case restoring it wholesale would empty the liked-by list
                // because one write was refused.
                localStore?.removeFavorite(userId, postId)
            }
        }

    suspend fun removeFavorite(userId: String, postId: String): Result<FavoritesSnapshot> =
        withContext(dispatchers.io) {
            val previousAll = cache.getAllPosts()
            val previousFavorites = cache.getFavoritePosts()
            val previousIds = cache.getFavoritePostIds()
            val previousCounts = cache.getPostLikeCounts()

            cache.removeFavorite(postId)
            localStore?.removeFavorite(userId, postId)
            runCatching {
                postApi.removeFavorite(userId, postId)
                FavoritesSnapshot(
                    favoritePosts = cache.getFavoritePosts(),
                    favoriteIds = cache.getFavoritePostIds(),
                    favoriteCounts = cache.getPostLikeCounts()
                )
            }.onFailure {
                cache.setAllPosts(previousAll)
                cache.setFavoritePosts(previousFavorites)
                cache.setFavoritePostIds(previousIds)
                cache.setPostLikeCounts(previousCounts)
                // The mirror of the rollback above: the row goes back, and the
                // count with it.
                localStore?.addFavorite(userId, postId)
            }
        }

    suspend fun completePost(postId: String, message: String?): Result<List<Post>> =
        withContext(dispatchers.io) {
            runCatching {
                postApi.completePost(postId, message)
                adoptRefetched(postApi.getAllPosts())
            }
        }

    suspend fun reopenPost(postId: String): Result<List<Post>> =
        withContext(dispatchers.io) {
            runCatching {
                postApi.reopenPost(postId)
                adoptRefetched(postApi.getAllPosts())
            }
        }

    suspend fun deletePost(post: Post): Result<List<Post>> = withContext(dispatchers.io) {
        val previous = cache.getAllPosts()
        val updated = previous.filter { it.guid != post.guid }
        cache.setAllPosts(updated)
        runCatching {
            postApi.removePost(post)
            adoptRefetched(postApi.getAllPosts())
        }.onFailure {
            cache.setAllPosts(previous)
        }
    }

    suspend fun getUser(userId: String): Result<User?> = withContext(dispatchers.io) {
        runCatching { userApi.logIn(userId) }
    }


    suspend fun clearCache() = cache.clear()

    private suspend fun Post.withUploaded(bytes: ByteArray?): Post {
        if (bytes == null) return this
        val extension = ImageRules.extensionOf(bytes) ?: error("not a JPEG, PNG or WebP")
        check(!ImageRules.tooLarge(bytes)) { "image larger than ${ImageRules.MAX_BYTES} bytes" }
        return copy(image = postApi.uploadImage(bytes, extension))
    }
}
