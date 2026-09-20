package com.example.poster.navigation

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object MyPosts : Screen("my_posts")
    object Favorites : Screen("favorites")
    object Settings : Screen("settings")
} 