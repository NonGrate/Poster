package com.example.poster.model

import com.example.poster.PostDatabase
import com.example.poster.config.Features
import kotlinx.datetime.LocalDateTime

// No default database, for the reason given on PostsLocalRepository.
class FavoritesLocalRepository(
    database: PostDatabase,
    private val tagRepository: TagLocalRepository = TagLocalRepository(database),
) : FavoritesRepository {
    private val favoriteQueries = database.userPostFavoriteQueries

    override fun getUserFavoritePosts(userId: String): List<Post> {
        return favoriteQueries.getUserFavoritePosts(userId).executeAsList().map {
            Post(
                guid = it.guid,
                title = it.title,
                message = it.message,
                author = it.author,
                group = it.groupId,
                image = it.image,
                likes = it.likes.toInt(),
                date = LocalDateTime.parse(it.date),
                tags = tagRepository.getTagsForPost(it.guid).map { tag -> tag.name },
                // Without these, a post someone has liked could be
                // answered and they would never see it here — which is the one
                // place they are most likely to look.
                completedAt = it.completed_at?.let { at -> LocalDateTime.parse(at) },
                completionMessage = it.completion_message,
                visibility = it.visibility,
                language = it.language,
            )
        }
    }

    override fun isPostFavorite(userId: String, postId: String): Boolean {
        return favoriteQueries.isPostFavorite(userId, postId).executeAsOne()
    }

    override fun addFavoritePost(userId: String, postId: String): Boolean {
        // The insert ignores a row that is already there, so "did anything
        // change" has to be asked rather than assumed. It decides whether the
        // author is told: without it, liking the same post in a loop sent a
        // notification and a push every time while changing nothing.
        return favoriteQueries.transactionWithResult {
            if (favoriteQueries.isPostFavorite(userId, postId).executeAsOne()) {
                false
            } else {
                // Stamped now so the detail screen can say "added N days ago".
                favoriteQueries.addFavoritePost(userId, postId, java.time.Instant.now().toString())
                true
            }
        }
    }

    override fun likers(postId: String): LikerList {
        val rows = favoriteQueries.getPostLikers(postId).executeAsList()
        val named = rows
            // The person's own choice, always. It used to be skipped when
            // feature.authors was on, on the reasoning that names are already
            // on posts — but liking is not posting, the switch is offered at
            // sign-up and in Settings as though it were live, and somebody who
            // turns it off and then likes a post about something difficult
            // meant it.
            .filter { it.show_name != 0L }
            .map { row ->
                // First name + surname initial ("Daniel P.") — enough to
                // recognise a friend without publishing a full name on a list.
                val initial = row.surname.trim().firstOrNull()?.let { " $it." } ?: ""
                Liker(name = row.name.trim() + initial, date = row.created_at)
            }
        return LikerList(named = named, total = rows.size)
    }

    override fun removeFavoritePost(userId: String, postId: String): Boolean {
        val isFavorite = isPostFavorite(userId, postId)
        if (isFavorite) {
            favoriteQueries.removeFavoritePost(userId, postId)
            return true
        }
        return false
    }

    override fun countPostFavorites(postId: String): Long {
        return favoriteQueries.countPostFavorites(postId).executeAsOne()
    }
}