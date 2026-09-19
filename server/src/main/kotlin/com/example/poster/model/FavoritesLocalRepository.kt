package com.example.poster.model

import com.example.poster.config.Features
import com.example.poster.db.DatabaseManager
import com.example.poster.db.DatabaseDriverFactory
import kotlinx.datetime.LocalDateTime

class FavoritesLocalRepository(
    private val tagRepository: TagLocalRepository = TagLocalRepository(),
) : FavoritesRepository {
    private val databaseManager = DatabaseManager(DatabaseDriverFactory())
    private val database = databaseManager.getDatabase()
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
                isFavorite = true
            )
        }
    }

    override fun isPostFavorite(userId: String, postId: String): Boolean {
        return favoriteQueries.isPostFavorite(userId, postId).executeAsOne()
    }

    override fun addFavoritePost(userId: String, postId: String) {
        // Stamped now so the detail screen can say "added N days ago".
        favoriteQueries.addFavoritePost(userId, postId, java.time.Instant.now().toString())
    }

    override fun likers(postId: String): LikerList {
        val rows = favoriteQueries.getPostLikers(postId).executeAsList()
        val named = rows
            // With names on every post (feature.authors) the opt-in is moot.
            .filter { Features.AUTHORS || it.show_name != 0L }
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

    override fun getUsersWhoFavoritedPost(postId: String): List<String> {
        return favoriteQueries.getUsersWhoFavoritedPost(postId).executeAsList()
    }

    override fun countPostFavorites(postId: String): Long {
        return favoriteQueries.countPostFavorites(postId).executeAsOne()
    }
}