package com.example.poster.navigation

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Details : Screen("details/{postId}") {
        fun createRoute(postId: String) = "details/$postId"
        const val ARG_POST_ID = "postId"
    }
    object MyPosts : Screen("my_posts")
    object Favorites : Screen("favorites")
    object Settings : Screen("settings")
} 