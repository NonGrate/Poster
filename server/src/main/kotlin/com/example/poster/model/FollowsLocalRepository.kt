package com.example.poster.model

import com.example.poster.PostDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Who follows whom (feature.follows). Thin over the queries; the feed reads the table itself. */
// No default database, for the reason given on PostsLocalRepository.
class FollowsLocalRepository(database: PostDatabase) {
    private val queries = database.followQueries

    fun follow(follower: String, followed: String) =
        queries.follow(follower, followed, Clock.System.now().toLocalDateTime(TimeZone.UTC).toString())

    fun unfollow(follower: String, followed: String) = queries.unfollow(follower, followed)

    fun following(follower: String): List<String> = queries.following(follower).executeAsList()
}
